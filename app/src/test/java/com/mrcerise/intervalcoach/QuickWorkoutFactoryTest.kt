package com.mrcerise.intervalcoach

import com.pulse.engine.PhaseKind
import com.pulse.engine.TimelineExpander
import com.mrcerise.intervalcoach.data.QuickWorkoutFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickWorkoutFactoryTest {

    @Test
    fun `translated generated labels keep the same interval schedule`() {
        val original = QuickWorkoutFactory.build(
            workMillis = 20_000, restMillis = 10_000, rounds = 2,
            preparationMillis = 5_000, cooldownMillis = 15_000,
        )
        val translated = QuickWorkoutFactory.build(
            workMillis = 20_000, restMillis = 10_000, rounds = 2,
            preparationMillis = 5_000, cooldownMillis = 15_000,
            name = "Séance rapide", workName = "Effort", restName = "Repos",
            preparationName = "Préparez-vous", cooldownName = "Retour au calme",
            roundName = "Bloc répété",
        )
        val timeline = TimelineExpander.expand(translated)
        assertEquals(
            TimelineExpander.expand(original).steps.map { it.kind to it.durationMillis },
            timeline.steps.map { it.kind to it.durationMillis },
        )
        // Only recovery at the end of the timeline is trimmed. The rest before a cooldown stays.
        assertEquals(
            listOf("Préparez-vous", "Effort", "Repos", "Effort", "Repos", "Retour au calme"),
            timeline.steps.map { it.name },
        )
        assertEquals(80_000L, timeline.knownMillis)
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
