package com.pulse.intervalcoach.wear.history

import com.pulse.sync.LoggedSessionEvent
import com.pulse.sync.SessionLog
import com.pulse.sync.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch's own record of what it ran. These tests cover the rule that matters for a device that
 * is expected to work with no phone at all: a finished session is remembered locally, in order,
 * with a bounded size — and the stored form never grows the event trail the queue is carrying.
 */
class WatchHistoryTest {

    private fun log(
        sessionId: String = "session-1",
        planName: String = "Tabata Classic",
        startedAt: Long = 1_000L,
        endedAt: Long = 241_000L,
        activeMillis: Long = 240_000L,
        completed: Int = 16,
        total: Int = 16,
        skipped: Int = 0,
        status: String = SessionStatus.COMPLETED,
        events: List<LoggedSessionEvent> = listOf(
            LoggedSessionEvent("INTERVAL_STARTED", 0, "Max effort", 0L),
            LoggedSessionEvent("FINISHED", 15, "", 240_000L, durationMillis = 240_000L),
        ),
    ) = SessionLog(
        sessionId = sessionId,
        workoutId = "builtin-tabata",
        planName = planName,
        planJson = """{"id":"builtin-tabata","name":"Tabata Classic"}""",
        startedAt = startedAt,
        endedAt = endedAt,
        activeMillis = activeMillis,
        wallMillis = endedAt - startedAt,
        sessionElapsedMillis = activeMillis,
        completedIntervals = completed,
        totalIntervals = total,
        skippedIntervals = skipped,
        status = status,
        events = events,
    )

    @Test
    fun `a finished session becomes a record without its event trail`() {
        val record = WatchSessionRecord.from(log())
        assertEquals("session-1", record.sessionId)
        assertEquals("Tabata Classic", record.planName)
        assertEquals(240_000L, record.activeMillis)
        assertEquals(16, record.completedIntervals)
        assertEquals(16, record.totalIntervals)
        assertFalse(record.stoppedEarly)
    }

    @Test
    fun `a session stopped early is marked as such`() {
        val record = WatchSessionRecord.from(log(status = SessionStatus.STOPPED_EARLY, completed = 4))
        assertTrue(record.stoppedEarly)
        assertEquals(4, record.completedIntervals)
    }

    @Test
    fun `the newest session is first and a repeated id replaces its own entry`() {
        val first = WatchSessionRecord.from(log(sessionId = "a"))
        val second = WatchSessionRecord.from(log(sessionId = "b"))
        val newest = WatchSessionRecord.from(log(sessionId = "a", activeMillis = 999L))

        val merged = mergeWatchHistory(mergeWatchHistory(emptyList(), first), second)
        assertEquals(listOf("b", "a"), merged.map { it.sessionId })

        val replaced = mergeWatchHistory(merged, newest)
        assertEquals(listOf("a", "b"), replaced.map { it.sessionId })
        assertEquals(2, replaced.size)
        assertEquals(999L, replaced.first().activeMillis)
    }

    @Test
    fun `the history keeps the most recent sessions and drops the rest`() {
        var records: List<WatchSessionRecord> = emptyList()
        for (index in 1..25) {
            records = mergeWatchHistory(records, WatchSessionRecord.from(log(sessionId = "s$index")))
        }
        assertEquals(WATCH_HISTORY_LIMIT, records.size)
        assertEquals("s25", records.first().sessionId)
        // The count is what the wrist shows, so the boundary has to be exact: s6..s25 survive.
        assertEquals("s6", records.last().sessionId)
    }

    @Test
    fun `the codec round trips and refuses anything it cannot read`() {
        val records = listOf(
            WatchSessionRecord.from(log(sessionId = "a")),
            WatchSessionRecord.from(log(sessionId = "b", planName = "Boxing Rounds", skipped = 2)),
        )
        val encoded = WatchHistoryCodec.encode(records)
        assertEquals(records, WatchHistoryCodec.decode(encoded))

        // A corrupt or truncated history must never block the app: it decodes to nothing.
        assertEquals(emptyList<WatchSessionRecord>(), WatchHistoryCodec.decode("{ not json"))
        assertEquals(emptyList<WatchSessionRecord>(), WatchHistoryCodec.decode(null))
        assertEquals(emptyList<WatchSessionRecord>(), WatchHistoryCodec.decode(""))
        assertEquals(emptyList<WatchSessionRecord>(), WatchHistoryCodec.decode("[{\"sessionId\":\"a\"}]"))
    }

    @Test
    fun `an over-long stored history is truncated on read, not trusted`() {
        val stored = WatchHistoryCodec.encode((1..40).map { WatchSessionRecord.from(log(sessionId = "s$it")) })
        assertEquals(WATCH_HISTORY_LIMIT, WatchHistoryCodec.decode(stored).size)
    }
}
