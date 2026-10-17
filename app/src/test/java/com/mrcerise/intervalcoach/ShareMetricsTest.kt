package com.mrcerise.intervalcoach

import com.mrcerise.intervalcoach.ui.summary.ShareMetrics
import org.junit.Assert.*
import org.junit.Test

class ShareMetricsTest {
    @Test fun `completion handles empty and invalid totals without overflow`() {
        assertEquals(0, ShareMetrics.completion(0, 0))
        assertEquals(0, ShareMetrics.completion(2, -1))
        assertEquals(0, ShareMetrics.completion(-1, 20))
        assertEquals(33, ShareMetrics.completion(1, 3))
        assertEquals(100, ShareMetrics.completion(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(100, ShareMetrics.completion(11, 10))
    }
    @Test fun `clock handles subsecond hour and extreme durations`() {
        assertEquals("0:00", ShareMetrics.clock(-1))
        assertEquals("0:01", ShareMetrics.clock(1))
        assertEquals("1:00", ShareMetrics.clock(59999))
        assertEquals("1:00:00", ShareMetrics.clock(3600000))
        assertEquals("25:01:01", ShareMetrics.clock(90061000))
        assertFalse(ShareMetrics.clock(Long.MAX_VALUE).startsWith("-"))
    }
}
