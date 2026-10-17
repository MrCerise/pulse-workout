package com.mrcerise.intervalcoach.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
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
import java.time.ZoneId

/** Health Connect permissions used by the app and requested from its permission screen. */
internal const val HC_PERMISSION_WORKOUT_WRITE = "android.permission.health.WRITE_EXERCISE"
internal val HC_REQUESTED_PERMISSIONS = setOf(
    HC_PERMISSION_WORKOUT_WRITE,
)

private const val MAX_SEGMENTS_PER_SESSION = 40
private const val TAG = "HealthHub"

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

/** The runtime state of the optional workout export permission. */
data class HealthPermissions(
    val workoutWrite: Boolean = false,
)

/** Health Connect connection and workout export status. */
data class HealthSnapshot(
    val source: String? = null,
    val lastSyncAt: Long = 0L,
)

/**
 * Writes completed workouts and their interval segments to Health Connect.
 * Missing providers or permissions never disrupt workout timing.
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

    private fun buildSnapshot(): HealthSnapshot {
        val source = if (healthConnectAvailable() && _permissions.value.workoutWrite) {
            "Health Connect"
        } else {
            null
        }
        return HealthSnapshot(
            source = source,
            lastSyncAt = _lastSyncAt.value,
        )
    }
}
