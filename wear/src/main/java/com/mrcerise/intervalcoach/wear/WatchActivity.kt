package com.mrcerise.intervalcoach.wear

import com.mrcerise.intervalcoach.wear.history.WatchSessionRecord
import com.pulse.sync.DEFAULT_WEEKLY_GOAL_MINUTES
import com.pulse.sync.FinishedSession
import com.pulse.sync.PlannedWorkout
import com.pulse.sync.WatchDashboards
import java.time.LocalDate
import java.time.ZoneId

/** The week at a glance, as the home screen and the tile show it. */
data class WatchActivity(
    val goalMinutes: Int,
    val weekMillis: Long,
    val streakDays: Int,
    val today: List<PlannedWorkout>,
) {
    val weekMinutes: Int get() = (weekMillis / 60_000L).toInt()
    val goalProgress: Float get() = if (goalMinutes <= 0) 0f else (weekMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)
}

/**
 * Today's activity on this watch, from the phone's dashboard when it has one.
 *
 * A paired watch adds only the sessions still waiting in the queue — everything delivered is
 * already in the phone's numbers. A watch that has never had a dashboard (solo, or the phone is on
 * an older version) counts its own history against the WHO default goal, so the card is useful
 * from the first workout instead of empty until a phone appears.
 */
fun watchActivity(
    prefs: WatchPreferences,
    history: List<WatchSessionRecord>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): WatchActivity {
    val dashboard = prefs.dashboard
    val local = if (dashboard != null) {
        prefs.pendingLogs.map { FinishedSession(it.workoutId, it.startedAt, it.activeMillis) }
    } else {
        history.map { FinishedSession(it.workoutId, it.startedAt, it.activeMillis) }
    }
    return WatchActivity(
        goalMinutes = dashboard?.weeklyGoalMinutes ?: DEFAULT_WEEKLY_GOAL_MINUTES,
        weekMillis = WatchDashboards.weekActiveMillis(dashboard, today, zone, local),
        streakDays = WatchDashboards.streak(dashboard, today, zone, local),
        // "Done" also counts what this watch finished today, delivered or not.
        today = WatchDashboards.plannedToday(
            dashboard,
            today,
            zone,
            history.map { FinishedSession(it.workoutId, it.startedAt, it.activeMillis) },
        ),
    )
}
