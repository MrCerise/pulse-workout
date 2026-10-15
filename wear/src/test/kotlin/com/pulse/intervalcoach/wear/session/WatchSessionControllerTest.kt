package com.pulse.intervalcoach.wear.session

import com.pulse.engine.FakeClock
import com.pulse.engine.HapticCue
import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.PhaseKind
import com.pulse.engine.TimerStatus
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import com.pulse.intervalcoach.wear.WatchPreferences
import com.pulse.sync.LiveSession
import com.pulse.sync.SessionLog
import com.pulse.sync.SessionOrigin
import com.pulse.sync.SessionStatus
import com.pulse.sync.SyncJson
import com.pulse.sync.WatchCommand
import com.pulse.sync.WatchCommandKind
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch session lifecycle, on the JVM: start, tick, control, finish, and the exact log that
 * is handed to the phone. Everything Android (haptics, transport) is faked.
 */
class WatchSessionControllerTest {

    private class RecordingCues : WatchCues {
        val calls = mutableListOf<String>()
        override fun intervalCue(cue: HapticCue) {
            calls += "interval:$cue"
        }

        override fun countdownTick() {
            calls += "tick"
        }
    }

    private class RecordingPublisher : WatchSessionPublisher {
        val lives = mutableListOf<LiveSession>()
        val logs = mutableListOf<SessionLog>()
        override fun onLiveSnapshot(live: LiveSession) {
            lives += live
        }

        override fun onSessionFinished(log: SessionLog) {
            logs += log
        }
    }

    private fun plan(id: String = "w-1") = WorkoutPlan(
        id = id,
        name = "Two steps",
        type = WorkoutType.HIIT,
        nodes = listOf(
            IntervalNode("n1", IntervalSpec("i1", "Get ready", PhaseKind.PREPARE, 5_000L)),
            IntervalNode("n2", IntervalSpec("i2", "Work", PhaseKind.WORK, 10_000L)),
        ),
    )

    @Test
    fun `runs to completion and produces a completed log`() = runTest {
        val clock = FakeClock()
        val cues = RecordingCues()
        val publisher = RecordingPublisher()
        val wall = AtomicLong(1_000_000)
        val controller = WatchSessionController(
            scope = backgroundScope,
            clock = clock,
            cues = cues,
            publisher = publisher,
            preferences = MutableStateFlow(WatchPreferences()),
            wallClock = { wall.addAndGet(50L) },
        )

        controller.startWorkout(plan()).getOrThrow()
        assertEquals(TimerStatus.RUNNING, controller.snapshot.value.status)
        assertEquals("Two steps", controller.snapshot.value.planName)

        // Halfway through preparation.
        clock.advance(4_000)
        advanceTimeBy(4_500)
        runCurrent()
        assertEquals(PhaseKind.PREPARE, controller.snapshot.value.step?.kind)

        // Cross into the work interval.
        clock.advance(2_000)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(PhaseKind.WORK, controller.snapshot.value.step?.kind)

        // Run the work interval out (past the boundary: settle() must see the crossing).
        clock.advance(9_500)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(TimerStatus.FINISHED, controller.snapshot.value.status)
        assertEquals(1, publisher.logs.size)
        val log = publisher.logs.single()
        assertEquals(SessionStatus.COMPLETED, log.status)
        assertEquals("w-1", log.workoutId)
        assertEquals(2, log.completedIntervals)
        assertEquals(2, log.totalIntervals)
        assertTrue(log.events.any { it.kind == "INTERVAL_STARTED" })
        assertTrue(log.events.any { it.kind == "INTERVAL_COMPLETED" })
        assertTrue(log.events.any { it.kind == "FINISHED" })
        // The log's plan snapshot must survive the wire format round trip.
        assertEquals(plan(), SyncJson.decodePlan(log.planJson))
        // The last live snapshot the phone sees says the session finished.
        assertEquals(TimerStatus.FINISHED.name, publisher.lives.last().status)
        assertEquals(SessionOrigin.WATCH, publisher.lives.last().origin)

        val summary = controller.summary.value
        assertNotNull(summary)
        assertEquals(2, summary!!.completedIntervals)
        assertEquals(false, summary.stoppedEarly)
        assertTrue(summary.activeMillis > 0L)
    }

    @Test
    fun `stopping early produces a stopped log and a summary`() = runTest {
        val clock = FakeClock()
        val publisher = RecordingPublisher()
        val controller = WatchSessionController(
            scope = backgroundScope,
            clock = clock,
            cues = RecordingCues(),
            publisher = publisher,
            preferences = MutableStateFlow(WatchPreferences()),
            wallClock = { 10_000L },
        )

        controller.startWorkout(plan()).getOrThrow()
        clock.advance(1_000)
        advanceTimeBy(1_500)
        controller.stop()

        assertEquals(TimerStatus.FINISHED, controller.snapshot.value.status)
        assertEquals(SessionStatus.STOPPED_EARLY, publisher.logs.single().status)
        assertEquals(true, controller.summary.value?.stoppedEarly)
        assertEquals(null, controller.active.value)
    }

    @Test
    fun `phone commands control the local session`() = runTest {
        val clock = FakeClock()
        val publisher = RecordingPublisher()
        val controller = WatchSessionController(
            scope = backgroundScope,
            clock = clock,
            cues = RecordingCues(),
            publisher = publisher,
            preferences = MutableStateFlow(WatchPreferences()),
            wallClock = { 10_000L },
        )

        controller.startWorkout(plan()).getOrThrow()
        controller.applyCommand(WatchCommand(WatchCommandKind.NEXT))
        assertEquals(PhaseKind.WORK, controller.snapshot.value.step?.kind)
        assertEquals(1, controller.snapshot.value.skippedSteps)

        controller.applyCommand(WatchCommand(WatchCommandKind.TOGGLE_PAUSE))
        assertEquals(TimerStatus.PAUSED, controller.snapshot.value.status)

        controller.applyCommand(WatchCommand(WatchCommandKind.TOGGLE_PAUSE))
        assertEquals(TimerStatus.RUNNING, controller.snapshot.value.status)

        controller.applyCommand(WatchCommand(WatchCommandKind.STOP))
        assertEquals(SessionStatus.STOPPED_EARLY, publisher.logs.single().status)
    }

    @Test
    fun `haptic cues are gated by the vibration preference`() = runTest {
        val clock = FakeClock()
        val cues = RecordingCues()
        val preferences = MutableStateFlow(WatchPreferences(vibrationEnabled = false))
        val controller = WatchSessionController(
            scope = backgroundScope,
            clock = clock,
            cues = cues,
            publisher = RecordingPublisher(),
            preferences = preferences,
            wallClock = { 10_000L },
        )

        controller.startWorkout(plan()).getOrThrow()
        clock.advance(1_000)
        advanceTimeBy(1_500)
        controller.stop()
        assertTrue("expected no haptics, got ${cues.calls}", cues.calls.isEmpty())

        // Re-enable mid-session: cues come back.
        preferences.value = WatchPreferences(vibrationEnabled = true)
        runCurrent()
        controller.startWorkout(plan()).getOrThrow()
        assertTrue(cues.calls.isNotEmpty())
    }

    @Test
    fun `starting a new workout finalizes the running one`() = runTest {
        val clock = FakeClock()
        val publisher = RecordingPublisher()
        val controller = WatchSessionController(
            scope = backgroundScope,
            clock = clock,
            cues = RecordingCues(),
            publisher = publisher,
            preferences = MutableStateFlow(WatchPreferences()),
            wallClock = { 10_000L },
        )

        controller.startWorkout(plan("a")).getOrThrow()
        clock.advance(2_000)
        advanceTimeBy(2_500)
        controller.startWorkout(plan("b")).getOrThrow()

        assertEquals(1, publisher.logs.size)
        assertEquals("a", publisher.logs.single().workoutId)
        assertEquals(SessionStatus.STOPPED_EARLY, publisher.logs.single().status)
        assertEquals("b", controller.active.value?.plan?.id)
        assertEquals(TimerStatus.RUNNING, controller.snapshot.value.status)
    }

    @Test
    fun `unplayable workouts are refused`() = runTest {
        val controller = WatchSessionController(
            scope = backgroundScope,
            clock = FakeClock(),
            cues = RecordingCues(),
            publisher = RecordingPublisher(),
            preferences = MutableStateFlow(WatchPreferences()),
            wallClock = { 10_000L },
        )
        val empty = WorkoutPlan(id = "empty", name = "Empty", nodes = emptyList())
        assertTrue(controller.startWorkout(empty).isFailure)
    }
}

/** The summary's start time, named so the assertion above reads like what it checks. */
private val WatchSessionSummary.startedAtPlaceholder: Long
    get() = endedAt - wallMillis
