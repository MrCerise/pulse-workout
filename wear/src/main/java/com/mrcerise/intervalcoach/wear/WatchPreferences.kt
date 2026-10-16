package com.mrcerise.intervalcoach.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mrcerise.intervalcoach.wear.ui.theme.WearThemeMode
import com.pulse.sync.LibraryManifest
import com.pulse.sync.SessionLog
import com.pulse.sync.SyncJson
import com.pulse.sync.SyncedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Watch-side settings.
 *
 * The appearance and cue fields are mirrored from the phone (see [applySynced]); the watch can
 * override them locally between syncs. Undelivered session logs are queued here so a workout
 * finished away from the phone still lands in the phone's history later.
 *
 * [syncEnabled] is deliberately **not** mirrored: it is the watch's own switch for whether it talks
 * to a phone at all. Off means the watch is a standalone device — no library, preference or
 * session traffic in either direction — and nothing that already happened locally is lost, because
 * the queue keeps its logs until a sync does happen.
 */
data class WatchPreferences(
    val themeMode: WearThemeMode = WearThemeMode.SYSTEM,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val countdownFromSeconds: Int = 3,
    val halfwayAnnouncements: Boolean = true,
    /** The watch's own switch for the phone link. Local-only, never mirrored from the phone. */
    val syncEnabled: Boolean = true,
    val lastSyncAt: Long = 0L,
    val phoneAppVersion: String = "",
    val libraryTruncated: Boolean = false,
    /** Session logs waiting for the phone, oldest first. */
    val pendingLogs: List<SessionLog> = emptyList(),
)

private val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_watch_prefs")

/** Key names — top-level so the mapping below stays a plain function. */
private object WatchPrefKeys {
    val theme = stringPreferencesKey("theme_mode")
    val highContrast = booleanPreferencesKey("high_contrast")
    val reducedMotion = booleanPreferencesKey("reduced_motion")
    val vibration = booleanPreferencesKey("vibration")
    val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    val countdown = intPreferencesKey("countdown_from_seconds")
    val halfway = booleanPreferencesKey("halfway_announcements")
    val syncEnabled = booleanPreferencesKey("sync_enabled")
    val lastSyncAt = longPreferencesKey("last_sync_at")
    val phoneVersion = stringPreferencesKey("phone_app_version")
    val libraryTruncated = booleanPreferencesKey("library_truncated")
    val pendingLogs = stringPreferencesKey("pending_session_logs")
}

private fun Preferences.toWatchPreferences(): WatchPreferences = WatchPreferences(
    themeMode = this[WatchPrefKeys.theme].toThemeMode(),
    highContrast = this[WatchPrefKeys.highContrast] ?: false,
    reducedMotion = this[WatchPrefKeys.reducedMotion] ?: false,
    vibrationEnabled = this[WatchPrefKeys.vibration] ?: true,
    keepScreenOn = this[WatchPrefKeys.keepScreenOn] ?: true,
    countdownFromSeconds = this[WatchPrefKeys.countdown] ?: 3,
    halfwayAnnouncements = this[WatchPrefKeys.halfway] ?: true,
    syncEnabled = this[WatchPrefKeys.syncEnabled] ?: true,
    lastSyncAt = this[WatchPrefKeys.lastSyncAt] ?: 0L,
    phoneAppVersion = this[WatchPrefKeys.phoneVersion] ?: "",
    libraryTruncated = this[WatchPrefKeys.libraryTruncated] ?: false,
    pendingLogs = this[WatchPrefKeys.pendingLogs]?.let { SyncJson.decodeLogQueue(it) } ?: emptyList(),
)

private fun String?.toThemeMode(): WearThemeMode = when (this) {
    "DARK" -> WearThemeMode.DARK
    "TRUE_BLACK" -> WearThemeMode.TRUE_BLACK
    // The watch has no light palette; a phone set to LIGHT follows the watch's system here.
    else -> WearThemeMode.SYSTEM
}

class WatchPreferencesRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val raw: Flow<Preferences> = context.watchDataStore.data

    /** Eager so the session controller and the sync link always see a current value. */
    val flow: StateFlow<WatchPreferences> = raw
        .map { it.toWatchPreferences() }
        .stateIn(scope, SharingStarted.Eagerly, WatchPreferences())

    fun setVibration(enabled: Boolean) = launchEdit { it[WatchPrefKeys.vibration] = enabled }

    fun setKeepScreenOn(enabled: Boolean) = launchEdit { it[WatchPrefKeys.keepScreenOn] = enabled }

    fun setThemeMode(mode: WearThemeMode) = launchEdit { it[WatchPrefKeys.theme] = mode.name }

    /** Turns the phone link on or off. The queue and the history are untouched either way. */
    fun setSyncEnabled(enabled: Boolean) = launchEdit { it[WatchPrefKeys.syncEnabled] = enabled }

    /** Records what the phone's manifest said, so settings can show sync state honestly. */
    fun markSynced(manifest: LibraryManifest) = launchEdit {
        it[WatchPrefKeys.lastSyncAt] = System.currentTimeMillis()
        it[WatchPrefKeys.phoneVersion] = manifest.appVersion
        it[WatchPrefKeys.libraryTruncated] = manifest.truncated
    }

    fun applySynced(synced: SyncedPreferences) = launchEdit {
        it[WatchPrefKeys.theme] = synced.themeMode
        it[WatchPrefKeys.highContrast] = synced.highContrast
        it[WatchPrefKeys.reducedMotion] = synced.reducedMotion
        it[WatchPrefKeys.vibration] = synced.vibrationEnabled
        it[WatchPrefKeys.keepScreenOn] = synced.keepScreenOn
        it[WatchPrefKeys.countdown] = synced.countdownFromSeconds
        it[WatchPrefKeys.halfway] = synced.halfwayAnnouncements
    }

    private fun launchEdit(block: (MutablePreferences) -> Unit) {
        // Setters are called from UI callbacks, which cannot suspend; every edit is fire-and-forget
        // on the container scope. DataStore serializes edits, so ordering is preserved.
        scope.launch {
            runCatching { context.watchDataStore.edit(block) }
        }
    }

    /** Appends a finished session log to the queue. */
    suspend fun queueSessionLog(log: SessionLog) {
        context.watchDataStore.edit { prefs ->
            val queue = prefs[WatchPrefKeys.pendingLogs]?.let { SyncJson.decodeLogQueue(it) } ?: emptyList()
            if (queue.none { it.sessionId == log.sessionId }) {
                prefs[WatchPrefKeys.pendingLogs] = SyncJson.encodeLogQueue(queue + log)
            }
        }
    }

    /** Removes a delivered log from the queue. */
    suspend fun removeQueuedSessionLog(sessionId: String) {
        context.watchDataStore.edit { prefs ->
            val queue = prefs[WatchPrefKeys.pendingLogs]?.let { SyncJson.decodeLogQueue(it) } ?: emptyList()
            val remaining = queue.filterNot { it.sessionId == sessionId }
            if (remaining.size != queue.size) {
                prefs[WatchPrefKeys.pendingLogs] = SyncJson.encodeLogQueue(remaining)
            }
        }
    }
}
