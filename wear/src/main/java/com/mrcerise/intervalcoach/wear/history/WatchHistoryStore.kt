package com.mrcerise.intervalcoach.wear.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pulse.sync.SessionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_watch_history")

private val RECORDS = stringPreferencesKey("session_records")

/**
 * The watch's own history of the sessions it ran.
 *
 * This exists because the watch is not a screen for the phone: a workout finished on a watch that
 * has never been paired — or one whose phone link is switched off — has nowhere else to be
 * recorded, and "it ran, but nothing anywhere says so" is the failure mode this store removes. The
 * phone's history stays the full record (event trail, Health Connect, statistics); this is the
 * wrist-sized list of what happened here.
 */
class WatchHistoryStore(
    private val context: Context,
    scope: CoroutineScope,
) : WatchHistoryRecorder {

    val records: StateFlow<List<WatchSessionRecord>> = context.historyDataStore.data
        .map { prefs -> WatchHistoryCodec.decode(prefs[RECORDS]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Reads the stored list directly, for callers (the tile) that cannot wait for [records] to load. */
    suspend fun current(): List<WatchSessionRecord> = WatchHistoryCodec.decode(context.historyDataStore.data.first()[RECORDS])

    /** Records a finished session, newest first, replacing any earlier entry for the same id. */
    override suspend fun record(log: SessionLog) {
        val record = WatchSessionRecord.from(log)
        context.historyDataStore.edit { prefs ->
            val current = WatchHistoryCodec.decode(prefs[RECORDS])
            prefs[RECORDS] = WatchHistoryCodec.encode(mergeWatchHistory(current, record))
        }
    }

    suspend fun clear() {
        context.historyDataStore.edit { prefs -> prefs.remove(RECORDS) }
    }
}
