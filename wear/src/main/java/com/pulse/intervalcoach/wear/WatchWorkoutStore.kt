package com.pulse.intervalcoach.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pulse.engine.WorkoutPlan
import com.pulse.sync.LibraryManifest
import com.pulse.sync.SyncJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first

/** A workout as stored on the watch: the plan plus the sync metadata needed to reconcile it. */
data class StoredWorkout(
    val id: String,
    val updatedAt: Long,
    val favorite: Boolean,
    val plan: WorkoutPlan,
)

private val Context.workoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_watch_workouts")

/**
 * The watch's copy of the workout library.
 *
 * The phone owns the library and pushes each workout as its own data item; this store keeps the
 * last received copy per workout id. [applyLibrary] reconciles against the phone's manifest so
 * deletions and renames propagate: anything the phone no longer lists is removed here.
 */
class WatchWorkoutStore(private val context: Context, scope: kotlinx.coroutines.CoroutineScope) {

    private object Keys {
        val known = stringSetPreferencesKey("known_workout_ids")
        fun plan(id: String) = stringPreferencesKey("plan:$id")
        fun updated(id: String) = longPreferencesKey("updated:$id")
        fun favorite(id: String) = booleanPreferencesKey("favorite:$id")
    }

    val workouts: StateFlow<List<StoredWorkout>> = context.workoutDataStore.data
        .map { prefs ->
            val ids = prefs[Keys.known] ?: emptySet()
            ids.mapNotNull { id ->
                val json = prefs[Keys.plan(id)] ?: return@mapNotNull null
                val plan = runCatching { SyncJson.decodePlan(json) }.getOrNull() ?: return@mapNotNull null
                StoredWorkout(
                    id = id,
                    updatedAt = prefs[Keys.updated(id)] ?: 0L,
                    favorite = prefs[Keys.favorite(id)] ?: false,
                    plan = plan,
                )
            }
                .sortedWith(compareByDescending<StoredWorkout> { it.favorite }.thenBy { it.plan.name.lowercase() })
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Upserts one workout from a `/pulse/workout/<id>` data item. */
    suspend fun applyWorkout(id: String, planJson: String, updatedAt: Long, favorite: Boolean) {
        // Reject a payload we cannot decode: keeping the previous copy is better than an empty row.
        runCatching { SyncJson.decodePlan(planJson) }.getOrNull() ?: return
        context.workoutDataStore.edit { prefs ->
            prefs[Keys.known] = (prefs[Keys.known] ?: emptySet()) + id
            prefs[Keys.plan(id)] = planJson
            prefs[Keys.updated(id)] = updatedAt
            prefs[Keys.favorite(id)] = favorite
        }
    }

    /** Drops workouts the phone's manifest no longer lists. */
    suspend fun applyLibrary(manifest: LibraryManifest) {
        val expected = manifest.workoutIds.toSet()
        context.workoutDataStore.edit { prefs ->
            val known = prefs[Keys.known] ?: emptySet()
            for (id in known - expected) {
                prefs.remove(Keys.plan(id))
                prefs.remove(Keys.updated(id))
                prefs.remove(Keys.favorite(id))
            }
            prefs[Keys.known] = known intersect expected
        }
    }

    suspend fun plan(id: String): WorkoutPlan? {
        val prefs = context.workoutDataStore.data.first()
        return prefs[Keys.plan(id)]?.let { runCatching { SyncJson.decodePlan(it) }.getOrNull() }
    }

    suspend fun clear() {
        context.workoutDataStore.edit { it.clear() }
    }
}
