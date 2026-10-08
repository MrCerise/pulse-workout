package com.pulse.intervalcoach

import com.pulse.intervalcoach.audio.DuckConfig
import com.pulse.intervalcoach.audio.DuckPhase
import com.pulse.intervalcoach.audio.DuckingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ducking is the difference between "the coach talks over my music" and "the coach is audible", so
 * the volume curve is pinned down here rather than tuned by ear: where the drop starts, how far it
 * goes, how long it holds after the last word, and that it always comes back to full volume.
 */
class DuckingTest {

    private var now = 0L

    private val config = DuckConfig(
        enabled = true,
        level = 0.30f,
        fadeDownMs = 100L,
        holdMs = 400L,
        fadeUpMs = 200L,
    )

    private fun engine(config: DuckConfig = this.config): DuckingEngine =
        DuckingEngine(nowMillis = { now }).also { it.update(config) }

    @Test
    fun `ducking off leaves the music at full volume even while the coach speaks`() {
        val engine = engine(config.copy(enabled = false))
        engine.beginDuck()
        now += 5_000
        assertEquals(DuckPhase.NONE, engine.stateAt().phase)
        assertEquals(1f, engine.stateAt().scale, 0.0001f)
        engine.release()
        now += 5_000
        assertEquals(1f, engine.stateAt().scale, 0.0001f)
    }

    @Test
    fun `the drop ramps from full volume to the configured level`() {
        val engine = engine()
        engine.beginDuck()

        assertEquals(DuckPhase.FADING_DOWN, engine.stateAt(now).phase)
        assertEquals(1f, engine.stateAt(now).scale, 0.0001f)

        now += 50 // halfway through a 100 ms drop towards 0.30
        assertEquals(0.65f, engine.stateAt(now).scale, 0.0001f)

        now += 50
        assertEquals(DuckPhase.HELD, engine.stateAt(now).phase)
        assertEquals(0.30f, engine.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `an open ended duck lasts as long as the coach is talking`() {
        val engine = engine()
        engine.beginDuck()
        now += 60_000 // a long announcement, no release
        assertEquals(DuckPhase.HELD, engine.stateAt(now).phase)
        assertEquals(0.30f, engine.stateAt(now).scale, 0.0001f)
        assertNull("nothing is scheduled while speech holds the duck open", engine.nextChangeAt(now))
    }

    @Test
    fun `after speech the duck holds, climbs back, and settles at full volume`() {
        val engine = engine()
        engine.beginDuck()
        now += 100
        engine.release()

        now += 200 // inside the 400 ms hold
        assertEquals(DuckPhase.HELD, engine.stateAt(now).phase)
        assertEquals(0.30f, engine.stateAt(now).scale, 0.0001f)

        now += 200 // hold over, 200 ms climb begins
        assertEquals(DuckPhase.FADING_UP, engine.stateAt(now).phase)
        now += 100
        assertEquals(0.65f, engine.stateAt(now).scale, 0.0001f)

        now += 100
        assertEquals(DuckPhase.NONE, engine.stateAt(now).phase)
        assertEquals(1f, engine.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `a second utterance before the hold ends keeps the music down`() {
        val engine = engine()
        engine.beginDuck()
        now += 100
        engine.release()
        now += 300 // still holding
        engine.beginDuck()
        now += 3_000 // the second sentence runs long
        assertEquals(DuckPhase.HELD, engine.stateAt(now).phase)
        assertEquals(0.30f, engine.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `ducking again mid recovery drops from the current volume instead of snapping`() {
        val engine = engine()
        engine.beginDuck()
        now += 100
        engine.release()
        now += 500 // halfway back up: 0.65
        assertEquals(0.65f, engine.stateAt(now).scale, 0.0001f)

        engine.beginDuck()
        assertEquals(0.65f, engine.stateAt(now).scale, 0.0001f)
        now += 50 // halfway from 0.65 down to 0.30
        assertEquals(0.475f, engine.stateAt(now).scale, 0.0001f)
        now += 50
        assertEquals(0.30f, engine.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `a cue tone ducks for its own length and then recovers`() {
        val engine = engine()
        engine.duckFor(900)

        now += 100
        assertEquals(0.30f, engine.stateAt(now).scale, 0.0001f)
        now += 800 // tone over, 400 ms hold begins
        assertEquals(DuckPhase.HELD, engine.stateAt(now).phase)
        now += 400
        assertEquals(DuckPhase.FADING_UP, engine.stateAt(now).phase)
        now += 200
        assertEquals(1f, engine.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `a cue tone during speech never shortens the duck`() {
        val engine = engine()
        engine.beginDuck()
        now += 100
        engine.duckFor(900) // a bell rings mid-announcement
        engine.release() // the coach stops immediately after
        now += 500
        assertEquals("the tone is still sounding", DuckPhase.HELD, engine.stateAt(now).phase)
        now += 400 // the tone ran to 1 000 ms, so the hold only starts there
        assertEquals(DuckPhase.HELD, engine.stateAt(now).phase)
        now += 400 // 400 ms of hold later the climb begins
        assertEquals(DuckPhase.FADING_UP, engine.stateAt(now).phase)
    }

    @Test
    fun `a duck after a full recovery ramps again instead of snapping`() {
        val engine = engine()
        engine.duckFor(200)
        now += 200 + config.releaseMillis + 1 // climbed all the way back
        assertEquals(1f, engine.stateAt(now).scale, 0.0001f)

        engine.duckFor(200)
        assertEquals(DuckPhase.FADING_DOWN, engine.stateAt(now).phase)
        assertEquals(1f, engine.stateAt(now).scale, 0.0001f)
        now += 50
        assertEquals(0.65f, engine.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `overlapping tones cover each other`() {
        val engine = engine()
        engine.duckFor(400)
        now += 300
        engine.duckFor(400) // second tone starts before the first ends
        now += 400 // 700 ms in: the second tone still has 100 ms left
        assertEquals(DuckPhase.HELD, engine.stateAt(now).phase)
    }

    @Test
    fun `cancel returns to full volume at once`() {
        val engine = engine()
        engine.beginDuck()
        now += 100
        assertEquals(0.30f, engine.stateAt(now).scale, 0.0001f)
        engine.cancel()
        assertEquals(1f, engine.stateAt(now).scale, 0.0001f)
        assertFalse(engine.isDucking(now))
    }

    @Test
    fun `switching ducking off mid session releases the music immediately`() {
        val engine = engine()
        engine.beginDuck()
        now += 100
        engine.update(config.copy(enabled = false))
        assertEquals(1f, engine.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `a new level applies to the duck already in progress`() {
        val engine = engine()
        engine.beginDuck()
        now += 100
        engine.update(config.copy(level = 0.05f))
        assertEquals(0.05f, engine.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `out of range levels are clamped instead of amplifying or inverting`() {
        val loud = engine(config.copy(level = 2.5f, fadeDownMs = 0L))
        loud.beginDuck()
        now += 10
        assertEquals(1f, loud.stateAt(now).scale, 0.0001f)

        val muted = engine(config.copy(level = -1f, fadeDownMs = 0L))
        muted.beginDuck()
        assertEquals(0f, muted.stateAt(now).scale, 0.0001f)
    }

    @Test
    fun `the duck scales the listener's own music volume`() {
        val engine = engine()
        engine.beginDuck()
        now += 100
        assertEquals(0.18f, engine.volumeFor(0.6f, now), 0.0001f) // 0.6 × 0.30
        assertEquals(0.6f, engine.volumeFor(0.6f, now - 100), 0.0001f) // before the drop starts
    }

    @Test
    fun `next change points at the end of the current ramp`() {
        val engine = engine()
        engine.beginDuck()
        assertEquals(100L, engine.nextChangeAt(now))
        now += 100
        engine.release()
        assertEquals(500L, engine.nextChangeAt(now)) // hold, then the climb
        now += 600
        assertNull(engine.nextChangeAt(now)) // settled
    }

    @Test
    fun `inaudible volume steps are not pushed to the player`() {
        val engine = engine()
        assertFalse(engine.shouldApply(applied = 0.600f, candidate = 0.601f))
        assertTrue(engine.shouldApply(applied = 0.600f, candidate = 0.580f))
    }

    @Test
    fun `a forty cue burst never leaves the music ducked afterwards`() {
        val engine = engine()
        repeat(40) {
            engine.beginDuck()
            now += 120
            engine.release()
            now += 60
        }
        now += config.releaseMillis + 1
        assertEquals(1f, engine.stateAt(now).scale, 0.0001f)
        assertFalse(engine.isDucking(now))
    }
}
