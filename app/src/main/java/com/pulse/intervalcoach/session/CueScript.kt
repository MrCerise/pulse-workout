package com.pulse.intervalcoach.session

import com.pulse.engine.PhaseKind
import com.pulse.engine.TimelineStep
import com.pulse.engine.WorkoutType
import com.pulse.engine.formatDurationSpoken
import com.pulse.intervalcoach.data.UserPreferences
import com.pulse.intervalcoach.data.VoiceVerbosity

/**
 * Builds the exact sentences the coach says.
 *
 * Kept pure and separate from the audio layer so the wording is unit-testable and so the Voice
 * Studio preview uses the same copy as a real workout.
 */
object CueScript {

    fun intervalStart(step: TimelineStep, next: TimelineStep?, prefs: UserPreferences, workoutType: WorkoutType): String? {
        if (prefs.verbosity == VoiceVerbosity.MINIMAL) {
            return minimalStart(step, workoutType)
        }
        val parts = mutableListOf<String>()
        step.roundInGroup?.let { round ->
            val rounds = step.roundsInGroup ?: 0
            if (rounds > 1 && round > 1) parts += "Round $round of $rounds."
        }
        parts += when (workoutType) {
            WorkoutType.BOXING -> if (step.kind == PhaseKind.WORK) "Round starts." else step.displaySpoken()
            WorkoutType.EMOM, WorkoutType.E2MOM -> "New minute."
            WorkoutType.AMRAP -> step.displaySpoken()
            WorkoutType.BREATHING -> step.displaySpoken()
            else -> step.displaySpoken()
        }
        step.sideLabel?.let { parts += "$it side." }
        if (!step.isIndefinite) parts += "${formatDurationSpoken(step.durationMillis)}."
        step.speechText?.takeIf { it.isNotBlank() }?.let { parts += it.trim().ensureSentence() }
        if (prefs.announceNext && next != null && prefs.verbosity == VoiceVerbosity.CHATTY) {
            parts += "Next: ${next.displaySpoken().lowercase()}" + if (next.isIndefinite) "." else " ${formatDurationSpoken(next.durationMillis)}."
        }
        return parts.joinToString(" ")
    }

    private fun minimalStart(step: TimelineStep, workoutType: WorkoutType): String? = when {
        step.kind == PhaseKind.WORK || step.kind == PhaseKind.WARM_UP -> "Go."
        step.kind == PhaseKind.REST -> "Rest."
        step.kind == PhaseKind.PREPARE -> "Get ready."
        step.kind == PhaseKind.COOLDOWN -> "Cool down."
        workoutType == WorkoutType.STOPWATCH -> null
        else -> step.displaySpoken() + "."
    }

    fun countdown(seconds: Int, prefs: UserPreferences): String? =
        if (prefs.spokenCountdown && prefs.verbosity != VoiceVerbosity.MINIMAL) seconds.toString() else null

    fun halfway(step: TimelineStep): String = "Halfway."

    fun resumed(step: TimelineStep?): String =
        step?.let { "Back to ${it.displaySpoken().lowercase()}." } ?: "Timer running."

    fun paused(): String = "Paused."

    fun lap(number: Int, lapMillis: Long): String = "Lap $number. ${formatDurationSpoken(lapMillis)}."

    fun manualComplete(step: TimelineStep): String = "${step.displaySpoken()} logged."

    fun periodicCue(step: TimelineStep?): String = step?.speechText?.takeIf { it.isNotBlank() } ?: "Cue."

    fun finished(stoppedEarly: Boolean, completed: Int, total: Int): String =
        if (stoppedEarly) {
            "Session ended. $completed of $total intervals completed."
        } else {
            "Workout complete. $total intervals. Well done."
        }

    /** Compact copy for the notification and the player's "next" line. */
    fun notificationLine(step: TimelineStep?): String {
        if (step == null) return "Ready"
        val suffix = if (step.isIndefinite) "" else " · ${formatDurationSpoken(step.durationMillis)}"
        return step.displaySpoken() + suffix
    }
}

private fun TimelineStep.displaySpoken(): String = name.ifBlank { PhaseKind.defaultLabel(kind) }

private fun String.ensureSentence(): String = if (endsWith(".") || endsWith("!") || endsWith("?")) this else "$this."
