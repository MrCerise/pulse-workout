package com.pulse.intervalcoach.wear.session

import com.pulse.engine.TimerStatus
import com.pulse.intervalcoach.wear.history.WatchHistoryRecorder
import com.pulse.sync.LiveSession
import com.pulse.sync.SessionLog
import com.pulse.sync.SessionOrigin
import com.pulse.sync.SessionStatus
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A finished watch session must reach *both* the local history and the phone link, and neither may
 * be able to lose it. The fakes here are the two sides the container wires together; the link's own
 * queueing and the sync switch are covered where they live.
 */
class WatchSessionRecorderTest {

    private class FakeHistory : WatchHistoryRecorder {
        val logged = mutableListOf<SessionLog>()
        override suspend fun record(log: SessionLog) {
            logged += log
        }
    }

    private class FakeLink : WatchSessionPublisher {
        val lives = mutableListOf<LiveSession>()
        val logged = mutableListOf<SessionLog>()
        override fun onLiveSnapshot(live: LiveSession) {
            lives += live
        }

        override fun onSessionFinished(log: SessionLog) {
            logged += log
        }
    }

    /** A link that cannot do anything with the session at all: no phone, so nothing to send to. */
    private class UnpairedLink : WatchSessionPublisher {
        var failures = 0

        override fun onLiveSnapshot(live: LiveSession) {
            failures++
            throw IllegalStateException("no phone paired")
        }

        override fun onSessionFinished(log: SessionLog) {
            failures++
            throw IllegalStateException("no phone paired")
        }
    }

    private fun log(sessionId: String = "watch-1") = SessionLog(
        sessionId = sessionId,
        workoutId = "builtin-tabata",
        planName = "Tabata Classic",
        planJson = """{"id":"builtin-tabata","name":"Tabata Classic"}""",
        startedAt = 1_000L,
        endedAt = 241_000L,
        activeMillis = 240_000L,
        wallMillis = 240_000L,
        sessionElapsedMillis = 240_000L,
        completedIntervals = 16,
        totalIntervals = 16,
        skippedIntervals = 0,
        status = SessionStatus.COMPLETED,
    )

    private fun live(sessionId: String = "watch-1") = LiveSession(
        origin = SessionOrigin.WATCH,
        sessionId = sessionId,
        planId = "builtin-tabata",
        planName = "Tabata Classic",
        status = TimerStatus.RUNNING.name,
        stepIndex = 0,
        totalSteps = 16,
        stepName = "Max effort",
        stepKind = "WORK",
        stepRemainingMillis = 20_000L,
        stepDurationMillis = 20_000L,
        sessionElapsedMillis = 0L,
        completedSteps = 0,
        skippedSteps = 0,
    )

    @Test
    fun `a finished session is recorded locally and handed to the link`() = runTest {
        val history = FakeHistory()
        val link = FakeLink()
        val recorder = WatchSessionRecorder(scope = backgroundScope, history = history, link = link)

        recorder.onSessionFinished(log())
        // `runCurrent`, not `advanceUntilIdle`: the latter stops once only backgroundScope work
        // remains, which is exactly what the recorder's history write is.
        runCurrent()

        assertEquals(1, history.logged.size)
        assertEquals(1, link.logged.size)
        assertEquals("watch-1", history.logged.single().sessionId)
        assertEquals("watch-1", link.logged.single().sessionId)
    }

    @Test
    fun `live snapshots pass straight through and are never written to the history`() = runTest {
        val history = FakeHistory()
        val link = FakeLink()
        val recorder = WatchSessionRecorder(scope = backgroundScope, history = history, link = link)

        recorder.onLiveSnapshot(live())
        // `runCurrent`, not `advanceUntilIdle`: the latter stops once only backgroundScope work
        // remains, which is exactly what the recorder's history write is.
        runCurrent()

        assertEquals(1, link.lives.size)
        assertTrue(history.logged.isEmpty())
    }

    @Test
    fun `recording does not depend on the link being able to deliver`() = runTest {
        // The history write is scheduled before the log is handed to the link, so a watch with no
        // phone — or one whose link fails outright — still remembers what it ran. The failure has
        // to stop *inside* the recorder: this callback is the last thing a finishing session does,
        // and an exception thrown here unwinds into the engine's own teardown.
        val history = FakeHistory()
        val link = UnpairedLink()
        val recorder = WatchSessionRecorder(scope = backgroundScope, history = history, link = link)

        recorder.onSessionFinished(log("offline-session"))
        // `runCurrent`, not `advanceUntilIdle`: the latter stops once only backgroundScope work
        // remains, which is exactly what the recorder's history write is.
        runCurrent()

        assertEquals("offline-session", history.logged.single().sessionId)
        assertEquals(1, link.failures)
    }

    @Test
    fun `a failing link cannot stop a live snapshot or the session behind it`() = runTest {
        val history = FakeHistory()
        val link = UnpairedLink()
        val recorder = WatchSessionRecorder(scope = backgroundScope, history = history, link = link)

        recorder.onLiveSnapshot(live())
        recorder.onSessionFinished(log("last-step"))
        runCurrent()

        assertEquals(2, link.failures)
        assertEquals("last-step", history.logged.single().sessionId)
    }
}
