package com.mrcerise.intervalcoach.session

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.pulse.engine.EngineLimits
import com.pulse.engine.ExpansionException
import com.pulse.engine.CompletionReason
import com.pulse.engine.CuePolicy
import com.pulse.engine.FinishReason
import com.pulse.engine.MonotonicClock
import com.pulse.engine.TimelineExpander
import com.pulse.engine.TimelineStep
import com.pulse.engine.TimerEngine
import com.pulse.engine.TimerEvent
import com.pulse.engine.TimerSnapshot
import com.pulse.engine.TimerStatus
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import com.mrcerise.intervalcoach.audio.CuePriority
import com.mrcerise.intervalcoach.audio.CueSoundPlayer
import com.mrcerise.intervalcoach.audio.FocusEvent
import com.mrcerise.intervalcoach.audio.Haptics
import com.mrcerise.intervalcoach.audio.SpeechCoach
import com.mrcerise.intervalcoach.data.AudioInterruptionBehavior
import com.mrcerise.intervalcoach.data.HeadphoneBehavior
import com.mrcerise.intervalcoach.data.PreferencesRepository
import com.mrcerise.intervalcoach.data.STATUS_COMPLETED
import com.mrcerise.intervalcoach.data.STATUS_STOPPED
import com.mrcerise.intervalcoach.data.SessionRepository
import com.mrcerise.intervalcoach.data.UserPreferences
import com.mrcerise.intervalcoach.data.WorkoutRepository
import com.mrcerise.intervalcoach.data.category
import com.mrcerise.intervalcoach.locale.AppLocale
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.db.SessionEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Real elapsed-time source, immune to wall-clock changes. */
object AndroidMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

data class ActiveSession(
    val sessionId: String,
    val plan: WorkoutPlan,
    val startedAtWallClock: Long,
    val timelineSteps: Int,
    /** True when this session was resumed from the database after a restart. */
    val recovered: Boolean = false,
    val journey: com.mrcerise.intervalcoach.journey.JourneyContext? = null,
)

data class SessionSummaryData(
    val sessionId: String,
    val workoutId: String,
    val planName: String,
    val status: String,
    val activeMillis: Long,
    val wallMillis: Long,
    val completedIntervals: Int,
    val totalIntervals: Int,
    val skippedIntervals: Int,
    val roundsLogged: Int,
    val stoppedEarly: Boolean,
    val journey: com.mrcerise.intervalcoach.journey.JourneyContext? = null,
    val endedAt: Long = 0L,
    /** Running system — race totals for a running workout, null otherwise. */
    val raceDistanceMeters: Double? = null,
    val raceAverageSpeedMps: Double? = null,
    val raceTargetDistanceMeters: Double? = null,
    /** "GPS" when measured, "ESTIMATED" when projected from the plan pace. */
    val raceSource: String? = null,
    val raceFinished: Boolean = false,
    /** GPS route taken, empty for estimated / non-running sessions. */
    val raceRoute: List<com.pulse.engine.RacePoint> = emptyList(),
)

/**
 * Orchestrates a workout: owns the engine, the cue output and the session record.
 *
 * Concurrency model: commands are synchronous engine calls (the engine serialises them) and the
 * session record is written by a **single consumer coroutine** fed by a [Channel] — the "serialized
 * event reducer". Nothing writes to the database from the UI thread, and ticks never touch storage
 * (the heartbeat is throttled).
 */
class SessionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sessionRepository: SessionRepository,
    private val workoutRepository: WorkoutRepository,
    private val prefs: PreferencesRepository,
    val soundPlayer: CueSoundPlayer,
    val speech: SpeechCoach,
    val haptics: Haptics,
    private val music: MusicController,
    /** The running system's GPS tracker — started with running workouts, frozen at session end. */
    val runTracker: RunTracker,
    private val limits: EngineLimits = EngineLimits(),
) {
    private val engine = TimerEngine(AndroidMonotonicClock)
    // Serialize the active-session check and creation so rapid taps cannot orphan a running record.
    private val startMutex = Mutex()

    private val _snapshot = MutableStateFlow(TimerSnapshot())
    val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    private val _summary = MutableStateFlow<SessionSummaryData?>(null)
    val summary: StateFlow<SessionSummaryData?> = _summary.asStateFlow()

    private val _cuesMuted = MutableStateFlow(false)
    val cuesMuted: StateFlow<Boolean> = _cuesMuted.asStateFlow()

    private val _lastCueText = MutableStateFlow<String?>(null)
    val lastCueText: StateFlow<String?> = _lastCueText.asStateFlow()

    /** Accelerated, non-recording preview used by "Preview cues" and Voice Studio. */
    private val _preview = MutableStateFlow<PreviewState?>(null)
    val preview: StateFlow<PreviewState?> = _preview.asStateFlow()

    data class PreviewState(val stepIndex: Int, val stepName: String, val totalSteps: Int, val finished: Boolean)

    private var prefsSnapshot: UserPreferences = UserPreferences()
    private var tickerJob: Job? = null
    private var previewJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastHeartbeatAt = 0L
    private var mutedByFocus = false
    private var mutedByUser = false
    /** The session [finalizeSession] has already recorded; guards against a second finalize. */
    private var finalizedSessionId: String? = null

    private val eventChannel = Channel<TimerEvent>(capacity = Channel.UNLIMITED)

    init {
        engine.setEventListener { event ->
            // The listener runs on whichever thread commanded the engine; only cheap, non-blocking
            // work happens here. Everything else is handed to the reducer channel.
            eventChannel.trySend(event)
        }
        music.onFocusEvent = ::onFocusEvent
        scope.launch { consumeEvents() }
        scope.launch {
            prefs.flow.collect {
                prefsSnapshot = it
                // Keep live coaching controls in sync too; music observes prefs in AppContainer.
                speech.rate = it.voiceRate
                speech.pitch = it.voicePitch
                speech.verbose = it.verbosity
                soundPlayer.volume = it.cueVolume
                haptics.enabled = it.vibrationEnabled
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------------------------

    /** Expands and validates a plan, then starts a real, recorded session. */
    suspend fun startWorkout(
        plan: WorkoutPlan,
        replaceActiveSession: Boolean = false,
        journey: com.mrcerise.intervalcoach.journey.JourneyContext? = null,
    ): Result<ActiveSession> = startMutex.withLock {
        val current = prefs.current()
        prefsSnapshot = current
        val timeline = try {
            TimelineExpander.expand(plan, limits)
        } catch (e: ExpansionException) {
            return@withLock Result.failure(e)
        }
        if (timeline.isEmpty) {
            return@withLock Result.failure(IllegalStateException("This workout has no playable intervals."))
        }

        if (_active.value != null) {
            if (!replaceActiveSession) {
                return@withLock Result.failure(
                    IllegalStateException("Confirm ending the current workout before starting another.")
                )
            }
            stopInternal()
        }
        // The player and notification show the last cue; never carry one over from the last session.
        // Cleared before engine.start(), whose first announcement the reducer may publish at once.
        _lastCueText.value = null
        val start = sessionRepository.start(plan, journey = journey)
        val problems = engine.prepare(timeline, plan.id, plan.name, periodicCueMillis = plan.periodicCueMillis)
        if (problems.isNotEmpty()) {
            sessionRepository.finish(
                sessionId = start.sessionId, endedAt = System.currentTimeMillis(), activeMillis = 0, wallMillis = 0,
                sessionElapsedMillis = 0, completed = 0, total = timeline.steps.size, skipped = 0,
                status = STATUS_STOPPED, roundsLogged = 0, weightUnit = current.weightUnit.name,
            )
            return@withLock Result.failure(IllegalStateException(problems.joinToString(" ")))
        }
        // The user's countdown length and halfway settings, fixed for the length of this session.
        engine.setCuePolicy(
            CuePolicy(
                countdownSeconds = current.countdownThresholds,
                halfwayCues = current.halfwayAnnouncements,
                halfwayMinMillis = current.halfwayMinSeconds * 1000L,
            ),
        )
        val errors = engine.start()
        if (errors.isNotEmpty()) return@withLock Result.failure(IllegalStateException(errors.joinToString(" ")))

        val session = ActiveSession(
            sessionId = start.sessionId,
            plan = start.plan,
            startedAtWallClock = System.currentTimeMillis(),
            timelineSteps = timeline.steps.size,
            journey = journey,
        )
        _active.value = session
        _summary.value = null
        speech.rate = current.voiceRate
        speech.pitch = current.voicePitch
        speech.verbose = current.verbosity
        speech.initialise()
        soundPlayer.volume = current.cueVolume
        haptics.enabled = current.vibrationEnabled
        music.configure(current)
        music.start()
        acquireWakeLockIfNeeded(current)
        // The running system: GPS race info for categories that carry it, projected pace otherwise.
        if (plan.category.capabilities.supportsRaceInfo) {
            runTracker.start(plan)
        }
        // Publish the active state before the service observes it; otherwise its first snapshot
        // can still be inactive and stop the GPS foreground service immediately after startup.
        publish()
        WorkoutService.start(context)
        ensureTicker()
        Result.success(session)
    }

    /** Pause because the app lost audio focus / headphones were unplugged, honouring prefs. */
    fun onFocusEvent(event: FocusEvent) {
        if (_active.value == null) return
        val behavior = prefsSnapshot.audioInterruptionBehavior
        when (event) {
            FocusEvent.LOSS -> when (behavior) {
                AudioInterruptionBehavior.PAUSE -> pause()
                AudioInterruptionBehavior.KEEP_TIMING_MUTE_CUES -> setFocusMuted(true)
                // A permanent loss never comes back with GAIN, so muting here silenced the coach for
                // the rest of the session. "Duck" keeps coaching: each cue requests may-duck focus
                // again and lowers the other app while it speaks.
                AudioInterruptionBehavior.KEEP_TIMING_DUCK -> Unit
            }
            FocusEvent.LOSS_TRANSIENT -> when (behavior) {
                AudioInterruptionBehavior.PAUSE -> pause()
                else -> setFocusMuted(true)
            }
            // The other app only needs room to be heard (a navigation prompt): lower the music and
            // keep coaching, which is what "may duck" means. Cues stay audible.
            FocusEvent.LOSS_TRANSIENT_CAN_DUCK -> music.setExternalDuck(true)
            FocusEvent.GAIN -> {
                music.setExternalDuck(false)
                if (mutedByFocus) setFocusMuted(false)
            }
        }
    }

    fun onHeadphonesDisconnected() {
        if (_active.value == null) return
        when (prefsSnapshot.headphoneBehavior) {
            HeadphoneBehavior.PAUSE -> {
                pause()
                _lastCueText.value = localizedString(R.string.cue_headphones_paused)
            }
            HeadphoneBehavior.KEEP_GOING -> Unit
        }
    }

    fun togglePause() {
        if (_active.value == null) return
        engine.togglePause()
        syncMusicWithEngine()
        publish()
    }

    fun pause() {
        engine.pause()
        syncMusicWithEngine()
        publish()
    }

    fun resume() {
        engine.resume()
        syncMusicWithEngine()
        publish()
    }

    /** Background audio follows the workout: pausing stops the track, resuming brings it back. */
    private fun syncMusicWithEngine() {
        if (engine.snapshot().isPaused) music.pause() else music.play()
    }

    fun next() {
        engine.next()
        publish()
    }

    fun previous() {
        engine.previous()
        publish()
    }

    fun addTime(millis: Long) {
        engine.addTime(millis)
        publish()
    }

    fun completeManual() {
        engine.completeManual()
        publish()
    }

    fun lap() {
        engine.lap()
        publish()
    }

    /**
     * The player's mute button. Unmuting clears an audio-focus mute too: otherwise a focus loss that
     * never returned a GAIN left the cues muted with a button that only flipped the user's own flag.
     */
    fun toggleCuesMuted() {
        if (_cuesMuted.value) {
            mutedByUser = false
            mutedByFocus = false
        } else {
            mutedByUser = true
        }
        updateCueMute()
    }

    private fun setFocusMuted(muted: Boolean) {
        mutedByFocus = muted
        updateCueMute()
    }

    private fun updateCueMute() {
        _cuesMuted.value = mutedByUser || mutedByFocus
        if (_cuesMuted.value) speech.stop()
    }

    /** Ends the session. [stoppedEarly] distinguishes "finish early" from a completed timeline. */
    fun endSession(stoppedEarly: Boolean) {
        val active = _active.value ?: return
        val snapshotBefore = engine.snapshot()
        if (stoppedEarly && snapshotBefore.status != TimerStatus.FINISHED) {
            engine.stop()
        }
        publish()
        scope.launch { finalizeSession(active, stoppedEarly) }
    }

    /** Finalizes an explicitly replaced session instead of leaving an orphaned RUNNING record. */
    private suspend fun stopInternal() {
        val active = _active.value ?: return
        engine.stop()
        finalizeSession(active, stoppedEarly = true)
    }

    /**
     * Claims the right to finalize [active] exactly once. A completed timeline and a Stop press (from
     * the player, the notification or the watch) can both reach [finalizeSession] on the
     * multi-threaded app scope, and only the first may write the record and tear down.
     */
    private fun claimFinalize(active: ActiveSession): Boolean = synchronized(this) {
        if (finalizedSessionId == active.sessionId) {
            false
        } else {
            finalizedSessionId = active.sessionId
            true
        }
    }

    private suspend fun finalizeSession(active: ActiveSession, stoppedEarly: Boolean) {
        if (!claimFinalize(active)) return
        val snapshot = engine.snapshot()
        val status = if (stoppedEarly && snapshot.status == TimerStatus.FINISHED && snapshot.completedSteps >= snapshot.totalSteps) {
            STATUS_COMPLETED
        } else if (stoppedEarly) {
            STATUS_STOPPED
        } else {
            STATUS_COMPLETED
        }
        val endedAt = System.currentTimeMillis()
        // Freeze the race read-out before the summary is built: the tracker is the only thing that
        // knows how far the runner actually went.
        val (race, route) = runTracker.stopAndFreeze()
        sessionRepository.finish(
            sessionId = active.sessionId,
            endedAt = endedAt,
            activeMillis = snapshot.sessionElapsedMillis,
            wallMillis = endedAt - active.startedAtWallClock,
            sessionElapsedMillis = snapshot.sessionElapsedMillis,
            completed = snapshot.completedSteps,
            total = snapshot.totalSteps,
            skipped = snapshot.skippedSteps,
            status = status,
            roundsLogged = snapshot.laps,
            weightUnit = prefsSnapshot.weightUnit.name,
            routeJson = com.mrcerise.intervalcoach.data.RouteCodec.encode(route),
        )
        if (active.journey == null) runCatching { workoutRepository.markUsed(active.plan.id) }
        else if (com.mrcerise.intervalcoach.journey.JourneyProgress.qualifies(
            status, snapshot.completedSteps, snapshot.totalSteps, snapshot.skippedSteps)) {
            runCatching { Notifications.manager(context).cancel(4202) }
        }
        _summary.value = SessionSummaryData(
            sessionId = active.sessionId,
            workoutId = active.plan.id,
            planName = active.plan.name,
            status = status,
            activeMillis = snapshot.sessionElapsedMillis,
            wallMillis = endedAt - active.startedAtWallClock,
            completedIntervals = snapshot.completedSteps,
            totalIntervals = snapshot.totalSteps,
            skippedIntervals = snapshot.skippedSteps,
            roundsLogged = snapshot.laps,
            stoppedEarly = stoppedEarly,
            journey = active.journey,
            endedAt = endedAt,
            raceDistanceMeters = race?.distanceMeters,
            raceAverageSpeedMps = race?.takeIf { it.movingMillis > 0L }?.averageSpeedMps,
            raceTargetDistanceMeters = race?.targetDistanceMeters,
            raceSource = race?.source?.name,
            raceFinished = race?.finished == true,
            raceRoute = route,
        )
        // A completed timeline has just queued its "workout complete" line; let it finish.
        teardown(silenceSpeech = stoppedEarly)
    }

    private fun teardown(silenceSpeech: Boolean = true) {
        tickerJob?.cancel()
        tickerJob = null
        releaseWakeLock()
        soundPlayer.release()
        if (silenceSpeech) speech.stop()
        music.stop()
        haptics.cancel()
        runTracker.stop()
        _active.value = null
        mutedByUser = false
        mutedByFocus = false
        _cuesMuted.value = false
        WorkoutService.stop(context)
    }

    fun clearSummary() {
        _summary.value = null
    }

    // -------------------------------------------------------------------------------------------
    // Ticking + event reduction
    // -------------------------------------------------------------------------------------------

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                if (engine.snapshot().isActive) {
                    engine.tick()
                    publish()
                    maybeHeartbeat()
                }
                delay(100)
            }
        }
    }

    private suspend fun maybeHeartbeat() {
        val active = _active.value ?: return
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatAt = now
        runCatching { sessionRepository.heartbeat(active.sessionId, now, engine.snapshot().sessionElapsedMillis) }
    }

    private fun publish() {
        val snapshot = engine.snapshot()
        // The race clock follows the timer: paused workouts cover no distance.
        runTracker.setPaused(snapshot.isPaused || !snapshot.isActive)
        _snapshot.value = snapshot
    }

    private suspend fun consumeEvents() {
        val pending = mutableListOf<SessionEventEntity>()
        for (event in eventChannel) {
            val active = _active.value
            val offset = engine.snapshot().sessionElapsedMillis
            handleCues(event)
            if (active != null) {
                pending += event.toEntity(active.sessionId, offset)
                if (pending.size >= 12 || event is TimerEvent.SessionFinished) {
                    flush(pending)
                }
            }
            if (event is TimerEvent.SessionFinished) {
                flush(pending)
                _snapshot.value = engine.snapshot()
                // Nothing else ends a session whose timeline ran out: without this the record stays
                // RUNNING, no summary is shown and the next start is refused as a replacement.
                // The status check keeps a late event from finalizing a session started since.
                if (event.reason == FinishReason.TIMELINE_COMPLETED && active != null &&
                    engine.snapshot().status == TimerStatus.FINISHED
                ) {
                    finalizeSession(active, stoppedEarly = false)
                }
            } else {
                _snapshot.value = engine.snapshot()
            }
        }
    }

    private suspend fun flush(pending: MutableList<SessionEventEntity>) {
        if (pending.isEmpty()) return
        val copy = pending.toList()
        pending.clear()
        runCatching { sessionRepository.recordEvents(copy.first().sessionId, copy) }
    }

    /** Turns engine events into sound, speech and haptics. Multiple boundary crossings in one tick
     *  are coalesced: sounds are capped and only the newest interval is announced. */
    private var pendingAnnouncements = mutableListOf<Pair<TimelineStep, TimelineStep?>>()
    private var lastAnnounceAt = 0L

    private suspend fun handleCues(event: TimerEvent) {
        if (_cuesMuted.value) return
        val cuesMutedNow = _cuesMuted.value
        when (event) {
            is TimerEvent.StepStarted -> {
                if (!cuesMutedNow) {
                    if (!event.resumed) playCue(event.step)
                    haptics.vibrate(event.step.haptic)
                }
                val next = engine.snapshot().nextStep
                pendingAnnouncements += event.step to next
                maybeAnnounce()
            }
            is TimerEvent.Countdown -> {
                if (!cuesMutedNow) {
                    speech.speak(CueScript.countdown(event.secondsRemaining, prefsSnapshot) ?: "", CuePriority.COUNTDOWN)
                }
            }
            is TimerEvent.Halfway -> {
                if (!cuesMutedNow && prefsSnapshot.halfwayAnnouncements) {
                    speech.speak(CueScript.halfway(event.step), CuePriority.INFO)
                }
            }
            is TimerEvent.PeriodicCue -> {
                if (!cuesMutedNow) {
                    music.onCue(CueSoundPlayer.durationMillis(com.pulse.engine.SoundCue.CHIME))
                    soundPlayer.play(com.pulse.engine.SoundCue.CHIME)
                    speech.speak(CueScript.periodicCue(engine.snapshot().step), CuePriority.INFO)
                }
            }
            is TimerEvent.LapLogged -> {
                if (!cuesMutedNow) speech.speak(CueScript.lap(event.lapNumber, event.lapMillis), CuePriority.INFO)
            }
            is TimerEvent.ManualIntervalCompleted -> {
                if (!cuesMutedNow) speech.speak(CueScript.manualComplete(event.step), CuePriority.INFO)
            }
            is TimerEvent.SessionPaused -> {
                speech.speak(CueScript.paused(), CuePriority.INFO)
            }
            is TimerEvent.SessionResumed -> {
                speech.speak(CueScript.resumed(engine.snapshot().step), CuePriority.INFO)
            }
            is TimerEvent.SessionFinished -> {
                val snapshot = engine.snapshot()
                speech.speak(
                    CueScript.finished(
                        stoppedEarly = event.reason == FinishReason.USER_STOPPED,
                        completed = snapshot.completedSteps,
                        total = snapshot.totalSteps,
                    ),
                    CuePriority.INTERVAL_CHANGE,
                )
            }
            is TimerEvent.StepCompleted, is TimerEvent.TimeAdded, is TimerEvent.RoundStarted -> Unit
        }
    }

    private suspend fun maybeAnnounce() {
        if (pendingAnnouncements.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val active = _active.value ?: return
        val latest = pendingAnnouncements.last()
        val skipped = pendingAnnouncements.size - 1
        pendingAnnouncements.clear()
        val text = CueScript.intervalStart(latest.first, latest.second, prefsSnapshot, active.plan.type)
        if (text != null) {
            _lastCueText.value = text
            speech.speak(text, CuePriority.INTERVAL_CHANGE)
        }
        if (skipped > 0) {
            _lastCueText.value = ((text ?: "") + " " + localizedString(R.string.cue_intervals_passed, skipped)).trim()
        }
        lastAnnounceAt = now
    }

    /** UI text in the app's chosen language; [context] is the application context. */
    private fun localizedString(id: Int, vararg args: Any): String =
        AppLocale.localizedContext(context, prefsSnapshot.appLanguage).getString(id, *args)

    private fun playCue(step: TimelineStep) {
        val cue = if (step.kind == com.pulse.engine.PhaseKind.REST) com.pulse.engine.SoundCue.BEEP else step.sound
        if (step.isIndefinite) return
        music.onCue(CueSoundPlayer.durationMillis(cue))
        soundPlayer.play(cue)
    }

    // -------------------------------------------------------------------------------------------
    // Power
    // -------------------------------------------------------------------------------------------

    private fun acquireWakeLockIfNeeded(current: UserPreferences) {
        if (!current.continueCuesScreenOff) return
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val lock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulse:workout")
        lock.setReferenceCounted(false)
        runCatching { lock.acquire(MAX_SESSION_WAKE_LOCK_MS) }
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    // -------------------------------------------------------------------------------------------
    // Accelerated preview (never records a session)
    // -------------------------------------------------------------------------------------------

    fun startPreview(plan: WorkoutPlan, millisPerStep: Long = 1_200L, announce: Boolean = true) {
        previewJob?.cancel()
        val timeline = runCatching { TimelineExpander.expand(plan, limits) }.getOrNull() ?: return
        if (timeline.isEmpty) return
        speech.initialise()
        previewJob = scope.launch {
            timeline.steps.forEachIndexed { index, step ->
                _preview.value = PreviewState(index, step.name, timeline.steps.size, finished = false)
                if (announce) {
                    soundPlayer.play(step.sound)
                    haptics.vibrate(step.haptic)
                    speech.speakPreview(step.name)
                }
                delay(millisPerStep)
            }
            _preview.value = PreviewState(timeline.steps.lastIndex, timeline.steps.last().name, timeline.steps.size, finished = true)
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        speech.stop()
        soundPlayer.release()
        _preview.value = null
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val MAX_SESSION_WAKE_LOCK_MS = 8 * 60 * 60 * 1000L
    }
}

private fun TimerEvent.toEntity(sessionId: String, offset: Long): SessionEventEntity = when (this) {
    is TimerEvent.StepStarted -> SessionEventEntity(
        sessionId = sessionId, stepIndex = index, stepName = step.name, kind = "INTERVAL_STARTED", atMillis = offset,
    )
    is TimerEvent.StepCompleted -> SessionEventEntity(
        sessionId = sessionId,
        stepIndex = index,
        stepName = step.name,
        kind = if (reason == CompletionReason.SKIPPED_NEXT) "INTERVAL_SKIPPED" else "INTERVAL_COMPLETED",
        atMillis = offset,
        durationMillis = elapsedMillis,
    )
    is TimerEvent.Countdown -> SessionEventEntity(
        sessionId = sessionId,
        stepIndex = index,
        stepName = step.name,
        kind = "COUNTDOWN",
        atMillis = offset,
        detail = secondsRemaining.toString(),
    )
    is TimerEvent.Halfway -> SessionEventEntity(
        sessionId = sessionId, stepIndex = index, stepName = step.name, kind = "HALFWAY", atMillis = offset,
    )
    is TimerEvent.RoundStarted -> SessionEventEntity(
        sessionId = sessionId,
        stepIndex = stepIndex,
        stepName = groupName,
        kind = "ROUND_STARTED",
        atMillis = offset,
        detail = "$round/$rounds",
    )
    is TimerEvent.TimeAdded -> SessionEventEntity(
        sessionId = sessionId, stepIndex = index, stepName = "", kind = "TIME_ADDED", atMillis = offset, durationMillis = millis,
    )
    is TimerEvent.LapLogged -> SessionEventEntity(
        sessionId = sessionId, stepIndex = index, stepName = "", kind = "LAP", atMillis = offset,
        durationMillis = lapMillis, detail = lapNumber.toString(),
    )
    is TimerEvent.PeriodicCue -> SessionEventEntity(
        sessionId = sessionId, stepIndex = -1, stepName = "", kind = "PERIODIC_CUE", atMillis = offset,
        detail = occurrence.toString(),
    )
    is TimerEvent.ManualIntervalCompleted -> SessionEventEntity(
        sessionId = sessionId, stepIndex = index, stepName = step.name, kind = "INTERVAL_COMPLETED",
        atMillis = offset, durationMillis = actualMillis, detail = "MANUAL",
    )
    TimerEvent.SessionPaused -> SessionEventEntity(
        sessionId = sessionId, stepIndex = -1, stepName = "", kind = "PAUSED", atMillis = offset,
    )
    TimerEvent.SessionResumed -> SessionEventEntity(
        sessionId = sessionId, stepIndex = -1, stepName = "", kind = "RESUMED", atMillis = offset,
    )
    is TimerEvent.SessionFinished -> SessionEventEntity(
        sessionId = sessionId, stepIndex = -1, stepName = "", kind = "FINISHED", atMillis = offset,
        detail = "${reason.name}:$completedSteps/$skippedSteps",
    )
}
