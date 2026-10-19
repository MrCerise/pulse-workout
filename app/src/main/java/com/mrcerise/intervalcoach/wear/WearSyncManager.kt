package com.mrcerise.intervalcoach.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mrcerise.intervalcoach.BuildConfig
import com.mrcerise.intervalcoach.data.STATUS_COMPLETED
import com.mrcerise.intervalcoach.data.STATUS_STOPPED
import com.mrcerise.intervalcoach.data.PreferencesRepository
import com.mrcerise.intervalcoach.data.SessionRepository
import com.mrcerise.intervalcoach.data.WorkoutRepository
import com.mrcerise.intervalcoach.data.db.SessionEventEntity
import com.mrcerise.intervalcoach.data.db.WorkoutEntity
import com.mrcerise.intervalcoach.health.HealthHub
import com.mrcerise.intervalcoach.session.SessionController
import com.pulse.sync.LibraryManifest
import com.pulse.sync.LiveSession
import com.pulse.sync.SessionLog
import com.pulse.sync.SessionOrigin
import com.pulse.sync.SessionStatus
import com.pulse.sync.SyncJson
import com.pulse.sync.SyncPaths
import com.pulse.sync.SyncedPreferences
import com.pulse.sync.WatchCommand
import com.pulse.sync.WatchCommandKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The phone half of the PULSE sync link.
 *
 * Owns everything that crosses to the watch (see `com.pulse.sync` for the payload formats):
 *
 *  - the **workout library** is pushed as one data item per workout plus a manifest item, so the
 *    watch's copy reconciles exactly (renames, edits and deletions included). Favourites and
 *    recently used workouts are pushed first and a byte budget caps the mirror, because a data
 *    item may not exceed 100 KB and the watch does not need a 600-workout library on a wrist;
 *  - the **appearance and cue preferences** the watch reuses are pushed whenever they change;
 *  - a **running phone session** is mirrored to the watch at ~1 Hz, and commands from the watch
 *    are applied to the [SessionController] — the wrist is a remote control for the phone;
 *  - a **finished watch session** arrives as a log message and is stored into the phone's history
 *    (and Health Connect, when enabled), exactly as if it had run on the phone.
 *
 * Everything is best-effort: without a paired watch, the data-layer calls are no-ops, and without
 * Google Play services the manager never throws into the app's startup path.
 */
class WearSyncManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val workouts: WorkoutRepository,
    private val sessions: SessionRepository,
    private val preferences: PreferencesRepository,
    private val sessionController: SessionController,
    private val health: HealthHub,
) {

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }

    private val _remoteWatchSession = MutableStateFlow<LiveSession?>(null)
    /** A session running on the watch, mirrored here; null when none is running. */
    val remoteWatchSession: StateFlow<LiveSession?> = _remoteWatchSession.asStateFlow()

    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    /** Ids and timestamps pushed so far, so removals can be detected and deleted. */
    private val pushedWorkouts = LinkedHashMap<String, Long>()

    private var remoteTimeoutJob: Job? = null
    private var wasPushingLive = false
    private var lastLiveSessionId: String = ""
    private var lastLivePushAt = 0L

    private val messageListener = MessageClient.OnMessageReceivedListener { message: MessageEvent ->
        handleMessage(message)
    }

    /** Registers listeners and starts the observers. Idempotent. */
    fun start() {
        runCatching { messageClient.addListener(messageListener) }
        scope.launch {
            // Re-push on start: identical data items are de-duplicated by the system, so this is
            // what makes the watch's "Sync now" and first pairing converge without bookkeeping.
            pushLibrary()
            pushPreferences()
        }
        scope.launch {
            workouts.workouts.collect { entities -> pushLibrary(entities) }
        }
        scope.launch {
            preferences.flow.debounce(PREFS_DEBOUNCE_MILLIS).collect { pushPreferences() }
        }
        scope.launch { mirrorSessionLoop() }
        scope.launch {
            while (isActive) {
                delay(NODE_REFRESH_MILLIS)
                refreshNodes()
            }
        }
    }

    /** Sends a command to the watch (remote-controlling a watch session from the phone). */
    fun sendCommand(command: WatchCommand) {
        scope.launch { sendToWatch(SyncPaths.MESSAGE_COMMAND, SyncJson.encodeCommand(command)) }
    }

    // -------------------------------------------------------------------------------------------
    // Outbound: library + preferences
    // -------------------------------------------------------------------------------------------

    private suspend fun pushLibrary() {
        pushLibrary(workouts.workouts.first())
    }

    private suspend fun pushLibrary(entities: List<WorkoutEntity>) {
        val ordered = entities.sortedWith(
            compareByDescending<WorkoutEntity> { it.isFavorite }
                .thenByDescending { it.lastUsedAt ?: it.updatedAt },
        )
        val ids = mutableListOf<String>()
        val updated = mutableListOf<Long>()
        var budget = LIBRARY_CHAR_BUDGET
        var truncated = false
        for (entity in ordered) {
            val json = entity.planJson
            if (json.length > budget || json.length > MAX_WORKOUT_CHARS) {
                truncated = true
                if (json.length <= MAX_WORKOUT_CHARS && ids.isEmpty()) {
                    // The library's single most relevant workout still fits: never leave the
                    // watch empty because the rest of the library is large.
                    putWorkoutItem(entity, json)
                    ids += entity.id
                    updated += entity.updatedAt
                }
                break
            }
            budget -= json.length
            putWorkoutItem(entity, json)
            ids += entity.id
            updated += entity.updatedAt
        }

        // Workouts deleted since the last push: remove their data items so the watch follows.
        for ((id, _) in pushedWorkouts) {
            if (id !in ids) deleteWorkoutItem(id)
        }
        pushedWorkouts.clear()
        ids.forEachIndexed { index, id -> pushedWorkouts[id] = updated[index] }

        val request = PutDataMapRequest.create(SyncPaths.DATA_LIBRARY)
        request.dataMap.putString(
            KEY_MANIFEST,
            SyncJson.encodeManifest(
                LibraryManifest(
                    appVersion = BuildConfig.VERSION_NAME,
                    workoutIds = ids,
                    updatedAt = updated,
                    truncated = truncated,
                ),
            ),
        )
        runCatching { dataClient.putDataItem(request.asPutDataRequest().setUrgent()) }
    }

    private fun putWorkoutItem(entity: WorkoutEntity, planJson: String) {
        val request = PutDataMapRequest.create(SyncPaths.workout(entity.id))
        request.dataMap.putString(KEY_PLAN, planJson)
        request.dataMap.putLong(KEY_UPDATED_AT, entity.updatedAt)
        request.dataMap.putBoolean(KEY_FAVORITE, entity.isFavorite)
        runCatching { dataClient.putDataItem(request.asPutDataRequest().setUrgent()) }
    }

    private fun deleteWorkoutItem(id: String) {
        val uri = Uri.Builder().scheme("wear").path(SyncPaths.workout(id)).build()
        runCatching { dataClient.deleteDataItems(uri) }
    }

    private suspend fun pushPreferences() {
        val prefs = preferences.current()
        val request = PutDataMapRequest.create(SyncPaths.DATA_PREFS)
        request.dataMap.putString(
            KEY_PREFS,
            SyncJson.encodePreferences(
                SyncedPreferences(
                    themeMode = prefs.themeMode.name,
                    highContrast = prefs.highContrast,
                    reducedMotion = prefs.reducedMotion,
                    vibrationEnabled = prefs.vibrationEnabled,
                    keepScreenOn = prefs.keepScreenOn,
                    countdownFromSeconds = prefs.countdownFromSeconds,
                    halfwayAnnouncements = prefs.halfwayAnnouncements,
                ),
            ),
        )
        runCatching { dataClient.putDataItem(request.asPutDataRequest().setUrgent()) }
    }

    // -------------------------------------------------------------------------------------------
    // Outbound: live session mirror
    // -------------------------------------------------------------------------------------------

    private suspend fun mirrorSessionLoop() {
        sessionController.snapshot.collect { snapshot ->
            val active = sessionController.active.value
            if (snapshot.isActive && active != null) {
                wasPushingLive = true
                lastLiveSessionId = active.sessionId
                val now = System.currentTimeMillis()
                if (now - lastLivePushAt >= LIVE_INTERVAL_MILLIS) {
                    lastLivePushAt = now
                    sendToWatch(
                        SyncPaths.MESSAGE_LIVE,
                        SyncJson.encodeLive(
                            LiveSession.fromSnapshot(
                                origin = SessionOrigin.PHONE,
                                sessionId = active.sessionId,
                                snapshot = snapshot,
                                sentAt = now,
                            ),
                        ),
                    )
                }
            } else if (wasPushingLive) {
                // The session just ended: one final snapshot with status FINISHED tells the watch
                // to close its remote screen.
                wasPushingLive = false
                sendToWatch(
                    SyncPaths.MESSAGE_LIVE,
                    SyncJson.encodeLive(
                        LiveSession.fromSnapshot(
                            origin = SessionOrigin.PHONE,
                            sessionId = lastLiveSessionId,
                            snapshot = snapshot,
                            sentAt = System.currentTimeMillis(),
                        ),
                    ),
                )
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Inbound
    // -------------------------------------------------------------------------------------------

    private fun handleMessage(message: MessageEvent) {
        when (message.path) {
            SyncPaths.MESSAGE_COMMAND -> {
                val command = SyncJson.decodeCommand(message.data) ?: return
                if (sessionController.active.value == null) return
                when (command.kind) {
                    WatchCommandKind.TOGGLE_PAUSE -> sessionController.togglePause()
                    WatchCommandKind.NEXT -> sessionController.next()
                    WatchCommandKind.PREVIOUS -> sessionController.previous()
                    WatchCommandKind.ADD_TIME -> sessionController.addTime(command.millis)
                    WatchCommandKind.STOP -> sessionController.endSession(stoppedEarly = true)
                    WatchCommandKind.COMPLETE_MANUAL -> sessionController.completeManual()
                }
            }
            SyncPaths.MESSAGE_LOG -> {
                val log = SyncJson.decodeLog(message.data) ?: return
                scope.launch { storeWatchSession(log, message.sourceNodeId) }
            }
            SyncPaths.MESSAGE_REQUEST_SYNC -> {
                scope.launch {
                    pushLibrary()
                    pushPreferences()
                }
            }
            SyncPaths.MESSAGE_LIVE -> {
                val live = SyncJson.decodeLive(message.data) ?: return
                if (live.origin == SessionOrigin.WATCH) updateRemoteWatchSession(live)
            }
        }
    }

    private fun updateRemoteWatchSession(live: LiveSession) {
        if (live.isFinished) {
            _remoteWatchSession.value = null
            remoteTimeoutJob?.cancel()
            return
        }
        _remoteWatchSession.value = live
        remoteTimeoutJob?.cancel()
        remoteTimeoutJob = scope.launch {
            delay(REMOTE_TIMEOUT_MILLIS)
            _remoteWatchSession.value = null
        }
    }

    /** Stores a finished watch session into the phone's history, then acknowledges it. */
    private suspend fun storeWatchSession(log: SessionLog, sourceNodeId: String) {
        // A redelivered log (the ack was lost) is already stored: only acknowledge it again, or
        // every retry would append a second copy of its events.
        val stored = sessions.session(log.sessionId) != null ||
            runCatching { storeNewWatchSession(log) }.isSuccess
        // Unacknowledged logs stay queued on the watch, so a failed write is retried, not lost.
        if (stored) runCatching { messageClient.sendMessage(sourceNodeId, SyncPaths.MESSAGE_LOG_ACK, log.sessionId.toByteArray()) }
    }

    private suspend fun storeNewWatchSession(log: SessionLog) {
        val events = log.events.map { event ->
            SessionEventEntity(
                sessionId = log.sessionId,
                stepIndex = event.stepIndex,
                stepName = event.stepName,
                kind = event.kind,
                atMillis = event.atMillis,
                durationMillis = event.durationMillis,
                detail = event.detail,
            )
        }
        // The plan snapshot is stored verbatim — history is never rewritten by later edits, and a
        // plan from a newer watch must still land even if this phone cannot fully decode it.
        val entity = sessions.recordWatchSession(
            sessionId = log.sessionId,
            workoutId = log.workoutId,
            workoutName = log.planName,
            snapshotJson = log.planJson,
            startedAt = log.startedAt,
            endedAt = log.endedAt,
            activeMillis = log.activeMillis,
            wallMillis = log.wallMillis,
            sessionElapsedMillis = log.sessionElapsedMillis,
            completed = log.completedIntervals,
            total = log.totalIntervals,
            skipped = log.skippedIntervals,
            status = if (log.status == SessionStatus.COMPLETED) STATUS_COMPLETED else STATUS_STOPPED,
            rounds = log.roundsLogged,
            weightUnit = log.weightUnit,
        )
        // Sessions under a minute are not kept (the same rule as phone sessions), so there is no
        // row for their events to reference; inserting them would fail the foreign key.
        if (sessions.session(log.sessionId) == null) return
        sessions.recordEvents(log.sessionId, events)
        runCatching { workouts.markUsed(log.workoutId) }
        val prefs = preferences.current()
        if (prefs.healthSyncSessions) {
            runCatching { health.writeSession(entity, events) }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Transport helpers
    // -------------------------------------------------------------------------------------------

    private suspend fun refreshNodes() {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        _watchConnected.value = nodes.isNotEmpty()
    }

    private suspend fun sendToWatch(path: String, payload: ByteArray) {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        for (node in nodes) {
            runCatching { messageClient.sendMessage(node.id, path, payload) }
        }
    }

    private companion object {
        const val KEY_MANIFEST = "manifest"
        const val KEY_PLAN = "plan"
        const val KEY_UPDATED_AT = "updatedAt"
        const val KEY_FAVORITE = "favorite"
        const val KEY_PREFS = "prefs"

        /** A data item is capped at 100 KB by the platform; keep a wide margin per workout. */
        const val MAX_WORKOUT_CHARS = 90_000
        const val LIBRARY_CHAR_BUDGET = 400_000
        const val PREFS_DEBOUNCE_MILLIS = 500L
        const val LIVE_INTERVAL_MILLIS = 900L
        const val REMOTE_TIMEOUT_MILLIS = 15_000L
        const val NODE_REFRESH_MILLIS = 15_000L
    }
}
