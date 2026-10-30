package com.mrcerise.intervalcoach.journey

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class JourneyContext(val nodeId: String, val version: Int = JourneyCatalogue.VERSION)
data class JourneyWeeklyProgress(val days: Int, val target: Int, val streak: Int, val practiceWeek: Boolean)

object JourneyProgress {
    fun week(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun qualifies(status: String, completed: Int, total: Int, skipped: Int): Boolean =
        status == "COMPLETED" && total > 0 && completed >= total && skipped == 0

    fun weekly(enrollment: JourneyEnrollment, targets: List<JourneyTarget>, credits: List<JourneyCredit>,
               now: Instant = Instant.now()): JourneyWeeklyProgress {
        val zone = ZoneId.of(enrollment.zoneId)
        val currentWeek = week(now.atZone(zone).toLocalDate())
        val firstWeek = week(Instant.ofEpochMilli(enrollment.enrolledAt).atZone(zone).toLocalDate()).plusWeeks(1)
        val byWeek = credits.filter { it.endedAt <= now.toEpochMilli() }.map {
            Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate()
        }.distinct().groupingBy(::week).eachCount()
        val ordered = targets.sortedBy { it.week }
        fun target(w: LocalDate) = ordered.lastOrNull { it.week <= w.toString() }?.days ?: 3
        fun met(w: LocalDate) = (byWeek[w] ?: 0) >= target(w)
        var cursor = if (currentWeek >= firstWeek && met(currentWeek)) currentWeek else currentWeek.minusWeeks(1)
        var streak = 0
        while (cursor >= firstWeek && met(cursor)) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return JourneyWeeklyProgress(byWeek[currentWeek] ?: 0, target(currentWeek), streak, currentWeek < firstWeek)
    }
}
