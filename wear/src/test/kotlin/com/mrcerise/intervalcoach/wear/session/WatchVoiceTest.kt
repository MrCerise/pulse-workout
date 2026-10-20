package com.mrcerise.intervalcoach.wear.session

import com.pulse.engine.FinishReason
import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.PhaseKind
import com.pulse.engine.TimelineExpander
import com.pulse.engine.TimerEvent
import com.pulse.engine.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchVoiceTest {

    private val phrases = object : VoicePhrases {
        override fun side(label: String) = "$label side"
        override val halfway = "Halfway."
        override val paused = "Paused"
        override val resumed = "Go."
        override val finished = "Workout complete."
        override val stopped = "Workout stopped."
    }

    private val steps = TimelineExpander.expand(
        WorkoutPlan(
            id = "p",
            name = "Plan",
            nodes = listOf(
                IntervalNode("a", IntervalSpec("a", "Lunges", PhaseKind.WORK, 30_000, sideLabel = "Left", speechText = "Keep your knee over your ankle")),
                IntervalNode("b", IntervalSpec("b", "", PhaseKind.REST, 15_000)),
            ),
        ),
    ).steps

    @Test
    fun `a starting step says its name, side and coaching line`() {
        assertEquals(
            "Lunges. Left side. Keep your knee over your ankle",
            spokenLine(TimerEvent.StepStarted(0, steps[0], 2), phrases),
        )
        // An unnamed step falls back to its phase's label, like the phone's coach.
        assertEquals(PhaseKind.defaultLabel(PhaseKind.REST) + ".", spokenLine(TimerEvent.StepStarted(1, steps[1], 2), phrases))
    }

    @Test
    fun `resuming a step is not announced as a new one`() {
        assertNull(spokenLine(TimerEvent.StepStarted(0, steps[0], 2, resumed = true), phrases))
    }

    @Test
    fun `countdown, halfway, pause and finish each have their line`() {
        assertEquals("3", spokenLine(TimerEvent.Countdown(0, steps[0], 3), phrases))
        assertEquals("Halfway.", spokenLine(TimerEvent.Halfway(0, steps[0]), phrases))
        assertEquals("Paused", spokenLine(TimerEvent.SessionPaused, phrases))
        assertEquals("Go.", spokenLine(TimerEvent.SessionResumed, phrases))
        assertEquals("Workout complete.", spokenLine(TimerEvent.SessionFinished(FinishReason.TIMELINE_COMPLETED, 0, 2, 0), phrases))
        assertEquals("Workout stopped.", spokenLine(TimerEvent.SessionFinished(FinishReason.USER_STOPPED, 0, 1, 0), phrases))
        assertNull(spokenLine(TimerEvent.TimeAdded(0, 15_000), phrases))
    }
}
