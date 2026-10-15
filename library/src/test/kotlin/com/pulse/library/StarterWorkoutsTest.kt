package com.pulse.library

import com.pulse.engine.TimelineExpander
import com.pulse.engine.Validation
import com.pulse.engine.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled catalogue is content, but it is load-bearing content: the phone seeds it into an
 * empty database and the watch seeds it when it has never been paired, so a broken template ships
 * as a broken first run on both devices. These tests are the reason a template cannot silently
 * stop expanding, stop validating or change identity.
 */
class StarterWorkoutsTest {

    @Test
    fun `every bundled workout expands to a playable timeline`() {
        assertTrue("the catalogue must stay useful", StarterWorkouts.templates.size >= 12)
        for (template in StarterWorkouts.templates) {
            val timeline = TimelineExpander.expand(template.plan)
            assertTrue("${template.plan.name} expanded to no steps", timeline.steps.isNotEmpty())
            assertTrue("${template.plan.name} is missing an id", template.plan.id.isNotBlank())
            assertTrue("${template.plan.name} is missing a name", template.plan.name.isNotBlank())
        }
    }

    @Test
    fun `every bundled workout passes the engine's own validation`() {
        for (template in StarterWorkouts.templates) {
            assertEquals(
                "${template.plan.name} would be refused by the builder: ${Validation.validate(template.plan)}",
                emptyList<String>(),
                Validation.validate(template.plan),
            )
        }
    }

    @Test
    fun `ids are derived from the seed, so the phone and the watch agree about a workout`() {
        // `UUID.nameUUIDFromBytes` is an MD5-based (v3) UUID: the same seed produces the same id on
        // every device and every run. That is what lets the watch's copy of a bundled workout be
        // *the same workout* as the phone's copy — the library mirror matches them by id — and it
        // is why a phone-side rename or deletion propagates instead of duplicating.
        assertEquals(
            "builtin-5b7713de-b05a-35ab-8482-4e91c79b8688",
            StarterWorkouts.templates.first { it.plan.name == "Tabata Classic" }.plan.id,
        )
        assertEquals(
            "builtin-253f26af-9cf4-3de1-a881-60afc878a35e",
            StarterWorkouts.templates.first { it.plan.name == "HIIT Starter" }.plan.id,
        )
        assertEquals(
            "builtin-8a8ef3cb-0c19-37ac-b39b-a185d157f54b",
            StarterWorkouts.templates.first { it.plan.name == "Stopwatch" }.plan.id,
        )

        val ids = StarterWorkouts.templates.map { it.plan.id }
        assertEquals("two templates share an id", ids.size, ids.toSet().size)
        assertEquals(
            "two templates share a name",
            ids.size,
            StarterWorkouts.templates.map { it.plan.name }.toSet().size,
        )
        assertTrue("bundled ids keep the builtin- prefix", ids.all { it.startsWith("builtin-") })
    }

    @Test
    fun `the catalogue covers the workout types it advertises`() {
        val types = StarterWorkouts.templates.map { it.plan.type }.toSet()
        assertTrue("a starter HIIT workout is required", types.contains(WorkoutType.HIIT))
        assertTrue("a starter Tabata workout is required", types.contains(WorkoutType.TABATA))
        assertTrue("the catalogue is thin: ${types.size} types", types.size >= 8)
    }

    @Test
    fun `the onboarding example is the HIIT starter with a computed duration`() {
        assertEquals("HIIT Starter", StarterWorkouts.examplePlan.name)
        assertTrue(
            "the example workout must report a real duration",
            StarterWorkouts.knownDuration(StarterWorkouts.examplePlan) > 0L,
        )
    }

    @Test
    fun `open-ended templates are declared open-ended rather than misreporting a total`() {
        val stopwatch = StarterWorkouts.templates.first { it.plan.name == "Stopwatch" }.plan
        val timeline = TimelineExpander.expand(stopwatch)
        assertTrue("the stopwatch counts up, so it has no known total", timeline.hasOpenEnded)
        assertNull(timeline.totalMillis)
        assertEquals(WorkoutType.STOPWATCH, stopwatch.type)
    }
}
