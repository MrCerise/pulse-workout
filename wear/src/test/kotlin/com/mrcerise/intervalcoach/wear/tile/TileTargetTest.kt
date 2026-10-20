package com.mrcerise.intervalcoach.wear.tile

import com.mrcerise.intervalcoach.wear.StoredWorkout
import com.mrcerise.intervalcoach.wear.WatchActivity
import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.PhaseKind
import com.pulse.engine.WorkoutPlan
import com.pulse.sync.PlannedWorkout
import org.junit.Assert.assertEquals
import org.junit.Test

class TileTargetTest {

    private fun workout(id: String, favorite: Boolean = false) = StoredWorkout(
        id = id,
        updatedAt = 0L,
        favorite = favorite,
        plan = WorkoutPlan(id = id, name = id, nodes = listOf(IntervalNode("n", IntervalSpec("n", "Go", PhaseKind.WORK, 30_000)))),
    )

    // The store's order: favourites first.
    private val library = listOf(workout("fav", favorite = true), workout("other"), workout("planned"))

    private fun activity(vararg planned: PlannedWorkout) = WatchActivity(150, 0L, 0, planned.toList())

    @Test
    fun `a running workout always wins, and is resumed`() {
        assertEquals(
            TileTarget("other", resume = true),
            tileTarget("other", activity(PlannedWorkout("planned", "Planned", 0L)), library),
        )
    }

    @Test
    fun `then the next planned workout not done yet, opened on its details`() {
        val today = activity(
            PlannedWorkout("planned", "Planned", 1L, done = true),
            PlannedWorkout("gone", "Deleted from the watch", 2L),
            PlannedWorkout("other", "Other", 3L),
        )
        assertEquals(TileTarget("other", resume = false), tileTarget(null, today, library))
    }

    @Test
    fun `otherwise the first favourite, and nothing when the library is empty`() {
        assertEquals(TileTarget("fav", resume = false), tileTarget(null, activity(), library))
        assertEquals(TileTarget(null, resume = false), tileTarget(null, activity(), emptyList()))
    }
}
