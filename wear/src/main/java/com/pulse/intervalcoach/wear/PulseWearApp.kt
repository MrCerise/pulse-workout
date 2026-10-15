package com.pulse.intervalcoach.wear

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.pulse.engine.MonotonicClock
import com.pulse.intervalcoach.wear.session.AndroidWatchCues
import com.pulse.intervalcoach.wear.session.WatchCues
import com.pulse.intervalcoach.wear.session.WatchSessionController
import com.pulse.intervalcoach.wear.sync.WearSyncLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    val cues: WatchCues by lazy { AndroidWatchCues(app) }

    val wearSync: WearSyncLink by lazy { WearSyncLink(app, appScope, preferences, workouts) }

    val session: WatchSessionController by lazy {
        WatchSessionController(
            scope = appScope,
            clock = WatchClock,
            cues = cues,
            publisher = wearSync,
            preferences = preferences.flow,
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
