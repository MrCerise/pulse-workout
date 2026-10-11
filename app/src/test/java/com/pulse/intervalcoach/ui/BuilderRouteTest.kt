package com.pulse.intervalcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The route an edit travels.
 *
 * These are the four facts that make *Edit workout* open the builder **for that workout**: the
 * destination declares an optional `workoutId`, an id is put in the route, a new workout still reaches
 * the same destination with no argument, and an id a URI query cannot carry survives the trip. The
 * destination used to have no argument at all, so the builder could not tell "new" from "edit", always
 * started a blank plan, and saving produced a second workout instead of changing the one being read.
 */
class BuilderRouteTest {

    @Test
    fun `the builder destination declares an optional workout id`() {
        val path = Routes.ADVANCED_BUILDER.substringBefore('?')
        val query = Routes.ADVANCED_BUILDER.substringAfter('?', "")
        assertEquals("builder/advanced", path)
        assertEquals("workoutId={workoutId}", query)
    }

    @Test
    fun `editing a workout puts its id in the route`() {
        assertEquals("builder/advanced?workoutId=2f0a-1b", Routes.advancedBuilder("2f0a-1b"))
    }

    @Test
    fun `a new workout reaches the same destination with no argument`() {
        val route = Routes.advancedBuilder(null)
        assertEquals("builder/advanced", route)
        assertFalse("must not carry an empty argument", route.contains("workoutId="))
        assertTrue("the bare path is the destination's own path", Routes.ADVANCED_BUILDER.startsWith("$route?"))
    }

    @Test
    fun `an id the query cannot carry is escaped rather than dropped`() {
        // Left raw, a space or an ampersand truncates the argument and the builder opens blank —
        // the exact failure these routes are here to prevent.
        assertEquals("builder/advanced?workoutId=hand%20edited", Routes.advancedBuilder("hand edited"))
        assertEquals("builder/advanced?workoutId=a%26b", Routes.advancedBuilder("a&b"))
        assertEquals("builder/advanced?workoutId=%C3%A9", Routes.advancedBuilder("é"))
    }
}
