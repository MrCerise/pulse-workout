package com.pulse.intervalcoach

import com.pulse.engine.TimelineExpander
import com.pulse.intervalcoach.data.BackupFile
import com.pulse.intervalcoach.data.PlanCodec
import com.pulse.intervalcoach.data.SessionRecord
import com.pulse.intervalcoach.data.StarterWorkouts
import com.pulse.intervalcoach.data.UserPreferences
import com.pulse.intervalcoach.data.VoiceVerbosity
import com.pulse.intervalcoach.data.WorkoutRecord
import com.pulse.intervalcoach.parser.WorkoutTextParser
import com.pulse.intervalcoach.session.CueScript
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Export/import round trips and the coaching copy (acceptance case 11 plus the wording guarantees).
 * These tests exercise the real serialization configuration — the same [PlanCodec] the app uses.
 */
class BackupAndCueTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a workout survives an export and import round trip exactly`() {
        val original = StarterWorkouts.templates.first { it.plan.name == "Bodyweight Circuit" }.plan
        val decoded = PlanCodec.decode(PlanCodec.encode(original))

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.nodes.size, decoded.nodes.size)

        val before = TimelineExpander.expand(original)
        val after = TimelineExpander.expand(decoded)
        assertEquals(before.steps.size, after.steps.size)
        assertEquals(before.knownMillis, after.knownMillis)
        assertEquals(before.steps.map { it.name }, after.steps.map { it.name })
    }

    @Test
    fun `every bundled starter template round trips and has a real duration`() {
        assertTrue("at least 12 starter templates are required", StarterWorkouts.templates.size >= 12)
        StarterWorkouts.templates.forEach { template ->
            val decoded = PlanCodec.decode(PlanCodec.encode(template.plan))
            val timeline = TimelineExpander.expand(decoded)
            assertTrue("${template.plan.name} should have intervals", timeline.steps.isNotEmpty())
            assertTrue(
                "${template.plan.name} must be timed or explicitly open ended",
                timeline.hasOpenEnded || timeline.knownMillis > 0,
            )
            assertEquals(
                "${template.plan.name} duration must be stable across a round trip",
                TimelineExpander.expand(template.plan).knownMillis,
                timeline.knownMillis,
            )
        }
    }

    @Test
    fun `starter templates do not collide on ids or names`() {
        val ids = StarterWorkouts.templates.map { it.plan.id }
        val names = StarterWorkouts.templates.map { it.plan.name }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(names.size, names.toSet().size)
        assertTrue("template ids are derived from a fixed seed, not regenerated per run", ids.all { it.startsWith("builtin-") })
    }

    @Test
    fun `an exported backup file lists what it contains`() {
        val backup = BackupFile(
            exportedAt = 1_700_000_000_000,
            appVersion = "1.0.0",
            workouts = listOf(
                WorkoutRecord(
                    id = "w1",
                    name = "Tabata Classic",
                    type = "TABATA",
                    planJson = PlanCodec.encode(StarterWorkouts.templates.first { it.plan.name == "Tabata Classic" }.plan),
                    tags = listOf("HIIT"),
                    createdAt = 1,
                    updatedAt = 2,
                )
            ),
            sessions = listOf(
                SessionRecord(
                    id = "s1", workoutId = "w1", workoutName = "Tabata Classic", workoutSnapshotJson = "{}",
                    startedAt = 1, endedAt = 2, activeMillis = 240_000, wallMillis = 250_000, sessionElapsedMillis = 240_000,
                    completedIntervals = 16, totalIntervals = 16, skippedIntervals = 0, completionPercent = 100,
                    status = "COMPLETED", roundsLogged = 0, notes = null,
                )
            ),
        )
        val text = json.encodeToString(BackupFile.serializer(), backup)
        val parsed = json.decodeFromString(BackupFile.serializer(), text)
        assertEquals("pulse.backup", parsed.format)
        assertEquals(1, parsed.workouts.size)
        assertEquals(1, parsed.sessions.size)
        val plan = PlanCodec.decode(parsed.workouts.single().planJson)
        assertEquals(16, TimelineExpander.expand(plan).steps.size)
    }

    @Test
    fun `a foreign or corrupt file fails to parse instead of importing junk`() {
        val notABackup = """{"hello":"world"}"""
        val failure = runCatching { json.decodeFromString(BackupFile.serializer(), notABackup) }.exceptionOrNull()
        assertTrue("expected a serialization failure", failure is SerializationException || failure is IllegalArgumentException)

        assertTrue(runCatching { json.decodeFromString(BackupFile.serializer(), "this is not json at all") }.isFailure)

        val brokenPlan = """{"id":"x","name":"Broken","nodes":[{"nodeType":"nope"}]}"""
        assertTrue("an unknown node type must be rejected", runCatching { PlanCodec.decode(brokenPlan) }.isFailure)
    }

    @Test
    fun `coaching copy adapts to verbosity`() {
        val plan = StarterWorkouts.templates.first { it.plan.name == "Tabata Classic" }.plan
        val timeline = TimelineExpander.expand(plan)
        val workIndex = timeline.steps.indexOfFirst {
            it.kind == com.pulse.engine.PhaseKind.WORK && it.roundInGroup == 2
        }
        assertTrue("the tabata template has a second round", workIndex > 0)
        val work = timeline.steps[workIndex]
        val next = timeline.steps[workIndex + 1]

        val standard = CueScript.intervalStart(work, next, UserPreferences(verbosity = VoiceVerbosity.STANDARD), plan.type)
        assertTrue("standard copy names the round", standard!!.contains("Round 2"))
        assertTrue("standard copy states the duration", standard.contains("20 seconds"))

        val minimal = CueScript.intervalStart(work, next, UserPreferences(verbosity = VoiceVerbosity.MINIMAL), plan.type)
        assertEquals("Go.", minimal)

        val chatty = CueScript.intervalStart(
            work, next,
            UserPreferences(verbosity = VoiceVerbosity.CHATTY, announceNext = true, spokenCountdown = true),
            plan.type,
        )
        assertTrue("chatty copy previews the next interval", chatty!!.contains("Next:"))
    }

    @Test
    fun `countdown speech respects the spoken countdown setting`() {
        assertNull(CueScript.countdown(3, UserPreferences(spokenCountdown = false)))
        assertNull(CueScript.countdown(3, UserPreferences(verbosity = VoiceVerbosity.MINIMAL)))
        assertEquals("3", CueScript.countdown(3, UserPreferences()))
    }

    @Test
    fun `finish copy distinguishes a completed workout from an early stop`() {
        assertEquals(
            "Workout complete. 16 intervals. Well done.",
            CueScript.finished(stoppedEarly = false, completed = 16, total = 16),
        )
        assertEquals(
            "Session ended. 5 of 16 intervals completed.",
            CueScript.finished(stoppedEarly = true, completed = 5, total = 16),
        )
    }

    @Test
    fun `a parsed plan can always be saved and re-read`() {
        val plan = WorkoutTextParser.parse("8 rounds, 30 seconds work, 15 seconds rest").plan!!
        val decoded = PlanCodec.decode(PlanCodec.encode(plan))
        assertEquals(TimelineExpander.expand(plan).steps.size, TimelineExpander.expand(decoded).steps.size)
        assertEquals(TimelineExpander.expand(plan).knownMillis, TimelineExpander.expand(decoded).knownMillis)
    }
}
