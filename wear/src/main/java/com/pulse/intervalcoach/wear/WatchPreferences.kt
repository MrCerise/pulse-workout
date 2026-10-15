package com.pulse.intervalcoach.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pulse.sync.LibraryManifest
import com.pulse.sync.SessionLog
import com.pulse.sync.SyncJson
import com.pulse.sync.SyncedPreferences
import com.pulse.intervalcoach.wear.ui.theme.WearThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Watch-side settings.
 *
 * The appearance and cue fields are mirrored from the phone (see [applySynced]); the watch can
 * override them locally between syncs. Undelivered session logs are queued here so a workout
 * finished away from the phone still lands in the phone's history later.
 */
data class WatchPreferences(
    val themeMode: WearThemeMode = WearThemeMode.SYSTEM,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val countdownFromSeconds: Int = 3,
    val halfwayAnnouncements: Boolean = true,
    val lastSyncAt: Long = 0L,
    val phoneAppVersion: String = "",
    val libraryTruncated: Boolean = false,
    /** Session logs waiting for the phone, oldest first. */
    val pendingLogs: List<SessionLog> = emptyList(),
)

private val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_watch_prefs")

class WatchPreferencesRepository(private val context: Context, scope: CoroutineScope) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val highContrast = booleanPreferencesKey("high_contrast")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val vibration = booleanPreferencesKey("vibration")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val countdown = intPreferencesKey("countdown_from_seconds")
        val halfway = booleanPreferencesKey("halfway_announcements")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val phoneVersion = stringPreferencesKey("phone_app_version")
        val libraryTruncated = booleanPreferencesKey("library_truncated")
        val pendingLogs = stringPreferencesKey("pending_session_logs")
    }

    private val raw: Flow<Preferences> = context.watchDataStore.data

    /** Eager so the session controller and the sync link always see a current value. */
    val flow: StateFlow<WatchPreferences> = raw
        .map { it.toPreferences() }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, WatchPreferences())

    private fun Preferences.toPreferences(): WatchPreferences = WatchPreferences(
        themeMode = this[Keys.theme]?.toThemeMode() ?: WearThemeMode.SYSTEM,
        highContrast = this[Keys.highContrast] ?: false,
        reducedMotion = this[Keys.reducedMotion] ?: false,
        vibrationEnabled = this[Keys.vibration] ?: true,
        keepScreenOn = this[Keys.keepScreenOn] ?: true,
        countdownFromSeconds = this[Keys.countdown] ?: 3,
        halfwayAnnouncements = this[Keys.halfway] ?: true,
        lastSyncAt = this[Keys.lastSyncAt] ?: 0L,
        phoneAppVersion = this[Keys.phoneVersion] ?: "",
        libraryTruncated = this[Keys.libraryTruncated] ?: false,
        pendingLogs = this[Keys.pendingLogs]?.let { SyncJson.decodeLogQueue(it) } ?: emptyList(),
    )

    private fun String.toThemeMode(): WearThemeMode = when (this) {
        "DARK" -> WearThemeMode.DARK
        "TRUE_BLACK" -> WearThemeMode.TRUE_BLACK
        // The watch has no light palette; a phone set to LIGHT follows the watch's system here.
        else -> WearThemeMode.SYSTEM
    }

    fun setVibration(enabled: Boolean) = launchEdit { it[Keys.vibration] = enabled }

    fun setKeepScreenOn(enabled: Boolean) = launchEdit { it[Keys.keepScreenOn] = enabled }

    fun setThemeMode(mode: WearThemeMode) = launchEdit { it[Keys.theme] = mode.name }

    /** Records what the phone's manifest said, so settings can show sync state honestly. */
    fun markSynced(manifest: LibraryManifest) = launchEdit {
        it[Keys.lastSyncAt] = System.currentTimeMillis()
        it[Keys.phoneVersion] = manifest.appVersion
        it[Keys.libraryTruncated] = manifest.truncated
    }

    fun applySynced(synced: SyncedPreferences) = launchEdit {
        it[Keys.theme] = synced.themeMode
        it[Keys.highContrast] = synced.highContrast
        it[Keys.reducedMotion] = synced.reducedMotion
        it[Keys.vibration] = synced.vibrationEnabled
        it[Keys.keepScreenOn] = synced.keepScreenOn
        it[Keys.countdown] = synced.countdownFromSeconds
        it[Keys.halfway] = synced.halfwayAnnouncements
    }

    private fun launchEdit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        // Setters are called from UI callbacks, which cannot suspend; every edit is fire-and-forget
        // on the container scope. DataStore serializes edits, so ordering is preserved.
        scope.launch {
            runCatching { context.watchDataStore.edit(block) }
        }
    }

    /** Appends a finished session log to the queue. */
    suspend fun queueSessionLog(log: SessionLog) {
        context.watchDataStore.edit { prefs ->
            val queue = prefs[Keys.pendingLogs]?.let { SyncJson.decodeLogQueue(it) } ?: emptyList()
            if (queue.none { it.sessionId == log.sessionId }) {
                prefs[Keys.pendingLogs] = SyncJson.encodeLogQueue(queue + log)
            }
        }
    }

    /** Removes a delivered log and returns whether it was still queued. */
    suspend fun removeQueuedSessionLog(sessionId: String) {
        context.watchDataStore.edit { prefs ->
            val queue = prefs[Keys.pendingLogs]?.let { SyncJson.decodeLogQueue(it) } ?: emptyList()
            val remaining = queue.filterNot { it.sessionId == sessionId }
            if (remaining.size != queue.size) {
                prefs[Keys.pendingLogs] = SyncJson.encodeLogQueue(remaining)
            }
        }
    }
}
