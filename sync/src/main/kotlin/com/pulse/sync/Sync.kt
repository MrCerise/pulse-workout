package com.pulse.sync

import com.pulse.engine.CompletionReason
import com.pulse.engine.TimerEvent
import com.pulse.engine.TimerSnapshot
import com.pulse.engine.TimerStatus
import com.pulse.engine.WorkoutPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * PULSE sync protocol — the messages the phone app and the Wear OS app exchange.
 *
 * This module is pure Kotlin (like `:engine`) so both apps compile against the exact same
 * payloads, and so every wire format is unit-testable on the JVM without a device. Nothing here
 * may import Android or Google Play services: the transport layer (data items, messages) lives in
 * each app and only moves the byte arrays produced here.
 *
 * Two transports are used, deliberately:
 *
 *  - **Data items** (`/pulse/…` paths) for durable state — the workout library, preferences.
 *    Data items are replicated by the system and survive reconnects, which is what a library
 *    wants. They are only ever written by their owner (the phone) and consumed by the watch.
 *  - **Messages** (`/pulse/…` paths) for transient traffic — live session snapshots, commands,
 *    finished-session logs. Messages are fire-and-forget and do not queue, which is what a
 *    once-per-second snapshot wants. Session logs are the exception: they are important, so the
 *    watch keeps undelivered ones queued and retries (the phone acknowledges with
 *    [SyncPaths.MESSAGE_LOG_ACK]).
 *
 * Every payload carries [PROTOCOL_VERSION]; a peer that does not understand the version ignores
 * the payload instead of misreading it.
 */

/** Bump when a payload shape changes in a way the other side cannot read. */
const val PROTOCOL_VERSION = 1

/**
 * Paths shared by both sides. Data-item paths are written by the phone and read by the watch;
 * message paths are exchanged in both directions.
 */
object SyncPaths {
    /** Data item: the manifest of workouts the phone expects the watch to hold. */
    const val DATA_LIBRARY = "/pulse/library"

    /** Data item prefix: one item per workout, at `$path/<id>`. */
    const val DATA_WORKOUT_PREFIX = "/pulse/workout/"

    /** Data item: the subset of preferences that is mirrored to the watch. */
    const val DATA_PREFS = "/pulse/prefs"

    /** Message: a live session snapshot, sender → peers, ~1 Hz while a session runs. */
    const val MESSAGE_LIVE = "/pulse/live"

    /** Message: a control command, either direction. */
    const val MESSAGE_COMMAND = "/pulse/cmd"

    /** Message: a finished watch session, watch → phone. */
    const val MESSAGE_LOG = "/pulse/log"

    /** Message: the phone confirmed it stored the session with this id. */
    const val MESSAGE_LOG_ACK = "/pulse/log-ack"

    /** Message: the watch asks the phone to re-push the library and preferences. */
    const val MESSAGE_REQUEST_SYNC = "/pulse/request-sync"

    /** Capability advertised by both apps so a peer can be discovered. */
    const val CAPABILITY = "pulse_sync"

    /** Data-item path of a single workout. */
    fun workout(id: String): String = "$DATA_WORKOUT_PREFIX$id"
}

/** Session status strings, identical to the phone's history values. */
object SessionStatus {
    const val COMPLETED = "COMPLETED"
    const val STOPPED_EARLY = "STOPPED_EARLY"
}

/** Which device a live session is running on. */
object SessionOrigin {
    const val PHONE = "phone"
    const val WATCH = "watch"
}

/**
 * The manifest of the watch's expected library. The watch reconciles against it: workouts that
 * disappear from the manifest are deleted locally, so renames and deletions propagate without a
 * second channel.
 */
@Serializable
data class LibraryManifest(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val appVersion: String = "",
    val workoutIds: List<String> = emptyList(),
    val updatedAt: List<Long> = emptyList(),
    /** True when the phone dropped workouts to stay inside the data-item size budget. */
    val truncated: Boolean = false,
)

/** The subset of user preferences the phone mirrors to the watch. */
@Serializable
data class SyncedPreferences(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val themeMode: String = "SYSTEM",
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val countdownFromSeconds: Int = 3,
    val halfwayAnnouncements: Boolean = true,
)

/** Commands the non-running device can send to the device that owns the session. */
enum class WatchCommandKind { TOGGLE_PAUSE, NEXT, PREVIOUS, ADD_TIME, STOP, COMPLETE_MANUAL }

@Serializable
data class WatchCommand(
    val kind: WatchCommandKind,
    val millis: Long = 0L,
    val protocolVersion: Int = PROTOCOL_VERSION,
)

/** A once-per-second view of a running session, in either direction. */
@Serializable
data class LiveSession(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val origin: String,
    val sessionId: String,
    val planId: String,
    val planName: String,
    val status: String,
    val stepIndex: Int,
    val totalSteps: Int,
    val stepName: String,
    val stepKind: String,
    val manualCompletion: Boolean = false,
    val stepRemainingMillis: Long,
    val stepDurationMillis: Long,
    val sessionElapsedMillis: Long,
    val sessionRemainingMillis: Long? = null,
    val completedSteps: Int,
    val skippedSteps: Int,
    /** Wall-clock send time; receivers drop snapshots that arrive too stale. */
    val sentAt: Long = 0L,
) {
    val isFinished: Boolean get() = status == TimerStatus.FINISHED.name

    companion object {
        fun fromSnapshot(origin: String, sessionId: String, snapshot: TimerSnapshot, sentAt: Long): LiveSession =
            LiveSession(
                origin = origin,
                sessionId = sessionId,
                planId = snapshot.planId,
                planName = snapshot.planName,
                status = snapshot.status.name,
                stepIndex = snapshot.stepIndex,
                totalSteps = snapshot.totalSteps,
                stepName = snapshot.step?.name ?: "",
                stepKind = snapshot.step?.kind?.name ?: "",
                manualCompletion = snapshot.step?.manualCompletion == true,
                stepRemainingMillis = snapshot.stepRemainingMillis,
                stepDurationMillis = snapshot.stepDurationMillis,
                sessionElapsedMillis = snapshot.sessionElapsedMillis,
                sessionRemainingMillis = snapshot.sessionRemainingMillis,
                completedSteps = snapshot.completedSteps,
                skippedSteps = snapshot.skippedSteps,
                sentAt = sentAt,
            )
    }
}

/** One recorded session event, using the phone's history event kinds. */
@Serializable
data class LoggedSessionEvent(
    val kind: String,
    val stepIndex: Int,
    val stepName: String,
    /** Milliseconds from session start. */
    val atMillis: Long,
    val durationMillis: Long = 0L,
    val detail: String? = null,
)

/** A finished watch session, delivered to the phone so it lands in the phone's history. */
@Serializable
data class SessionLog(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val sessionId: String,
    val workoutId: String,
    val planName: String,
    /** Full snapshot of the plan as it was run — history must never be rewritten by later edits. */
    val planJson: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeMillis: Long,
    val wallMillis: Long,
    val sessionElapsedMillis: Long,
    val completedIntervals: Int,
    val totalIntervals: Int,
    val skippedIntervals: Int,
    val status: String,
    val roundsLogged: Int = 0,
    val weightUnit: String? = null,
    val events: List<LoggedSessionEvent> = emptyList(),
)

/**
 * Maps an engine event to the history event kinds the phone stores, or `null` for events with no
 * history meaning (countdowns, halfway marks, periodic cues).
 */
fun TimerEvent.toLoggedEvent(atMillis: Long): LoggedSessionEvent? {
    val step: com.pulse.engine.TimelineStep? = when (this) {
        is TimerEvent.StepStarted -> step
        is TimerEvent.StepCompleted -> step
        is TimerEvent.ManualIntervalCompleted -> step
        is TimerEvent.RoundStarted -> null
        else -> null
    }
    val index = when (this) {
        is TimerEvent.StepStarted -> index
        is TimerEvent.StepCompleted -> index
        is TimerEvent.ManualIntervalCompleted -> index
        is TimerEvent.RoundStarted -> stepIndex
        else -> 0
    }
    return when (this) {
        is TimerEvent.StepStarted ->
            LoggedSessionEvent("INTERVAL_STARTED", index, step?.name ?: "", atMillis)
        is TimerEvent.StepCompleted ->
            LoggedSessionEvent(
                if (reason == CompletionReason.SKIPPED_NEXT) "INTERVAL_SKIPPED" else "INTERVAL_COMPLETED",
                index,
                step?.name ?: "",
                atMillis,
                durationMillis = elapsedMillis,
            )
        is TimerEvent.ManualIntervalCompleted ->
            LoggedSessionEvent("INTERVAL_COMPLETED", index, step?.name ?: "", atMillis, durationMillis = actualMillis)
        is TimerEvent.RoundStarted ->
            LoggedSessionEvent(
                "ROUND_LOGGED",
                index,
                groupName,
                atMillis,
                detail = "Round $round of $rounds",
            )
        TimerEvent.SessionPaused -> LoggedSessionEvent("PAUSED", index, "", atMillis)
        TimerEvent.SessionResumed -> LoggedSessionEvent("RESUMED", index, "", atMillis)
        is TimerEvent.TimeAdded -> LoggedSessionEvent("TIME_ADDED", index, "", atMillis, durationMillis = millis)
        is TimerEvent.LapLogged -> LoggedSessionEvent("LAP", index, "", atMillis, durationMillis = lapMillis)
        is TimerEvent.SessionFinished ->
            LoggedSessionEvent("FINISHED", snapshotIndex(), "", atMillis, durationMillis = elapsedMillis)
        is TimerEvent.Countdown, is TimerEvent.Halfway, is TimerEvent.PeriodicCue -> null
    }
}

private fun TimerEvent.snapshotIndex(): Int = when (this) {
    is TimerEvent.StepStarted -> index
    is TimerEvent.StepCompleted -> index
    is TimerEvent.ManualIntervalCompleted -> index
    is TimerEvent.RoundStarted -> stepIndex
    else -> 0
}

/**
 * One JSON configuration for everything that crosses the wire. The plan codec matches the phone's
 * `PlanCodec` (same `nodeType` discriminator) so a plan serialized on the phone decodes on the
 * watch without a conversion step, and vice versa.
 */
object SyncJson {
    /** Plans use the phone's persisted format: `nodeType` discriminator, defaults encoded. */
    val plans: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "nodeType"
    }

    /** Messages are internal; unknown keys are tolerated so a newer phone still talks to an older watch. */
    val messages: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodePlan(plan: WorkoutPlan): String = plans.encodeToString(WorkoutPlan.serializer(), plan)

    fun decodePlan(text: String): WorkoutPlan = plans.decodeFromString(WorkoutPlan.serializer(), text)

    fun encodeManifest(manifest: LibraryManifest): String =
        messages.encodeToString(LibraryManifest.serializer(), manifest)

    fun decodeManifest(text: String): LibraryManifest? =
        runCatching { messages.decodeFromString(LibraryManifest.serializer(), text) }
            .getOrNull()
            ?.takeIf { it.protocolVersion == PROTOCOL_VERSION }

    fun encodePreferences(preferences: SyncedPreferences): String =
        messages.encodeToString(SyncedPreferences.serializer(), preferences)

    fun decodePreferences(text: String): SyncedPreferences? =
        runCatching { messages.decodeFromString(SyncedPreferences.serializer(), text) }
            .getOrNull()
            ?.takeIf { it.protocolVersion == PROTOCOL_VERSION }

    fun encodeCommand(command: WatchCommand): ByteArray =
        messages.encodeToString(WatchCommand.serializer(), command).toByteArray(Charsets.UTF_8)

    fun decodeCommand(bytes: ByteArray): WatchCommand? =
        runCatching { messages.decodeFromString(WatchCommand.serializer(), bytes.decodeToString()) }
            .getOrNull()
            ?.takeIf { it.protocolVersion == PROTOCOL_VERSION }

    fun encodeLive(live: LiveSession): ByteArray =
        messages.encodeToString(LiveSession.serializer(), live).toByteArray(Charsets.UTF_8)

    fun decodeLive(bytes: ByteArray): LiveSession? =
        runCatching { messages.decodeFromString(LiveSession.serializer(), bytes.decodeToString()) }
            .getOrNull()
            ?.takeIf { it.protocolVersion == PROTOCOL_VERSION }

    fun encodeLog(log: SessionLog): ByteArray =
        messages.encodeToString(SessionLog.serializer(), log).toByteArray(Charsets.UTF_8)

    fun decodeLog(bytes: ByteArray): SessionLog? =
        runCatching { messages.decodeFromString(SessionLog.serializer(), bytes.decodeToString()) }
            .getOrNull()
            ?.takeIf { it.protocolVersion == PROTOCOL_VERSION }

    /** Pending logs are queued as a JSON list on the watch; this is that list's serializer. */
    fun encodeLogQueue(logs: List<SessionLog>): String =
        messages.encodeToString(ListSerializer(SessionLog.serializer()), logs)

    fun decodeLogQueue(text: String): List<SessionLog> =
        runCatching { messages.decodeFromString(ListSerializer(SessionLog.serializer()), text) }
            .getOrDefault(emptyList())
            .filter { it.protocolVersion == PROTOCOL_VERSION }
}
