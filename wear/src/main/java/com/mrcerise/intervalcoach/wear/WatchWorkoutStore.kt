package com.mrcerise.intervalcoach.wear

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
import com.pulse.library.StarterWorkouts
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
 *
 * A watch that has **never** seen a phone is a first-class case, not an error path: until a
 * manifest arrives ([phoneLibrarySeen]), the store seeds the bundled `:library` catalogue, so a
 * watch worn on its own still has workouts to run. The seeded ids are the same `builtin-…` ids the
 * phone derives from the same seeds, so the first sync reconciles them rather than duplicating
 * them — and if the workout was deleted on the phone, it is deleted here too, because the phone
 * stays the owner of the library once there is one.
 */
class WatchWorkoutStore(private val context: Context, scope: kotlinx.coroutines.CoroutineScope) {

    private object Keys {
        val known = stringSetPreferencesKey("known_workout_ids")
        val librarySynced = booleanPreferencesKey("library_synced")
        fun plan(id: String) = stringPreferencesKey("plan:$id")
        fun updated(id: String) = longPreferencesKey("updated:$id")
        fun favorite(id: String) = booleanPreferencesKey("favorite:$id")
    }

    val workouts: StateFlow<List<StoredWorkout>> = context.workoutDataStore.data
        .map { prefs -> prefs.toStoredWorkouts() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Reads the library directly, for callers (the tile) that cannot wait for [workouts] to load. */
    suspend fun current(): List<StoredWorkout> = context.workoutDataStore.data.first().toStoredWorkouts()

    private fun Preferences.toStoredWorkouts(): List<StoredWorkout> {
        val ids = this[Keys.known] ?: emptySet()
        return ids.mapNotNull { id ->
            val json = this[Keys.plan(id)] ?: return@mapNotNull null
            val plan = runCatching { SyncJson.decodePlan(json) }.getOrNull() ?: return@mapNotNull null
            StoredWorkout(
                id = id,
                updatedAt = this[Keys.updated(id)] ?: 0L,
                favorite = this[Keys.favorite(id)] ?: false,
                plan = plan,
            )
        }
            .sortedWith(compareByDescending<StoredWorkout> { it.favorite }.thenBy { it.plan.name.lowercase() })
    }

    /**
     * True once a phone's manifest has been applied at least once. Until then this watch is
     * running solo and its library is the bundled catalogue.
     */
    val phoneLibrarySeen: StateFlow<Boolean> = context.workoutDataStore.data
        .map { prefs -> prefs[Keys.librarySynced] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Stars or unstars a workout on this watch; the sync link tells the phone. */
    suspend fun setFavorite(id: String, favorite: Boolean) {
        context.workoutDataStore.edit { prefs ->
            if (id in (prefs[Keys.known] ?: emptySet())) prefs[Keys.favorite(id)] = favorite
        }
    }

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

    /**
     * Drops workouts the phone's manifest no longer lists, and records that this watch now mirrors
     * a phone — the one-way switch that stops the bundled catalogue from being re-seeded.
     */
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
            prefs[Keys.librarySynced] = true
        }
    }

    // -------------------------------------------------------------------------------------------
    // Solo mode: the bundled catalogue
    // -------------------------------------------------------------------------------------------

    /**
     * Seeds the bundled catalogue **only** when this watch has never received a phone library and
     * has nothing stored. Returns the number of workouts written, or 0 when it left the store
     * alone. Safe to call on every launch.
     */
    suspend fun seedBundledWorkoutsIfNeeded(): Int {
        val prefs = context.workoutDataStore.data.first()
        if (prefs[Keys.librarySynced] == true) return 0
        if (!prefs[Keys.known].isNullOrEmpty()) return 0
        return seedBundledWorkouts()
    }

    /**
     * Writes the bundled catalogue, replacing any previous copy of the same ids. Used by the
     * empty-solo-library action, so a user who cleared the watch gets the starters back.
     */
    suspend fun seedBundledWorkouts(): Int {
        val templates = StarterWorkouts.templates
        context.workoutDataStore.edit { prefs ->
            val known = prefs[Keys.known] ?: emptySet()
            val seeded = mutableSetOf<String>()
            for (template in templates) {
                val id = template.plan.id
                seeded += id
                prefs[Keys.plan(id)] = SyncJson.encodePlan(template.plan)
                // 0 marks "bundled on the watch, not delivered by a phone".
                prefs[Keys.updated(id)] = 0L
                prefs[Keys.favorite(id)] = false
            }
            prefs[Keys.known] = known + seeded
        }
        return templates.size
    }

    suspend fun plan(id: String): WorkoutPlan? {
        val prefs = context.workoutDataStore.data.first()
        return prefs[Keys.plan(id)]?.let { runCatching { SyncJson.decodePlan(it) }.getOrNull() }
    }

    /** Forgets everything, including the fact that a phone ever synced. */
    suspend fun clear() {
        context.workoutDataStore.edit { it.clear() }
    }
}
