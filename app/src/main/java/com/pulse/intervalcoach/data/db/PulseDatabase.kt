package com.pulse.intervalcoach.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Room schema.
 *
 * Workout *structure* is stored as JSON produced by the pure engine model ([com.pulse.engine.WorkoutPlan]).
 * That keeps one authoritative representation of a workout (the engine's), makes export/import a
 * straight copy, and lets repeat groups nest without recursive SQL. Everything else — folders, tags,
 * labels, voice profiles, media, sessions and planner entries — is a first-class table.
 */

// ---------------------------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------------------------

@Entity(tableName = "workouts", indices = [Index("folderId"), Index("lastUsedAt")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val type: String,
    /** Serialized com.pulse.engine.WorkoutPlan (structure only; metadata lives in columns). */
    @ColumnInfo(name = "planJson") val planJson: String,
    val folderId: String? = null,
    val iconKey: String = "bolt",
    val colorArgb: Long? = null,
    val equipment: String? = null,
    val voiceProfileId: String? = null,
    val isFavorite: Boolean = false,
    /** True for bundled starter templates so the UI can label them honestly. */
    val builtIn: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Int = 1,
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "workout_tags",
    primaryKeys = ["workoutId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class WorkoutTagCrossRef(val workoutId: String, val tagId: String)

@Entity(tableName = "exercise_labels")
data class ExerciseLabelEntity(
    @PrimaryKey val id: String,
    val label: String,
    val phase: String,
    val defaultDurationMillis: Long = 0L,
    val notes: String? = null,
    val speechText: String? = null,
    val useCount: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val verbosity: String = "STANDARD",
    val spokenCountdown: Boolean = true,
    val halfwayAnnouncements: Boolean = true,
    val announceNext: Boolean = true,
    val languageTag: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "audio_assets")
data class AudioAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    /** USER_FILE, RECORDING or BUNDLED */
    val source: String,
    val durationMillis: Long = 0L,
    /** Set when the file could not be opened on the last attempt, so the UI can warn. */
    val missingDetectedAt: Long? = null,
    val createdAt: Long,
)

@Entity(tableName = "sessions", indices = [Index("startedAt"), Index("workoutId")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val workoutName: String,
    /** Full snapshot of the workout as it was run, so later edits never rewrite history. */
    val workoutSnapshotJson: String,
    val startedAt: Long,
    val endedAt: Long,
    /** Time the timer was actually running (excludes pauses). */
    val activeMillis: Long,
    /** Wall-clock span from start to finish. */
    val wallMillis: Long,
    val sessionElapsedMillis: Long,
    val completedIntervals: Int,
    val totalIntervals: Int,
    val skippedIntervals: Int,
    val completionPercent: Int,
    /** COMPLETED, STOPPED_EARLY or INTERRUPTED */
    val status: String,
    val roundsLogged: Int = 0,
    val notes: String? = null,
    val weightUnitAtRun: String? = null,
    /** Last moment the app proved it was alive — used to detect process loss honestly. */
    val lastHeartbeatAtMillis: Long? = null,
)

@Entity(tableName = "session_events", indices = [Index("sessionId")], foreignKeys = [
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
])
data class SessionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val stepIndex: Int,
    val stepName: String,
    /** INTERVAL_STARTED, INTERVAL_COMPLETED, INTERVAL_SKIPPED, PAUSED, RESUMED, TIME_ADDED, LAP, ROUND_LOGGED, NOTE, FINISHED */
    val kind: String,
    /** Milliseconds from session start. */
    val atMillis: Long,
    val durationMillis: Long = 0L,
    val detail: String? = null,
)

@Entity(tableName = "reminders", indices = [Index("scheduledAt")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val scheduledAt: Long,
    /** NONE, DAILY, WEEKLY */
    val repeatRule: String = "NONE",
    val enabled: Boolean = true,
    val notifiedAt: Long? = null,
    val note: String? = null,
)

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val startDateMillis: Long,
    val weeks: Int,
    val daysPerWeek: Int,
    val createdAt: Long,
)

@Entity(tableName = "program_entries", indices = [Index("programId"), Index("workoutId")])
data class ProgramEntryEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val weekIndex: Int,
    val dayIndex: Int,
    val workoutId: String,
)

// ---------------------------------------------------------------------------------------------
// DAOs
// ---------------------------------------------------------------------------------------------

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY isFavorite DESC, COALESCE(lastUsedAt, createdAt) DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeById(id: String): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun byId(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE isFavorite = 1 ORDER BY COALESCE(lastUsedAt, createdAt) DESC LIMIT :limit")
    fun observeFavorites(limit: Int = 12): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 12): Flow<List<WorkoutEntity>>

    @Upsert
    suspend fun upsert(workout: WorkoutEntity)

    @Upsert
    suspend fun upsertAll(workouts: List<WorkoutEntity>)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE workouts SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE workouts SET lastUsedAt = :now, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM workouts WHERE builtIn = 1")
    suspend fun countBuiltIn(): Int
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<FolderEntity>>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM workout_tags")
    fun observeLinks(): Flow<List<WorkoutTagCrossRef>>

    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: WorkoutTagCrossRef)

    @Query("DELETE FROM workout_tags WHERE workoutId = :workoutId")
    suspend fun clearLinks(workoutId: String)
}

@Dao
interface ExerciseLabelDao {
    @Query("SELECT * FROM exercise_labels ORDER BY useCount DESC, label")
    fun observeAll(): Flow<List<ExerciseLabelEntity>>

    @Upsert
    suspend fun upsert(label: ExerciseLabelEntity)

    @Query("DELETE FROM exercise_labels WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles ORDER BY isDefault DESC, name")
    fun observeAll(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles WHERE id = :id")
    suspend fun byId(id: String): VoiceProfileEntity?

    @Upsert
    suspend fun upsert(profile: VoiceProfileEntity)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AudioAssetDao {
    @Query("SELECT * FROM audio_assets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AudioAssetEntity>>

    @Upsert
    suspend fun upsert(asset: AudioAssetEntity)

    @Query("DELETE FROM audio_assets WHERE id = :id")
    suspend fun delete(id: String)

    /** Records that the file behind an asset could not be opened (revoked access, deleted file). */
    @Query("UPDATE audio_assets SET missingDetectedAt = :at WHERE id = :id")
    suspend fun markMissing(id: String, at: Long)

    @Query("UPDATE audio_assets SET missingDetectedAt = NULL WHERE id = :id")
    suspend fun clearMissing(id: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    suspend fun between(from: Long, to: Long): List<SessionEntity>

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("UPDATE sessions SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String?)

    @Query("UPDATE sessions SET lastHeartbeatAtMillis = :at WHERE id = :id")
    suspend fun heartbeat(id: String, at: Long)

    @Query("UPDATE sessions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    /** Sessions left in the RUNNING state — i.e. the process died mid-workout. */
    @Query("SELECT * FROM sessions WHERE status = 'RUNNING'")
    suspend fun runningSessions(): List<SessionEntity>

    @Query("SELECT COALESCE(SUM(activeMillis), 0) FROM sessions")
    suspend fun totalActiveMillis(): Long
}

@Dao
interface SessionEventDao {
    @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY atMillis, id")
    fun observeForSession(sessionId: String): Flow<List<SessionEventEntity>>

    @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY atMillis, id")
    suspend fun forSession(sessionId: String): List<SessionEventEntity>

    @Insert
    suspend fun insertAll(events: List<SessionEventEntity>)

    @Insert
    suspend fun insert(event: SessionEventEntity): Long
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY scheduledAt")
    fun observeEnabled(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY scheduledAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt")
    suspend fun between(from: Long, to: Long): List<ReminderEntity>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET notifiedAt = :at WHERE id = :id")
    suspend fun markNotified(id: String, at: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY startDateMillis DESC")
    fun observeAll(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM program_entries ORDER BY weekIndex, dayIndex")
    fun observeEntries(): Flow<List<ProgramEntryEntity>>

    @Upsert
    suspend fun upsert(program: ProgramEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<ProgramEntryEntity>)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun delete(id: String)
}

// ---------------------------------------------------------------------------------------------
// Database
// ---------------------------------------------------------------------------------------------

@Database(
    entities = [
        WorkoutEntity::class,
        FolderEntity::class,
        TagEntity::class,
        WorkoutTagCrossRef::class,
        ExerciseLabelEntity::class,
        VoiceProfileEntity::class,
        AudioAssetEntity::class,
        SessionEntity::class,
        SessionEventEntity::class,
        ReminderEntity::class,
        ProgramEntity::class,
        ProgramEntryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(PulseConverters::class)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun exerciseLabelDao(): ExerciseLabelDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun audioAssetDao(): AudioAssetDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionEventDao(): SessionEventDao
    abstract fun reminderDao(): ReminderDao
    abstract fun programDao(): ProgramDao

    companion object {
        const val NAME = "pulse.db"
    }
}

class PulseConverters {
    @TypeConverter fun toBool(value: Int): Boolean = value != 0
    @TypeConverter fun fromBool(value: Boolean): Int = if (value) 1 else 0
}

/**
 * Migration 1 → 2 (shipped with the first public build).
 *
 * Adds the columns that make process-loss reporting honest: a session heartbeat so an interrupted
 * session can be distinguished from one the user stopped, and the `builtIn` flag that marks bundled
 * starter templates. Existing workouts, sessions and events are preserved untouched.
 *
 * The statements live in [PulseMigrations.ONE_TO_TWO] so the JVM migration test can run the exact
 * same SQL against a real SQLite database (app/src/test/.../DatabaseMigrationTest.kt).
 */
object PulseMigrations {
    val ONE_TO_TWO: List<String> = listOf(
        "ALTER TABLE sessions ADD COLUMN lastHeartbeatAtMillis INTEGER",
        "ALTER TABLE workouts ADD COLUMN builtIn INTEGER NOT NULL DEFAULT 0",
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ONE_TO_TWO.forEach(db::execSQL)
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
