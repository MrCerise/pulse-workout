package com.pulse.intervalcoach

import com.pulse.intervalcoach.audio.UtteranceQueue
import org.junit.Assert.*
import org.junit.Test

class UtteranceQueueTest {
    @Test fun `queued speech holds the duck between callbacks`() {
        val queue = UtteranceQueue()
        assertEquals(1000L, queue.submit("one", 1000, false))
        assertEquals(1500L, queue.submit("two", 500, false))
        assertFalse(queue.finish("one"))
        assertTrue(queue.finish("two"))
        assertFalse(queue.finish("two"))
    }
    @Test fun `flushed callbacks cannot release replacement speech`() {
        val queue = UtteranceQueue()
        queue.submit("old", 1000, false)
        assertEquals(800L, queue.submit("new", 800, true))
        assertFalse(queue.finish("old"))
        assertFalse(queue.finish(null))
        assertTrue(queue.finish("new"))
    }
    @Test fun `stop invalidates callbacks and rejection releases the last cue`() {
        val queue = UtteranceQueue()
        queue.submit("old", 1000, false)
        queue.clear()
        queue.submit("rejected", 100, false)
        assertFalse(queue.finish("old"))
        assertTrue(queue.finish("rejected"))
    }
}
