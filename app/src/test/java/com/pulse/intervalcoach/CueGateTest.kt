package com.pulse.intervalcoach

import com.pulse.intervalcoach.audio.CueGate
import com.pulse.intervalcoach.audio.CuePriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cue gate is the guard against speech backlog: a Tabata block produces far more cues than a
 * voice can finish, so these tests pin down exactly what gets dropped.
 */
class CueGateTest {

    private var now = 0L

    private fun gate(): CueGate = CueGate(nowMillis = { now }, speakingRate = 14.0)

    @Test
    fun `interval changes always speak and interrupt previous speech`() {
        val gate = gate()
        val first = gate.decide("Work, twenty seconds.", CuePriority.INTERVAL_CHANGE)
        assertTrue(first.speak)
        assertTrue(first.flush)

        val second = gate.decide("Rest, ten seconds.", CuePriority.INTERVAL_CHANGE)
        assertTrue(second.speak)
        assertTrue(second.flush)
    }

    @Test
    fun `countdown cues are dropped instead of stacking up`() {
        val gate = gate()
        // ~3 s of speech: the round announcement.
        gate.decide("Round 2 of 8. Max effort. Twenty seconds.", CuePriority.INTERVAL_CHANGE)
        // One second later there is still over 1.5 s of speech left, so a countdown would run past
        // the next boundary — it is dropped rather than spoken late.
        now += 1_000
        val countdown = gate.decide("Three", CuePriority.COUNTDOWN)
        assertFalse(countdown.speak)
        assertTrue(countdown.droppedForBacklog)
    }

    @Test
    fun `a countdown speaks once the announcement is nearly finished`() {
        val gate = gate()
        gate.decide("Rest. Ten seconds.", CuePriority.INTERVAL_CHANGE)
        // Only the tail of the announcement is left: a short countdown may follow it.
        now += 1_100
        val countdown = gate.decide("Three", CuePriority.COUNTDOWN)
        assertTrue(countdown.speak)
        assertFalse(countdown.droppedForBacklog)
    }

    @Test
    fun `cues speak again once the previous utterance has finished`() {
        val gate = gate()
        val text = "Work, twenty seconds."
        gate.decide(text, CuePriority.INTERVAL_CHANGE)
        now += gate.estimateDuration(text) + 1
        val next = gate.decide("Rest, ten seconds.", CuePriority.INTERVAL_CHANGE)
        assertTrue(next.speak)
    }

    @Test
    fun `a rapid sequence of short intervals is throttled to the speech rate`() {
        val gate = gate()
        val total = 40
        val cadence = 250L
        var spoken = 0
        var dropped = 0
        // 40 cues arriving every 250 ms — a short-interval storm that no voice could keep up with.
        repeat(total) { index ->
            val decision = gate.decide("Step $index", CuePriority.INFO)
            if (decision.speak) spoken++
            if (decision.droppedForBacklog) dropped++
            now += cadence
        }
        // The gate is a speech-rate limiter, not a queue: at most one utterance per cue-length
        // window may start, so a storm can never build a backlog.
        val windows = (total * cadence) / CueGate.MIN_CUE_MILLIS
        assertTrue("spoken $spoken of $total exceeds one cue per ${CueGate.MIN_CUE_MILLIS} ms", spoken <= windows + 1)
        assertTrue("most cues must be dropped, not queued ($dropped of $total)", dropped >= total / 2)
        assertTrue("far fewer cues spoken than requested ($spoken of $total)", spoken < total / 2)
    }

    @Test
    fun `estimate duration scales with text length`() {
        val gate = gate()
        val short = gate.estimateDuration("Go")
        val long = gate.estimateDuration("Round three. Work, forty seconds. Next: rest, twenty seconds.")
        assertEquals("the short-cue floor is 750 ms", CueGate.MIN_CUE_MILLIS, short)
        assertTrue(long > short)
    }

    @Test
    fun `reset clears the backlog bookkeeping`() {
        val gate = gate()
        gate.decide("Round 2 of 8. Max effort. Twenty seconds.", CuePriority.INTERVAL_CHANGE)
        now += 500
        assertFalse("still 2.5 s of speech queued", gate.decide("Three", CuePriority.COUNTDOWN).speak)
        gate.reset()
        assertTrue("after a reset the gate is free again", gate.decide("Three", CuePriority.COUNTDOWN).speak)
    }

    @Test
    fun `dropped cues are counted exactly`() {
        val gate = gate()
        gate.decide("A long interval announcement that takes a while to speak", CuePriority.INTERVAL_CHANGE)
        now += 200
        val dropped = (1..10).count { gate.decide("Three", CuePriority.COUNTDOWN).droppedForBacklog }
        assertEquals(10, dropped)
    }
}
