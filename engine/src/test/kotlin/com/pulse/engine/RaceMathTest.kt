package com.pulse.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pure arithmetic tests for the running system: distance accumulation from GPS fixes, the
 * no-GPS projection, and every user-facing race number the player prints.
 */
class RaceMathTest {

    private fun point(lat: Double, lon: Double, accuracy: Float = 5f) = RacePoint(lat, lon, accuracy)

    // Rough reference points: a degree of latitude is ≈ 111.2 km.
    private val origin = point(48.8566, 2.3522)

    // -------------------------------------------------------------------------------------------
    // Haversine
    // -------------------------------------------------------------------------------------------

    @Test
    fun `identical points are zero metres`() {
        assertEquals(0.0, RaceMath.haversineMeters(origin, origin), 0.001)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val moved = point(49.8566, 2.3522)
        val meters = RaceMath.haversineMeters(origin, moved)
        assertEquals(111_195.0, meters, 200.0)
    }

    @Test
    fun `short distance is measured to metre scale`() {
        // ≈ 0.0001° latitude ≈ 11.1 m.
        val moved = point(48.8567, 2.3522)
        val meters = RaceMath.haversineMeters(origin, moved)
        assertEquals(11.1, meters, 0.5)
    }

    // -------------------------------------------------------------------------------------------
    // Step acceptance
    // -------------------------------------------------------------------------------------------

    @Test
    fun `first fix seeds without contributing distance`() {
        assertEquals(0.0, RaceMath.stepMeters(null, origin), 0.0)
    }

    @Test
    fun `plausible step is accumulated`() {
        val next = point(48.8567, 2.3522) // ≈ 11 m north
        assertEquals(11.1, RaceMath.stepMeters(origin, next), 0.5)
    }

    @Test
    fun `inaccurate fix contributes nothing`() {
        val next = point(48.8567, 2.3522, accuracy = 80f)
        assertEquals(0.0, RaceMath.stepMeters(origin, next), 0.0)
    }

    @Test
    fun `gps jump contributes nothing`() {
        val jump = point(48.9, 2.3522) // ≈ 4.8 km away in one step
        assertEquals(0.0, RaceMath.stepMeters(origin, jump), 0.0)
    }

    @Test
    fun `invalid coordinates and accuracy never seed a measured route`() {
        listOf(
            point(Double.NaN, 2.0), point(48.0, Double.POSITIVE_INFINITY),
            point(91.0, 2.0), point(48.0, 181.0),
            point(48.0, 2.0, Float.NaN), point(48.0, 2.0, Float.POSITIVE_INFINITY),
            point(48.0, 2.0, -1f),
        ).forEach { invalid ->
            assertFalse(RaceMath.isUsablePoint(invalid))
            assertEquals(0.0, RaceMath.stepMeters(origin, invalid), 0.0)
            assertEquals(0.0, RaceMath.stepMeters(invalid, origin), 0.0)
        }
        assertTrue(RaceMath.isUsablePoint(origin))
    }

    // -------------------------------------------------------------------------------------------
    // Projection without GPS
    // -------------------------------------------------------------------------------------------

    @Test
    fun `projection uses the planned pace`() {
        // 6:00 /km for 12 minutes = 2 km.
        assertEquals(2_000.0, RaceMath.projectedDistanceMeters(12 * 60_000L, 360), 0.01)
    }

    @Test
    fun `projection of zero time or bad pace is zero`() {
        assertEquals(0.0, RaceMath.projectedDistanceMeters(0L, 360), 0.0)
        assertEquals(0.0, RaceMath.projectedDistanceMeters(60_000L, 0), 0.0)
    }

    // -------------------------------------------------------------------------------------------
    // RaceInfo read-outs
    // -------------------------------------------------------------------------------------------

    @Test
    fun `remaining distance clamps at zero`() {
        val info = RaceInfo(
            source = RaceSource.ESTIMATED,
            distanceMeters = 6_000.0,
            movingMillis = 30 * 60_000L,
            speedMps = 3.33,
            targetDistanceMeters = 5_000.0,
        )
        assertEquals(0.0, info.remainingMeters!!, 0.001)
        assertTrue(info.finished)
        assertEquals(0L, info.etaMillis)
    }

    @Test
    fun `eta is projected from the average speed`() {
        // 2 km done in 10 min at 5:00 /km; 3 km to go → 15 min.
        val info = RaceInfo(
            source = RaceSource.GPS,
            distanceMeters = 2_000.0,
            movingMillis = 10 * 60_000L,
            speedMps = 3.33,
            targetDistanceMeters = 5_000.0,
            hasFix = true,
        )
        assertEquals(3_000.0, info.remainingMeters!!, 0.001)
        val eta = info.etaMillis!!
        assertTrue("eta was $eta", eta >= 15 * 60_000L - 2_000L && eta <= 15 * 60_000L + 2_000L)
    }

    @Test
    fun `open-ended run has no remaining and no eta`() {
        val info = RaceInfo(
            source = RaceSource.GPS,
            distanceMeters = 1_200.0,
            movingMillis = 6 * 60_000L,
            speedMps = 3.33,
            hasFix = true,
        )
        assertNull(info.remainingMeters)
        assertNull(info.etaMillis)
        assertFalse(info.finished)
    }

    @Test
    fun `average speed is distance over moving time`() {
        val info = RaceInfo(
            source = RaceSource.GPS,
            distanceMeters = 3_000.0,
            movingMillis = 15 * 60_000L,
            speedMps = 1.0,
        )
        assertEquals(3.33, info.averageSpeedMps, 0.01)
    }

    @Test
    fun `pace is the inverse of speed`() {
        val info = RaceInfo(
            source = RaceSource.GPS,
            distanceMeters = 0.0,
            movingMillis = 0L,
            speedMps = 1000.0 / 300.0, // 5:00 /km
        )
        assertEquals(300.0, info.paceSecondsPerKm!!, 0.5)
    }

    @Test
    fun `standing still has no pace`() {
        val info = RaceInfo(
            source = RaceSource.GPS,
            distanceMeters = 0.0,
            movingMillis = 0L,
            speedMps = 0.0,
        )
        assertNull(info.paceSecondsPerKm)
    }

    // -------------------------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------------------------

    @Test
    fun `speed is printed as km h in the current locale`() {
        withLocale(Locale.US) {
            assertEquals("10.0", RaceMath.formatSpeedKph(100.0 / 36.0))
            assertEquals("0.0", RaceMath.formatSpeedKph(0.0))
        }
        withLocale(Locale.FRANCE) {
            assertEquals("10,0", RaceMath.formatSpeedKph(100.0 / 36.0))
            assertEquals("0,0", RaceMath.formatSpeedKph(0.0))
        }
    }

    @Test
    fun `distance is printed in kilometres in the current locale`() {
        withLocale(Locale.US) {
            assertEquals("3.24", RaceMath.formatDistanceKm(3_240.0))
            assertEquals("0.00", RaceMath.formatDistanceKm(0.0))
            assertEquals("12.5", RaceMath.formatDistanceKm(12_500.0))
        }
        withLocale(Locale.FRANCE) {
            assertEquals("3,24", RaceMath.formatDistanceKm(3_240.0))
            assertEquals("0,00", RaceMath.formatDistanceKm(0.0))
            assertEquals("12,5", RaceMath.formatDistanceKm(12_500.0))
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `pace is printed as minutes and seconds`() {
        assertEquals("5:00", RaceMath.formatPace(300.0))
        assertEquals("5:30", RaceMath.formatPace(330.4))
        assertEquals("1:02:03", RaceMath.formatPace(3_723.0)) // > 60 min/km falls back to h:mm:ss
        assertEquals("--:--", RaceMath.formatPace(null))
        assertEquals("--:--", RaceMath.formatPace(0.0))
    }

    @Test
    fun `eta is printed as a duration`() {
        assertEquals("--:--", RaceMath.formatEta(null))
        assertEquals("1:30", RaceMath.formatEta(90_000L))
    }

    @Test
    fun `new plan fields default to no race`() {
        val plan = WorkoutPlan(id = "p", name = "Run")
        assertNull(plan.raceDistanceMeters)
        assertNull(plan.racePaceSecondsPerKm)
    }

    @Test
    fun `run workout types include running`() {
        assertEquals(WorkoutType.RUN, WorkoutType.entries.first { it.name == "RUN" })
    }
}
