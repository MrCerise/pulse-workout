package com.mrcerise.intervalcoach.wear

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.pulse.engine.MonotonicClock
import com.mrcerise.intervalcoach.wear.history.WatchHistoryStore
import com.mrcerise.intervalcoach.wear.session.AndroidWatchCues
import com.mrcerise.intervalcoach.wear.session.AndroidWatchVoice
import com.mrcerise.intervalcoach.wear.session.WatchCues
import com.mrcerise.intervalcoach.wear.session.WatchSessionController
import com.mrcerise.intervalcoach.wear.session.WatchSessionPublisher
import com.mrcerise.intervalcoach.wear.session.WatchSessionRecorder
import com.mrcerise.intervalcoach.wear.sync.WearSyncLink
import com.mrcerise.intervalcoach.wear.tile.PulseTileService
import androidx.wear.tiles.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Real elapsed-time source on the watch, immune to wall-clock changes (same as the phone). */
object WatchClock : MonotonicClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

/**
 * Hand-rolled dependency container, mirroring the phone's `AppContainer`: a small, fixed object
 * graph where everything is lazy and nothing touches disk until a screen asks for it.
 */
class WearAppContainer(private val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences: WatchPreferencesRepository by lazy { WatchPreferencesRepository(app, appScope) }
    val workouts: WatchWorkoutStore by lazy { WatchWorkoutStore(app, appScope) }

    /** The watch's own record of what it ran, independent of whether a phone is ever there. */
    val history: WatchHistoryStore by lazy { WatchHistoryStore(app, appScope) }

    val cues: WatchCues by lazy { AndroidWatchCues(app) }

    val wearSync: WearSyncLink by lazy { WearSyncLink(app, appScope, preferences, workouts) }

    /** A finished session is written to the watch's history *and* handed to the phone link. */
    private val sessionRecorder: WatchSessionPublisher by lazy {
        WatchSessionRecorder(scope = appScope, history = history, link = wearSync)
    }

    val session: WatchSessionController by lazy {
        WatchSessionController(
            scope = appScope,
            clock = WatchClock,
            cues = cues,
            publisher = sessionRecorder,
            preferences = preferences.flow,
            voice = AndroidWatchVoice(app),
        )
    }

    val wakeLock: WatchSessionWakeLock by lazy { WatchSessionWakeLock(app) }
}

class PulseWearApp : Application() {

    val container: WearAppContainer by lazy { WearAppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Commands from the phone apply to the local session; wire before listening so an early
        // command cannot be missed.
        container.wearSync.onCommand = { command -> container.session.applyCommand(command) }
        container.wearSync.start()
        // A watch that has never been paired (or was just installed) gets the bundled workouts, so
        // the library is usable before any phone is involved. A no-op once a phone has synced.
        container.appScope.launch { container.workouts.seedBundledWorkoutsIfNeeded() }
        // The tile shows the library, the week and the running session: refresh it when any changes.
        container.appScope.launch {
            combine(
                container.preferences.flow,
                container.history.records,
                container.workouts.workouts,
                container.session.active.map { it?.plan?.id },
            ) { _, _, _, _ -> }
                .debounce(TILE_REFRESH_DEBOUNCE_MILLIS)
                .collect {
                    runCatching { TileService.getUpdater(this@PulseWearApp).requestUpdate(PulseTileService::class.java) }
                }
        }
    }

    private companion object {
        const val TILE_REFRESH_DEBOUNCE_MILLIS = 1_000L
    }
}

/** Convenience accessor used by activities. */
val Context.pulseWearContainer: WearAppContainer
    get() = (applicationContext as PulseWearApp).container

/**
 * A partial wake lock held while a session runs so timing survives the wrist turn. The lock is
 * bounded and re-acquired by the UI whenever activity continues.
 */
class WatchSessionWakeLock(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun set(active: Boolean) {
        if (active) acquire() else release()
    }

    private fun acquire() {
        val manager = powerManager ?: return
        if (wakeLock?.isHeld == true) return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulse:watch-session").apply {
            setReferenceCounted(false)
            acquire(TIMEOUT_MILLIS)
        }
    }

    private fun release() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    }

    private companion object {
        // Re-armed by the UI on every active frame; long enough to ride out a screen-off gap.
        const val TIMEOUT_MILLIS = 30L * 60L * 1000L
    }
}
