package com.mrcerise.intervalcoach

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrcerise.intervalcoach.data.*
import com.mrcerise.intervalcoach.data.db.PulseDatabase
import com.mrcerise.intervalcoach.journey.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class JourneyPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: PulseDatabase
    private lateinit var sessions: SessionRepository
    private lateinit var journey: JourneyRepository
    private val name = "journey-test-${UUID.randomUUID()}.db"
    private val monday = Instant.parse("2026-10-12T10:00:00Z")
    private val node = JourneyCatalogue.nodes.first()

    private fun open() {
        db = Room.databaseBuilder(context, PulseDatabase::class.java, name).build()
        sessions = SessionRepository(db)
        journey = JourneyRepository(db)
    }
    @Before fun setup() = open()
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private suspend fun finish(id: String, status: String = STATUS_COMPLETED, skipped: Int = 0) {
        sessions.finish(id, monday.toEpochMilli() + 600_000, 600_000, 600_000,
            600_000, 10, 10, skipped, status, 0, null)
    }

    @Test fun explicitOriginSeparatesHistoryAndCreditsAndSurvivesReopening() = runBlocking {
        journey.configure(JourneyLevel.BEGINNER, 3, 21, null, monday)
        val plan = node.plan(false)
        val route = sessions.start(plan, monday.toEpochMilli(), JourneyContext(node.id))
        finish(route.sessionId)
        finish(route.sessionId) // Redelivered finalization must not grant another credit.
        val free = sessions.start(plan, monday.toEpochMilli()) // Even an identical id cannot advance the route.
        finish(free.sessionId)
        assertEquals(listOf(free.sessionId), sessions.history.first().map { it.id })
        assertEquals(1L, sessions.totalActiveMillis() / 600_000)
        assertEquals(1, journey.state.first().credits.size)
        assertEquals(setOf(node.id), journey.state.first().completed)
        sessions.delete(free.sessionId)
        assertTrue(sessions.history.first().isEmpty())
        assertNotNull(sessions.session(route.sessionId))
        db.close()
        open()
        assertEquals(setOf(node.id), journey.state.first().completed)
        assertEquals("JOURNEY", sessions.session(route.sessionId)?.source)
        assertEquals(JourneyCatalogue.VERSION, sessions.session(route.sessionId)?.journeyVersion)
        sessions.deleteAll()
        assertNull(sessions.session(route.sessionId))
        assertTrue(journey.state.first().credits.isEmpty())
        assertNotNull(journey.state.first().enrollment)
    }

    @Test fun generatedSessionsContinueAfterTheMilestoneAndSurviveRestart() = runBlocking {
        journey.configure(JourneyLevel.ADVANCED, 3, 21, null, monday)
        JourneyCatalogue.path(JourneyLevel.ADVANCED).forEachIndexed { index, entry ->
            db.journeyDao().credit(JourneyCredit("foundation-$index", entry.id, monday.toEpochMilli()))
        }
        val generated = journey.state.first().next
        assertEquals(4, generated.chapter)
        assertEquals(JourneyCatalogue.at(JourneyLevel.ADVANCED, 24), generated)
        val attempt = sessions.start(generated.plan(false), monday.toEpochMilli(), JourneyContext(generated.id))
        finish(attempt.sessionId)
        assertTrue(sessions.history.first().isEmpty())
        val state = journey.state.first()
        assertTrue(state.levelComplete)
        assertEquals(24, state.foundationCompleted)
        assertEquals(25, state.completedCount())
        assertEquals(JourneyCatalogue.at(JourneyLevel.ADVANCED, 25), state.next)
        db.close()
        open()
        assertEquals(state.next, journey.state.first().next)
        assertEquals(generated.id, sessions.session(attempt.sessionId)?.journeyNodeId)
    }

    @Test fun stoppedSkippedAndInterruptedAttemptsNeverEarnCredit() = runBlocking {
        val stopped = sessions.start(node.plan(false), monday.toEpochMilli(), JourneyContext(node.id))
        finish(stopped.sessionId, STATUS_STOPPED)
        val skipped = sessions.start(node.plan(false), monday.toEpochMilli(), JourneyContext(node.id))
        finish(skipped.sessionId, skipped = 1)
        val interrupted = sessions.start(node.plan(false), monday.toEpochMilli(), JourneyContext(node.id))
        sessions.heartbeat(interrupted.sessionId, monday.toEpochMilli() + 5000, 5000)
        sessions.recoverInterruptedSessions()
        assertEquals(STATUS_INTERRUPTED, sessions.session(interrupted.sessionId)?.status)
        assertEquals(STATUS_STOPPED, sessions.session(stopped.sessionId)?.status)
        assertTrue(journey.state.first().credits.isEmpty())
    }

    @Test fun targetChangesTakeEffectNextWeekAndLevelSwitchingKeepsEnrollment() = runBlocking {
        journey.configure(JourneyLevel.BEGINNER, 3, 21, null, monday)
        val initial = journey.state.first().enrollment!!
        journey.configure(JourneyLevel.INTERMEDIATE, 2, 9, null, monday.plusSeconds(3600))
        val state = journey.state.first()
        assertEquals(initial.enrolledAt, state.enrollment!!.enrolledAt)
        assertEquals(initial.zoneId, state.enrollment!!.zoneId)
        assertEquals(JourneyLevel.INTERMEDIATE, state.level)
        val week = JourneyProgress.week(monday.atZone(ZoneId.of(initial.zoneId)).toLocalDate())
        assertEquals(3, state.targets.single { it.week == week.toString() }.days)
        assertEquals(2, state.targets.single { it.week == week.plusWeeks(1).toString() }.days)
        journey.selectLevel(JourneyLevel.ADVANCED)
        assertEquals(JourneyLevel.ADVANCED, journey.state.first().level)
    }

    @Test fun readinessRequiresCompletionButLevelSelectionIsAlwaysFree() = runBlocking {
        journey.configure(JourneyLevel.BEGINNER, 3, 21, null, monday)
        try { journey.readiness(JourneyLevel.BEGINNER, true); fail("Incomplete level must not be marked ready") }
        catch (_: IllegalArgumentException) { }
        journey.selectLevel(JourneyLevel.ADVANCED)
        assertEquals(JourneyLevel.ADVANCED, journey.state.first().level)
        JourneyCatalogue.path(JourneyLevel.BEGINNER).forEachIndexed { index, entry ->
            db.journeyDao().credit(JourneyCredit("seed-$index", entry.id, monday.toEpochMilli()))
        }
        journey.readiness(JourneyLevel.BEGINNER, false)
        assertEquals(JourneyLevel.ADVANCED, journey.state.first().level)
        journey.readiness(JourneyLevel.BEGINNER, true)
        assertEquals(JourneyLevel.INTERMEDIATE, journey.state.first().level)
        assertTrue(journey.state.first().readiness.single().ready)
    }

    @Test fun remindersAreOptionalOncePerDayAndSuppressedAfterCompletion() = runBlocking {
        journey.configure(JourneyLevel.BEGINNER, 3, 21, null, monday)
        val initial = journey.state.first().enrollment!!
        db.journeyDao().saveEnrollment(initial.copy(zoneId = "UTC", reminderMinute = 9 * 60))
        assertFalse(journey.reminderDue(Instant.parse("2026-10-12T08:59:00Z")))
        assertTrue(journey.reminderDue(monday))
        var posted = 0
        journey.notifyIfDue(monday) { posted++ }
        journey.notifyIfDue(monday) { posted++ }
        assertEquals(1, posted)
        assertFalse(journey.reminderDue(monday.plusSeconds(3600)))
        db.journeyDao().saveEnrollment(initial.copy(zoneId = "UTC", reminderMinute = 9 * 60))
        val attempt = sessions.start(node.plan(false), monday.toEpochMilli(), JourneyContext(node.id))
        finish(attempt.sessionId)
        assertFalse(journey.reminderDue(monday.plusSeconds(7200)))
        assertFalse(journey.reminderDue(Instant.parse("2026-10-13T10:00:00Z"))) // Unscheduled Tuesday.
    }
}
