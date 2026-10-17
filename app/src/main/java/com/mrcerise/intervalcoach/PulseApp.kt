package com.mrcerise.intervalcoach

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import com.pulse.engine.EngineLimits
import com.mrcerise.intervalcoach.audio.CueGate
import com.mrcerise.intervalcoach.audio.CueSoundPlayer
import com.mrcerise.intervalcoach.audio.Haptics
import com.mrcerise.intervalcoach.audio.SpeechCoach
import com.mrcerise.intervalcoach.data.AudioAssetRepository
import com.mrcerise.intervalcoach.data.PreferencesRepository
import com.mrcerise.intervalcoach.data.ProgramRepository
import com.mrcerise.intervalcoach.data.ReminderRepository
import com.mrcerise.intervalcoach.data.SessionRepository
import com.mrcerise.intervalcoach.data.VoiceProfileRepository
import com.mrcerise.intervalcoach.data.WorkoutRepository
import com.mrcerise.intervalcoach.data.db.PulseDatabase
import com.mrcerise.intervalcoach.data.db.PulseMigrations
import com.mrcerise.intervalcoach.health.HealthHub
import com.mrcerise.intervalcoach.locale.AppLocale
import com.mrcerise.intervalcoach.session.MusicController
import com.mrcerise.intervalcoach.session.Notifications
import com.mrcerise.intervalcoach.session.RunTracker
import com.mrcerise.intervalcoach.session.SessionController
import com.mrcerise.intervalcoach.wear.WearSyncManager
import com.mrcerise.intervalcoach.work.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency container.
 *
 * The app has a small, fixed object graph, so an explicit container is clearer (and cheaper) than a
 * DI framework. Everything is `lazy` — nothing touches disk until a screen asks for it.
 */
class AppContainer(private val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: PulseDatabase by lazy {
        Room.databaseBuilder(app, PulseDatabase::class.java, PulseDatabase.NAME)
            .addMigrations(*PulseMigrations.ALL)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    val preferences: PreferencesRepository by lazy { PreferencesRepository(app) }
    val workouts: WorkoutRepository by lazy { WorkoutRepository(database) }
    val sessions: SessionRepository by lazy { SessionRepository(database) }
    val voiceProfiles: VoiceProfileRepository by lazy { VoiceProfileRepository(database) }
    val audioAssets: AudioAssetRepository by lazy { AudioAssetRepository(database, java.io.File(app.filesDir, "recordings")) }
    val reminders: ReminderRepository by lazy { ReminderRepository(database) }
    val programs: ProgramRepository by lazy { ProgramRepository(database) }

    val health: HealthHub by lazy { HealthHub(app, preferences) }

    val cueSoundPlayer: CueSoundPlayer by lazy { CueSoundPlayer(app) }
    val haptics: Haptics by lazy { Haptics(app) }
    val speech: SpeechCoach by lazy { SpeechCoach(app, CueGate(nowMillis = { SystemClock.elapsedRealtime() })) }
    val music: MusicController by lazy {
        MusicController(app, preferences, appScope).also { controller ->
            // Voice Studio previews work even before a session controller has been created.
            speech.onSpeechStarted = controller::onSpeechStarted
            speech.onSpeechFinished = controller::onSpeechFinished
            appScope.launch { preferences.flow.collect { controller.configure(it) } }
        }
    }

    /** The running system's GPS tracker, shared by the session and the player. */
    val runTracker: RunTracker by lazy { RunTracker(app, appScope) }

    val sessionController: SessionController by lazy {
        SessionController(
            context = app,
            scope = appScope,
            sessionRepository = sessions,
            workoutRepository = workouts,
            prefs = preferences,
            soundPlayer = cueSoundPlayer,
            speech = speech,
            haptics = haptics,
            music = music,
            runTracker = runTracker,
            limits = EngineLimits(),
        )
    }

    /** The phone half of the Wear OS sync link; started once in [PulseApp.onCreate]. */
    val wearSync: WearSyncManager by lazy {
        WearSyncManager(
            context = app,
            scope = appScope,
            workouts = workouts,
            sessions = sessions,
            preferences = preferences,
            sessionController = sessionController,
            health = health,
        )
    }
}

class PulseApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // First-run housekeeping happens off the main thread and is idempotent.
        container.appScope.launch {
            // The app's own preference is the record of the chosen language; the platform's per-app
            // locale store is a copy of it that Android's backup restores separately, so the two can
            // disagree after a restore onto a new device — screens in one language, widgets and
            // notifications in another. Re-applying it here costs nothing once the value already
            // matches, and it is what makes a restored choice complete.
            runCatching {
                AppLocale.applySystemLocales(this@PulseApp, container.preferences.current().appLanguage)
            }
            runCatching {
                container.workouts.seedStarterWorkoutsIfNeeded()
                container.voiceProfiles.ensureDefault()
                container.sessions.recoverInterruptedSessions()
            }
            runCatching { ReminderWorker.schedule(this@PulseApp) }
            // First look at Health Connect. All of this is read-only and cheap; without the
            // Health Connect app it simply reports unavailable.
            runCatching { container.health.refresh() }
        }
    }
}

/** Convenience accessor used by services, receivers and widgets. */
val Context.pulseContainer: AppContainer
    get() = (applicationContext as PulseApp).container
