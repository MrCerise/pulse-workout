package com.mrcerise.intervalcoach

import com.mrcerise.intervalcoach.session.RunLocationAccess
import com.mrcerise.intervalcoach.session.RunLocationFix
import com.mrcerise.intervalcoach.session.RunLocationSource
import com.mrcerise.intervalcoach.session.RunTracker
import com.mrcerise.intervalcoach.session.RunTrackingStatus
import com.pulse.engine.RacePoint
import com.pulse.engine.RaceSource
import com.pulse.engine.WorkoutPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunTrackerTest {
    private class Source : RunLocationSource {
        var availability = RunLocationAccess.AVAILABLE
        var canSubscribe = true
        var starts = 0
        var fix: ((RunLocationFix) -> Unit)? = null
        var changedProviders = false
        var cached: RunLocationFix? = null
        override fun access() = availability
        override fun start(onFix: (RunLocationFix) -> Unit): Boolean {
            starts++
            fix = onFix
            changedProviders = false
            cached?.let(onFix)
            return canSubscribe
        }
        override fun providersChanged() = changedProviders
        override fun stop() { fix = null }
    }

    private val plan = WorkoutPlan(id = "run", name = "Run", racePaceSecondsPerKm = 360)
    private fun sample(at: Long, lat: Double = 48.8566, accuracy: Float = 5f) =
        RunLocationFix(RacePoint(lat, 2.3522, accuracy), at)

    private fun TestScope.tracker(source: Source) = RunTracker(
        source, backgroundScope, UnconfinedTestDispatcher(testScheduler), { testScheduler.currentTime },
    )

    @Test fun `approximate permission explains why GPS cannot measure the run`() = runTest {
        val source = Source().apply { availability = RunLocationAccess.APPROXIMATE }
        val tracker = tracker(source)
        tracker.start(plan)
        tracker.setPaused(false)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(RunTrackingStatus.PRECISE_LOCATION_REQUIRED, tracker.trackingStatus.value)
        assertEquals(0, source.starts)
        assertEquals(RaceSource.ESTIMATED, tracker.raceInfo.value!!.source)
        assertFalse(tracker.hasFix)
        assertTrue(tracker.raceInfo.value!!.distanceMeters > 0)
    }

    @Test fun `turning location back on during a run starts acquisition automatically`() = runTest {
        val source = Source().apply { availability = RunLocationAccess.DISABLED }
        val tracker = tracker(source)
        tracker.start(plan)
        tracker.setPaused(false)
        assertEquals(RunTrackingStatus.LOCATION_DISABLED, tracker.trackingStatus.value)
        source.availability = RunLocationAccess.AVAILABLE
        advanceTimeBy(5_000)
        runCurrent()
        source.fix!!.invoke(sample(testScheduler.currentTime))
        assertEquals(1, source.starts)
        assertEquals(RunTrackingStatus.TRACKING, tracker.trackingStatus.value)
        assertTrue(tracker.hasFix)
    }

    @Test fun `failed subscriptions are retried instead of waiting forever`() = runTest {
        val source = Source().apply { canSubscribe = false }
        val tracker = tracker(source)
        tracker.start(plan)
        assertEquals(RunTrackingStatus.UNAVAILABLE, tracker.trackingStatus.value)
        source.canSubscribe = true
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, source.starts)
        assertEquals(RunTrackingStatus.SEARCHING, tracker.trackingStatus.value)
        source.fix!!.invoke(sample(testScheduler.currentTime))
        assertTrue(tracker.hasFix)
    }

    @Test fun `provider changes reconnect without losing the session distance`() = runTest {
        val source = Source()
        val tracker = tracker(source)
        tracker.start(plan)
        tracker.setPaused(false)
        source.fix!!.invoke(sample(0))
        advanceTimeBy(1_000)
        source.fix!!.invoke(sample(1_000, lat = 48.8567))
        val distance = tracker.raceInfo.value!!.distanceMeters
        source.changedProviders = true
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(2, source.starts)
        assertEquals(distance, tracker.raceInfo.value!!.distanceMeters, 0.0)
        source.fix!!.invoke(sample(5_000, lat = 49.0))
        assertEquals(distance, tracker.raceInfo.value!!.distanceMeters, 0.0)
    }

    @Test fun `cached stale inaccurate and out of order fixes do not seed or corrupt a route`() = runTest {
        val source = Source().apply { cached = sample(-60_000) }
        val tracker = tracker(source)
        tracker.start(plan)
        tracker.setPaused(false)
        assertFalse(tracker.hasFix)
        source.fix!!.invoke(sample(0, accuracy = 500f))
        source.fix!!.invoke(sample(0, accuracy = Float.NaN))
        assertFalse(tracker.hasFix)
        source.fix!!.invoke(sample(0))
        advanceTimeBy(1_000)
        source.fix!!.invoke(sample(1_000, lat = 48.8567))
        val distance = tracker.raceInfo.value!!.distanceMeters
        assertEquals(11.1, distance, 0.5)
        source.fix!!.invoke(sample(0, lat = 48.0))
        assertEquals(distance, tracker.raceInfo.value!!.distanceMeters, 0.0)
        assertEquals(2, tracker.route.size)
    }

    @Test fun `fixes can acquire while paused without counting paused movement`() = runTest {
        val source = Source().apply { cached = sample(0) }
        val tracker = tracker(source)
        tracker.start(plan)
        assertTrue(tracker.hasFix)
        assertTrue(tracker.route.isEmpty())
        tracker.setPaused(false)
        advanceTimeBy(1_000)
        source.fix!!.invoke(sample(1_000))
        advanceTimeBy(1_000)
        source.fix!!.invoke(sample(2_000, lat = 48.8567))
        val distance = tracker.raceInfo.value!!.distanceMeters
        tracker.setPaused(true)
        advanceTimeBy(1_000)
        source.fix!!.invoke(sample(3_000, lat = 48.9))
        tracker.setPaused(false)
        advanceTimeBy(1_000)
        source.fix!!.invoke(sample(4_000, lat = 48.9))
        assertEquals(distance, tracker.raceInfo.value!!.distanceMeters, 0.0)
    }

    @Test fun `no signal becomes an explicit state and a returning fix does not bridge the gap`() = runTest {
        val source = Source()
        val tracker = tracker(source)
        tracker.start(plan)
        tracker.setPaused(false)
        source.fix!!.invoke(sample(0))
        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(RunTrackingStatus.SIGNAL_LOST, tracker.trackingStatus.value)
        source.fix!!.invoke(sample(31_000, lat = 49.0))
        assertEquals(RunTrackingStatus.TRACKING, tracker.trackingStatus.value)
        assertEquals(0.0, tracker.raceInfo.value!!.distanceMeters, 0.0)
    }

    @Test fun `an unsuccessful first fix search reports the problem and can still recover`() = runTest {
        val source = Source()
        val tracker = tracker(source)
        tracker.start(plan)
        tracker.setPaused(false)
        advanceTimeBy(31_000)
        runCurrent()
        assertFalse(tracker.hasFix)
        assertEquals(RunTrackingStatus.SIGNAL_LOST, tracker.trackingStatus.value)
        assertEquals(RaceSource.ESTIMATED, tracker.raceInfo.value!!.source)
        source.fix!!.invoke(sample(31_000))
        assertEquals(RunTrackingStatus.TRACKING, tracker.trackingStatus.value)
        assertEquals(RaceSource.GPS, tracker.raceInfo.value!!.source)
    }

    @Test fun `a rejected GPS jump is not reused as the next distance seed`() = runTest {
        val source = Source()
        val tracker = tracker(source)
        tracker.start(plan)
        tracker.setPaused(false)
        source.fix!!.invoke(sample(0))
        advanceTimeBy(1_000)
        source.fix!!.invoke(sample(1_000, lat = 49.0))
        advanceTimeBy(1_000)
        source.fix!!.invoke(sample(2_000, lat = 48.8567))
        assertEquals(11.1, tracker.raceInfo.value!!.distanceMeters, 0.5)
        assertEquals(2, tracker.route.size)
    }

    @Test fun `late callbacks from a stopped run cannot change the next run`() = runTest {
        val source = Source()
        val tracker = tracker(source)
        tracker.start(plan)
        tracker.setPaused(false)
        val oldCallback = source.fix!!
        tracker.stop()
        assertNull(source.fix)
        assertEquals(RunTrackingStatus.INACTIVE, tracker.trackingStatus.value)
        tracker.start(plan)
        tracker.setPaused(false)
        oldCallback(sample(0))
        assertFalse(tracker.hasFix)
        source.fix!!.invoke(sample(0))
        assertTrue(tracker.hasFix)
    }
}
