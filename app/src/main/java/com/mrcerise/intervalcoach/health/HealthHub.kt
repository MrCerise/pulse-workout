package com.mrcerise.intervalcoach.health

import android.content.Context
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
import com.mrcerise.intervalcoach.data.PreferencesRepository
import com.mrcerise.intervalcoach.data.db.SessionEntity
import com.mrcerise.intervalcoach.data.db.SessionEventEntity
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

/** How fresh a sample must be to count as a *live* reading in the player. */
private const val LIVE_HR_WINDOW_MILLIS = 60_000L

private val INTERVALS_DEVICE = Device(
    type = Device.TYPE_PHONE,
    manufacturer = "Intervals",
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

/** The runtime state of the three data INTERVALS cares about on the connected backend. */
data class HealthPermissions(
    val heartRateRead: Boolean = false,
    val stepsRead: Boolean = false,
    val workoutWrite: Boolean = false,
) {
    val anyReadGranted: Boolean get() = heartRateRead || stepsRead
}

/** Average / peak heart rate for one window. */
data class HrStats(val average: Int, val max: Int, val sampleCount: Int)

/** What the dashboards show: today's steps, the newest heart-rate reading and the sync state. */
data class HealthSnapshot(
    val stepsToday: Long? = null,
    val latestHeartRate: Int? = null,
    val source: String? = null,
    val lastSyncAt: Long = 0L,
)

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
 * INTERVALS ↔ Health Connect bridge.
 *
 * Health Connect is the one backend: it reads heart rate and steps and receives finished workout
 * sessions with per-interval segments. Missing providers or permissions are handled as unavailable
 * data rather than allowing a health-service failure to disrupt workout timing.
 *
 * Two read windows, two honest answers:
 *  - the **player** wants a live beat, so it reads only the last [LIVE_HR_WINDOW_MILLIS] — a stale
 *    number would be worse than no number mid-effort;
 *  - the **dashboard** wants today's latest reading, so it reads since midnight — a resting
 *    measurement from this morning is exactly what "Today's health" means.
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

    private val _lastSyncAt = MutableStateFlow(0L)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private val _snapshot = MutableStateFlow(HealthSnapshot())
    val snapshot: StateFlow<HealthSnapshot> = _snapshot.asStateFlow()

    /** Re-reads platform state and the dashboard snapshot. Safe to call any time. */
    fun refresh() {
        scope.launch {
            _availability.value = healthConnectStatus()
            _permissions.value = currentPermissions()
            _lastSyncAt.value = runCatching { prefs.current().healthLastSyncAt }.getOrDefault(0L)
            _snapshot.value = buildSnapshot()
        }
    }

    /** Newest heart-rate reading within the last minute, or null when none is available. */
    suspend fun latestLiveHeartRate(): Int? {
        val end = Instant.now()
        return latestHeartRate(end.minusMillis(LIVE_HR_WINDOW_MILLIS), end)
    }

    /** Newest heart-rate reading since the start of today, or null when none is available. */
    suspend fun latestHeartRateToday(): Int? {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        return latestHeartRate(start, Instant.now())
    }

    /** Average / peak heart rate over one window. Returns null when no data is available. */
    suspend fun heartRateStats(startMillis: Long, endMillis: Long): HrStats? {
        if (endMillis <= startMillis) return null
        return healthConnectHrStats(startMillis, endMillis)
    }

    /** Total steps recorded today. */
    suspend fun stepsToday(): Long? = healthConnectStepsToday()

    /** Writes a finished workout to Health Connect. */
    suspend fun writeSession(session: SessionEntity, events: List<SessionEventEntity>): Boolean {
        val synced = writeSessionToHealthConnect(session, events)
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

    private suspend fun latestHeartRate(after: Instant, until: Instant): Int? {
        if (!_permissions.value.heartRateRead) return null
        val records = queryRecords(HeartRateRecord::class, after, until) ?: return null
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
                device = INTERVALS_DEVICE,
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

    private suspend fun buildSnapshot(): HealthSnapshot {
        val source = if (healthConnectAvailable() && _permissions.value.anyReadGranted) {
            "Health Connect"
        } else {
            null
        }
        return HealthSnapshot(
            stepsToday = stepsToday(),
            latestHeartRate = latestHeartRateToday(),
            source = source,
            lastSyncAt = _lastSyncAt.value,
        )
    }
}
