package com.pulse.intervalcoach

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import com.pulse.engine.EngineLimits
import com.pulse.intervalcoach.audio.CueGate
import com.pulse.intervalcoach.audio.CueSoundPlayer
import com.pulse.intervalcoach.audio.Haptics
import com.pulse.intervalcoach.audio.SpeechCoach
import com.pulse.intervalcoach.data.AudioAssetRepository
import com.pulse.intervalcoach.data.PreferencesRepository
import com.pulse.intervalcoach.data.ProgramRepository
import com.pulse.intervalcoach.data.ReminderRepository
import com.pulse.intervalcoach.data.SessionRepository
import com.pulse.intervalcoach.data.VoiceProfileRepository
import com.pulse.intervalcoach.data.WorkoutRepository
import com.pulse.intervalcoach.data.db.PulseDatabase
import com.pulse.intervalcoach.data.db.PulseMigrations
import com.pulse.intervalcoach.health.HealthHub
import com.pulse.intervalcoach.session.MusicController
import com.pulse.intervalcoach.session.Notifications
import com.pulse.intervalcoach.session.SessionController
import com.pulse.intervalcoach.work.BackupWorker
import com.pulse.intervalcoach.work.ReminderWorker
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
    val audioAssets: AudioAssetRepository by lazy { AudioAssetRepository(database) }
    val reminders: ReminderRepository by lazy { ReminderRepository(database) }
    val programs: ProgramRepository by lazy { ProgramRepository(database) }

    val health: HealthHub by lazy { HealthHub(app, preferences) }

    val cueSoundPlayer: CueSoundPlayer by lazy { CueSoundPlayer(app) }
    val haptics: Haptics by lazy { Haptics(app) }
    val speech: SpeechCoach by lazy { SpeechCoach(app, CueGate(nowMillis = { SystemClock.elapsedRealtime() })) }
    val music: MusicController by lazy { MusicController(app, preferences, appScope) }

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
            limits = EngineLimits(),
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
            runCatching {
                container.workouts.seedStarterWorkoutsIfNeeded()
                container.voiceProfiles.ensureDefault()
                container.sessions.recoverInterruptedSessions()
            }
            runCatching {
                BackupWorker.schedule(this@PulseApp, container)
                ReminderWorker.schedule(this@PulseApp)
            }
            // First look at the health platforms (Health Connect / Google Fit). All of this is
            // read-only and cheap; without the Health Connect app it simply reports unavailable.
            runCatching { container.health.refresh() }
        }
    }
}

/** Convenience accessor used by services, receivers and widgets. */
val Context.pulseContainer: AppContainer
    get() = (applicationContext as PulseApp).container
