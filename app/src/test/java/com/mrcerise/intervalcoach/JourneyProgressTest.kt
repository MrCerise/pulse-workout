package com.mrcerise.intervalcoach

import com.mrcerise.intervalcoach.journey.*
import com.pulse.engine.TimelineExpander
import com.pulse.engine.Validation
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class JourneyProgressTest {
    private fun instant(date: String) = Instant.parse(date)
    private fun enrollment(zone: String = "Europe/Paris") = JourneyEnrollment(
        level = "BEGINNER", enrolledAt = instant("2026-10-07T10:00:00Z").toEpochMilli(),
        zoneId = zone, trainingDays = 21)
    private val targets = listOf(JourneyTarget("2026-10-05", 2))
    private fun credit(id: String, time: String) = JourneyCredit(id, JourneyCatalogue.nodes.first().id, instant(time).toEpochMilli())

    @Test fun catalogueHasStableUniquePlayableLocalizedPaths() {
        assertEquals(72, JourneyCatalogue.nodes.size)
        assertEquals(72, JourneyCatalogue.nodes.map { it.id }.distinct().size)
        JourneyLevel.entries.forEach { level ->
            val path = JourneyCatalogue.path(level)
            assertEquals(24, path.size)
            assertEquals(4, path.count { it.checkpoint })
            path.forEach { node ->
                listOf(false, true).forEach { french ->
                    val plan = node.plan(french)
                    assertTrue(Validation.validate(plan).toString(), Validation.validate(plan).isEmpty())
                    val timeline = TimelineExpander.expand(plan)
                    assertFalse(timeline.hasOpenEnded)
                    assertTrue(timeline.knownMillis > 60_000)
                    assertEquals(2 + node.exercises.size * node.rounds * 2, timeline.steps.size)
                    assertTrue(timeline.steps.all { it.name.isNotBlank() })
                }
            }
            assertTrue(path.last().plan(false).let(TimelineExpander::knownDurationMillis) >
                path.first().plan(false).let(TimelineExpander::knownDurationMillis))
        }
    }
    @Test fun skippingStoppingOrInterruptionDoesNotEarnCredit() {
        assertTrue(JourneyProgress.qualifies("COMPLETED", 10, 10, 0))
        assertFalse(JourneyProgress.qualifies("COMPLETED", 10, 10, 1))
        assertFalse(JourneyProgress.qualifies("COMPLETED", 9, 10, 0))
        assertFalse(JourneyProgress.qualifies("STOPPED_EARLY", 10, 10, 0))
        assertFalse(JourneyProgress.qualifies("INTERRUPTED", 10, 10, 0))
        assertFalse(JourneyProgress.qualifies("COMPLETED", 0, 0, 0))
    }
    @Test fun practiceWeekDoesNotEarnOrBreakStreak() {
        val credits = listOf(credit("1", "2026-10-08T10:00:00Z"), credit("2", "2026-10-09T10:00:00Z"))
        val progress = JourneyProgress.weekly(enrollment(), targets, credits, instant("2026-10-10T12:00:00Z"))
        assertTrue(progress.practiceWeek)
        assertEquals(2, progress.days)
        assertEquals(0, progress.streak)
    }
    @Test fun replaysAndSeveralSessionsOnSameDayCountOnce() {
        val credits = listOf(credit("1", "2026-10-12T10:00:00Z"), credit("2", "2026-10-12T18:00:00Z"))
        val progress = JourneyProgress.weekly(enrollment(), targets, credits, instant("2026-10-13T12:00:00Z"))
        assertEquals(1, progress.days)
        assertEquals(0, progress.streak)
    }
    @Test fun streakSurvivesUnfinishedCurrentWeekButResetsAfterMissedWeek() {
        val credits = listOf(credit("1", "2026-10-12T10:00:00Z"), credit("2", "2026-10-14T10:00:00Z"),
            credit("3", "2026-10-19T10:00:00Z"), credit("4", "2026-10-21T10:00:00Z"))
        assertEquals(1, JourneyProgress.weekly(enrollment(), targets, credits, instant("2026-10-19T08:00:00Z")).streak)
        assertEquals(2, JourneyProgress.weekly(enrollment(), targets, credits, instant("2026-10-22T08:00:00Z")).streak)
        assertEquals(2, JourneyProgress.weekly(enrollment(), targets, credits, instant("2026-10-26T08:00:00Z")).streak)
        assertEquals(0, JourneyProgress.weekly(enrollment(), targets, credits, instant("2026-11-02T08:00:00Z")).streak)
    }
    @Test fun targetHistoryDoesNotReevaluatePreviousSuccessfulWeeks() {
        val credits = listOf(credit("1", "2026-10-12T10:00:00Z"), credit("2", "2026-10-14T10:00:00Z"),
            credit("3", "2026-10-19T10:00:00Z"), credit("4", "2026-10-21T10:00:00Z"))
        val updated = targets + JourneyTarget("2026-10-19", 3)
        val progress = JourneyProgress.weekly(enrollment(), updated, credits, instant("2026-10-22T12:00:00Z"))
        assertEquals(3, progress.target)
        assertEquals(1, progress.streak)
    }
    @Test fun fixedEnrollmentTimezoneHandlesMidnightAndDst() {
        val credits = listOf(credit("1", "2026-10-25T22:30:00Z"), credit("2", "2026-10-25T23:30:00Z"))
        val progress = JourneyProgress.weekly(enrollment(), targets, credits, instant("2026-10-26T01:00:00Z"))
        assertEquals(1, progress.days)
    }
    @Test fun recommendationFillsGapsAndKeepsProgressPerLevel() {
        val beginner = JourneyCatalogue.path(JourneyLevel.BEGINNER)
        val done = setOf(beginner[0].id, beginner[2].id)
        assertEquals(beginner[1], JourneyCatalogue.recommended(JourneyLevel.BEGINNER, done))
        assertEquals(JourneyCatalogue.path(JourneyLevel.ADVANCED).first(),
            JourneyCatalogue.recommended(JourneyLevel.ADVANCED, done))
    }
}
