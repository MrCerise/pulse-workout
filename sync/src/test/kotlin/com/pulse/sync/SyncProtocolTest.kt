package com.pulse.sync

import com.pulse.engine.FakeClock
import com.pulse.engine.HapticCue
import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.PhaseKind
import com.pulse.engine.RepeatGroup
import com.pulse.engine.SoundCue
import com.pulse.engine.TimelineExpander
import com.pulse.engine.TimerEngine
import com.pulse.engine.TimerEvent
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProtocolTest {

    private fun samplePlan(): WorkoutPlan = WorkoutPlan(
        id = "w-1",
        name = "Tabata",
        type = WorkoutType.TABATA,
        nodes = listOf(
            IntervalNode(
                "n1",
                IntervalSpec("i1", "Get ready", PhaseKind.PREPARE, 5_000L, sound = SoundCue.BELL, haptic = HapticCue.LIGHT),
            ),
            RepeatGroup(
                "g1",
                name = "Tabata block",
                repeat = 2,
                children = listOf(
                    IntervalNode("n2", IntervalSpec("i2", "Work", PhaseKind.WORK, 20_000L)),
                    IntervalNode("n3", IntervalSpec("i3", "Rest", PhaseKind.REST, 10_000L)),
                ),
            ),
        ),
    )

    @Test
    fun `plan json round-trips with the phone's nodeType discriminator`() {
        val plan = samplePlan()
        val json = SyncJson.encodePlan(plan)
        assertTrue("the discriminator must match the phone's PlanCodec", json.contains("\"nodeType\":\"group\""))
        val decoded = SyncJson.decodePlan(json)
        assertEquals(plan, decoded)
    }

    @Test
    fun `plan decoding tolerates unknown keys`() {
        // A future phone adds a field the watch does not know; the codec must skip it.
        val json = SyncJson.encodePlan(samplePlan())
        val withExtraField = "{\"futureField\":42," + json.removePrefix("{")
        val decoded = SyncJson.decodePlan(withExtraField)
        assertEquals("w-1", decoded.id)
    }

    @Test
    fun `manifest round-trips and rejects protocol drift`() {
        val manifest = LibraryManifest(
            appVersion = "1.7.1",
            workoutIds = listOf("a", "b"),
            updatedAt = listOf(1L, 2L),
            truncated = true,
        )
        val decoded = SyncJson.decodeManifest(SyncJson.encodeManifest(manifest))
        assertEquals(manifest, decoded)
        val stale = SyncJson.encodeManifest(manifest).replace("\"protocolVersion\":1", "\"protocolVersion\":99")
        assertNull(SyncJson.decodeManifest(stale))
        assertNull(SyncJson.decodeManifest("not json"))
    }

    @Test
    fun `preferences round-trip and reject protocol drift`() {
        val prefs = SyncedPreferences(
            themeMode = "TRUE_BLACK",
            highContrast = true,
            vibrationEnabled = false,
            countdownFromSeconds = 5,
        )
        assertEquals(prefs, SyncJson.decodePreferences(SyncJson.encodePreferences(prefs)))
        val stale = SyncJson.encodePreferences(prefs).replace("\"protocolVersion\":1", "\"protocolVersion\":2")
        assertNull(SyncJson.decodePreferences(stale))
    }

    @Test
    fun `commands round-trip as bytes`() {
        val command = WatchCommand(WatchCommandKind.ADD_TIME, millis = 15_000L)
        assertEquals(command, SyncJson.decodeCommand(SyncJson.encodeCommand(command)))
        assertNull(SyncJson.decodeCommand("garbage".toByteArray()))
    }

    @Test
    fun `live messages round-trip as bytes`() {
        val clock = FakeClock()
        val engine = TimerEngine(clock)
        val timeline = TimelineExpander.expand(samplePlan())
        engine.prepare(timeline, "w-1", "Tabata")
        engine.start()
        clock.advance(3_000)
        engine.tick()
        val live = LiveSession.fromSnapshot(
            origin = SessionOrigin.WATCH,
            sessionId = "s-1",
            snapshot = engine.snapshot(),
            sentAt = 1_000L,
        )
        val decoded = SyncJson.decodeLive(SyncJson.encodeLive(live))
        assertNotNull(decoded)
        assertEquals("PREPARE", decoded!!.stepKind)
        assertEquals(SessionOrigin.WATCH, decoded.origin)
        assertEquals(5_000L, decoded.stepDurationMillis)
        assertTrue(decoded.stepRemainingMillis in 1_000L..2_500L)
        assertEquals("RUNNING", decoded.status)
    }

    @Test
    fun `engine events map to history event kinds`() {
        val step = TimelineExpander.expand(samplePlan()).steps.first()
        assertEquals(
            LoggedSessionEvent("INTERVAL_STARTED", 0, "Get ready", 250L),
            TimerEvent.StepStarted(0, step, 8).toLoggedEvent(250L),
        )
        val skipped = TimerEvent.StepCompleted(0, step, com.pulse.engine.CompletionReason.SKIPPED_NEXT, 1_000L)
        assertEquals("INTERVAL_SKIPPED", skipped.toLoggedEvent(1_500L)!!.kind)
        val expired = TimerEvent.StepCompleted(0, step, com.pulse.engine.CompletionReason.EXPIRED, 1_000L)
        assertEquals("INTERVAL_COMPLETED", expired.toLoggedEvent(1_500L)!!.kind)
        assertEquals("PAUSED", TimerEvent.SessionPaused.toLoggedEvent(2_000L)!!.kind)
        assertEquals("RESUMED", TimerEvent.SessionResumed.toLoggedEvent(2_100L)!!.kind)
        val added = TimerEvent.TimeAdded(3, 15_000L).toLoggedEvent(3_000L)
        assertEquals(15_000L, added!!.durationMillis)
        assertEquals("LAP", TimerEvent.LapLogged(2, 1, 30_000L, 90_000L).toLoggedEvent(4_000L)!!.kind)
        val round = TimerEvent.RoundStarted("g1", "Block", 1, 4, 2)
        assertEquals("ROUND_LOGGED", round.toLoggedEvent(5_000L)!!.kind)
        val finished = TimerEvent.SessionFinished(
            com.pulse.engine.FinishReason.TIMELINE_COMPLETED, 120_000L, 8, 0,
        )
        assertEquals("FINISHED", finished.toLoggedEvent(120_000L)!!.kind)
        assertNull(TimerEvent.Countdown(0, step, 3).toLoggedEvent(600L))
        assertNull(TimerEvent.Halfway(0, step).toLoggedEvent(600L))
    }

    @Test
    fun `session log queue round-trips and drops protocol drift`() {
        val log = SessionLog(
            sessionId = "s-9",
            workoutId = "w-1",
            planName = "Tabata",
            planJson = SyncJson.encodePlan(samplePlan()),
            startedAt = 100L,
            endedAt = 200L,
            activeMillis = 80L,
            wallMillis = 100L,
            sessionElapsedMillis = 80L,
            completedIntervals = 5,
            totalIntervals = 5,
            skippedIntervals = 0,
            status = SessionStatus.COMPLETED,
            events = listOf(LoggedSessionEvent("FINISHED", 5, "", 80L)),
        )
        val queued = SyncJson.decodeLogQueue(SyncJson.encodeLogQueue(listOf(log, log.copy(sessionId = "s-10"))))
        assertEquals(listOf("s-9", "s-10"), queued.map { it.sessionId })
        val stale = SyncJson.encodeLogQueue(listOf(log)).replace("\"protocolVersion\":1", "\"protocolVersion\":7")
        assertTrue(SyncJson.decodeLogQueue(stale).isEmpty())
        assertTrue(SyncJson.decodeLogQueue("[]").isEmpty())
    }

    @Test
    fun `favourite changes round-trip and reject blanks and other versions`() {
        val change = FavoriteChange(workoutId = "w-1", favorite = true)
        assertEquals(change, SyncJson.decodeFavorite(SyncJson.encodeFavorite(change)))
        assertNull(SyncJson.decodeFavorite(SyncJson.encodeFavorite(change.copy(workoutId = " "))))
        assertNull(SyncJson.decodeFavorite(SyncJson.encodeFavorite(change.copy(protocolVersion = PROTOCOL_VERSION + 1))))
        assertNull(SyncJson.decodeFavorite("{".toByteArray()))
    }

    private companion object {
        const val TimerEngineStatus_RUNNING = "RUNNING"
    }
}
