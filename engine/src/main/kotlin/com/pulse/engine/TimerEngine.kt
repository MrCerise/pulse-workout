package com.pulse.engine

/**
 * Monotonic time source. Production uses `SystemClock.elapsedRealtime()`; tests use [FakeClock].
 *
 * Wall-clock time is deliberately never used for timing: changing the device clock or a timezone
 * cannot move an active interval.
 */
fun interface MonotonicClock {
    fun nowMillis(): Long
}

/** Deterministic clock for unit tests. */
class FakeClock(private var current: Long = 0L) : MonotonicClock {
    override fun nowMillis(): Long = current
    fun advance(millis: Long): FakeClock {
        require(millis >= 0) { "cannot travel backwards in monotonic time" }
        current += millis
        return this
    }
    fun advanceTo(millis: Long): FakeClock {
        require(millis >= current) { "cannot travel backwards in monotonic time" }
        current = millis
        return this
    }
}

enum class TimerStatus {
    /** No session loaded. */
    IDLE,
    /** Timeline loaded, waiting for start. */
    READY,
    RUNNING,
    PAUSED,
    /** Current interval waits for the user (manual set / AMRAP lap press). */
    AWAITING_MANUAL,
    FINISHED,
}

/** Why a step ended, recorded in history so summaries can distinguish real work from skips. */
enum class CompletionReason { EXPIRED, SKIPPED_NEXT, STOPPED }

enum class FinishReason { TIMELINE_COMPLETED, USER_STOPPED }

/** Immutable view of the session used by UI, notification and audio layers. */
data class TimerSnapshot(
    val status: TimerStatus = TimerStatus.IDLE,
    val planId: String = "",
    val planName: String = "",
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val step: TimelineStep? = null,
    val nextStep: TimelineStep? = null,
    val stepDurationMillis: Long = 0L,
    val stepElapsedMillis: Long = 0L,
    val stepRemainingMillis: Long = 0L,
    val sessionElapsedMillis: Long = 0L,
    /** Null while the session contains open-ended intervals. */
    val sessionRemainingMillis: Long? = null,
    val intervalProgress: Float = 0f,
    val sessionProgress: Float = 0f,
    val completedSteps: Int = 0,
    val skippedSteps: Int = 0,
    val laps: Int = 0,
) {
    val isActive: Boolean get() = status == TimerStatus.RUNNING || status == TimerStatus.PAUSED ||
        status == TimerStatus.AWAITING_MANUAL

    /** Integer seconds left, rounded up so "1" is shown for the final second. */
    val stepRemainingSeconds: Int get() = ((stepRemainingMillis + 999) / 1000).toInt()

    val isPaused: Boolean get() = status == TimerStatus.PAUSED
}

/** Everything the outside world needs to know. Emitted in strict order by the engine. */
sealed interface TimerEvent {
    data class StepStarted(
        val index: Int,
        val step: TimelineStep,
        val totalSteps: Int,
        val resumed: Boolean = false,
    ) : TimerEvent

    data class StepCompleted(
        val index: Int,
        val step: TimelineStep,
        val reason: CompletionReason,
        val elapsedMillis: Long,
    ) : TimerEvent

    /** Warning cue `secondsRemaining` seconds before a step ends (3-2-1 countdown etc.). */
    data class Countdown(val index: Int, val step: TimelineStep, val secondsRemaining: Int) : TimerEvent

    data class Halfway(val index: Int, val step: TimelineStep) : TimerEvent

    data class RoundStarted(
        val groupId: String,
        val groupName: String,
        val round: Int,
        val rounds: Int,
        val stepIndex: Int,
    ) : TimerEvent

    data class TimeAdded(val index: Int, val millis: Long) : TimerEvent

    data class LapLogged(val index: Int, val lapNumber: Int, val lapMillis: Long, val totalMillis: Long) : TimerEvent

    /** Repeating cue timer tick (independent of interval boundaries). */
    data class PeriodicCue(val occurrence: Int, val everyMillis: Long, val sessionElapsedMillis: Long) : TimerEvent

    data class ManualIntervalCompleted(val index: Int, val step: TimelineStep, val actualMillis: Long) : TimerEvent

    data object SessionPaused : TimerEvent
    data object SessionResumed : TimerEvent

    data class SessionFinished(
        val reason: FinishReason,
        val elapsedMillis: Long,
        val completedSteps: Int,
        val skippedSteps: Int,
    ) : TimerEvent
}

/** Cue configuration shared by the player, the voice studio preview and the engine tests. */
data class CuePolicy(
    val countdownSeconds: List<Int> = listOf(3, 2, 1),
    val halfwayCues: Boolean = true,
    /** Only emit halfway cues for intervals at least this long. */
    val halfwayMinMillis: Long = 60_000L,
    val suppressCountdownUnderMillis: Long = 2_000L,
)

/**
 * The interval timer state machine.
 *
 * Design notes:
 *  - All state lives behind this single object; every public command is `@Synchronized`, so the UI,
 *    the foreground service and the notification actions can never race each other. Callers never
 *    mutate state directly.
 *  - Timing is anchored to [MonotonicClock] readings only. Elapsed time is `accumulated + (now - anchor)`
 *    while running, which makes pause/resume exact and immune to wall-clock edits.
 *  - Advancing across interval boundaries is a loop in [settle], so several intervals can complete in
 *    one settle (e.g. after a long tick gap) without duplicating transitions or history records.
 *  - The engine never touches Android APIs, audio, or the database: it returns [TimerEvent]s and the app
 *    layer decides what they mean.
 */
class TimerEngine(
    private val clock: MonotonicClock,
    private val cuePolicy: CuePolicy = CuePolicy(),
) {
    private var timeline: ExpandedTimeline = ExpandedTimeline(emptyList(), 0L, 0, emptyList())
    private var planId: String = ""
    private var planName: String = ""
    private var periodicCueMillis: Long? = null

    private var status: TimerStatus = TimerStatus.IDLE

    /** Session-elapsed milliseconds accumulated up to the last anchor reset. */
    private var accumulated: Long = 0L

    /** Clock reading of the last resume/start; null while paused or idle. */
    private var anchor: Long? = null

    /** Session-elapsed value at which the current step began. */
    private var stepStartAt: Long = 0L

    private var stepIndex: Int = 0
    private val completedStepIndexes: MutableSet<Int> = linkedSetOf()
    private var skippedSteps: Int = 0

    /** Distinct intervals that ran to completion (a rewind + re-run counts once). */
    private val completedSteps: Int get() = completedStepIndexes.size

    /** Extra time added by "Add time", keyed by step index. */
    private val extraTime: MutableMap<Int, Long> = linkedMapOf()

    /** Cue de-duplication: "stepIndex:cueTag". */
    private val firedCues: MutableSet<String> = mutableSetOf()

    private var lastPeriodicCue: Int = 0
    private var lapCount: Int = 0
    private var lastLapAt: Long = 0L

    private val _events = mutableListOf<TimerEvent>()
    private var onEvent: ((TimerEvent) -> Unit)? = null

    /** Sets the consumer for engine events. Called by the app-layer session controller. */
    fun setEventListener(listener: (TimerEvent) -> Unit) { onEvent = listener }

    private fun emit(event: TimerEvent) {
        _events += event
        onEvent?.invoke(event)
    }

    // -------------------------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------------------------

    /** Loads a timeline and reports whether it can be run. Returns validation problems. */
    @Synchronized
    fun prepare(timeline: ExpandedTimeline, planId: String, planName: String, periodicCueMillis: Long? = null): List<String> {
        val problems = mutableListOf<String>()
        if (timeline.isEmpty) problems += "This workout has no playable intervals."
        if (problems.isNotEmpty()) return problems
        reset()
        this.timeline = timeline
        this.planId = planId
        this.planName = planName
        this.periodicCueMillis = periodicCueMillis
        status = TimerStatus.READY
        return problems
    }

    @Synchronized
    fun start(): List<String> {
        if (timeline.isEmpty) {
            return listOf("Nothing to run: load a workout first.")
        }
        if (status == TimerStatus.IDLE) {
            return listOf("Load a workout before starting.")
        }
        reset()
        status = TimerStatus.RUNNING
        anchor = clock.nowMillis()
        stepStartAt = 0L
        stepIndex = 0
        announceStep(resumed = false)
        settle()
        return emptyList()
    }

    @Synchronized
    fun pause() {
        if (status != TimerStatus.RUNNING) return
        accumulated += clock.nowMillis() - (anchor ?: clock.nowMillis())
        anchor = null
        status = TimerStatus.PAUSED
        emit(TimerEvent.SessionPaused)
    }

    @Synchronized
    fun resume() {
        if (status != TimerStatus.PAUSED) return
        anchor = clock.nowMillis()
        status = TimerStatus.RUNNING
        emit(TimerEvent.SessionResumed)
        settle()
    }

    @Synchronized
    fun togglePause() {
        when (status) {
            TimerStatus.RUNNING -> pause()
            TimerStatus.PAUSED -> resume()
            TimerStatus.AWAITING_MANUAL -> settle() // tapping play on a manual interval just re-checks state
            else -> Unit
        }
    }

    /** Ends the session early. Returns the final snapshot; a SessionFinished event is emitted. */
    @Synchronized
    fun stop(): TimerSnapshot {
        if (!isLoaded() || status == TimerStatus.FINISHED) return snapshot()
        val elapsed = currentPosition()
        val current = currentStep()
        if (current != null) {
            emit(TimerEvent.StepCompleted(stepIndex, current, CompletionReason.STOPPED, stepElapsed(elapsed)))
        }
        freezeAt(elapsed)
        status = TimerStatus.FINISHED
        emit(
            TimerEvent.SessionFinished(
                reason = FinishReason.USER_STOPPED,
                elapsedMillis = elapsed,
                completedSteps = completedSteps,
                skippedSteps = skippedSteps,
            )
        )
        return snapshot()
    }

    @Synchronized
    fun reset() {
        accumulated = 0L
        anchor = null
        stepStartAt = 0L
        stepIndex = 0
        completedStepIndexes.clear()
        skippedSteps = 0
        extraTime.clear()
        firedCues.clear()
        lastPeriodicCue = 0
        lapCount = 0
        lastLapAt = 0L
        status = TimerStatus.IDLE
    }

    // -------------------------------------------------------------------------------------------
    // Manual control
    // -------------------------------------------------------------------------------------------

    /** Moves to the next interval. Skipped reminders are reported separately from completions. */
    @Synchronized
    fun next() {
        if (!isLoaded() || status == TimerStatus.FINISHED) return
        val elapsed = positionOrFrozen()
        val current = currentStep() ?: return
        if (status != TimerStatus.AWAITING_MANUAL) {
            emit(TimerEvent.StepCompleted(stepIndex, current, CompletionReason.SKIPPED_NEXT, stepElapsed(elapsed)))
            skippedSteps++
        }
        goTo(stepIndex + 1, elapsed, resumingFromManual = status == TimerStatus.AWAITING_MANUAL)
    }

    @Synchronized
    fun previous() {
        if (!isLoaded() || status == TimerStatus.FINISHED) return
        val elapsed = positionOrFrozen()
        val current = currentStep() ?: return
        if (stepIndex == 0) {
            // Restart the first interval rather than doing nothing: predictable for the user.
            stepStartAt = elapsed
            forgottenCuesFrom(0)
            emit(TimerEvent.StepStarted(0, current, timeline.steps.size, resumed = true))
            return
        }
        val previousStep = timeline.steps[stepIndex - 1]
        val newStart = (stepStartAt - previousStep.durationMillis).coerceAtLeast(0L)
        stepIndex -= 1
        stepStartAt = newStart
        forgottenCuesFrom(stepIndex)
        // Rewinding re-arms cues for the step we return to. Completion is tracked per distinct
        // interval index, so history can never claim more completions than the timeline has steps.
        emit(TimerEvent.StepStarted(stepIndex, timeline.steps[stepIndex], timeline.steps.size, resumed = true))
    }

    /** Adds time to the current interval (and therefore to the session remaining). */
    @Synchronized
    fun addTime(millis: Long) {
        if (millis == 0L || !isLoaded() || status == TimerStatus.FINISHED) return
        val step = currentStep() ?: return
        if (step.isIndefinite) return
        val next = ((extraTime[stepIndex] ?: 0L) + millis).coerceAtLeast(-step.durationMillis)
        extraTime[stepIndex] = next
        forgottenCuesFrom(stepIndex)
        emit(TimerEvent.TimeAdded(stepIndex, millis))
    }

    /** Completes a manual interval (manual set, AMRAP lap, stretch hold). */
    @Synchronized
    fun completeManual(): TimerSnapshot {
        if (status != TimerStatus.AWAITING_MANUAL) return snapshot()
        val elapsed = currentPosition()
        val step = currentStep() ?: return snapshot()
        val actual = elapsed - stepStartAt
        emit(TimerEvent.ManualIntervalCompleted(stepIndex, step, actual))
        goTo(stepIndex + 1, elapsed, resumingFromManual = true)
        return snapshot()
    }

    /** Records a lap on a stopwatch-style session. */
    @Synchronized
    fun lap(): TimerSnapshot {
        if (!isLoaded() || status == TimerStatus.FINISHED) return snapshot()
        val elapsed = currentPosition()
        lapCount++
        emit(TimerEvent.LapLogged(stepIndex, lapCount, elapsed - lastLapAt, elapsed))
        lastLapAt = elapsed
        return snapshot()
    }

    // -------------------------------------------------------------------------------------------
    // Ticking
    // -------------------------------------------------------------------------------------------

    /**
     * Advances state. Call every ~100 ms while running (UI ticker and foreground service share this
     * method; calling it twice in a tick is harmless because advancement is boundary-driven).
     */
    @Synchronized
    fun tick(): TimerSnapshot {
        if (status == TimerStatus.RUNNING || status == TimerStatus.AWAITING_MANUAL) settle()
        return snapshot()
    }

    private fun settle() {
        if (status != TimerStatus.RUNNING && status != TimerStatus.AWAITING_MANUAL) {
            if (status == TimerStatus.AWAITING_MANUAL) maybeEmitPeriodicCue()
            return
        }
        var guard = 0
        while (guard++ < timeline.steps.size + 2) {
            val step = currentStep() ?: return
            val elapsed = currentPosition()
            maybeEmitPeriodicCue()

            if (step.manualCompletion) {
                if (status != TimerStatus.AWAITING_MANUAL) {
                    status = TimerStatus.AWAITING_MANUAL
                }
                return
            }

            val duration = step.durationMillis + (extraTime[stepIndex] ?: 0L)
            maybeEmitCues(step, duration, elapsed - stepStartAt)

            if (elapsed - stepStartAt >= duration) {
                emit(TimerEvent.StepCompleted(stepIndex, step, CompletionReason.EXPIRED, duration))
                completedStepIndexes += stepIndex
                completedStepsForCues(stepIndex)
                if (stepIndex == timeline.steps.lastIndex) {
                    finish(elapsed)
                    return
                }
                advanceTo(stepIndex + 1, stepStartAt + duration)
                continue
            }
            return
        }
    }

    private fun finish(elapsed: Long) {
        freezeAt(elapsed)
        status = TimerStatus.FINISHED
        emit(
            TimerEvent.SessionFinished(
                reason = FinishReason.TIMELINE_COMPLETED,
                elapsedMillis = elapsed,
                completedSteps = completedSteps,
                skippedSteps = skippedSteps,
            )
        )
    }

    private fun advanceTo(index: Int, startAt: Long) {
        stepIndex = index
        stepStartAt = startAt
        announceStep(resumed = false)
    }

    private fun goTo(index: Int, elapsed: Long, resumingFromManual: Boolean) {
        if (index >= timeline.steps.size) {
            finish(elapsed)
            return
        }
        stepIndex = index
        stepStartAt = elapsed
        if (status == TimerStatus.AWAITING_MANUAL) status = TimerStatus.RUNNING
        announceStep(resumed = !resumingFromManual)
        settle()
    }

    private fun announceStep(resumed: Boolean) {
        val step = currentStep() ?: return
        emit(TimerEvent.StepStarted(stepIndex, step, timeline.steps.size, resumed))
        step.roundInGroup?.let { round ->
            val rounds = step.roundsInGroup ?: return@let
            if (round > 1 && step.groupId != null) {
                emit(TimerEvent.RoundStarted(step.groupId, step.groupName ?: "Block", round, rounds, stepIndex))
            }
        }
    }

    private fun maybeEmitCues(step: TimelineStep, duration: Long, elapsedInStep: Long) {
        if (step.isIndefinite) return
        val remaining = duration - elapsedInStep
        if (duration > cuePolicy.suppressCountdownUnderMillis) {
            cuePolicy.countdownSeconds.forEach { seconds ->
                val threshold = seconds * 1000L
                val tag = "cd:$seconds"
                if (remaining <= threshold && remaining > threshold - 1000L && !firedCues.contains("$stepIndex:$tag")) {
                    firedCues += "$stepIndex:$tag"
                    emit(TimerEvent.Countdown(stepIndex, step, seconds))
                }
            }
        }
        if (cuePolicy.halfwayCues && duration >= cuePolicy.halfwayMinMillis) {
            val half = duration / 2
            val tag = "half"
            if (remaining <= half && !firedCues.contains("$stepIndex:$tag")) {
                firedCues += "$stepIndex:$tag"
                emit(TimerEvent.Halfway(stepIndex, step))
            }
        }
    }

    private fun maybeEmitPeriodicCue() {
        val every = periodicCueMillis ?: return
        if (every <= 0L) return
        val elapsed = positionOrFrozen()
        val occurrence = (elapsed / every).toInt()
        if (occurrence > lastPeriodicCue) {
            lastPeriodicCue = occurrence
            emit(TimerEvent.PeriodicCue(occurrence, every, elapsed))
        }
    }

    private fun completedStepsForCues(index: Int) {
        firedCues.removeAll { it.startsWith("$index:") }
    }

    private fun forgottenCuesFrom(index: Int) {
        firedCues.removeAll { it.startsWith("$index:") }
    }

    // -------------------------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------------------------

    private fun isLoaded(): Boolean = status != TimerStatus.IDLE

    private fun currentStep(): TimelineStep? = timeline.steps.getOrNull(stepIndex)

    /** Live session position while running; frozen position otherwise. */
    private fun currentPosition(): Long = accumulated + ((anchor?.let { clock.nowMillis() - it }) ?: 0L)

    private fun positionOrFrozen(): Long = currentPosition()

    private fun stepElapsed(elapsed: Long): Long = (elapsed - stepStartAt).coerceAtLeast(0L)

    /**
     * Folds the running anchor into [accumulated] so that elapsed time survives leaving the RUNNING
     * state (finish/stop). Without this the final snapshot would report the elapsed value as of the
     * last pause instead of the last tick.
     */
    private fun freezeAt(elapsed: Long) {
        accumulated = elapsed
        anchor = null
    }

    @Synchronized
    fun snapshot(): TimerSnapshot {
        val step = currentStep()
        if (status == TimerStatus.IDLE) return TimerSnapshot(status = TimerStatus.IDLE)
        val elapsed = currentPosition()
        val stepElapsed = stepElapsed(elapsed)
        val duration = (step?.durationMillis ?: 0L) + (extraTime[stepIndex] ?: 0L)
        val remaining = if (step == null) 0L else (duration - stepElapsed).coerceAtLeast(0L)
        val remainingTotal = remainingTotalMillis(stepElapsed)
        val intervalProgress = when {
            step == null -> 0f
            step.manualCompletion -> 0f
            duration <= 0L -> 0f
            else -> (stepElapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
        val sessionProgress = remainingTotal?.let { total ->
            val known = timeline.knownMillis
            if (known <= 0L) 0f else ((known - total).toFloat() / known.toFloat()).coerceIn(0f, 1f)
        } ?: 0f
        return TimerSnapshot(
            status = status,
            planId = planId,
            planName = planName,
            stepIndex = stepIndex,
            totalSteps = timeline.steps.size,
            step = step,
            nextStep = timeline.steps.getOrNull(stepIndex + 1),
            stepDurationMillis = duration,
            stepElapsedMillis = stepElapsed,
            stepRemainingMillis = remaining,
            sessionElapsedMillis = elapsed,
            sessionRemainingMillis = remainingTotal,
            intervalProgress = intervalProgress,
            sessionProgress = sessionProgress,
            completedSteps = completedSteps,
            skippedSteps = skippedSteps,
            laps = lapCount,
        )
    }

    /** Remaining timed content from the current position, or null if anything left is open-ended. */
    private fun remainingTotalMillis(stepElapsedNow: Long): Long? {
        var total = 0L
        for (i in stepIndex until timeline.steps.size) {
            val step = timeline.steps[i]
            if (step.isIndefinite) return null
            val dur = step.durationMillis + (extraTime[i] ?: 0L)
            total += if (i == stepIndex) (dur - stepElapsedNow).coerceAtLeast(0L) else dur
        }
        return total
    }

    /** Test/diagnostic helper: the events emitted since the last drain. */
    @Synchronized
    fun drainEvents(): List<TimerEvent> {
        val copy = _events.toList()
        _events.clear()
        return copy
    }
}
