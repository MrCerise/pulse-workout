package com.pulse.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DashboardTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 20) // a Tuesday

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(day: LocalDate, minutes: Long, workoutId: String = "w") =
        FinishedSession(workoutId, at(day, 9), minutes * 60_000L)

    @Test
    fun `the week is the rolling seven days ending today, as on the phone`() {
        val sessions = listOf(
            session(today, 20),
            session(today.minusDays(6), 30),
            session(today.minusDays(7), 999), // eight days ago: outside the week
        )
        val dashboard = WatchDashboards.build(today, zone, 150, sessions, emptyList(), emptyMap())
        assertEquals(7, dashboard.dailyActiveMillis.size)
        assertEquals(50 * 60_000L, WatchDashboards.weekActiveMillis(dashboard, today, zone, emptyList()))
    }

    @Test
    fun `a dashboard from an earlier day still counts only the days inside today's week`() {
        val dashboard = WatchDashboards.build(today, zone, 150, listOf(session(today.minusDays(6), 30), session(today, 10)), emptyList(), emptyMap())
        // Two days later, the session from six days before the dashboard has left the window.
        val later = today.plusDays(2)
        assertEquals(10 * 60_000L, WatchDashboards.weekActiveMillis(dashboard, later, zone, emptyList()))
        // A session the watch ran itself, not delivered yet, adds on top.
        assertEquals(25 * 60_000L, WatchDashboards.weekActiveMillis(dashboard, later, zone, listOf(session(later, 15))))
    }

    @Test
    fun `the streak counts back from today or yesterday and merges the watch's own sessions`() {
        val dashboard = WatchDashboards.build(
            today, zone, 150,
            listOf(session(today.minusDays(1), 10), session(today.minusDays(2), 10), session(today.minusDays(4), 10)),
            emptyList(), emptyMap(),
        )
        // Nothing yet today: the streak from yesterday still stands.
        assertEquals(2, WatchDashboards.streak(dashboard, today, zone, emptyList()))
        // A workout on the watch today extends it.
        assertEquals(3, WatchDashboards.streak(dashboard, today, zone, listOf(session(today, 5))))
        // Two empty days break it.
        assertEquals(0, WatchDashboards.streak(dashboard, today.plusDays(2), zone, emptyList()))
        // A solo watch (no dashboard) counts from its own history alone.
        assertEquals(1, WatchDashboards.streak(null, today, zone, listOf(session(today, 5))))
    }

    @Test
    fun `today's plan expands repeats, drops deleted workouts and ticks off what was done`() {
        val planner = listOf(
            PlannerEntry("a", at(today, 18), "NONE"),
            PlannerEntry("b", at(today.minusDays(10), 7, 30), "DAILY"),
            PlannerEntry("c", at(today.minusDays(7), 12), "WEEKLY"), // also a Tuesday
            PlannerEntry("d", at(today.minusDays(1), 12), "WEEKLY"), // Mondays: not today
            PlannerEntry("e", at(today.plusDays(1), 12), "DAILY"), // starts tomorrow
            PlannerEntry("gone", at(today, 8), "NONE"),
            PlannerEntry("off", at(today, 9), "NONE", enabled = false),
        )
        val names = mapOf("a" to "A", "b" to "B", "c" to "C", "d" to "D", "e" to "E", "off" to "Off")
        val dashboard = WatchDashboards.build(today, zone, 150, listOf(session(today, 10, workoutId = "b")), planner, names)
        assertEquals(listOf("b", "c", "a"), dashboard.today.map { it.workoutId })
        assertEquals(at(today, 7, 30), dashboard.today.first().atMillis)
        assertEquals(listOf(true, false, false), dashboard.today.map { it.done })

        // The watch ticks off a workout it ran itself, before the phone has heard of it.
        val onWatch = WatchDashboards.plannedToday(dashboard, today, zone, listOf(session(today, 5, workoutId = "a")))
        assertEquals(listOf(true, false, true), onWatch.map { it.done })
        // Yesterday's plan is never shown as today's.
        assertTrue(WatchDashboards.plannedToday(dashboard, today.plusDays(1), zone, emptyList()).isEmpty())
    }

    @Test
    fun `the dashboard survives the wire and rejects another protocol version`() {
        val dashboard = WatchDashboards.build(
            today, zone, 200, listOf(session(today, 10)),
            listOf(PlannerEntry("a", at(today, 18), "NONE")), mapOf("a" to "Evening run"),
        )
        assertEquals(dashboard, SyncJson.decodeDashboard(SyncJson.encodeDashboard(dashboard)))
        assertNull(SyncJson.decodeDashboard(SyncJson.encodeDashboard(dashboard.copy(protocolVersion = PROTOCOL_VERSION + 1))))
        assertNull(SyncJson.decodeDashboard("not json"))
    }
}
