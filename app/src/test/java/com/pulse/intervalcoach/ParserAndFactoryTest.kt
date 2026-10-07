package com.pulse.intervalcoach

import com.pulse.engine.PhaseKind
import com.pulse.engine.TimelineExpander
import com.pulse.engine.WorkoutType
import com.pulse.intervalcoach.data.PlanCodec
import com.pulse.intervalcoach.data.QuickWorkoutFactory
import com.pulse.intervalcoach.parser.WorkoutTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The text parser is deliberately deterministic: it recognises a documented set of patterns, reports
 * what it could not understand, and never invents structure from ambiguous wording. The factory is
 * checked too, because Quick Start promises an exact duration.
 */
class ParserAndFactoryTest {

    @Test
    fun `parses rounds with work rest and a warm-up`() {
        val result = WorkoutTextParser.parse("6 rounds, 40 seconds work, 20 seconds rest, 1 minute warm-up")
        val plan = result.plan
        assertNotNull("this input matches a documented pattern", plan)
        val timeline = TimelineExpander.expand(plan!!)
        assertEquals("1 warm-up + 6 x (work + rest)", 13, timeline.steps.size)
        assertEquals(60_000L + 6 * 60_000L, timeline.knownMillis)
        assertFalse(timeline.hasOpenEnded)
        val decoded = PlanCodec.decode(PlanCodec.encode(plan))
        assertEquals(plan.nodes.size, decoded.nodes.size)
        assertEquals(timeline.steps.size, TimelineExpander.expand(decoded).steps.size)
    }

    @Test
    fun `reports words it could not understand instead of guessing`() {
        val result = WorkoutTextParser.parse("6 rounds, 40 seconds work, go absolutely wild until the bell")
        assertTrue(result.unmatched.any { it.contains("wild") })
        assertTrue(result.message.contains("Not understood"))
        assertTrue(result.message.contains("deterministic pattern parser"))
    }

    @Test
    fun `the tabata keyword produces the textbook protocol`() {
        val plan = WorkoutTextParser.parse("tabata").plan
        assertNotNull("tabata is a recognised keyword", plan)
        assertEquals(WorkoutType.TABATA, plan!!.type)
        val timeline = TimelineExpander.expand(plan)
        assertEquals("8 rounds x (20 s work + 10 s rest)", 16, timeline.steps.size)
        assertEquals(240_000L, timeline.knownMillis)
    }

    @Test
    fun `emom produces one block per minute and amrap stays open ended`() {
        val emom = WorkoutTextParser.parse("emom 8 minutes").plan!!
        val emomTimeline = TimelineExpander.expand(emom)
        assertEquals(8, emomTimeline.steps.size)
        assertEquals(480_000L, emomTimeline.knownMillis)

        val amrap = WorkoutTextParser.parse("amrap 12 minutes").plan!!
        assertTrue("rounds are counted by the user, not by the clock", TimelineExpander.expand(amrap).hasOpenEnded)
        assertEquals(720_000L, amrap.allIntervals().single().durationMillis)
    }

    @Test
    fun `box breathing produces eight paced cycles`() {
        val plan = WorkoutTextParser.parse("box breathing").plan!!
        assertEquals(WorkoutType.BREATHING, plan.type)
        val timeline = TimelineExpander.expand(plan)
        assertEquals("8 cycles x 4 phases", 32, timeline.steps.size)
        assertEquals(128_000L, timeline.knownMillis)
    }

    @Test
    fun `duration parsing understands the documented formats`() {
        assertEquals(90_000L, WorkoutTextParser.parseDuration("90 seconds"))
        assertEquals(90_000L, WorkoutTextParser.parseDuration("1 minute 30 seconds"))
        assertEquals(90_000L, WorkoutTextParser.parseDuration("1:30"))
        assertEquals(45_000L, WorkoutTextParser.parseDuration("45s"))
        assertEquals(120_000L, WorkoutTextParser.parseDuration("2m"))
        assertEquals(3_600_000L, WorkoutTextParser.parseDuration("1 hour"))
        assertNull(WorkoutTextParser.parseDuration("as long as you like"))
    }

    @Test
    fun `unsupported input produces no plan and a helpful message`() {
        val result = WorkoutTextParser.parse("do something fun")
        assertNull("nothing may be guessed from prose", result.plan)
        assertTrue(result.message.contains("No pattern recognised"))
    }

    @Test
    fun `quick workout factory totals match what the player will show`() {
        val plan = QuickWorkoutFactory.build(
            workMillis = 20_000,
            restMillis = 10_000,
            rounds = 8,
            preparationMillis = 10_000,
            includeFinalRest = true,
        )
        val timeline = TimelineExpander.expand(plan)
        assertEquals("preparation + 8 work + 8 rest", 17, timeline.steps.size)
        assertEquals(250_000L, timeline.knownMillis)

        // Acceptance case 1: the same workout is 10 s shorter with the final rest switched off.
        val withoutFinalRest = TimelineExpander.expand(plan.copy(includeFinalRest = false))
        assertEquals(16, withoutFinalRest.steps.size)
        assertEquals(240_000L, withoutFinalRest.knownMillis)
    }

    @Test
    fun `zero preparation is omitted rather than shown as an empty interval`() {
        val plan = QuickWorkoutFactory.build(
            workMillis = 30_000,
            restMillis = 15_000,
            rounds = 2,
            preparationMillis = 0,
        )
        val timeline = TimelineExpander.expand(plan)
        // The factory's default is "no rest after the final round", so two rounds are 3 steps…
        assertEquals(3, timeline.steps.size)
        assertTrue("no empty 'get ready' interval", timeline.steps.none { it.kind == PhaseKind.PREPARE })
        // …and 4 when the final rest is switched on.
        assertEquals(4, TimelineExpander.expand(plan.copy(includeFinalRest = true)).steps.size)
    }

    @Test
    fun `the quick start tabata preset is the exact four minute protocol`() {
        val timeline = TimelineExpander.expand(QuickWorkoutFactory.tabata())
        assertEquals(16, timeline.steps.size)
        assertEquals(240_000L, timeline.knownMillis)
        assertTrue("no preparation interval in the textbook preset", timeline.steps.none { it.kind == PhaseKind.PREPARE })

        // …and 3:50 when the final rest is switched off (acceptance case 1).
        val withoutFinalRest = TimelineExpander.expand(QuickWorkoutFactory.tabata(includeFinalRest = false))
        assertEquals(15, withoutFinalRest.steps.size)
        assertEquals(230_000L, withoutFinalRest.knownMillis)

        // A "get ready" countdown can still be requested explicitly.
        assertEquals(17, TimelineExpander.expand(QuickWorkoutFactory.tabata(preparationMillis = 10_000)).steps.size)
    }
}
