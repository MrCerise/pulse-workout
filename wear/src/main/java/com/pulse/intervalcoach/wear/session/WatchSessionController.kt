package com.pulse.intervalcoach.wear.session

import com.pulse.engine.CuePolicy
import com.pulse.engine.ExpansionException
import com.pulse.engine.FinishReason
import com.pulse.engine.MonotonicClock
import com.pulse.engine.TimelineExpander
import com.pulse.engine.TimerEngine
import com.pulse.engine.TimerEvent
import com.pulse.engine.TimerSnapshot
import com.pulse.engine.TimerStatus
import com.pulse.engine.WorkoutPlan
import com.pulse.intervalcoach.wear.WatchPreferences
import com.pulse.sync.LiveSession
import com.pulse.sync.LoggedSessionEvent
import com.pulse.sync.SessionLog
import com.pulse.sync.SessionOrigin
import com.pulse.sync.SessionStatus
import com.pulse.sync.SyncJson
import com.pulse.sync.WatchCommand
import com.pulse.sync.WatchCommandKind
import com.pulse.sync.toLoggedEvent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the running session tells the outside world. */
interface WatchSessionPublisher {

    /** A ~1 Hz snapshot while a session runs, plus one final snapshot with status FINISHED. */
    fun onLiveSnapshot(live: LiveSession)

    /** A finished session that must be delivered to the phone's history. */
    fun onSessionFinished(log: SessionLog)
}

data class ActiveWatchSession(
    val sessionId: String,
    val plan: WorkoutPlan,
    val startedAtWallClock: Long,
)

data class WatchSessionSummary(
    val planName: String,
    val activeMillis: Long,
    val wallMillis: Long,
    val completedIntervals: Int,
    val totalIntervals: Int,
    val skippedIntervals: Int,
    val stoppedEarly: Boolean,
    val endedAt: Long,
)

/**
 * Runs a workout on the watch with the same engine the phone uses.
 *
 * This is the watch's counterpart of the phone's `SessionController`: it owns a
 * [TimerEngine] (same monotonic-clock anchoring, same pause/skip/add-time semantics, same
 * expansion limits), turns engine events into haptic cues, records the event trail, and hands a
 * finished [SessionLog] to the publisher so the session lands in the phone's history. The
 * controller is Android-free — cues and publishing are injected — so the whole lifecycle is
 * unit-testable with a fake clock.
 */
class WatchSessionController(
    private val scope: CoroutineScope,
    private val clock: MonotonicClock,
    private val cues: WatchCues,
    private val publisher: WatchSessionPublisher,
    private val preferences: StateFlow<WatchPreferences>,
    private val wallClock: () -> Long = { System.currentTimeMillis() },
) {

    private var engine = TimerEngine(clock)
    private var finalized = true
    private val loggedEvents = mutableListOf<LoggedSessionEvent>()
    private var tickerJob: Job? = null
    private var lastLivePublishAt = 0L
    private var prefs: WatchPreferences = WatchPreferences()

    private val _snapshot = MutableStateFlow(TimerSnapshot())
    val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()

    private val _active = MutableStateFlow<ActiveWatchSession?>(null)
    val active: StateFlow<ActiveWatchSession?> = _active.asStateFlow()

    private val _summary = MutableStateFlow<WatchSessionSummary?>(null)
    val summary: StateFlow<WatchSessionSummary?> = _summary.asStateFlow()

    init {
        // The engine is rebuilt per session so its cue policy can follow the synced countdown
        // preference; the event listener must follow the engine instance.
        engine.setEventListener(::handleEvent)
        scope.launch {
            preferences.collect { prefs = it }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------------

    /**
     * Expands and starts a workout. A running session is replaced: its log is finalized first so
     * the phone's history keeps it as an early stop instead of losing it.
     */
    @Synchronized
    fun startWorkout(plan: WorkoutPlan): Result<Unit> {
        val timeline = try {
            TimelineExpander.expand(plan)
        } catch (e: ExpansionException) {
            return Result.failure(e)
        }
        if (timeline.isEmpty) {
            return Result.failure(IllegalStateException("This workout has no playable intervals."))
        }

        if (_active.value != null) {
            // Replacing: finalize the running session now, exactly like an explicit early stop.
            engine.stop()
        }

        engine = TimerEngine(
            clock,
            CuePolicy(
                countdownSeconds = (prefs.countdownFromSeconds downTo 1).toList(),
                halfwayCues = prefs.halfwayAnnouncements,
            ),
        )
        engine.setEventListener(::handleEvent)
        val problems = engine.prepare(timeline, plan.id, plan.name, periodicCueMillis = plan.periodicCueMillis)
        if (problems.isNotEmpty()) return Result.failure(IllegalStateException(problems.joinToString(" ")))
        // Event bookkeeping must be reset before start(): engine.start() announces the first step
        // synchronously, and clearing after it would drop INTERVAL_STARTED for step 0.
        loggedEvents.clear()
        finalized = false
        lastLivePublishAt = 0L
        val errors = engine.start()
        if (errors.isNotEmpty()) return Result.failure(IllegalStateException(errors.joinToString(" ")))

        _active.value = ActiveWatchSession(
            sessionId = UUID.randomUUID().toString(),
            plan = plan,
            startedAtWallClock = wallClock(),
        )
        _summary.value = null
        ensureTicker()
        publishSnapshot()
        publishLive(force = true)
        return Result.success(Unit)
    }

    @Synchronized
    fun togglePause() {
        if (_active.value == null) return
        engine.togglePause()
        publishSnapshot()
        publishLive(force = true)
    }

    @Synchronized
    fun next() {
        if (_active.value == null) return
        engine.next()
        publishSnapshot()
        publishLive(force = true)
    }

    @Synchronized
    fun previous() {
        if (_active.value == null) return
        engine.previous()
        publishSnapshot()
        publishLive(force = true)
    }

    @Synchronized
    fun addTime(millis: Long) {
        if (_active.value == null) return
        engine.addTime(millis)
        publishSnapshot()
        publishLive(force = true)
    }

    /** Completes an interval that waits for the user (manual sets, AMRAP laps). */
    @Synchronized
    fun completeManual() {
        if (_active.value == null) return
        engine.completeManual()
        publishSnapshot()
        publishLive(force = true)
    }

    /** Ends the session early. Finalization happens through the engine's finished event. */
    @Synchronized
    fun stop() {
        if (_active.value == null) return
        engine.stop()
        publishSnapshot()
    }

    /** Applies a command that arrived from the phone. */
    @Synchronized
    fun applyCommand(command: WatchCommand) {
        if (_active.value == null) return
        when (command.kind) {
            WatchCommandKind.TOGGLE_PAUSE -> togglePause()
            WatchCommandKind.NEXT -> next()
            WatchCommandKind.PREVIOUS -> previous()
            WatchCommandKind.ADD_TIME -> addTime(command.millis)
            WatchCommandKind.STOP -> stop()
            WatchCommandKind.COMPLETE_MANUAL -> completeManual()
        }
    }

    fun clearSummary() {
        _summary.value = null
    }

    // -------------------------------------------------------------------------------------------
    // Ticking + events
    // -------------------------------------------------------------------------------------------

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val active = _active.value
                if (active != null) {
                    tickOnce()
                    publishLive(force = false)
                }
                delay(TICK_MILLIS)
            }
        }
    }

    @Synchronized
    private fun tickOnce() {
        if (engine.snapshot().isActive) {
            engine.tick()
            publishSnapshot()
        }
    }

    private fun handleEvent(event: TimerEvent) {
        val atMillis = engine.snapshot().sessionElapsedMillis
        when (event) {
            is TimerEvent.StepStarted -> if (!event.resumed) cues.intervalCue(event.step.haptic)
            is TimerEvent.Countdown -> cues.countdownTick()
            is TimerEvent.Halfway -> cues.countdownTick()
            TimerEvent.SessionPaused, TimerEvent.SessionResumed -> cues.intervalCue(com.pulse.engine.HapticCue.LIGHT)
            else -> Unit
        }
        if (!prefs.vibrationEnabled) {
            // Cue output is a no-op unless enabled; the calls above are cheap regardless.
        }
        event.toLoggedEvent(atMillis)?.let {
            if (loggedEvents.size < MAX_LOGGED_EVENTS) loggedEvents += it
        }
        if (event is TimerEvent.SessionFinished && !finalized) {
            finalize(event.reason == FinishReason.TIMELINE_COMPLETED)
        }
    }

    private fun finalize(timelineCompleted: Boolean) {
        val active = _active.value ?: return
        finalized = true
        val snapshot = engine.snapshot()
        val endedAt = wallClock()

        _summary.value = WatchSessionSummary(
            planName = active.plan.name,
            activeMillis = snapshot.sessionElapsedMillis,
            wallMillis = (endedAt - active.startedAtWallClock).coerceAtLeast(0L),
            completedIntervals = snapshot.completedSteps,
            totalIntervals = snapshot.totalSteps,
            skippedIntervals = snapshot.skippedSteps,
            stoppedEarly = !timelineCompleted,
            endedAt = endedAt,
        )
        _active.value = null
        tickerJob?.cancel()
        tickerJob = null

        // One last live snapshot with status FINISHED so the phone drops its remote screen. Built
        // directly because publishLive() refuses once no session is active.
        publisher.onLiveSnapshot(
            LiveSession.fromSnapshot(
                origin = SessionOrigin.WATCH,
                sessionId = active.sessionId,
                snapshot = snapshot,
                sentAt = endedAt,
            ),
        )
        publisher.onSessionFinished(
            SessionLog(
                sessionId = active.sessionId,
                workoutId = active.plan.id,
                planName = active.plan.name,
                planJson = SyncJson.encodePlan(active.plan),
                startedAt = active.startedAtWallClock,
                endedAt = endedAt,
                activeMillis = snapshot.sessionElapsedMillis,
                wallMillis = (endedAt - active.startedAtWallClock).coerceAtLeast(0L),
                sessionElapsedMillis = snapshot.sessionElapsedMillis,
                completedIntervals = snapshot.completedSteps,
                totalIntervals = snapshot.totalSteps,
                skippedIntervals = snapshot.skippedSteps,
                status = if (timelineCompleted) SessionStatus.COMPLETED else SessionStatus.STOPPED_EARLY,
                roundsLogged = snapshot.laps,
                events = loggedEvents.toList(),
            ),
        )
    }

    private fun publishSnapshot() {
        _snapshot.value = engine.snapshot()
    }

    private fun publishLive(force: Boolean, snapshot: TimerSnapshot = engine.snapshot()) {
        val active = _active.value ?: return
        val now = wallClock()
        if (!force && now - lastLivePublishAt < LIVE_INTERVAL_MILLIS) return
        lastLivePublishAt = now
        publisher.onLiveSnapshot(
            LiveSession.fromSnapshot(
                origin = SessionOrigin.WATCH,
                sessionId = active.sessionId,
                snapshot = snapshot,
                sentAt = now,
            ),
        )
    }

    private companion object {
        const val TICK_MILLIS = 100L
        const val LIVE_INTERVAL_MILLIS = 900L
        const val MAX_LOGGED_EVENTS = 2_000
    }
}
