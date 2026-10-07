package com.pulse.intervalcoach.data

import androidx.room.withTransaction
import com.pulse.engine.EngineLimits
import com.pulse.engine.IntervalNode
import com.pulse.engine.Node
import com.pulse.engine.RepeatGroup
import com.pulse.engine.TimelineExpander
import com.pulse.engine.Validation
import com.pulse.engine.WorkoutPlan
import com.pulse.intervalcoach.data.db.AudioAssetDao
import com.pulse.intervalcoach.data.db.AudioAssetEntity
import com.pulse.intervalcoach.data.db.ExerciseLabelDao
import com.pulse.intervalcoach.data.db.ExerciseLabelEntity
import com.pulse.intervalcoach.data.db.FolderDao
import com.pulse.intervalcoach.data.db.FolderEntity
import com.pulse.intervalcoach.data.db.ProgramDao
import com.pulse.intervalcoach.data.db.ProgramEntity
import com.pulse.intervalcoach.data.db.PulseDatabase
import com.pulse.intervalcoach.data.db.ReminderDao
import com.pulse.intervalcoach.data.db.ReminderEntity
import com.pulse.intervalcoach.data.db.SessionDao
import com.pulse.intervalcoach.data.db.SessionEntity
import com.pulse.intervalcoach.data.db.SessionEventDao
import com.pulse.intervalcoach.data.db.SessionEventEntity
import com.pulse.intervalcoach.data.db.TagDao
import com.pulse.intervalcoach.data.db.TagEntity
import com.pulse.intervalcoach.data.db.VoiceProfileDao
import com.pulse.intervalcoach.data.db.VoiceProfileEntity
import com.pulse.intervalcoach.data.db.WorkoutDao
import com.pulse.intervalcoach.data.db.WorkoutEntity
import com.pulse.intervalcoach.data.db.WorkoutTagCrossRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Single JSON configuration for workouts, backups and import/export. */
object PlanCodec {
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "nodeType"
    }

    private val compact: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "nodeType"
    }

    fun encode(plan: WorkoutPlan): String = compact.encodeToString(WorkoutPlan.serializer(), plan)
    fun decode(text: String): WorkoutPlan = json.decodeFromString(WorkoutPlan.serializer(), text)
}

// ---------------------------------------------------------------------------------------------
// Workouts
// ---------------------------------------------------------------------------------------------

data class WorkoutSummary(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val intervalCount: Int,
    val knownDurationMillis: Long,
    val hasOpenEnded: Boolean,
    val isFavorite: Boolean,
    val builtIn: Boolean,
    val folderId: String?,
    val tags: List<String>,
    val equipment: String?,
    val lastUsedAt: Long?,
    val updatedAt: Long,
)

class WorkoutRepository(
    private val db: PulseDatabase,
    private val workoutDao: WorkoutDao = db.workoutDao(),
    private val folderDao: FolderDao = db.folderDao(),
    private val tagDao: TagDao = db.tagDao(),
    private val labelDao: ExerciseLabelDao = db.exerciseLabelDao(),
) {
    val workouts: Flow<List<WorkoutEntity>> = workoutDao.observeAll()
    val folders: Flow<List<FolderEntity>> = folderDao.observeAll()
    val tags: Flow<List<TagEntity>> = tagDao.observeAll()
    val labels: Flow<List<ExerciseLabelEntity>> = labelDao.observeAll()

    val summaries: Flow<List<WorkoutSummary>> =
        combine(workoutDao.observeAll(), tagDao.observeAll(), tagDao.observeLinks()) { list, allTags, links ->
            val tagsById = allTags.associateBy { it.id }
            val linksByWorkout = links.groupBy { it.workoutId }
            list.map { entity ->
                val plan = runCatching { PlanCodec.decode(entity.planJson) }.getOrNull()
                val expanded = plan?.let { runCatching { TimelineExpander.expand(it) }.getOrNull() }
                WorkoutSummary(
                    id = entity.id,
                    name = entity.name,
                    description = entity.description,
                    type = entity.type,
                    intervalCount = expanded?.steps?.size ?: 0,
                    knownDurationMillis = expanded?.knownMillis ?: 0L,
                    hasOpenEnded = expanded?.hasOpenEnded ?: false,
                    isFavorite = entity.isFavorite,
                    builtIn = entity.builtIn,
                    folderId = entity.folderId,
                    tags = (linksByWorkout[entity.id].orEmpty()).mapNotNull { link -> tagsById[link.tagId]?.name },
                    equipment = entity.equipment,
                    lastUsedAt = entity.lastUsedAt,
                    updatedAt = entity.updatedAt,
                )
            }
        }

    fun observeFavorites(limit: Int = 12) = workoutDao.observeFavorites(limit)
    fun observeRecent(limit: Int = 12) = workoutDao.observeRecent(limit)

    fun observeEntity(id: String): Flow<WorkoutEntity?> = workoutDao.observeById(id)

    suspend fun entity(id: String): WorkoutEntity? = workoutDao.byId(id)

    suspend fun plan(id: String): WorkoutPlan? = workoutDao.byId(id)?.let { runCatching { PlanCodec.decode(it.planJson) }.getOrNull() }

    suspend fun save(plan: WorkoutPlan, folderId: String? = null, tags: List<String> = emptyList()): String {
        val problems = Validation.validate(plan, EngineLimits())
        check(problems.isEmpty()) { problems.joinToString(" ") }
        val now = System.currentTimeMillis()
        val existing = workoutDao.byId(plan.id)
        val entity = WorkoutEntity(
            id = plan.id,
            name = plan.name,
            description = plan.description,
            type = plan.type.name,
            planJson = PlanCodec.encode(plan),
            folderId = folderId ?: existing?.folderId,
            iconKey = plan.iconKey,
            colorArgb = plan.colorArgb,
            equipment = plan.equipment,
            voiceProfileId = plan.voiceProfileId,
            isFavorite = plan.isFavorite,
            builtIn = existing?.builtIn ?: false,
            createdAt = existing?.createdAt ?: plan.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
            revision = (existing?.revision ?: 0) + 1,
            lastUsedAt = existing?.lastUsedAt,
            useCount = existing?.useCount ?: 0,
        )
        db.withTransaction {
            workoutDao.upsert(entity)
            tagDao.clearLinks(plan.id)
            tags.filter { it.isNotBlank() }.forEach { tagName ->
                val tagId = tagIdFor(tagName)
                tagDao.upsert(TagEntity(tagId, tagName.trim()))
                tagDao.link(WorkoutTagCrossRef(plan.id, tagId))
            }
        }
        return plan.id
    }

    suspend fun duplicate(source: WorkoutPlan, newName: String? = null): WorkoutPlan {
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = (newName ?: "${source.name} copy"),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isFavorite = false,
            revision = 1,
        )
        save(copy, folderId = workoutDao.byId(source.id)?.folderId)
        return copy
    }

    /** Clones with all timed intervals scaled, e.g. 1.5× for a longer session. */
    suspend fun cloneScaled(source: WorkoutPlan, factor: Double, newName: String): WorkoutPlan {
        require(factor in 0.25..4.0) { "Scale factor must be between 0.25 and 4" }
        fun scaleNode(node: Node): Node = when (node) {
            is IntervalNode -> node.copy(
                interval = node.interval.copy(durationMillis = (node.interval.durationMillis * factor).toLong()),
            )
            is RepeatGroup -> node.copy(
                children = node.children.map(::scaleNode),
                restAfterGroupMillis = (node.restAfterGroupMillis * factor).toLong(),
            )
        }
        val clone = source.copy(
            id = UUID.randomUUID().toString(),
            name = newName,
            nodes = source.nodes.map(::scaleNode),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isFavorite = false,
            revision = 1,
        )
        save(clone)
        return clone
    }

    suspend fun delete(id: String) = workoutDao.delete(id)

    suspend fun setFavorite(id: String, favorite: Boolean) = workoutDao.setFavorite(id, favorite, System.currentTimeMillis())

    suspend fun markUsed(id: String) = workoutDao.markUsed(id, System.currentTimeMillis())

    suspend fun createFolder(name: String): String {
        val id = UUID.randomUUID().toString()
        folderDao.upsert(FolderEntity(id = id, name = name, createdAt = System.currentTimeMillis()))
        return id
    }

    suspend fun deleteFolder(id: String) = folderDao.delete(id)

    /** Remembers an exercise label so it can be reused across workouts. */
    suspend fun rememberLabel(label: String, phase: String, durationMillis: Long, notes: String?, speech: String?) {
        if (label.isBlank()) return
        val id = "label-" + UUID.nameUUIDFromBytes(label.trim().lowercase().toByteArray())
        labelDao.upsert(
            ExerciseLabelEntity(
                id = id,
                label = label.trim(),
                phase = phase,
                defaultDurationMillis = durationMillis,
                notes = notes,
                speechText = speech,
                useCount = 0,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteLabel(id: String) = labelDao.delete(id)

    /**
     * Seeds bundled starter workouts the first time the app runs. Never touches user data, and
     * never creates fake sessions.
     */
    suspend fun seedStarterWorkoutsIfNeeded() {
        if (workoutDao.count() > 0) return
        val now = System.currentTimeMillis()
        val entities = StarterWorkouts.templates.map { template ->
            val plan = template.plan.copy(createdAt = now, updatedAt = now)
            WorkoutEntity(
                id = plan.id,
                name = plan.name,
                description = plan.description,
                type = plan.type.name,
                planJson = PlanCodec.encode(plan),
                iconKey = template.iconKey,
                equipment = plan.equipment,
                isFavorite = false,
                builtIn = true,
                createdAt = now,
                updatedAt = now,
                revision = 1,
            )
        }
        workoutDao.upsertAll(entities)
    }

    private suspend fun tagIdFor(name: String): String = "tag-" + UUID.nameUUIDFromBytes(name.trim().lowercase().toByteArray())
}

// ---------------------------------------------------------------------------------------------
// Sessions
// ---------------------------------------------------------------------------------------------

data class SessionStartInfo(
    val sessionId: String,
    val plan: WorkoutPlan,
)

class SessionRepository(
    private val db: PulseDatabase,
    private val sessionDao: SessionDao = db.sessionDao(),
    private val eventDao: SessionEventDao = db.sessionEventDao(),
) {
    val history: Flow<List<SessionEntity>> = sessionDao.observeAll()

    fun observeRecent(limit: Int = 5) = sessionDao.observeRecent(limit)
    fun observeSession(id: String) = sessionDao.observeById(id)
    fun observeEvents(id: String) = eventDao.observeForSession(id)
    suspend fun session(id: String) = sessionDao.byId(id)
    suspend fun events(id: String) = eventDao.forSession(id)

    /**
     * Creates the session row up front with status RUNNING. If the process dies mid-workout the
     * row stays RUNNING and [recoverInterruptedSessions] turns it into INTERRUPTED on next launch —
     * which is how PULSE avoids claiming an interrupted workout completed.
     */
    suspend fun start(plan: WorkoutPlan, startedAtMillis: Long = System.currentTimeMillis()): SessionStartInfo {
        val sessionId = UUID.randomUUID().toString()
        val snapshot = plan.copy()
        sessionDao.upsert(
            SessionEntity(
                id = sessionId,
                workoutId = plan.id,
                workoutName = plan.name,
                workoutSnapshotJson = PlanCodec.encode(snapshot),
                startedAt = startedAtMillis,
                endedAt = startedAtMillis,
                activeMillis = 0,
                wallMillis = 0,
                sessionElapsedMillis = 0,
                completedIntervals = 0,
                totalIntervals = 0,
                skippedIntervals = 0,
                completionPercent = 0,
                status = STATUS_RUNNING,
                lastHeartbeatAtMillis = startedAtMillis,
            )
        )
        return SessionStartInfo(sessionId, snapshot)
    }

    suspend fun recordEvent(sessionId: String, event: SessionEventEntity) = eventDao.insert(event)

    suspend fun recordEvents(sessionId: String, events: List<SessionEventEntity>) {
        if (events.isNotEmpty()) eventDao.insertAll(events)
    }

    suspend fun heartbeat(sessionId: String, at: Long) = sessionDao.heartbeat(sessionId, at)

    suspend fun finish(
        sessionId: String,
        endedAt: Long,
        activeMillis: Long,
        wallMillis: Long,
        sessionElapsedMillis: Long,
        completed: Int,
        total: Int,
        skipped: Int,
        status: String,
        roundsLogged: Int,
        weightUnit: String?,
    ) {
        val existing = sessionDao.byId(sessionId) ?: return
        sessionDao.upsert(
            existing.copy(
                endedAt = endedAt,
                activeMillis = activeMillis,
                wallMillis = wallMillis,
                sessionElapsedMillis = sessionElapsedMillis,
                completedIntervals = completed,
                totalIntervals = total,
                skippedIntervals = skipped,
                completionPercent = if (total <= 0) 0 else ((completed * 100) / total).coerceIn(0, 100),
                status = status,
                roundsLogged = roundsLogged,
                weightUnitAtRun = weightUnit,
                lastHeartbeatAtMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateNotes(id: String, notes: String?) = sessionDao.updateNotes(id, notes)

    suspend fun delete(id: String) = sessionDao.delete(id)

    suspend fun deleteAll() = sessionDao.deleteAll()

    /**
     * Marks sessions that were still RUNNING when the process died. Returns how many were fixed so
     * the UI can tell the user plainly that a workout was interrupted rather than completed.
     */
    suspend fun recoverInterruptedSessions(): Int {
        val stale = sessionDao.runningSessions()
        stale.forEach { session ->
            sessionDao.upsert(
                session.copy(
                    status = STATUS_INTERRUPTED,
                    endedAt = session.lastHeartbeatAtMillis ?: session.startedAt,
                    activeMillis = session.sessionElapsedMillis,
                    wallMillis = (session.lastHeartbeatAtMillis ?: session.startedAt) - session.startedAt,
                )
            )
        }
        return stale.size
    }

    suspend fun weeklyActivity(days: Int = 14, zone: ZoneId = ZoneId.systemDefault()): List<DailyActivity> {
        val today = LocalDate.now(zone)
        val from = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = sessionDao.between(from, to)
        val byDay = sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        return (0 until days).map { offset ->
            val date = today.minusDays((days - 1 - offset).toLong())
            val list = byDay[date].orEmpty()
            DailyActivity(
                date = date,
                sessionCount = list.size,
                activeMillis = list.sumOf { it.activeMillis },
            )
        }
    }

    /** Consecutive days ending today (or yesterday) with at least one finished session. */
    suspend fun currentStreak(zone: ZoneId = ZoneId.systemDefault()): Int {
        val all = sessionDao.between(0, Long.MAX_VALUE)
        if (all.isEmpty()) return 0
        val days = all.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
        var streak = 0
        var cursor = LocalDate.now(zone)
        if (!days.contains(cursor)) {
            cursor = cursor.minusDays(1)
            if (!days.contains(cursor)) return 0
        }
        while (days.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    suspend fun totalActiveMillis(): Long = sessionDao.totalActiveMillis()
    suspend fun totalSessions(): Int = sessionDao.count()
}

data class DailyActivity(val date: LocalDate, val sessionCount: Int, val activeMillis: Long)

const val STATUS_RUNNING = "RUNNING"
const val STATUS_COMPLETED = "COMPLETED"
const val STATUS_STOPPED = "STOPPED_EARLY"
const val STATUS_INTERRUPTED = "INTERRUPTED"

// ---------------------------------------------------------------------------------------------
// Voice profiles, audio assets, reminders, programs
// ---------------------------------------------------------------------------------------------

class VoiceProfileRepository(private val db: PulseDatabase) {
    val profiles: Flow<List<VoiceProfileEntity>> = db.voiceProfileDao().observeAll()

    /** Creates the built-in profile once; user profiles are never overwritten. */
    suspend fun ensureDefault(): VoiceProfileEntity {
        val dao = db.voiceProfileDao()
        dao.byId(DEFAULT_VOICE_PROFILE_ID)?.let { return it }
        val default = VoiceProfileEntity(
            id = DEFAULT_VOICE_PROFILE_ID,
            name = "Coach",
            rate = 1.0f,
            pitch = 1.0f,
            verbosity = VoiceVerbosity.STANDARD.name,
            isDefault = true,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(default)
        return default
    }

    suspend fun save(profile: VoiceProfileEntity) = db.voiceProfileDao().upsert(profile)
    suspend fun delete(id: String) = db.voiceProfileDao().delete(id)
    suspend fun byId(id: String) = db.voiceProfileDao().byId(id)
}

const val DEFAULT_VOICE_PROFILE_ID = "voice-default"

class AudioAssetRepository(private val db: PulseDatabase) {
    val assets: Flow<List<AudioAssetEntity>> = db.audioAssetDao().observeAll()
    suspend fun add(asset: AudioAssetEntity) = db.audioAssetDao().upsert(asset)
    suspend fun remove(id: String) = db.audioAssetDao().delete(id)
    suspend fun markMissing(id: String, at: Long) = db.audioAssetDao().markMissing(id, at)
    suspend fun clearMissing(id: String) = db.audioAssetDao().clearMissing(id)
}

class ReminderRepository(private val db: PulseDatabase) {
    val reminders: Flow<List<ReminderEntity>> = db.reminderDao().observeAll()
    val enabled: Flow<List<ReminderEntity>> = db.reminderDao().observeEnabled()

    suspend fun schedule(workoutId: String, atMillis: Long, repeatRule: String = "NONE", note: String? = null): String {
        val id = UUID.randomUUID().toString()
        db.reminderDao().upsert(
            ReminderEntity(id = id, workoutId = workoutId, scheduledAt = atMillis, repeatRule = repeatRule, note = note)
        )
        return id
    }

    suspend fun delete(id: String) = db.reminderDao().delete(id)
    suspend fun markNotified(id: String) = db.reminderDao().markNotified(id, System.currentTimeMillis())
    suspend fun due(from: Long, to: Long) = db.reminderDao().between(from, to)
}

class ProgramRepository(private val db: PulseDatabase) {
    val programs: Flow<List<ProgramEntity>> = db.programDao().observeAll()
    suspend fun save(program: ProgramEntity) = db.programDao().upsert(program)
    suspend fun delete(id: String) = db.programDao().delete(id)
}
