package com.pulse.intervalcoach.session

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.pulse.engine.EngineLimits
import com.pulse.engine.ExpansionException
import com.pulse.engine.CompletionReason
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
import com.pulse.intervalcoach.audio.CuePriority
import com.pulse.intervalcoach.audio.CueSoundPlayer
import com.pulse.intervalcoach.audio.FocusEvent
import com.pulse.intervalcoach.audio.Haptics
import com.pulse.intervalcoach.audio.SpeechCoach
import com.pulse.intervalcoach.audio.AudioFocusController
import com.pulse.intervalcoach.data.AudioInterruptionBehavior
import com.pulse.intervalcoach.data.HeadphoneBehavior
import com.pulse.intervalcoach.data.PreferencesRepository
import com.pulse.intervalcoach.data.STATUS_COMPLETED
import com.pulse.intervalcoach.data.STATUS_STOPPED
import com.pulse.intervalcoach.data.SessionRepository
import com.pulse.intervalcoach.data.UserPreferences
import com.pulse.intervalcoach.data.WorkoutRepository
import com.pulse.intervalcoach.data.db.SessionEventEntity
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
    private val limits: EngineLimits = EngineLimits(),
) {
    private val engine = TimerEngine(AndroidMonotonicClock)

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
    private var focusController: AudioFocusController? = null
    private var lastHeartbeatAt = 0L
    private var mutedByFocus = false

    private val eventChannel = Channel<TimerEvent>(capacity = Channel.UNLIMITED)

    init {
        engine.setEventListener { event ->
            // The listener runs on whichever thread commanded the engine; only cheap, non-blocking
            // work happens here. Everything else is handed to the reducer channel.
            eventChannel.trySend(event)
        }
        // Ducking follows the speech engine itself rather than an estimate: the music drops when an
        // utterance is handed over and comes back once the queue is empty.
        speech.onSpeechStarted = { estimatedMillis -> music.onSpeechStarted(estimatedMillis) }
        speech.onSpeechFinished = { music.onSpeechFinished() }
        scope.launch { consumeEvents() }
        scope.launch {
            prefs.flow.collect {
                prefsSnapshot = it
                // Settings changed mid-workout (ducking off, louder music) apply immediately.
                music.configure(it)
                music.setVolume(it.musicVolume)
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------------------------

    /** Expands and validates a plan, then starts a real, recorded session. */
    suspend fun startWorkout(plan: WorkoutPlan): Result<ActiveSession> {
        val current = prefs.current()
        prefsSnapshot = current
        val timeline = try {
            TimelineExpander.expand(plan, limits)
        } catch (e: ExpansionException) {
            return Result.failure(e)
        }
        if (timeline.isEmpty) return Result.failure(IllegalStateException("This workout has no playable intervals."))

        stopInternal(record = false)
        val start = sessionRepository.start(plan)
        val problems = engine.prepare(timeline, plan.id, plan.name, periodicCueMillis = plan.periodicCueMillis)
        if (problems.isNotEmpty()) {
            sessionRepository.finish(
                sessionId = start.sessionId, endedAt = System.currentTimeMillis(), activeMillis = 0, wallMillis = 0,
                sessionElapsedMillis = 0, completed = 0, total = timeline.steps.size, skipped = 0,
                status = STATUS_STOPPED, roundsLogged = 0, weightUnit = current.weightUnit.name,
            )
            return Result.failure(IllegalStateException(problems.joinToString(" ")))
        }
        val errors = engine.start()
        if (errors.isNotEmpty()) return Result.failure(IllegalStateException(errors.joinToString(" ")))

        val session = ActiveSession(
            sessionId = start.sessionId,
            plan = start.plan,
            startedAtWallClock = System.currentTimeMillis(),
            timelineSteps = timeline.steps.size,
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
        requestAudioFocus()
        ensureTicker()
        WorkoutService.start(context)
        return Result.success(session)
    }

    /** Pause because the app lost audio focus / headphones were unplugged, honouring prefs. */
    fun onFocusEvent(event: FocusEvent) {
        val behavior = prefsSnapshot.audioInterruptionBehavior
        when (event) {
            FocusEvent.LOSS -> when (behavior) {
                AudioInterruptionBehavior.PAUSE -> pause()
                AudioInterruptionBehavior.KEEP_TIMING_MUTE_CUES -> muteCues(true)
                AudioInterruptionBehavior.KEEP_TIMING_DUCK -> muteCues(true)
            }
            FocusEvent.LOSS_TRANSIENT -> when (behavior) {
                AudioInterruptionBehavior.PAUSE -> pause()
                else -> muteCues(true)
            }
            // The other app only needs room to be heard (a navigation prompt): lower the music and
            // keep coaching, which is what "may duck" means. Cues stay audible.
            FocusEvent.LOSS_TRANSIENT_CAN_DUCK -> music.setExternalDuck(true)
            FocusEvent.GAIN -> {
                music.setExternalDuck(false)
                if (mutedByFocus) muteCues(false)
            }
        }
    }

    fun onHeadphonesDisconnected() {
        when (prefsSnapshot.headphoneBehavior) {
            HeadphoneBehavior.PAUSE -> {
                pause()
                _lastCueText.value = "Headphones unplugged — workout paused"
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

    fun toggleCuesMuted() = muteCues(!_cuesMuted.value)

    private fun muteCues(muted: Boolean) {
        mutedByFocus = muted
        _cuesMuted.value = muted
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

    /** Ends the session without recording a summary (used internally before starting a new one). */
    private suspend fun stopInternal(record: Boolean) {
        if (_active.value == null) return
        engine.stop()
        if (record) {
            _active.value?.let { finalizeSession(it, stoppedEarly = true) }
        } else {
            teardown()
        }
    }

    private suspend fun finalizeSession(active: ActiveSession, stoppedEarly: Boolean) {
        val snapshot = engine.snapshot()
        val status = if (stoppedEarly && snapshot.status == TimerStatus.FINISHED && snapshot.completedSteps >= snapshot.totalSteps) {
            STATUS_COMPLETED
        } else if (stoppedEarly) {
            STATUS_STOPPED
        } else {
            STATUS_COMPLETED
        }
        val endedAt = System.currentTimeMillis()
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
        )
        runCatching { workoutRepository.markUsed(active.plan.id) }
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
        )
        teardown()
    }

    private fun teardown() {
        tickerJob?.cancel()
        tickerJob = null
        releaseWakeLock()
        focusController?.abandon()
        focusController = null
        soundPlayer.release()
        speech.stop()
        music.stop()
        haptics.cancel()
        _active.value = null
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
        runCatching { sessionRepository.heartbeat(active.sessionId, now) }
    }

    private fun publish() {
        _snapshot.value = engine.snapshot()
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
        if (skipped > 0) _lastCueText.value = (text ?: "") + " (+$skipped intervals passed while the app was busy)"
        lastAnnounceAt = now
    }

    private fun playCue(step: TimelineStep) {
        val cue = if (step.kind == com.pulse.engine.PhaseKind.REST) com.pulse.engine.SoundCue.BEEP else step.sound
        if (step.isIndefinite) return
        soundPlayer.play(cue)
        // The music drops for exactly as long as the tone sounds, then climbs back.
        music.onCue(CueSoundPlayer.durationMillis(cue))
    }

    private fun requestAudioFocus() {
        focusController?.abandon()
        val controller = AudioFocusController(context) { focusEvent -> onFocusEvent(focusEvent) }
        controller.request()
        focusController = controller
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
