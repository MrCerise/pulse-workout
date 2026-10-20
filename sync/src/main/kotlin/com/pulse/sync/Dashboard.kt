package com.pulse.sync

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The phone's activity dashboard, mirrored to the watch: the weekly goal, how much of it is done,
 * the streak, and what is planned for today.
 *
 * The phone sends the *ingredients*, not the totals. "This week" is the rolling seven days ending
 * today and the streak counts back from today, so totals computed on the phone go stale at
 * midnight — and the watch may be away from the phone for days. With per-day minutes and the set
 * of active days, the watch recomputes both for whatever day it is, adding the sessions it ran
 * itself and has not delivered yet. [WatchDashboards] holds that arithmetic once, for both sides.
 */
@Serializable
data class WatchDashboard(
    val protocolVersion: Int = PROTOCOL_VERSION,
    /** The local day this was computed for, as an epoch day. */
    val epochDay: Long,
    val weeklyGoalMinutes: Int = DEFAULT_WEEKLY_GOAL_MINUTES,
    /** Active milliseconds per local day for the [WEEK_DAYS] days ending at [epochDay], oldest first. */
    val dailyActiveMillis: List<Long> = emptyList(),
    /** Local days with at least one finished session, newest first, capped at [ACTIVE_DAYS_LIMIT]. */
    val activeDays: List<Long> = emptyList(),
    /** What is planned for [epochDay], in time order. */
    val today: List<PlannedWorkout> = emptyList(),
)

/** One planned workout on a given day. */
@Serializable
data class PlannedWorkout(
    val workoutId: String,
    val name: String,
    /** When it is planned on that day (wall-clock millis). */
    val atMillis: Long,
    /** A session of this workout already finished that day. */
    val done: Boolean = false,
)

/** A finished session, reduced to what the dashboard needs. Built from either app's history. */
data class FinishedSession(val workoutId: String, val startedAt: Long, val activeMillis: Long)

/** A planner entry as the phone stores it: a first date and time, and how it repeats. */
data class PlannerEntry(val workoutId: String, val scheduledAt: Long, val repeatRule: String, val enabled: Boolean = true)

/** The WHO recommendation the phone uses as its default goal. */
const val DEFAULT_WEEKLY_GOAL_MINUTES = 150

/** Rolling window of "this week", in days, including today — the phone's definition. */
const val WEEK_DAYS = 7

/** Enough active days to count any streak a user will plausibly reach, within a small data item. */
const val ACTIVE_DAYS_LIMIT = 400

object WatchDashboards {

    /** Builds the payload on the phone. Pure: the caller passes the zone and "today". */
    fun build(
        today: LocalDate,
        zone: ZoneId,
        weeklyGoalMinutes: Int,
        sessions: List<FinishedSession>,
        planner: List<PlannerEntry>,
        workoutNames: Map<String, String>,
    ): WatchDashboard {
        val todayEpoch = today.toEpochDay()
        val byDay = sessions.groupBy { it.epochDay(zone) }
        val daily = (WEEK_DAYS - 1 downTo 0).map { back ->
            byDay[todayEpoch - back].orEmpty().sumOf { it.activeMillis }
        }
        val doneToday = byDay[todayEpoch].orEmpty().map { it.workoutId }.toSet()
        return WatchDashboard(
            epochDay = todayEpoch,
            weeklyGoalMinutes = weeklyGoalMinutes,
            dailyActiveMillis = daily,
            activeDays = byDay.keys.sortedDescending().take(ACTIVE_DAYS_LIMIT),
            today = plannedOn(today, zone, planner)
                .mapNotNull { (entry, at) ->
                    // A plan for a workout that no longer exists has nothing to start.
                    val name = workoutNames[entry.workoutId] ?: return@mapNotNull null
                    PlannedWorkout(entry.workoutId, name, at, done = entry.workoutId in doneToday)
                },
        )
    }

    /** Active milliseconds in the rolling week ending [today]: the phone's days plus [local] sessions. */
    fun weekActiveMillis(dashboard: WatchDashboard?, today: LocalDate, zone: ZoneId, local: List<FinishedSession>): Long {
        val todayEpoch = today.toEpochDay()
        val window = (todayEpoch - (WEEK_DAYS - 1))..todayEpoch
        val fromPhone = dashboard?.let { d ->
            d.dailyActiveMillis.withIndex().sumOf { (index, millis) ->
                val day = d.epochDay - (d.dailyActiveMillis.size - 1 - index)
                if (day in window) millis else 0L
            }
        } ?: 0L
        return fromPhone + local.filter { it.epochDay(zone) in window }.sumOf { it.activeMillis }
    }

    /**
     * Consecutive days ending today — or yesterday, so a streak survives until the day is over —
     * with at least one finished session. The same rule as the phone's `currentStreak`.
     */
    fun streak(dashboard: WatchDashboard?, today: LocalDate, zone: ZoneId, local: List<FinishedSession>): Int {
        val days = buildSet {
            dashboard?.activeDays?.let(::addAll)
            local.forEach { add(it.epochDay(zone)) }
        }
        var cursor = today.toEpochDay()
        if (cursor !in days) cursor -= 1
        var count = 0
        while (cursor in days) {
            count++
            cursor -= 1
        }
        return count
    }

    /**
     * Today's plan as the watch shows it: empty when the dashboard was computed for another day,
     * and with workouts the watch itself finished today ticked off.
     */
    fun plannedToday(dashboard: WatchDashboard?, today: LocalDate, zone: ZoneId, local: List<FinishedSession>): List<PlannedWorkout> {
        if (dashboard == null || dashboard.epochDay != today.toEpochDay()) return emptyList()
        val doneHere = local.filter { it.epochDay(zone) == today.toEpochDay() }.map { it.workoutId }.toSet()
        return dashboard.today.map { if (it.workoutId in doneHere) it.copy(done = true) else it }
    }

    /** Planner entries that fall on [day], each with its time on that day, in time order. */
    fun plannedOn(day: LocalDate, zone: ZoneId, planner: List<PlannerEntry>): List<Pair<PlannerEntry, Long>> =
        planner.filter { it.enabled }.mapNotNull { entry ->
            val first = Instant.ofEpochMilli(entry.scheduledAt).atZone(zone)
            val occurs = when (entry.repeatRule) {
                "DAILY" -> !day.isBefore(first.toLocalDate())
                "WEEKLY" -> !day.isBefore(first.toLocalDate()) && day.dayOfWeek == first.dayOfWeek
                else -> day == first.toLocalDate()
            }
            if (occurs) entry to day.atTime(first.toLocalTime()).atZone(zone).toInstant().toEpochMilli() else null
        }.sortedBy { it.second }

    private fun FinishedSession.epochDay(zone: ZoneId): Long =
        Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate().toEpochDay()
}
