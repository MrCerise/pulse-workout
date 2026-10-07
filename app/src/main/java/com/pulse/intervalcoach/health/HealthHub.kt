package com.pulse.intervalcoach.health

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Activity
import androidx.health.connect.client.records.Device
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WorkoutSegment
import androidx.health.connect.client.records.WorkoutSession
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.QueryRecordsRequest
import androidx.health.connect.client.request.TimeRangeFilter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.SessionConfiguration
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionInsertRequest
import com.google.android.gms.fitness.request.SessionUpdateRequest
import com.google.android.gms.tasks.Tasks
import com.pulse.intervalcoach.data.PreferencesRepository
import com.pulse.intervalcoach.data.db.SessionEntity
import com.pulse.intervalcoach.data.db.SessionEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * PULSE ↔ health-platform bridge.
 *
 * One hub, two backends, zero assumptions:
 *
 *  - **Health Connect** (the platform Google recommends) is the primary backend. It needs the
 *    Health Connect app on the device (preinstalled on Android 14+ Pixels, otherwise a Play
 *    update). Every call degrades to `null` when the platform, an app or a permission is missing,
 *    so the rest of the app never has to branch on health availability.
 *  - **Google Fit** (Google Play services) is the fallback / companion backend for devices where
 *    Health Connect is not present. It covers reading heart rate and steps and writing finished
 *    sessions. Google is phasing the Fit platform out, so the UI presents Health Connect first;
 *    Fit never masks a working Health Connect setup.
 *
 * Nothing here is a stub: every feature checks its real preconditions at runtime and reports
 * them honestly.
 */

// --- Health Connect permission strings (manifest + requestPermission share these) ------------

internal const val HC_PERMISSION_HEART_RATE_READ = "android.permission.health.READ_HEART_RATE"
internal const val HC_PERMISSION_STEPS_READ = "android.permission.health.READ_STEPS"
internal const val HC_PERMISSION_ACTIVITY_READ = "android.permission.health.READ_ACTIVITY"
internal const val HC_PERMISSION_WORKOUT_READ = "android.permission.health.READ_WORKOUT"
internal const val HC_PERMISSION_WORKOUT_WRITE = "android.permission.health.WRITE_WORKOUT"

// Health Connect 1.1.0 workout type constants. Session type 1 = "a single exercise";
// segment type 1 = "exercise". PULSE writes rest intervals as exercise-typed segments
// (their timing is exact; the subtype is cosmetic metadata in the Health app).
private const val HC_SESSION_TYPE_EXERCISE = 1
private const val HC_SEGMENT_TYPE_EXERCISE = 1

private const val MAX_SEGMENTS_PER_SESSION = 40

private val DEVICE = Device(name = "PULSE", make = "PULSE", model = "Interval Coach")

// --- Public state -----------------------------------------------------------------------------

/** What the Health Connect platform itself reports about availability on this device. */
enum class HcAvailability {
    AVAILABLE,
    NOT_INSTALLED,
    NOT_ENABLED,
    NO_PROVIDER,
    NOT_SUPPORTED,
    UNKNOWN,
}

/** Whether Google Fit (Play services + developer configuration) can be used. */
enum class FitAvailability {
    AVAILABLE,
    PLAY_SERVICES_MISSING,
    NOT_CONFIGURED,
}

/** The runtime state of the three data PULSE cares about on the connected backend. */
data class HealthPermissions(
    val heartRateRead: Boolean = false,
    val stepsRead: Boolean = false,
    val workoutWrite: Boolean = false,
) {
    val anyReadGranted: Boolean get() = heartRateRead || stepsRead
}

/** Average / peak heart rate for one window, from whatever backend has the data. */
data class HrStats(val average: Int, val max: Int, val sampleCount: Int)

/** What the dashboards show: today's steps, the newest heart-rate reading and the sync state. */
data class HealthSnapshot(
    val stepsToday: Long? = null,
    val latestHeartRate: Int? = null,
    val source: String? = null,
    val lastSyncAt: Long = 0L,
)

/**
 * Bridge so [HealthHub] (an `Application`-scoped object) can run the Google Fit authorisation
 * flow, whose result always lands in the *activity*'s `onActivityResult`.
 * [MainActivity] installs the launcher once on startup.
 */
object FitAuthBridge {
    var launcher: ((Activity, FitnessOptions, (Boolean) -> Unit) -> Unit)? = null
}

/** Approximate heart-rate zones used for the live display (no age input required). */
object HrZones {
    fun zone(bpm: Int): String = when {
        bpm < 90 -> "Z1 · Recovery"
        bpm < 115 -> "Z2 · Easy"
        bpm < 140 -> "Z3 · Tempo"
        bpm < 165 -> "Z4 · Hard"
        else -> "Z5 · Max"
    }
}

// --- The hub ------------------------------------------------------------------------------------

class HealthHub(
    private val context: Context,
    private val prefs: PreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tag = "HealthHub"

    private var hcClient: HealthConnectClient? = null

    private val _availability = MutableStateFlow(HcAvailability.UNKNOWN)
    val availability: StateFlow<HcAvailability> = _availability.asStateFlow()

    private val _permissions = MutableStateFlow(HealthPermissions())
    val permissions: StateFlow<HealthPermissions> = _permissions.asStateFlow()

    private val _fitAvailability = MutableStateFlow(FitAvailability.NOT_CONFIGURED)
    val fitAvailability: StateFlow<FitAvailability> = _fitAvailability.asStateFlow()

    private val _fitAuthorized = MutableStateFlow(false)
    val fitAuthorized: StateFlow<Boolean> = _fitAuthorized.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(0L)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private val _snapshot = MutableStateFlow(HealthSnapshot())
    val snapshot: StateFlow<HealthSnapshot> = _snapshot.asStateFlow()

    private val fitOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .build()

    /** Re-reads every platform state and the dashboard snapshot. Safe to call any time. */
    fun refresh() {
        scope.launch {
            _availability.value = healthConnectStatus()
            _permissions.value = currentPermissions()
            _fitAvailability.value = fitPlatformStatus()
            _lastSyncAt.value = runCatching { prefs.current().healthLastSyncAt }.getOrDefault(0L)
            _fitAuthorized.value = runCatching { prefs.current().fitAuthorized }.getOrDefault(false)
            _snapshot.value = buildSnapshot()
        }
    }

    /**
     * Asks the system for the Health Connect permissions PULSE uses. Returns whether the dialog
     * (or the existing grant) resulted in at least one read permission.
     */
    suspend fun requestPermissions(): Boolean {
        val requested = listOf(HC_PERMISSION_HEART_RATE_READ, HC_PERMISSION_STEPS_READ, HC_PERMISSION_WORKOUT_WRITE)
        val result = withContext(Dispatchers.Main) {
            runCatching { HealthConnectClient.requestPermission(requested) }.getOrNull()
        } ?: return false
        result.onSuccess { refresh() }
        return _permissions.value.anyReadGranted
    }

    /** Starts the Google Fit authorisation dialog (must be called from the UI thread). */
    fun requestFitAuthorization(activity: Activity) {
        val launcher = FitAuthBridge.launcher
        if (launcher == null) {
            Log.w(tag, "Fit authorisation bridge not installed by the activity")
            return
        }
        launcher(activity, fitOptions) { granted ->
            scope.launch {
                if (granted) {
                    _fitAuthorized.value = true
                    runCatching { prefs.setFitAuthorized(true) }
                    refresh()
                }
            }
        }
    }

    // --- Reads ---------------------------------------------------------------------------------

    /** Newest heart-rate reading within the last few minutes, or null when nothing is available. */
    suspend fun latestHeartRate(): Int? {
        healthConnectLatestHeartRate()?.let { return it }
        return fitLatestHeartRate()
    }

    /** Average / peak heart rate over one window (a finished session). Null without data. */
    suspend fun heartRateStats(startMillis: Long, endMillis: Long): HrStats? {
        if (endMillis <= startMillis) return null
        healthConnectHrStats(startMillis, endMillis)?.let { return it }
        return fitHrStats(startMillis, endMillis)
    }

    /** Total steps recorded today, from whichever backend is connected. Null when none. */
    suspend fun stepsToday(): Long? {
        healthConnectStepsToday()?.let { return it }
        return fitStepsToday()
    }

    // --- Writes --------------------------------------------------------------------------------

    /**
     * Writes a finished session to Health Connect (workout session + interval segments + a heart
     * rate record when HR read permission is granted). Returns true on success. Idempotent per
     * session id: the record ids are derived from the session id, so re-syncing replaces instead
     * of duplicating.
     */
    suspend fun writeSession(session: SessionEntity, events: List<SessionEventEntity>): Boolean {
        if (healthConnectAvailable()) {
            return writeSessionToHealthConnect(session, events)
        }
        return writeSessionToFit(session)
    }

    /** Marks a sync as successful (also persisted so the "last sync" label survives restarts). */
    fun markSynced() {
        val now = System.currentTimeMillis()
        _lastSyncAt.value = now
        scope.launch { runCatching { prefs.setHealthLastSyncAt(now) } }
    }

    // --- Health Connect internals --------------------------------------------------------------

    private suspend fun healthConnectStatus(): HcAvailability = withContext(Dispatchers.Main) {
        runCatching {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SdkStatus.AVAILABLE -> HcAvailability.AVAILABLE
                HealthConnectClient.SdkStatus.NOT_INSTALLED -> HcAvailability.NOT_INSTALLED
                HealthConnectClient.SdkStatus.NOT_ENABLED -> HcAvailability.NOT_ENABLED
                HealthConnectClient.SdkStatus.NO_PROVIDER -> HcAvailability.NO_PROVIDER
                HealthConnectClient.SdkStatus.NOT_SUPPORTED -> HcAvailability.NOT_SUPPORTED
                else -> HcAvailability.UNKNOWN
            }
        }.getOrDefault(HcAvailability.UNKNOWN)
    }

    private fun healthConnectAvailable(): Boolean = _availability.value == HcAvailability.AVAILABLE

    private suspend fun currentPermissions(): HealthPermissions = withContext(Dispatchers.Main) {
        runCatching {
            val client = healthConnectClientOrNull() ?: return@runCatching HealthPermissions()
            val granted = client.permissionController.getGrantedPermissions()
            HealthPermissions(
                heartRateRead = granted.contains(HC_PERMISSION_HEART_RATE_READ),
                stepsRead = granted.contains(HC_PERMISSION_STEPS_READ),
                workoutWrite = granted.contains(HC_PERMISSION_WORKOUT_WRITE),
            )
        }.getOrDefault(HealthPermissions())
    }

    private fun healthConnectClientOrNull(): HealthConnectClient? {
        hcClient?.let { return it }
        if (!healthConnectAvailable()) return null
        val client = runCatching {
            HealthConnectClient.getClient(
                context,
                listOf(
                    HC_PERMISSION_HEART_RATE_READ,
                    HC_PERMISSION_STEPS_READ,
                    HC_PERMISSION_ACTIVITY_READ,
                    HC_PERMISSION_WORKOUT_READ,
                    HC_PERMISSION_WORKOUT_WRITE,
                ),
            )
        }.getOrElse { e ->
            Log.w(tag, "Health Connect client unavailable: ${e.message}")
            return null
        }
        hcClient = client
        return client
    }

    private suspend fun queryRecords(request: QueryRecordsRequest): List<androidx.health.connect.client.records.Record>? =
        withContext(Dispatchers.Main) {
            runCatching {
                val client = healthConnectClientOrNull() ?: return@runCatching null
                client.queryRecords(request).let { list -> list.toList() }
            }.getOrNull()
        }

    private suspend fun healthConnectLatestHeartRate(): Int? {
        if (_permissions.value.heartRateRead.not()) return null
        val end = Instant.now()
        val start = end.minusSeconds(5 * 60L)
        val records = queryRecords(heartRateQuery(start, end)) ?: return null
        var latestTime: Instant? = null
        var latestBpm: Int? = null
        for (record in records) {
            val hr = record as? HeartRateRecord ?: continue
            for (sample in hr.samples) {
                if (latestTime == null || sample.time.isAfter(latestTime)) {
                    latestTime = sample.time
                    latestBpm = sample.beatsPerMinute.toInt()
                }
            }
        }
        return latestBpm
    }

    private suspend fun healthConnectHrStats(startMillis: Long, endMillis: Long): HrStats? {
        if (_permissions.value.heartRateRead.not()) return null
        val records = queryRecords(heartRateQuery(Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis))) ?: return null
        var sum = 0L
        var max = 0
        var count = 0
        for (record in records) {
            val hr = record as? HeartRateRecord ?: continue
            for (sample in hr.samples) {
                sum += sample.beatsPerMinute
                count += 1
                if (sample.beatsPerMinute > max) max = sample.beatsPerMinute.toInt()
            }
        }
        return if (count > 0) HrStats((sum / count).toInt(), max, count) else null
    }

    private suspend fun healthConnectStepsToday(): Long? {
        if (_permissions.value.stepsRead.not()) return null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val records = queryRecords(stepsQuery(start, Instant.now())) ?: return null
        var total = 0L
        for (record in records) {
            val steps = record as? StepsRecord ?: continue
            total += steps.count
        }
        return total
    }

    private fun heartRateQuery(start: Instant, end: Instant): QueryRecordsRequest =
        QueryRecordsRequest.Builder(HeartRateRecord::class)
            .setTimeRangeFilter(TimeRangeFilter(start, end, TimeRangeFilter.INCLUSIVE))
            .build()

    private fun stepsQuery(start: Instant, end: Instant): QueryRecordsRequest =
        QueryRecordsRequest.Builder(StepsRecord::class)
            .setTimeRangeFilter(TimeRangeFilter(start, end, TimeRangeFilter.INCLUSIVE))
            .build()

    private suspend fun writeSessionToHealthConnect(session: SessionEntity, events: List<SessionEventEntity>): Boolean {
        if (_permissions.value.workoutWrite.not()) return false
        val start = Instant.ofEpochMilli(session.startedAt)
        val end = Instant.ofEpochMilli(session.endedAt.coerceAtLeast(session.startedAt + 1_000L))
        val zone = ZoneOffset.systemDefault().rules.getOffset(start)
        val sessionUuid = UUID.nameUUIDFromBytes("pulse-session:${session.id}")

        val workoutSession = WorkoutSession.Builder(sessionUuid)
            .startTime(start)
            .endTime(end)
            .activity(Activity(name = session.workoutName.take(50), category = Activity.CATEGORY_EXERCISE, parentName = null))
            .type(HC_SESSION_TYPE_EXERCISE)
            .metadata(metadata())
            .build()

        val records = mutableListOf<androidx.health.connect.client.records.Record>(workoutSession)
        records += intervalSegments(session, events, sessionUuid, start, end)

        // Attach the heart-rate trace when we may read it — the Health app shows it on the session.
        if (_permissions.value.heartRateRead) {
            val records2 = queryRecords(heartRateQuery(start, end))
            var sum = 0L
            var count = 0
            val samples = mutableListOf<HeartRateRecord.Sample>()
            for (record in records2.orEmpty()) {
                val hr = record as? HeartRateRecord ?: continue
                for (sample in hr.samples) {
                    samples += sample
                    sum += sample.beatsPerMinute
                    count += 1
                }
            }
            if (samples.isNotEmpty()) {
                samples.sortBy { it.time }
                records += HeartRateRecord(
                    startTime = start,
                    startZoneOffset = zone,
                    endTime = end,
                    endZoneOffset = zone,
                    samples = samples,
                    metadata = metadata(),
                )
            }
        }

        val ok = withContext(Dispatchers.Main) {
            runCatching {
                val client = healthConnectClientOrNull() ?: return@runCatching false
                client.insertRecords(records)
                true
            }.getOrDefault(false)
        }
        if (ok) markSynced()
        return ok
    }

    private fun intervalSegments(
        session: SessionEntity,
        events: List<SessionEventEntity>,
        sessionUuid: UUID,
        start: Instant,
        end: Instant,
    ): List<WorkoutSegment> {
        val starts = events.filter { it.kind == "INTERVAL_STARTED" }.sortedBy { it.atMillis }
        return starts.take(MAX_SEGMENTS_PER_SESSION).mapIndexedNotNull { index, event ->
            val segmentStart = start.plusMillis(event.atMillis)
            val segmentEnd = if (index + 1 < starts.size) {
                start.plusMillis(starts[index + 1].atMillis)
            } else {
                end
            }
            if (segmentEnd <= segmentStart) null else WorkoutSegment.Builder(
                UUID.nameUUIDFromBytes("pulse-segment:${session.id}:${event.stepIndex}:${event.atMillis}"),
            )
                .workoutSessionId(sessionUuid)
                .startTime(segmentStart)
                .endTime(segmentEnd)
                .type(HC_SEGMENT_TYPE_EXERCISE)
                .metadata(metadata())
                .build()
        }
    }

    private fun metadata(): Metadata = Metadata.actuallyRecorded(device = DEVICE)

    // --- Google Fit internals --------------------------------------------------------------------

    private fun fitPlatformStatus(): FitAvailability {
        val playStatus = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(GoogleApiAvailability.API_UNAVAILABLE)
        if (playStatus != GoogleApiAvailability.SUCCESS) return FitAvailability.PLAY_SERVICES_MISSING
        if (fitOAuthConfigured().isNullOrBlank()) return FitAvailability.NOT_CONFIGURED
        return FitAvailability.AVAILABLE
    }

    private fun fitOAuthConfigured(): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
        info.metaData?.getString("com.google.android.gms.fitness.OAUTH_CLIENT_ID")
    }.getOrNull()

    private fun fitAccount(): com.google.android.gms.fitness.data.Account =
        GoogleSignIn.getAccountForExtension(context, fitOptions)

    private fun fitHasPermissions(): Boolean = runCatching {
        GoogleSignIn.hasPermissions(fitAccount(), fitOptions)
    }.getOrDefault(false)

    private suspend fun fitLatestHeartRate(): Int? {
        if (_fitAvailability.value != FitAvailability.AVAILABLE || _fitAuthorized.value.not()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis() / 1000L
                val request = DataReadRequest.Builder()
                    .read(DataType.TYPE_HEART_RATE_BPM)
                    .setTimeRange(now - 5 * 60, now, TimeUnit.SECONDS)
                    .build()
                val response = Tasks.await(Fitness.getHistoryClient(context, fitAccount()).readData(request))
                val dataSet = response.dataSet.firstOrNull { it.dataType == DataType.TYPE_HEART_RATE_BPM } ?: return@runCatching null
                var latestTime = 0L
                var latestBpm: Int? = null
                for (data in dataSet.getData(DataType.TYPE_HEART_RATE_BPM)) {
                    val time = data.startTimeNanos / 1_000_000L
                    val bpm = data.getField(Field.FIELD_BPM).firstValue
                    if (time >= latestTime) {
                        latestTime = time
                        latestBpm = bpm
                    }
                }
                latestBpm
            }.getOrNull()
        }
    }

    private suspend fun fitHrStats(startMillis: Long, endMillis: Long): HrStats? {
        if (_fitAvailability.value != FitAvailability.AVAILABLE || _fitAuthorized.value.not()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = DataReadRequest.Builder()
                    .read(DataType.TYPE_HEART_RATE_BPM)
                    .setTimeRange(startMillis / 1000L, endMillis / 1000L, TimeUnit.SECONDS)
                    .build()
                val response = Tasks.await(Fitness.getHistoryClient(context, fitAccount()).readData(request))
                val dataSet = response.dataSet.firstOrNull { it.dataType == DataType.TYPE_HEART_RATE_BPM } ?: return@runCatching null
                var sum = 0L
                var max = 0
                var count = 0
                for (data in dataSet.getData(DataType.TYPE_HEART_RATE_BPM)) {
                    val bpm = data.getField(Field.FIELD_BPM).firstValue
                    sum += bpm
                    count += 1
                    if (bpm > max) max = bpm
                }
                if (count > 0) HrStats((sum / count).toInt(), max, count) else null
            }.getOrNull()
        }
    }

    private suspend fun fitStepsToday(): Long? {
        if (_fitAvailability.value != FitAvailability.AVAILABLE || _fitAuthorized.value.not()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val zone = ZoneId.systemDefault()
                val start = LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
                val now = System.currentTimeMillis() / 1000L
                val request = DataReadRequest.Builder()
                    .read(DataType.TYPE_STEP_COUNT_DELTA)
                    .setTimeRange(start, now, TimeUnit.SECONDS)
                    .build()
                val response = Tasks.await(Fitness.getHistoryClient(context, fitAccount()).readData(request))
                var total = 0L
                for (dataSet in response.dataSet) {
                    for (data in dataSet) {
                        total += data.getField(Field.FIELD_STEPS).firstValue
                    }
                }
                total
            }.getOrNull()
        }
    }

    private suspend fun writeSessionToFit(session: SessionEntity): Boolean {
        if (_fitAvailability.value != FitAvailability.AVAILABLE || _fitAuthorized.value.not()) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val account = fitAccount()
                val description = "Interval workout recorded by PULSE (${session.workoutName})"
                val config = SessionConfiguration(
                    session.workoutName.take(50),
                    description,
                    SessionConfiguration.TYPE_WORKOUT,
                    SessionConfiguration.STATUS_IN_PROGRESS,
                )
                val insertRequest = SessionInsertRequest.Builder()
                    .setSessionConfiguration(config)
                    .build()
                val inserted = Tasks.await(Fitness.getSessionsClient(context, account).insertSession(insertRequest))
                val completed = SessionConfiguration(
                    inserted.name,
                    description,
                    SessionConfiguration.TYPE_WORKOUT,
                    SessionConfiguration.STATUS_COMPLETE,
                )
                val updateRequest = SessionUpdateRequest.Builder(
                    inserted.id,
                    completed,
                    session.startedAt,
                    session.endedAt.coerceAtLeast(session.startedAt + 1_000L),
                ).build()
                Tasks.await(Fitness.getSessionsClient(context, account).updateSession(updateRequest))
            }.getOrDefault(false)
        }
    }

    // --- Snapshot ------------------------------------------------------------------------------

    private suspend fun buildSnapshot(): HealthSnapshot {
        val steps = stepsToday()
        val hr = latestHeartRate()
        val source = when {
            healthConnectAvailable() && _permissions.value.anyReadGranted -> "Health Connect"
            _fitAvailability.value == FitAvailability.AVAILABLE && _fitAuthorized.value -> "Google Fit"
            else -> null
        }
        return HealthSnapshot(
            stepsToday = steps,
            latestHeartRate = hr,
            source = source,
            lastSyncAt = _lastSyncAt.value,
        )
    }
}
