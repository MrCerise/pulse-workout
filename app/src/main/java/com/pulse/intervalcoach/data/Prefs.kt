package com.pulse.intervalcoach.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pulse.intervalcoach.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * User preferences, stored with DataStore.
 *
 * These are deliberately separate from workout data: a user can delete every session and backup
 * without losing their theme, and vice versa.
 */

enum class PlayerDensity { COMPACT, STANDARD, LARGE }
enum class WeightUnit { KG, LB }

/** What happens when another app takes audio focus (call, navigator, video). */
enum class AudioInterruptionBehavior { PAUSE, KEEP_TIMING_MUTE_CUES, KEEP_TIMING_DUCK }

/** What happens when the headphones are unplugged. */
enum class HeadphoneBehavior { PAUSE, KEEP_GOING }

/** How much the coach says. */
enum class VoiceVerbosity { MINIMAL, STANDARD, CHATTY }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val leftHandedPlayer: Boolean = false,
    val playerDensity: PlayerDensity = PlayerDensity.STANDARD,
    val defaultWorkMillis: Long = 40_000L,
    val defaultRestMillis: Long = 20_000L,
    val defaultRounds: Int = 8,
    val defaultPreparationMillis: Long = 10_000L,
    val defaultCooldownMillis: Long = 0L,
    val defaultIncludeFinalRest: Boolean = true,
    val defaultShuffle: Boolean = false,
    val voiceProfileId: String? = null,
    val voiceRate: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val verbosity: VoiceVerbosity = VoiceVerbosity.STANDARD,
    val spokenCountdown: Boolean = true,
    val halfwayAnnouncements: Boolean = true,
    val announceNext: Boolean = true,
    val cueVolume: Float = 0.8f,
    val vibrationEnabled: Boolean = true,
    val duckMusicDuringCues: Boolean = true,
    val keepScreenOn: Boolean = true,
    val continueCuesScreenOff: Boolean = true,
    val audioInterruptionBehavior: AudioInterruptionBehavior = AudioInterruptionBehavior.KEEP_TIMING_DUCK,
    val headphoneBehavior: HeadphoneBehavior = HeadphoneBehavior.PAUSE,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val countdownFromSeconds: Int = 3,
    val halfwayMinSeconds: Int = 60,
    val onboardingComplete: Boolean = false,
    val autoBackupEnabled: Boolean = true,
    val backupFolderUri: String? = null,
    val lastAutoBackupAt: Long = 0L,
    val musicUri: String? = null,
    val musicVolume: Float = 0.6f,
    val volumeKeysControlSession: Boolean = false,
    val showInstructorViewByDefault: Boolean = false,
    // --- Health & fitness integrations (v1.3) ---
    /** Master switch for writing finished sessions to Health / Google Fit. */
    val healthSyncSessions: Boolean = true,
    /** Last successful sync-to-Health moment, in epoch millis. */
    val healthLastSyncAt: Long = 0L,
    /** True after the user authorised the Google Fit scopes on this device. */
    val fitAuthorized: Boolean = false,
) {
    /** Warning thresholds derived from the "count down from N seconds" preference. */
    val countdownThresholds: List<Int>
        get() = (countdownFromSeconds downTo 1).toList()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val highContrast = booleanPreferencesKey("high_contrast")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val leftHanded = booleanPreferencesKey("left_handed_player")
        val density = stringPreferencesKey("player_density")
        val workMillis = longPreferencesKey("default_work_ms")
        val restMillis = longPreferencesKey("default_rest_ms")
        val rounds = intPreferencesKey("default_rounds")
        val prepMillis = longPreferencesKey("default_prep_ms")
        val cooldownMillis = longPreferencesKey("default_cooldown_ms")
        val includeFinalRest = booleanPreferencesKey("default_final_rest")
        val shuffle = booleanPreferencesKey("default_shuffle")
        val voiceProfileId = stringPreferencesKey("voice_profile_id")
        val voiceRate = floatPreferencesKey("voice_rate")
        val voicePitch = floatPreferencesKey("voice_pitch")
        val verbosity = stringPreferencesKey("verbosity")
        val spokenCountdown = booleanPreferencesKey("spoken_countdown")
        val halfway = booleanPreferencesKey("halfway")
        val announceNext = booleanPreferencesKey("announce_next")
        val cueVolume = floatPreferencesKey("cue_volume")
        val vibration = booleanPreferencesKey("vibration")
        val duck = booleanPreferencesKey("duck_music")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val cuesScreenOff = booleanPreferencesKey("cues_screen_off")
        val interruption = stringPreferencesKey("audio_interruption")
        val headphone = stringPreferencesKey("headphone_behavior")
        val weightUnit = stringPreferencesKey("weight_unit")
        val countdownFrom = intPreferencesKey("countdown_from_seconds")
        val halfwayMin = intPreferencesKey("halfway_min_seconds")
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val backupFolder = stringPreferencesKey("backup_folder_uri")
        val lastBackup = longPreferencesKey("last_auto_backup_at")
        val musicUri = stringPreferencesKey("music_uri")
        val musicVolume = floatPreferencesKey("music_volume")
        val volumeKeys = booleanPreferencesKey("volume_keys_control")
        val instructorView = booleanPreferencesKey("instructor_view_default")
        val healthSyncSessions = booleanPreferencesKey("health_sync_sessions")
        val healthLastSyncAt = longPreferencesKey("health_last_sync_at")
        val fitAuthorized = booleanPreferencesKey("fit_authorized")
    }

    val flow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.dynamicColor] ?: false,
            highContrast = prefs[Keys.highContrast] ?: false,
            reducedMotion = prefs[Keys.reducedMotion] ?: false,
            leftHandedPlayer = prefs[Keys.leftHanded] ?: false,
            playerDensity = prefs[Keys.density]?.let { runCatching { PlayerDensity.valueOf(it) }.getOrNull() } ?: PlayerDensity.STANDARD,
            defaultWorkMillis = prefs[Keys.workMillis] ?: 40_000L,
            defaultRestMillis = prefs[Keys.restMillis] ?: 20_000L,
            defaultRounds = prefs[Keys.rounds] ?: 8,
            defaultPreparationMillis = prefs[Keys.prepMillis] ?: 10_000L,
            defaultCooldownMillis = prefs[Keys.cooldownMillis] ?: 0L,
            defaultIncludeFinalRest = prefs[Keys.includeFinalRest] ?: true,
            defaultShuffle = prefs[Keys.shuffle] ?: false,
            voiceProfileId = prefs[Keys.voiceProfileId],
            voiceRate = prefs[Keys.voiceRate] ?: 1.0f,
            voicePitch = prefs[Keys.voicePitch] ?: 1.0f,
            verbosity = prefs[Keys.verbosity]?.let { runCatching { VoiceVerbosity.valueOf(it) }.getOrNull() } ?: VoiceVerbosity.STANDARD,
            spokenCountdown = prefs[Keys.spokenCountdown] ?: true,
            halfwayAnnouncements = prefs[Keys.halfway] ?: true,
            announceNext = prefs[Keys.announceNext] ?: true,
            cueVolume = prefs[Keys.cueVolume] ?: 0.8f,
            vibrationEnabled = prefs[Keys.vibration] ?: true,
            duckMusicDuringCues = prefs[Keys.duck] ?: true,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            continueCuesScreenOff = prefs[Keys.cuesScreenOff] ?: true,
            audioInterruptionBehavior = prefs[Keys.interruption]?.let { runCatching { AudioInterruptionBehavior.valueOf(it) }.getOrNull() }
                ?: AudioInterruptionBehavior.KEEP_TIMING_DUCK,
            headphoneBehavior = prefs[Keys.headphone]?.let { runCatching { HeadphoneBehavior.valueOf(it) }.getOrNull() }
                ?: HeadphoneBehavior.PAUSE,
            weightUnit = prefs[Keys.weightUnit]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG,
            countdownFromSeconds = prefs[Keys.countdownFrom] ?: 3,
            halfwayMinSeconds = prefs[Keys.halfwayMin] ?: 60,
            onboardingComplete = prefs[Keys.onboarding] ?: false,
            autoBackupEnabled = prefs[Keys.autoBackup] ?: true,
            backupFolderUri = prefs[Keys.backupFolder],
            lastAutoBackupAt = prefs[Keys.lastBackup] ?: 0L,
            musicUri = prefs[Keys.musicUri],
            musicVolume = prefs[Keys.musicVolume] ?: 0.6f,
            volumeKeysControlSession = prefs[Keys.volumeKeys] ?: false,
            showInstructorViewByDefault = prefs[Keys.instructorView] ?: false,
            healthSyncSessions = prefs[Keys.healthSyncSessions] ?: true,
            healthLastSyncAt = prefs[Keys.healthLastSyncAt] ?: 0L,
            fitAuthorized = prefs[Keys.fitAuthorized] ?: false,
        )
    }

    suspend fun current(): UserPreferences = flow.first()

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamicColor] = value }
    suspend fun setHighContrast(value: Boolean) = edit { it[Keys.highContrast] = value }
    suspend fun setReducedMotion(value: Boolean) = edit { it[Keys.reducedMotion] = value }
    suspend fun setLeftHanded(value: Boolean) = edit { it[Keys.leftHanded] = value }
    suspend fun setPlayerDensity(value: PlayerDensity) = edit { it[Keys.density] = value.name }
    suspend fun setDefaultWork(millis: Long) = edit { it[Keys.workMillis] = millis }
    suspend fun setDefaultRest(millis: Long) = edit { it[Keys.restMillis] = millis }
    suspend fun setDefaultRounds(rounds: Int) = edit { it[Keys.rounds] = rounds.coerceIn(1, 200) }
    suspend fun setDefaultPreparation(millis: Long) = edit { it[Keys.prepMillis] = millis }
    suspend fun setDefaultCooldown(millis: Long) = edit { it[Keys.cooldownMillis] = millis }
    suspend fun setDefaultIncludeFinalRest(value: Boolean) = edit { it[Keys.includeFinalRest] = value }
    suspend fun setDefaultShuffle(value: Boolean) = edit { it[Keys.shuffle] = value }
    suspend fun setVoiceProfile(id: String?) = edit { prefs ->
        if (id == null) prefs.remove(Keys.voiceProfileId) else prefs[Keys.voiceProfileId] = id
    }
    suspend fun setVoiceRate(value: Float) = edit { it[Keys.voiceRate] = value.coerceIn(0.5f, 2.0f) }
    suspend fun setVoicePitch(value: Float) = edit { it[Keys.voicePitch] = value.coerceIn(0.5f, 2.0f) }
    suspend fun setVerbosity(value: VoiceVerbosity) = edit { it[Keys.verbosity] = value.name }
    suspend fun setSpokenCountdown(value: Boolean) = edit { it[Keys.spokenCountdown] = value }
    suspend fun setHalfway(value: Boolean) = edit { it[Keys.halfway] = value }
    suspend fun setAnnounceNext(value: Boolean) = edit { it[Keys.announceNext] = value }
    suspend fun setCueVolume(value: Float) = edit { it[Keys.cueVolume] = value.coerceIn(0f, 1f) }
    suspend fun setVibration(value: Boolean) = edit { it[Keys.vibration] = value }
    suspend fun setDuckMusic(value: Boolean) = edit { it[Keys.duck] = value }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }
    suspend fun setContinueCuesScreenOff(value: Boolean) = edit { it[Keys.cuesScreenOff] = value }
    suspend fun setAudioInterruption(value: AudioInterruptionBehavior) = edit { it[Keys.interruption] = value.name }
    suspend fun setHeadphoneBehavior(value: HeadphoneBehavior) = edit { it[Keys.headphone] = value.name }
    suspend fun setWeightUnit(value: WeightUnit) = edit { it[Keys.weightUnit] = value.name }
    suspend fun setCountdownFrom(seconds: Int) = edit { it[Keys.countdownFrom] = seconds.coerceIn(0, 10) }
    suspend fun setHalfwayMin(seconds: Int) = edit { it[Keys.halfwayMin] = seconds.coerceIn(15, 600) }
    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboarding] = value }
    suspend fun setAutoBackup(value: Boolean) = edit { it[Keys.autoBackup] = value }
    suspend fun setBackupFolder(uri: String?) = edit { prefs ->
        if (uri == null) prefs.remove(Keys.backupFolder) else prefs[Keys.backupFolder] = uri
    }
    suspend fun setLastBackup(at: Long) = edit { it[Keys.lastBackup] = at }
    suspend fun setMusic(uri: String?, volume: Float? = null) = edit { prefs ->
        if (uri == null) prefs.remove(Keys.musicUri) else prefs[Keys.musicUri] = uri
        if (volume != null) prefs[Keys.musicVolume] = volume.coerceIn(0f, 1f)
    }
    suspend fun setMusicVolume(volume: Float) = edit { it[Keys.musicVolume] = volume.coerceIn(0f, 1f) }
    suspend fun setVolumeKeysControl(value: Boolean) = edit { it[Keys.volumeKeys] = value }
    suspend fun setInstructorViewDefault(value: Boolean) = edit { it[Keys.instructorView] = value }
    suspend fun setHealthSyncSessions(value: Boolean) = edit { it[Keys.healthSyncSessions] = value }
    suspend fun setHealthLastSyncAt(at: Long) = edit { it[Keys.healthLastSyncAt] = at }
    suspend fun setFitAuthorized(value: Boolean) = edit { it[Keys.fitAuthorized] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
