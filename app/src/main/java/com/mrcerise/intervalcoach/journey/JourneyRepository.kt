package com.mrcerise.intervalcoach.journey

import androidx.room.withTransaction
import com.mrcerise.intervalcoach.data.db.PulseDatabase
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

data class JourneyState(
    val enrollment: JourneyEnrollment?,
    val targets: List<JourneyTarget>,
    val credits: List<JourneyCredit>,
    val readiness: List<JourneyReadiness>,
) {
    val completed: Set<String> get() = credits.map { it.nodeId }.toSet()
    val level: JourneyLevel get() = JourneyLevel.entries.firstOrNull { it.name == enrollment?.level } ?: JourneyLevel.BEGINNER
    val next get() = JourneyCatalogue.recommended(level, completed)
    fun completedCount(level: JourneyLevel = this.level) =
        completed.count { JourneyCatalogue.belongsToLevel(it, level) }
    val foundationCompleted get() = JourneyCatalogue.path(level).count { it.id in completed }
    val levelComplete get() = JourneyCatalogue.path(level).all { it.id in completed }
    fun weekly(now: Instant = Instant.now()) = enrollment?.let { JourneyProgress.weekly(it, targets, credits, now) }
}

class JourneyRepository(private val db: PulseDatabase) {
    private val dao = db.journeyDao()
    val state = combine(dao.observeEnrollment(), dao.observeTargets(), dao.observeCredits(), dao.observeReadiness(), ::JourneyState)

    suspend fun configure(level: JourneyLevel, target: Int, days: Int, reminderMinute: Int?, now: Instant = Instant.now()) {
        require(target in 2..5)
        require(days in 1..127 && Integer.bitCount(days) == target)
        require(reminderMinute == null || reminderMinute in 0..1439)
        db.withTransaction {
            val previous = dao.enrollment()
            val zone = previous?.zoneId ?: ZoneId.systemDefault().id
            val week = JourneyProgress.week(now.atZone(ZoneId.of(zone)).toLocalDate())
            val oldTargets = dao.targets()
            val activeTarget = oldTargets.lastOrNull { it.week <= week.toString() }?.days
            dao.saveEnrollment(JourneyEnrollment(
                level = level.name, enrolledAt = previous?.enrolledAt ?: now.toEpochMilli(),
                zoneId = zone, trainingDays = days, reminderMinute = reminderMinute,
                lastReminderDate = previous?.lastReminderDate,
            ))
            val effectiveWeek = if (previous == null) week else week.plusWeeks(1)
            // Re-saving preferences also replaces a previously scheduled target change.
            if (previous == null || target != activeTarget || oldTargets.any { it.week == effectiveWeek.toString() }) {
                dao.saveTarget(JourneyTarget(effectiveWeek.toString(), target))
            }
        }
    }
    suspend fun selectLevel(level: JourneyLevel) = db.withTransaction {
        dao.enrollment()?.let { dao.saveEnrollment(it.copy(level = level.name)) }
    }
    suspend fun readiness(level: JourneyLevel, ready: Boolean, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val completed = dao.credits().map { it.nodeId }.toSet()
        require(JourneyCatalogue.path(level).all { it.id in completed })
        dao.saveReadiness(JourneyReadiness(level.name, ready, now))
        if (ready) level.next()?.let { next ->
            dao.enrollment()?.let { dao.saveEnrollment(it.copy(level = next.name)) }
        }
    }
    suspend fun reminderDue(now: Instant = Instant.now()): Boolean {
        val enrollment = dao.enrollment() ?: return false
        val minute = enrollment.reminderMinute ?: return false
        val local = now.atZone(ZoneId.of(enrollment.zoneId))
        if (enrollment.trainingDays and (1 shl (local.dayOfWeek.value - 1)) == 0 ||
            local.hour * 60 + local.minute < minute || enrollment.lastReminderDate == local.toLocalDate().toString()) return false
        return dao.credits().none { Instant.ofEpochMilli(it.endedAt).atZone(local.zone).toLocalDate() == local.toLocalDate() }
    }
    /** Serialize posting with completion so a late worker cannot restore a canceled reminder. */
    suspend fun notifyIfDue(now: Instant = Instant.now(), post: () -> Unit) = db.withTransaction {
        if (reminderDue(now)) {
            val enrollment = dao.enrollment() ?: return@withTransaction
            post()
            dao.markReminded(now.atZone(ZoneId.of(enrollment.zoneId)).toLocalDate().toString())
        }
    }
}
