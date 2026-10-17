package com.mrcerise.intervalcoach

import com.pulse.engine.TimelineExpander
import com.mrcerise.intervalcoach.data.PlanCodec
import com.mrcerise.intervalcoach.data.UserPreferences
import com.mrcerise.intervalcoach.data.VoiceVerbosity
import com.mrcerise.intervalcoach.data.QuickWorkoutFactory
import com.mrcerise.intervalcoach.session.CueScript
import com.pulse.library.StarterWorkouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Workout serialization round trips and the coaching copy. These tests exercise the real
 * serialization configuration — the same [PlanCodec] the app saves and loads with — plus the cue
 * wording guarantees (acceptance case 11).
 */
class PlansAndCueTest {

    @Test
    fun `a workout survives an export and import round trip exactly`() {
        val original = StarterWorkouts.templates.first { it.plan.name == "Bodyweight Circuit" }.plan
        val decoded = PlanCodec.decode(PlanCodec.encode(original))

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.nodes.size, decoded.nodes.size)

        val before = TimelineExpander.expand(original)
        val after = TimelineExpander.expand(decoded)
        assertEquals(before.steps.size, after.steps.size)
        assertEquals(before.knownMillis, after.knownMillis)
        assertEquals(before.steps.map { it.name }, after.steps.map { it.name })
    }

    @Test
    fun `every bundled starter template round trips and has a real duration`() {
        assertTrue("at least 12 starter templates are required", StarterWorkouts.templates.size >= 12)
        StarterWorkouts.templates.forEach { template ->
            val decoded = PlanCodec.decode(PlanCodec.encode(template.plan))
            val timeline = TimelineExpander.expand(decoded)
            assertTrue("${template.plan.name} should have intervals", timeline.steps.isNotEmpty())
            assertTrue(
                "${template.plan.name} must be timed or explicitly open ended",
                timeline.hasOpenEnded || timeline.knownMillis > 0,
            )
            assertEquals(
                "${template.plan.name} duration must be stable across a round trip",
                TimelineExpander.expand(template.plan).knownMillis,
                timeline.knownMillis,
            )
        }
    }

    @Test
    fun `starter templates do not collide on ids or names`() {
        val ids = StarterWorkouts.templates.map { it.plan.id }
        val names = StarterWorkouts.templates.map { it.plan.name }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(names.size, names.toSet().size)
        assertTrue("template ids are derived from a fixed seed, not regenerated per run", ids.all { it.startsWith("builtin-") })
    }

    @Test
    fun `coaching copy adapts to verbosity`() {
        val plan = StarterWorkouts.templates.first { it.plan.name == "Tabata Classic" }.plan
        val timeline = TimelineExpander.expand(plan)
        val workIndex = timeline.steps.indexOfFirst {
            it.kind == com.pulse.engine.PhaseKind.WORK && it.roundInGroup == 2
        }
        assertTrue("the tabata template has a second round", workIndex > 0)
        val work = timeline.steps[workIndex]
        val next = timeline.steps[workIndex + 1]

        val standard = CueScript.intervalStart(work, next, UserPreferences(verbosity = VoiceVerbosity.STANDARD), plan.type)
        assertTrue("standard copy names the round", standard!!.contains("Round 2"))
        assertTrue("standard copy states the duration", standard.contains("20 seconds"))

        val minimal = CueScript.intervalStart(work, next, UserPreferences(verbosity = VoiceVerbosity.MINIMAL), plan.type)
        assertEquals("Go.", minimal)

        val chatty = CueScript.intervalStart(
            work, next,
            UserPreferences(verbosity = VoiceVerbosity.CHATTY, announceNext = true, spokenCountdown = true),
            plan.type,
        )
        assertTrue("chatty copy previews the next interval", chatty!!.contains("Next:"))
    }

    @Test
    fun `countdown speech respects the spoken countdown setting`() {
        assertNull(CueScript.countdown(3, UserPreferences(spokenCountdown = false)))
        assertNull(CueScript.countdown(3, UserPreferences(verbosity = VoiceVerbosity.MINIMAL)))
        assertEquals("3", CueScript.countdown(3, UserPreferences()))
    }

    @Test
    fun `finish copy distinguishes a completed workout from an early stop`() {
        assertEquals(
            "Workout complete. 16 intervals. Well done.",
            CueScript.finished(stoppedEarly = false, completed = 16, total = 16),
        )
        assertEquals(
            "Session ended. 5 of 16 intervals completed.",
            CueScript.finished(stoppedEarly = true, completed = 5, total = 16),
        )
    }

    @Test
    fun `legacy descriptions survive backup round trips without a description editor`() {
        val plan = QuickWorkoutFactory.build(30_000, 15_000, 8, 0).copy(description = "My old note")
        assertEquals(plan, PlanCodec.decode(PlanCodec.encode(plan)))
    }

    @Test
    fun `a quick plan can always be saved and re-read`() {
        val plan = QuickWorkoutFactory.build(30_000, 15_000, 8, 0)
        val decoded = PlanCodec.decode(PlanCodec.encode(plan))
        assertEquals(TimelineExpander.expand(plan).steps.size, TimelineExpander.expand(decoded).steps.size)
        assertEquals(TimelineExpander.expand(plan).knownMillis, TimelineExpander.expand(decoded).knownMillis)
    }
}
