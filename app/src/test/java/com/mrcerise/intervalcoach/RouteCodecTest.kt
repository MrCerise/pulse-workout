package com.mrcerise.intervalcoach

import com.pulse.engine.RacePoint
import com.mrcerise.intervalcoach.data.RouteCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteCodecTest {
    @Test fun roundTripsRoute() {
        val points = listOf(RacePoint(48.8566, 2.3522, 5f), RacePoint(48.8570, 2.3530, 5f))
        val decoded = RouteCodec.decode(RouteCodec.encode(points))
        assertEquals(2, decoded.size)
        assertEquals(48.8566, decoded[0].latitude, 0.000001)
        assertEquals(2.3530, decoded[1].longitude, 0.000001)
    }
    @Test fun singlePointIsNotARoute() {
        assertNull(RouteCodec.encode(listOf(RacePoint(1.0, 2.0, 0f))))
        assertEquals(emptyList<RacePoint>(), RouteCodec.decode(null))
    }
}
