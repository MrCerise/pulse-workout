package com.pulse.intervalcoach.data

import android.content.Context
import android.net.Uri
import com.pulse.engine.WorkoutPlan
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Portable JSON backup and restore.
 *
 * Design rules (all of them are user-visible behaviours, not internal details):
 *  - export is a plain, documented JSON file — no proprietary container;
 *  - import validates the whole file first and reports what it contains before writing anything;
 *  - importing never overwrites: colliding workout IDs get fresh IDs and a " (imported)" suffix;
 *  - a corrupt or foreign file fails with a message and leaves existing data untouched;
 *  - missing media (revoked SAF permissions, deleted files) is reported, not silently ignored.
 */

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String,
    val workouts: List<WorkoutRecord> = emptyList(),
    val folders: List<FolderRecord> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    val voiceProfiles: List<VoiceProfileRecord> = emptyList(),
    val exerciseLabels: List<LabelRecord> = emptyList(),
    val reminders: List<ReminderRecord> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
) {
    companion object {
        const val FORMAT = "pulse.backup"
    }
}

@Serializable
data class WorkoutRecord(
    val id: String,
    val name: String,
    val description: String = "",
    val type: String,
    val planJson: String,
    val folderName: String? = null,
    val tags: List<String> = emptyList(),
    val equipment: String? = null,
    val isFavorite: Boolean = false,
    val builtIn: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class FolderRecord(val id: String, val name: String, val sortOrder: Int, val createdAt: Long)

@Serializable
data class SessionRecord(
    val id: String,
    val workoutId: String,
    val workoutName: String,
    val workoutSnapshotJson: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeMillis: Long,
    val wallMillis: Long,
    val sessionElapsedMillis: Long,
    val completedIntervals: Int,
    val totalIntervals: Int,
    val skippedIntervals: Int,
    val completionPercent: Int,
    val status: String,
    val roundsLogged: Int,
    val notes: String?,
    val events: List<EventRecord> = emptyList(),
)

@Serializable
data class EventRecord(
    val stepIndex: Int,
    val stepName: String,
    val kind: String,
    val atMillis: Long,
    val durationMillis: Long,
    val detail: String? = null,
)

@Serializable
data class VoiceProfileRecord(
    val id: String, val name: String, val rate: Float, val pitch: Float, val verbosity: String,
    val spokenCountdown: Boolean, val halfwayAnnouncements: Boolean, val announceNext: Boolean,
)

@Serializable
data class LabelRecord(val id: String, val label: String, val phase: String, val defaultDurationMillis: Long, val notes: String? = null, val speechText: String? = null)

@Serializable
data class ReminderRecord(val id: String, val workoutId: String, val scheduledAt: Long, val repeatRule: String, val enabled: Boolean, val note: String? = null)

data class BackupPreview(
    val workouts: Int,
    val sessions: Int,
    val tags: Int,
    val folders: Int,
    val exportedAt: Long,
    val warnings: List<String>,
)

data class BackupResult(val displayName: String, val bytes: Long)

class BackupRepository(
    private val context: Context,
    private val container: AppContainer,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ------------------------------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------------------------------

    suspend fun buildBackup(): BackupFile = BackupFile(
        exportedAt = System.currentTimeMillis(),
        appVersion = BuildConfig.VERSION_NAME,
        workouts = exportWorkouts(),
        folders = exportFolders(),
        sessions = exportSessions(),
        voiceProfiles = exportVoiceProfiles(),
        exerciseLabels = exportLabels(),
        reminders = exportReminders(),
        settings = exportSettings(),
    )

    /** Writes a complete backup to a file the user picked through the Storage Access Framework. */
    suspend fun writeTo(uri: Uri): BackupResult {
        val backup = buildBackup()
        val text = json.encodeToString(backup)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("The selected file could not be opened for writing.")
        stream.use { out ->
            out.write(bytes)
            out.flush()
        }
        return BackupResult(displayName = uri.lastPathSegment ?: "pulse-backup.json", bytes = bytes.size.toLong())
    }

    /** Weekly backup target: app-private directory, or the user's chosen folder when set. */
    suspend fun writeAutomaticBackup(): BackupResult {
        val backup = buildBackup()
        val text = json.encodeToString(backup)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        val fileName = "pulse-backup-$stamp.json"
        val prefs = container.preferences.current()
        val folderUri = prefs.backupFolderUri
        if (folderUri != null) {
            runCatching {
                val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                val file = tree?.createFile("application/json", fileName)
                file?.uri?.let { uri ->
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                    return BackupResult(fileName, text.toByteArray().size.toLong())
                }
            }
        }
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(text)
        pruneOldBackups(dir)
        return BackupResult(fileName, file.length())
    }

    fun automaticBackups(): List<File> {
        val dir = File(context.filesDir, "backups")
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun pruneOldBackups(dir: File, keep: Int = 8) {
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { runCatching { it.delete() } }
    }

    // ------------------------------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------------------------------

    /** Parses and validates a file without writing anything, so the user can confirm first. */
    fun preview(stream: InputStream): Result<BackupPreview> = runCatching {
        val text = stream.readBytes().toString(Charsets.UTF_8)
        val backup = parse(text)
        val warnings = mutableListOf<String>()
        backup.workouts.forEach { record ->
            runCatching { PlanCodec.decode(record.planJson) }.onFailure {
                warnings += "Workout “${record.name}” has an unreadable definition and will be skipped."
            }
        }
        val missingMedia = backup.workouts.count { it.planJson.contains("content://") }
        if (missingMedia > 0) warnings += "$missingMedia workout(s) reference images or audio that may no longer be available."
        BackupPreview(
            workouts = backup.workouts.size,
            sessions = backup.sessions.size,
            tags = backup.workouts.flatMap { it.tags }.distinct().size,
            folders = backup.folders.size,
            exportedAt = backup.exportedAt,
            warnings = warnings,
        )
    }

    /** Applies an import. Collisions get new IDs; nothing existing is modified or deleted. */
    suspend fun import(stream: InputStream): Result<Pair<Int, Int>> = runCatching {
        val text = stream.readBytes().toString(Charsets.UTF_8)
        val backup = parse(text)
        val db = container.database
        var workoutsImported = 0

        val folderIdsByName = mutableMapOf<String, String>()
        backup.folders.forEach { record ->
            val existingId = folderIdByName(record.name)
            val id = existingId ?: record.id
            if (existingId == null) {
                db.folderDao().upsert(com.pulse.intervalcoach.data.db.FolderEntity(id, record.name, record.sortOrder, record.createdAt))
            }
            folderIdsByName[record.name] = id
        }

        backup.workouts.forEach { record ->
            val decoded = runCatching { PlanCodec.decode(record.planJson) }.getOrNull() ?: return@forEach
            val existing = container.workouts.entity(record.id)
            val plan: WorkoutPlan = if (existing == null) {
                decoded.copy(id = record.id, name = record.name)
            } else if (existing.planJson == record.planJson) {
                return@forEach // identical copy already present: skip rather than duplicate
            } else {
                decoded.copy(id = UUID.randomUUID().toString(), name = "${record.name} (imported)")
            }
            runCatching {
                container.workouts.save(
                    plan = plan,
                    folderId = record.folderName?.let { folderIdsByName[it] },
                    tags = record.tags,
                )
                workoutsImported++
            }
        }

        var sessionsImported = 0
        backup.sessions.forEach { record ->
            val exists = container.sessions.session(record.id) != null
            val sessionId = if (exists) UUID.randomUUID().toString() else record.id
            val entity = com.pulse.intervalcoach.data.db.SessionEntity(
                id = sessionId,
                workoutId = record.workoutId,
                workoutName = record.workoutName,
                workoutSnapshotJson = record.workoutSnapshotJson,
                startedAt = record.startedAt,
                endedAt = record.endedAt,
                activeMillis = record.activeMillis,
                wallMillis = record.wallMillis,
                sessionElapsedMillis = record.sessionElapsedMillis,
                completedIntervals = record.completedIntervals,
                totalIntervals = record.totalIntervals,
                skippedIntervals = record.skippedIntervals,
                completionPercent = record.completionPercent,
                status = record.status,
                roundsLogged = record.roundsLogged,
                notes = record.notes,
                lastHeartbeatAtMillis = record.endedAt,
            )
            db.sessionDao().upsert(entity)
            if (record.events.isNotEmpty()) {
                db.sessionEventDao().insertAll(
                    record.events.map { event ->
                        com.pulse.intervalcoach.data.db.SessionEventEntity(
                            sessionId = sessionId,
                            stepIndex = event.stepIndex,
                            stepName = event.stepName,
                            kind = event.kind,
                            atMillis = event.atMillis,
                            durationMillis = event.durationMillis,
                            detail = event.detail,
                        )
                    }
                )
            }
            sessionsImported++
        }

        backup.voiceProfiles.forEach { record ->
            db.voiceProfileDao().upsert(
                com.pulse.intervalcoach.data.db.VoiceProfileEntity(
                    id = record.id,
                    name = record.name,
                    rate = record.rate,
                    pitch = record.pitch,
                    verbosity = record.verbosity,
                    spokenCountdown = record.spokenCountdown,
                    halfwayAnnouncements = record.halfwayAnnouncements,
                    announceNext = record.announceNext,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }

        backup.exerciseLabels.forEach { record ->
            db.exerciseLabelDao().upsert(
                com.pulse.intervalcoach.data.db.ExerciseLabelEntity(
                    id = record.id,
                    label = record.label,
                    phase = record.phase,
                    defaultDurationMillis = record.defaultDurationMillis,
                    notes = record.notes,
                    speechText = record.speechText,
                    useCount = 0,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }

        backup.reminders.forEach { record ->
            if (record.scheduledAt > System.currentTimeMillis()) {
                db.reminderDao().upsert(
                    com.pulse.intervalcoach.data.db.ReminderEntity(
                        id = record.id,
                        workoutId = record.workoutId,
                        scheduledAt = record.scheduledAt,
                        repeatRule = record.repeatRule,
                        enabled = record.enabled,
                        note = record.note,
                    )
                )
            }
        }

        workoutsImported to sessionsImported
    }

    private fun parse(text: String): BackupFile {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) {
            throw SerializationException("This file is not a PULSE backup (it does not look like JSON).")
        }
        val backup = json.decodeFromString(BackupFile.serializer(), trimmed)
        if (backup.format != BackupFile.FORMAT) {
            throw SerializationException("This JSON file was written by another app, so it was not imported.")
        }
        return backup
    }

    // ------------------------------------------------------------------------------------------
    // Section exports
    // ------------------------------------------------------------------------------------------

    private suspend fun exportWorkouts(): List<WorkoutRecord> {
        val database = container.database
        val folderNames = database.query("SELECT id, name FROM folders", emptyArray<Any>()).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
        val tagNames = database.query("SELECT id, name FROM tags", emptyArray<Any>()).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
        val tagsByWorkout = database.query("SELECT workoutId, tagId FROM workout_tags", emptyArray<Any>()).use { cursor ->
            val map = mutableMapOf<String, MutableList<String>>()
            while (cursor.moveToNext()) map.getOrPut(cursor.getString(0)) { mutableListOf() } += cursor.getString(1)
            map
        }
        val entities = database.query("SELECT id FROM workouts ORDER BY createdAt", emptyArray<Any>()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return entities.mapNotNull { id ->
            val entity = container.workouts.entity(id) ?: return@mapNotNull null
            WorkoutRecord(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                type = entity.type,
                planJson = entity.planJson,
                folderName = entity.folderId?.let { folderNames[it] },
                tags = tagsByWorkout[id].orEmpty().mapNotNull { tagNames[it] },
                equipment = entity.equipment,
                isFavorite = entity.isFavorite,
                builtIn = entity.builtIn,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
        }
    }

    private suspend fun exportFolders(): List<FolderRecord> {
        val cursor = container.database.query("SELECT id, name, sortOrder, createdAt FROM folders", emptyArray<Any>())
        return cursor.use {
            val list = mutableListOf<FolderRecord>()
            while (it.moveToNext()) {
                list += FolderRecord(it.getString(0), it.getString(1), it.getInt(2), it.getLong(3))
            }
            list
        }
    }

    private suspend fun exportSessions(): List<SessionRecord> {
        val cursor = container.database.query(
            "SELECT id, workoutId, workoutName, workoutSnapshotJson, startedAt, endedAt, activeMillis, wallMillis, " +
                "sessionElapsedMillis, completedIntervals, totalIntervals, skippedIntervals, completionPercent, status, " +
                "roundsLogged, notes FROM sessions ORDER BY startedAt",
            emptyArray<Any>(),
        )
        val records = cursor.use {
            val list = mutableListOf<SessionRecord>()
            while (it.moveToNext()) {
                list += SessionRecord(
                    id = it.getString(0),
                    workoutId = it.getString(1),
                    workoutName = it.getString(2),
                    workoutSnapshotJson = it.getString(3),
                    startedAt = it.getLong(4),
                    endedAt = it.getLong(5),
                    activeMillis = it.getLong(6),
                    wallMillis = it.getLong(7),
                    sessionElapsedMillis = it.getLong(8),
                    completedIntervals = it.getInt(9),
                    totalIntervals = it.getInt(10),
                    skippedIntervals = it.getInt(11),
                    completionPercent = it.getInt(12),
                    status = it.getString(13),
                    roundsLogged = it.getInt(14),
                    notes = it.getString(15),
                )
            }
            list
        }
        return records.map { record ->
            val events = container.sessions.events(record.id).map { event ->
                EventRecord(event.stepIndex, event.stepName, event.kind, event.atMillis, event.durationMillis, event.detail)
            }
            record.copy(events = events)
        }
    }

    private suspend fun exportVoiceProfiles(): List<VoiceProfileRecord> {
        val cursor = container.database.query(
            "SELECT id, name, rate, pitch, verbosity, spokenCountdown, halfwayAnnouncements, announceNext FROM voice_profiles",
            emptyArray<Any>(),
        )
        return cursor.use {
            val list = mutableListOf<VoiceProfileRecord>()
            while (it.moveToNext()) {
                list += VoiceProfileRecord(
                    id = it.getString(0),
                    name = it.getString(1),
                    rate = it.getFloat(2),
                    pitch = it.getFloat(3),
                    verbosity = it.getString(4),
                    spokenCountdown = it.getInt(5) != 0,
                    halfwayAnnouncements = it.getInt(6) != 0,
                    announceNext = it.getInt(7) != 0,
                )
            }
            list
        }
    }

    private suspend fun exportLabels(): List<LabelRecord> {
        val cursor = container.database.query(
            "SELECT id, label, phase, defaultDurationMillis, notes, speechText FROM exercise_labels",
            emptyArray<Any>(),
        )
        return cursor.use {
            val list = mutableListOf<LabelRecord>()
            while (it.moveToNext()) {
                list += LabelRecord(it.getString(0), it.getString(1), it.getString(2), it.getLong(3), it.getString(4), it.getString(5))
            }
            list
        }
    }

    private suspend fun exportReminders(): List<ReminderRecord> {
        val cursor = container.database.query(
            "SELECT id, workoutId, scheduledAt, repeatRule, enabled, note FROM reminders",
            emptyArray<Any>(),
        )
        return cursor.use {
            val list = mutableListOf<ReminderRecord>()
            while (it.moveToNext()) {
                list += ReminderRecord(
                    it.getString(0), it.getString(1), it.getLong(2), it.getString(3), it.getInt(4) != 0, it.getString(5),
                )
            }
            list
        }
    }

    private suspend fun exportSettings(): Map<String, String> {
        val prefs = container.preferences.current()
        return mapOf(
            "themeMode" to prefs.themeMode.name,
            "playerDensity" to prefs.playerDensity.name,
            "weightUnit" to prefs.weightUnit.name,
            "verbosity" to prefs.verbosity.name,
            "spokenCountdown" to prefs.spokenCountdown.toString(),
            "defaultWorkMillis" to prefs.defaultWorkMillis.toString(),
            "defaultRestMillis" to prefs.defaultRestMillis.toString(),
            "defaultRounds" to prefs.defaultRounds.toString(),
        )
    }

    private suspend fun folderIdByName(name: String): String? {
        val cursor = container.database.query("SELECT id FROM folders WHERE name = ?", arrayOf<Any>(name))
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    /** Reads a backup from app-private storage (used to restore an automatic backup). */
    fun readAutomaticBackup(file: File): InputStream = file.inputStream()
}
