package com.pulse.intervalcoach.wear.sync

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import com.pulse.intervalcoach.wear.WatchPreferencesRepository
import com.pulse.intervalcoach.wear.WatchWorkoutStore
import com.pulse.intervalcoach.wear.session.WatchSessionPublisher
import com.pulse.sync.LiveSession
import com.pulse.sync.SessionLog
import com.pulse.sync.SessionOrigin
import com.pulse.sync.SyncJson
import com.pulse.sync.SyncPaths
import com.pulse.sync.WatchCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The watch half of the PULSE sync link.
 *
 * Transport rules (see `com.pulse.sync` for the payload formats):
 *  - the phone pushes the workout library, manifest and preferences as **data items**, which the
 *    system replicates and queues across reconnects — durable state belongs there;
 *  - live session snapshots, commands and finished-session logs travel as **messages** — they are
 *    point-in-time and never queue, which is what a 1 Hz snapshot wants;
 *  - finished-session logs are the exception on the message path: they are important, so they are
 *    queued locally and redelivered until the phone acknowledges them.
 *
 * The link also exposes the phone's live session ([remoteSession]) so the watch can mirror and
 * remote-control a workout running on the phone.
 *
 * None of this is required for the watch to work. With no phone — or with the phone link switched
 * off in settings ([com.pulse.intervalcoach.wear.WatchPreferences.syncEnabled]) — every method here
 * becomes a no-op: no data items are applied, nothing is sent, and the watch keeps running the
 * bundled library, its own sessions and its own history. Traffic that is worth keeping (finished
 * sessions) is queued while the link is off and delivered when it is switched back on.
 */
class WearSyncLink(
    private val context: Context,
    private val scope: CoroutineScope,
    private val preferences: WatchPreferencesRepository,
    private val workouts: WatchWorkoutStore,
) : WatchSessionPublisher {

    /** Commands received from the phone are applied to the local session; wired by the container. */
    var onCommand: ((WatchCommand) -> Unit)? = null

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }
    private val capabilityClient: CapabilityClient by lazy { Wearable.getCapabilityClient(context) }

    /**
     * The watch's own switch, read live: flipping it in settings takes effect on the next payload
     * in either direction, without restarting the link.
     */
    private val syncEnabled: Boolean get() = preferences.flow.value.syncEnabled

    private val _remoteSession = MutableStateFlow<LiveSession?>(null)
    /** The phone's live session, or null. Stale snapshots expire on their own. */
    val remoteSession: StateFlow<LiveSession?> = _remoteSession.asStateFlow()

    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    private val _pendingLogCount = MutableStateFlow(0)
    val pendingLogCount: StateFlow<Int> = _pendingLogCount.asStateFlow()

    private var remoteTimeoutJob: Job? = null
    private var lastLogDeliveryAttemptAt = 0L

    private val dataListener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
        try {
            for (event in events) handleDataEvent(event)
        } finally {
            events.release()
        }
    }

    private val messageListener = MessageClient.OnMessageReceivedListener { message: MessageEvent ->
        handleMessage(message)
    }

    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info: CapabilityInfo ->
        onPeersChanged(info.nodes)
    }

    fun start() {
        runCatching { dataClient.addListener(dataListener) }
        runCatching { messageClient.addListener(messageListener) }
        runCatching { capabilityClient.addListener(capabilityListener, SyncPaths.CAPABILITY) }
        scope.launch {
            refreshNodes()
            requestSync()
            deliverPendingLogs()
        }
        scope.launch {
            while (isActive) {
                delay(NODE_REFRESH_MILLIS)
                refreshNodes()
            }
        }
        scope.launch {
            // Delivery is retried on a timer, not only when a peer appears: a session finished
            // while the phone was off is delivered on the next cycle instead of waiting for
            // another session to be run.
            while (isActive) {
                delay(LOG_RETRY_MILLIS)
                deliverPendingLogs()
            }
        }
        scope.launch {
            // Keep the pending-log counter in step with the persisted queue.
            preferences.flow
                .map { it.pendingLogs.size }
                .distinctUntilChanged()
                .collect { _pendingLogCount.value = it }
        }
    }

    // -------------------------------------------------------------------------------------------
    // WatchSessionPublisher
    // -------------------------------------------------------------------------------------------

    override fun onLiveSnapshot(live: LiveSession) {
        if (!syncEnabled) return
        scope.launch { sendToPeers(SyncPaths.MESSAGE_LIVE, SyncJson.encodeLive(live)) }
    }

    override fun onSessionFinished(log: SessionLog) {
        // Queued even when the link is off: the session happened, and turning sync on later must
        // still deliver it rather than quietly dropping the workout.
        scope.launch {
            preferences.queueSessionLog(log)
            deliverPendingLogs()
        }
    }

    /** Sends a command to the phone (remote-controlling a phone session from the watch). */
    fun sendCommand(command: WatchCommand) {
        if (!syncEnabled) return
        scope.launch { sendToPeers(SyncPaths.MESSAGE_COMMAND, SyncJson.encodeCommand(command)) }
    }

    /** Asks the phone to re-push the library and preferences. */
    fun requestSync() {
        if (!syncEnabled) return
        scope.launch { sendToPeers(SyncPaths.MESSAGE_REQUEST_SYNC, ByteArray(0)) }
    }

    // -------------------------------------------------------------------------------------------
    // Inbound
    // -------------------------------------------------------------------------------------------

    private fun handleDataEvent(event: DataEvent) {
        // Sync off means the watch is standalone: the phone's data items are ignored rather than
        // mixed into a library the user asked to keep local.
        if (!syncEnabled) return
        val uri = event.dataItem.uri
        when (event.type) {
            DataEvent.TYPE_CHANGED -> when {
                uri.path == SyncPaths.DATA_LIBRARY -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val manifest = SyncJson.decodeManifest(map.getString(KEY_MANIFEST) ?: return)
                    if (manifest != null) {
                        scope.launch {
                            workouts.applyLibrary(manifest)
                            preferences.markSynced(manifest)
                        }
                    }
                }
                uri.path?.startsWith(SyncPaths.DATA_WORKOUT_PREFIX) == true -> {
                    val id = uri.path?.removePrefix(SyncPaths.DATA_WORKOUT_PREFIX) ?: return
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val planJson = map.getString(KEY_PLAN) ?: return
                    val updatedAt = map.getLong(KEY_UPDATED_AT, 0L)
                    val favorite = map.getBoolean(KEY_FAVORITE, false)
                    scope.launch { workouts.applyWorkout(id, planJson, updatedAt, favorite) }
                }
                uri.path == SyncPaths.DATA_PREFS -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val synced = SyncJson.decodePreferences(map.getString(KEY_PREFS) ?: return)
                    if (synced != null) {
                        scope.launch { preferences.applySynced(synced) }
                    }
                }
            }
            DataEvent.TYPE_DELETED -> Unit // manifest reconciliation on the watch covers deletions
            else -> Unit
        }
    }

    private fun handleMessage(message: MessageEvent) {
        if (!syncEnabled) return
        when (message.path) {
            SyncPaths.MESSAGE_LIVE -> {
                val live = SyncJson.decodeLive(message.data) ?: return
                if (live.origin != SessionOrigin.PHONE) return
                if (live.isFinished) {
                    _remoteSession.value = null
                    remoteTimeoutJob?.cancel()
                } else {
                    _remoteSession.value = live
                    remoteTimeoutJob?.cancel()
                    remoteTimeoutJob = scope.launch {
                        delay(REMOTE_TIMEOUT_MILLIS)
                        _remoteSession.value = null
                    }
                }
            }
            SyncPaths.MESSAGE_COMMAND -> {
                val command = SyncJson.decodeCommand(message.data) ?: return
                onCommand?.invoke(command)
            }
            SyncPaths.MESSAGE_LOG_ACK -> {
                val sessionId = message.data.decodeToString()
                scope.launch { preferences.removeQueuedSessionLog(sessionId) }
            }
        }
    }

    private fun onPeersChanged(nodes: Collection<Node>) {
        _phoneConnected.value = nodes.isNotEmpty()
        if (nodes.isEmpty() || !syncEnabled) return
        scope.launch {
            requestSync()
            deliverPendingLogs()
        }
    }

    // -------------------------------------------------------------------------------------------
    // Outbound
    // -------------------------------------------------------------------------------------------

    private suspend fun refreshNodes() {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        _phoneConnected.value = nodes.isNotEmpty()
    }

    private suspend fun sendToPeers(path: String, payload: ByteArray) {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        for (node in nodes) {
            runCatching { messageClient.sendMessage(node.id, path, payload) }
        }
    }

    private suspend fun deliverPendingLogs() {
        if (!syncEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastLogDeliveryAttemptAt < LOG_RETRY_MILLIS) return
        lastLogDeliveryAttemptAt = now
        val pending = preferences.flow.value.pendingLogs
        if (pending.isEmpty()) return
        for (log in pending) {
            sendToPeers(SyncPaths.MESSAGE_LOG, SyncJson.encodeLog(log))
        }
    }

    private companion object {
        const val KEY_MANIFEST = "manifest"
        const val KEY_PLAN = "plan"
        const val KEY_UPDATED_AT = "updatedAt"
        const val KEY_FAVORITE = "favorite"
        const val KEY_PREFS = "prefs"

        /** A phone snapshot older than this is dropped: the phone has likely stopped. */
        const val REMOTE_TIMEOUT_MILLIS = 15_000L
        const val NODE_REFRESH_MILLIS = 15_000L
        const val LOG_RETRY_MILLIS = 10_000L
    }
}
