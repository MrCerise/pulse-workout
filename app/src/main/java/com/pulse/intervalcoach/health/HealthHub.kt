package com.pulse.intervalcoach.health

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.Session
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionInsertRequest
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
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/** Health Connect permissions used by the app and requested from its permission screen. */
internal const val HC_PERMISSION_HEART_RATE_READ = "android.permission.health.READ_HEART_RATE"
internal const val HC_PERMISSION_STEPS_READ = "android.permission.health.READ_STEPS"
internal const val HC_PERMISSION_WORKOUT_WRITE = "android.permission.health.WRITE_EXERCISE"
internal val HC_REQUESTED_PERMISSIONS = setOf(
    HC_PERMISSION_HEART_RATE_READ,
    HC_PERMISSION_STEPS_READ,
    HC_PERMISSION_WORKOUT_WRITE,
)

private const val MAX_SEGMENTS_PER_SESSION = 40
private const val TAG = "HealthHub"

private val PULSE_DEVICE = Device(
    type = Device.TYPE_PHONE,
    manufacturer = "PULSE",
    model = "Interval Coach",
)

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
 * Bridge so [HealthHub] can start Google Fit authorisation from the activity, where its result is
 * delivered through `onActivityResult`.
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

/**
 * PULSE ↔ health-platform bridge.
 *
 * Health Connect is the primary backend; Google Fit is an optional legacy fallback. Missing
 * providers, permissions, or account configuration are handled as unavailable data rather than
 * allowing a health-service failure to disrupt workout timing.
 */
class HealthHub(
    private val context: Context,
    private val prefs: PreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .build()

    /** Re-reads platform state and the dashboard snapshot. Safe to call any time. */
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

    /** Starts the Google Fit authorisation dialog (must be called from the UI thread). */
    fun requestFitAuthorization(activity: Activity) {
        val launch = FitAuthBridge.launcher
        if (launch == null) {
            Log.w(TAG, "Fit authorisation bridge not installed by the activity")
            return
        }
        launch(activity, fitOptions) { granted ->
            scope.launch {
                if (granted) {
                    _fitAuthorized.value = true
                    runCatching { prefs.setFitAuthorized(true) }
                }
                refresh()
            }
        }
    }

    /** Newest heart-rate reading within the last few minutes, or null when none is available. */
    suspend fun latestHeartRate(): Int? {
        healthConnectLatestHeartRate()?.let { return it }
        return fitLatestHeartRate()
    }

    /** Average / peak heart rate over one window. Returns null when no data is available. */
    suspend fun heartRateStats(startMillis: Long, endMillis: Long): HrStats? {
        if (endMillis <= startMillis) return null
        healthConnectHrStats(startMillis, endMillis)?.let { return it }
        return fitHrStats(startMillis, endMillis)
    }

    /** Total steps recorded today, from whichever backend is connected. */
    suspend fun stepsToday(): Long? {
        healthConnectStepsToday()?.let { return it }
        return fitStepsToday()
    }

    /** Writes a finished workout to Health Connect, falling back to Fit if HC is unavailable. */
    suspend fun writeSession(session: SessionEntity, events: List<SessionEventEntity>): Boolean {
        val synced = if (healthConnectAvailable()) {
            writeSessionToHealthConnect(session, events)
        } else {
            writeSessionToFit(session)
        }
        if (synced) markSynced()
        return synced
    }

    /** Persists the last successful sync time so the status survives restarts. */
    fun markSynced() {
        val now = System.currentTimeMillis()
        _lastSyncAt.value = now
        scope.launch { runCatching { prefs.setHealthLastSyncAt(now) } }
    }

    private suspend fun healthConnectStatus(): HcAvailability = withContext(Dispatchers.IO) {
        runCatching {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
                // The provider is missing or too old for this SDK version.
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.NOT_INSTALLED
                HealthConnectClient.SDK_UNAVAILABLE -> HcAvailability.NOT_SUPPORTED
                else -> HcAvailability.UNKNOWN
            }
        }.getOrDefault(HcAvailability.UNKNOWN)
    }

    private fun healthConnectAvailable(): Boolean = _availability.value == HcAvailability.AVAILABLE

    private suspend fun currentPermissions(): HealthPermissions = withContext(Dispatchers.IO) {
        val client = healthConnectClientOrNull() ?: return@withContext HealthPermissions()
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        HealthPermissions(
            heartRateRead = HC_PERMISSION_HEART_RATE_READ in granted,
            stepsRead = HC_PERMISSION_STEPS_READ in granted,
            workoutWrite = HC_PERMISSION_WORKOUT_WRITE in granted,
        )
    }

    private fun healthConnectClientOrNull(): HealthConnectClient? {
        hcClient?.let { return it }
        if (!healthConnectAvailable()) return null
        val client = runCatching { HealthConnectClient.getOrCreate(context) }
            .getOrElse { error ->
                Log.w(TAG, "Health Connect client unavailable: ${error.message}")
                return null
            }
        hcClient = client
        return client
    }

    private suspend fun <T : Record> queryRecords(
        recordType: KClass<T>,
        start: Instant,
        end: Instant,
    ): List<T>? = withContext(Dispatchers.IO) {
        if (!start.isBefore(end)) return@withContext emptyList()
        val client = healthConnectClientOrNull() ?: return@withContext null
        runCatching {
            val request = ReadRecordsRequest<T>(
                recordType = recordType,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
            client.readRecords(request).records
        }.onFailure { error ->
            Log.w(TAG, "Health Connect read failed: ${error.message}")
        }.getOrNull()
    }

    private suspend fun healthConnectLatestHeartRate(): Int? {
        if (!_permissions.value.heartRateRead) return null
        val end = Instant.now()
        val records = queryRecords(
            HeartRateRecord::class,
            end.minusSeconds(5 * 60L),
            end,
        ) ?: return null
        var latestTime: Instant? = null
        var latestBpm: Int? = null
        for (record in records) {
            for (sample in record.samples) {
                val previousTime = latestTime
                if (previousTime == null || sample.time.isAfter(previousTime)) {
                    latestTime = sample.time
                    latestBpm = sample.beatsPerMinute.toInt()
                }
            }
        }
        return latestBpm
    }

    private suspend fun healthConnectHrStats(startMillis: Long, endMillis: Long): HrStats? {
        if (!_permissions.value.heartRateRead) return null
        val records = queryRecords(
            HeartRateRecord::class,
            Instant.ofEpochMilli(startMillis),
            Instant.ofEpochMilli(endMillis),
        ) ?: return null
        var sum = 0L
        var max = 0
        var count = 0
        for (record in records) {
            for (sample in record.samples) {
                val bpm = sample.beatsPerMinute.toInt()
                sum += bpm
                max = maxOf(max, bpm)
                count++
            }
        }
        return if (count > 0) HrStats((sum / count).toInt(), max, count) else null
    }

    private suspend fun healthConnectStepsToday(): Long? {
        if (!_permissions.value.stepsRead) return null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val records = queryRecords(StepsRecord::class, start, Instant.now()) ?: return null
        return records.sumOf { it.count }
    }

    private suspend fun writeSessionToHealthConnect(
        session: SessionEntity,
        events: List<SessionEventEntity>,
    ): Boolean {
        if (!_permissions.value.workoutWrite) return false
        val start = Instant.ofEpochMilli(session.startedAt)
        val end = Instant.ofEpochMilli(session.endedAt.coerceAtLeast(session.startedAt + 1_000L))
        val durationMillis = end.toEpochMilli() - start.toEpochMilli()
        val zone = ZoneId.systemDefault()
        val record = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = zone.rules.getOffset(start),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            metadata = Metadata.activelyRecorded(
                device = PULSE_DEVICE,
                clientRecordId = "pulse-session-${session.id}",
            ),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            title = session.workoutName.take(50),
            segments = intervalSegments(events, start, durationMillis),
        )

        val result = withContext(Dispatchers.IO) {
            val client = healthConnectClientOrNull() ?: return@withContext false
            runCatching {
                client.insertRecords(listOf(record))
                true
            }.onFailure { error ->
                Log.w(TAG, "Could not write workout to Health Connect: ${error.message}")
            }.getOrDefault(false)
        }
        return result
    }

    private fun intervalSegments(
        events: List<SessionEventEntity>,
        sessionStart: Instant,
        sessionDurationMillis: Long,
    ): List<ExerciseSegment> {
        if (sessionDurationMillis <= 0L) return emptyList()
        val starts = events
            .asSequence()
            .filter { it.kind == "INTERVAL_STARTED" }
            .sortedBy { it.atMillis }
            .distinctBy { it.atMillis }
            .take(MAX_SEGMENTS_PER_SESSION)
            .toList()

        return starts.mapIndexedNotNull { index, event ->
            val startOffset = event.atMillis.coerceAtLeast(0L)
            if (startOffset >= sessionDurationMillis) return@mapIndexedNotNull null
            val nextOffset = starts.getOrNull(index + 1)?.atMillis ?: sessionDurationMillis
            val endOffset = nextOffset.coerceIn(startOffset + 1L, sessionDurationMillis)
            ExerciseSegment(
                startTime = sessionStart.plusMillis(startOffset),
                endTime = sessionStart.plusMillis(endOffset),
                segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
            )
        }
    }

    private fun fitPlatformStatus(): FitAvailability {
        val playServices = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(ConnectionResult.API_UNAVAILABLE)
        if (playServices != ConnectionResult.SUCCESS) return FitAvailability.PLAY_SERVICES_MISSING
        if (fitOAuthConfigured().isNullOrBlank()) return FitAvailability.NOT_CONFIGURED
        return FitAvailability.AVAILABLE
    }

    private fun fitOAuthConfigured(): String? = runCatching {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        info.metaData?.getString("com.google.android.gms.fitness.OAUTH_CLIENT_ID")
    }.getOrNull()

    private fun fitAccount() = GoogleSignIn.getAccountForExtension(context, fitOptions)

    private fun fitHasPermissions(): Boolean = runCatching {
        GoogleSignIn.hasPermissions(fitAccount(), fitOptions)
    }.getOrDefault(false)

    private suspend fun fitLatestHeartRate(): Int? {
        if (_fitAvailability.value != FitAvailability.AVAILABLE || !_fitAuthorized.value) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val request = DataReadRequest.Builder()
                    .read(DataType.TYPE_HEART_RATE_BPM)
                    .setTimeRange(now - TimeUnit.MINUTES.toMillis(5), now, TimeUnit.MILLISECONDS)
                    .build()
                val response = Tasks.await(Fitness.getHistoryClient(context, fitAccount()).readData(request))
                val dataSet = response.dataSets.firstOrNull { it.dataType == DataType.TYPE_HEART_RATE_BPM }
                    ?: return@runCatching null
                var latestTime = Long.MIN_VALUE
                var latestBpm: Int? = null
                for (point in dataSet.dataPoints) {
                    val time = point.getStartTime(TimeUnit.MILLISECONDS)
                    val bpm = point.getValue(Field.FIELD_BPM).asFloat().toInt()
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
        if (_fitAvailability.value != FitAvailability.AVAILABLE || !_fitAuthorized.value) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = DataReadRequest.Builder()
                    .read(DataType.TYPE_HEART_RATE_BPM)
                    .setTimeRange(startMillis, endMillis, TimeUnit.MILLISECONDS)
                    .build()
                val response = Tasks.await(Fitness.getHistoryClient(context, fitAccount()).readData(request))
                val dataSet = response.dataSets.firstOrNull { it.dataType == DataType.TYPE_HEART_RATE_BPM }
                    ?: return@runCatching null
                var sum = 0L
                var max = 0
                var count = 0
                for (point in dataSet.dataPoints) {
                    val bpm = point.getValue(Field.FIELD_BPM).asFloat().toInt()
                    sum += bpm
                    max = maxOf(max, bpm)
                    count++
                }
                if (count > 0) HrStats((sum / count).toInt(), max, count) else null
            }.getOrNull()
        }
    }

    private suspend fun fitStepsToday(): Long? {
        if (_fitAvailability.value != FitAvailability.AVAILABLE || !_fitAuthorized.value) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val zone = ZoneId.systemDefault()
                val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()
                val request = DataReadRequest.Builder()
                    .read(DataType.TYPE_STEP_COUNT_DELTA)
                    .setTimeRange(start, now, TimeUnit.MILLISECONDS)
                    .build()
                val response = Tasks.await(Fitness.getHistoryClient(context, fitAccount()).readData(request))
                response.dataSets
                    .filter { it.dataType == DataType.TYPE_STEP_COUNT_DELTA }
                    .flatMap { it.dataPoints }
                    .sumOf { it.getValue(Field.FIELD_STEPS).asInt().toLong() }
            }.getOrNull()
        }
    }

    private suspend fun writeSessionToFit(session: SessionEntity): Boolean {
        if (_fitAvailability.value != FitAvailability.AVAILABLE || !_fitAuthorized.value) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val end = session.endedAt.coerceAtLeast(session.startedAt + 1_000L)
                val fitSession = Session.Builder()
                    .setName(session.workoutName.take(50))
                    .setDescription("Interval workout recorded by PULSE")
                    .setIdentifier("pulse-session-${session.id}")
                    .setActivity(FitnessActivities.OTHER)
                    .setStartTime(session.startedAt, TimeUnit.MILLISECONDS)
                    .setEndTime(end, TimeUnit.MILLISECONDS)
                    .build()
                val request = SessionInsertRequest.Builder()
                    .setSession(fitSession)
                    .build()
                Tasks.await(Fitness.getSessionsClient(context, fitAccount()).insertSession(request))
                true
            }.onFailure { error ->
                Log.w(TAG, "Could not write workout to Google Fit: ${error.message}")
            }.getOrDefault(false)
        }
    }

    private suspend fun buildSnapshot(): HealthSnapshot {
        val steps = stepsToday()
        val heartRate = latestHeartRate()
        val source = when {
            healthConnectAvailable() && _permissions.value.anyReadGranted -> "Health Connect"
            _fitAvailability.value == FitAvailability.AVAILABLE && _fitAuthorized.value -> "Google Fit"
            else -> null
        }
        return HealthSnapshot(
            stepsToday = steps,
            latestHeartRate = heartRate,
            source = source,
            lastSyncAt = _lastSyncAt.value,
        )
    }
}
