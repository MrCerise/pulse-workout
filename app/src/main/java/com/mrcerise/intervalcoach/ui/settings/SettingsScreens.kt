package com.mrcerise.intervalcoach.ui.settings

import com.mrcerise.intervalcoach.locale.soundLabel
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.SoundCue
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.BuildConfig
import com.mrcerise.intervalcoach.PrivacyPolicyActivity
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.audio.DuckPhase
import com.mrcerise.intervalcoach.audio.SpeechAvailability
import com.mrcerise.intervalcoach.data.AppLanguage
import com.mrcerise.intervalcoach.data.AudioInterruptionBehavior
import com.mrcerise.intervalcoach.data.HeadphoneBehavior
import com.mrcerise.intervalcoach.data.PlayerDensity
import com.mrcerise.intervalcoach.data.VoiceVerbosity
import com.mrcerise.intervalcoach.data.WeightUnit
import com.mrcerise.intervalcoach.data.db.AudioAssetEntity
import com.mrcerise.intervalcoach.locale.AppLocale
import com.mrcerise.intervalcoach.ui.components.ConfirmDialog
import com.mrcerise.intervalcoach.ui.components.DangerActionButton
import com.mrcerise.intervalcoach.ui.components.DurationStepper
import com.mrcerise.intervalcoach.ui.components.NeutralChip
import com.mrcerise.intervalcoach.ui.components.NumberStepper
import com.mrcerise.intervalcoach.ui.components.OptionRow
import com.mrcerise.intervalcoach.ui.components.PulseActionMenu
import com.mrcerise.intervalcoach.ui.components.PulseIconButton
import com.mrcerise.intervalcoach.ui.components.PulseMenuAction
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.PulseCard
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.PulseSliderRow
import com.mrcerise.intervalcoach.ui.components.SecondaryActionButton
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.mrcerise.intervalcoach.ui.components.ToggleRow
import com.mrcerise.intervalcoach.ui.components.ThemePicker
import com.mrcerise.intervalcoach.ui.components.LanguagePicker
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.ThemeMode
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenVoiceStudio: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWelcome: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val strings = LocalContext.current.resources
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val colors = LocalPulseColors.current
    val scope = rememberCoroutineScope()
    val healthSnapshot by container.health.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDeleteSessions by remember { mutableStateOf(false) }
    val current = prefs

    PulseScaffold { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = LocalPulseDimens.current.pagePadding,
                end = LocalPulseDimens.current.pagePadding,
                top = LocalPulseDimens.current.cardGap,
                bottom = LocalPulseDimens.current.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.cardGap),
        ) {
            item {
                PulsePageHeader(
                    title = stringResource(R.string.nav_settings),
                )
            }

            if (current == null) return@LazyColumn

            // --- Health sync (the real thing now) ---
            item { SectionHeader(strings.getString(R.string.nav_health)) }
            item {
                PulseCard(onClick = onOpenHealth) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = if (healthSnapshot.source != null) colors.work else colors.textSecondary,
                                modifier = Modifier.padding(end = LocalPulseDimens.current.m),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_health_title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            }
                            healthSnapshot.source?.let { NeutralChip(it) }
                        }
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        Text(
                            when {
                                healthSnapshot.source == null -> strings.getString(R.string.health_disconnected)
                                else -> if (healthSnapshot.lastSyncAt > 0) strings.getString(R.string.health_last_sync, formatSync(healthSnapshot.lastSyncAt)) else strings.getString(R.string.health_not_synced)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(LocalPulseDimens.current.m))
                        ToggleRow(
                            label = strings.getString(R.string.settings_sync_workouts),
                            checked = current.healthSyncSessions,
                            onCheckedChange = { scope.launch { container.preferences.setHealthSyncSessions(it) } },
                        )
                    }
                }
            }

            item { SectionHeader(strings.getString(R.string.settings_appearance)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.m)) {
                    ThemePicker(
                        selected = current.themeMode,
                        onSelect = { mode -> scope.launch { container.preferences.setTheme(mode) } },
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_dynamic_color),
                        checked = current.dynamicColor,
                        onCheckedChange = { scope.launch { container.preferences.setDynamicColor(it) } },
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_reduced_motion),
                        checked = current.reducedMotion,
                        onCheckedChange = { scope.launch { container.preferences.setReducedMotion(it) } },
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_high_contrast),
                        checked = current.highContrast,
                        onCheckedChange = { scope.launch { container.preferences.setHighContrast(it) } },
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_left_handed),
                        checked = current.leftHandedPlayer,
                        onCheckedChange = { scope.launch { container.preferences.setLeftHanded(it) } },
                    )
                    OptionRow(
                        label = stringResource(R.string.settings_player_density),
                        options = PlayerDensity.entries.map { it to it.label() },
                        selected = current.playerDensity,
                        onSelect = { density -> scope.launch { container.preferences.setPlayerDensity(density) } },
                    )

                }
            }

            // Language gets its own section: it is about content, not chrome, so it sat oddly
            // inside Appearance — and its picker deserves to be findable in one glance.
            item { SectionHeader(strings.getString(R.string.settings_language)) }
            item {
                Column {
                    LanguagePicker(
                        selected = current.appLanguage,
                        onSelect = { language ->
                            // The app scope, not a scope tied to this composition: the platform
                            // applies the locale as a configuration change, and a coroutine
                            // cancelled mid-tap would leave the preference unwritten while the
                            // screens below had already moved.
                            container.appScope.launch {
                                container.preferences.setAppLanguage(language)
                                AppLocale.applySystemLocales(context.applicationContext, language)
                            }
                        },
                    )
                }
            }

            item { SectionHeader(strings.getString(R.string.settings_defaults)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.m)) {
                    DurationStepper(strings.getString(R.string.settings_default_work), current.defaultWorkMillis) { scope.launch { container.preferences.setDefaultWork(it) } }
                    DurationStepper(strings.getString(R.string.settings_default_rest), current.defaultRestMillis) { scope.launch { container.preferences.setDefaultRest(it) } }
                    NumberStepper(strings.getString(R.string.settings_default_rounds), current.defaultRounds, onChange = { scope.launch { container.preferences.setDefaultRounds(it) } })
                    DurationStepper(strings.getString(R.string.settings_default_preparation), current.defaultPreparationMillis) { scope.launch { container.preferences.setDefaultPreparation(it) } }
                    ToggleRow(
                        label = strings.getString(R.string.settings_final_rest),
                        checked = current.defaultIncludeFinalRest,
                        onCheckedChange = { scope.launch { container.preferences.setDefaultIncludeFinalRest(it) } },
                    )
                    OptionRow(
                        label = stringResource(R.string.settings_units),
                        options = WeightUnit.entries.map { it to it.label() },
                        selected = current.weightUnit,
                        onSelect = { unit -> scope.launch { container.preferences.setWeightUnit(unit) } },
                    )

                }
            }

            item { SectionHeader(strings.getString(R.string.settings_behaviour)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.m)) {
                    ToggleRow(
                        label = strings.getString(R.string.settings_keep_screen_on),
                        checked = current.keepScreenOn,
                        onCheckedChange = { scope.launch { container.preferences.setKeepScreenOn(it) } },
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_screen_off_cues),
                        checked = current.continueCuesScreenOff,
                        onCheckedChange = { scope.launch { container.preferences.setContinueCuesScreenOff(it) } },
                    )
                    OptionRow(
                        label = strings.getString(R.string.settings_audio_interruption),
                        options = AudioInterruptionBehavior.entries.map { it to it.label() },
                        selected = current.audioInterruptionBehavior,
                        onSelect = { value -> scope.launch { container.preferences.setAudioInterruption(value) } },
                    )
                    OptionRow(
                        label = stringResource(R.string.settings_headphone_unplug),
                        options = HeadphoneBehavior.entries.map { it to it.label() },
                        selected = current.headphoneBehavior,
                        onSelect = { value -> scope.launch { container.preferences.setHeadphoneBehavior(value) } },
                    )
                    Spacer(Modifier.height(LocalPulseDimens.current.xxl))
                    SecondaryActionButton(
                        text = strings.getString(R.string.voice_title),
                        onClick = onOpenVoiceStudio,
                        icon = Icons.Filled.VolumeDown,
                        modifier = Modifier.fillMaxWidth(),
                    )

                }
            }

            item { SectionHeader(strings.getString(R.string.settings_help_about)) }
            item {
                Column {
                    SecondaryActionButton(strings.getString(R.string.help_title), onOpenHelp, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    SecondaryActionButton(strings.getString(R.string.settings_welcome_tour), onOpenWelcome, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    SecondaryActionButton(
                        stringResource(R.string.privacy_policy_title),
                        { context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)) },
                        Modifier.fillMaxWidth(),
                    )

                }
            }

            item { SectionHeader(strings.getString(R.string.settings_danger)) }
            item {
                DangerActionButton(strings.getString(R.string.settings_delete_all), { confirmDeleteSessions = true }, Modifier.fillMaxWidth())
            }

            item {
                Text(
                    text = stringResource(R.string.privacy_medical_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            // Build identity remains available at the foot of Settings.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textDisabled,
                    )
                }
            }
        }
    }

    if (confirmDeleteSessions) {
        ConfirmDialog(
            title = strings.getString(R.string.settings_delete_title),
            body = strings.getString(R.string.settings_delete_all_confirm),
            confirmLabel = strings.getString(R.string.settings_delete_confirm),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmDeleteSessions = false
                scope.launch { container.sessions.deleteAll() }
            },
            onDismiss = { confirmDeleteSessions = false },
        )
    }
}

@Composable
private fun formatSync(millis: Long): String {
    val strings = LocalContext.current.resources
    val then = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    return if (then.toLocalDate() == LocalDateTime.now(ZoneId.systemDefault()).toLocalDate()) {
        strings.getString(R.string.date_today_time, then.format(DateTimeFormatter.ofPattern("HH:mm", strings.configuration.locales[0])))
    } else {
        then.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", strings.configuration.locales[0]))
    }
}

/**
 * Language names are written the way each language writes its own — a German speaker finds
 * "Deutsch", not "German", in a list of languages. Only the "follow the device" row is a sentence,
 * so it is the only one that is translated.
 */
@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.FRENCH -> "Français"
    AppLanguage.GERMAN -> "Deutsch"
    AppLanguage.SPANISH -> "Español"
    AppLanguage.ITALIAN -> "Italiano"
    else -> language.nativeName
}

@Composable
private fun PlayerDensity.label(): String = when (this) {
    PlayerDensity.COMPACT -> stringResource(R.string.density_compact)
    PlayerDensity.STANDARD -> stringResource(R.string.density_standard)
    PlayerDensity.LARGE -> stringResource(R.string.density_large)
}

private fun WeightUnit.label(): String = when (this) {
    WeightUnit.KG -> "kg"
    WeightUnit.LB -> "lb"
}

@Composable
private fun AudioInterruptionBehavior.label(): String = when (this) {
    AudioInterruptionBehavior.PAUSE -> stringResource(R.string.notif_action_pause)
    AudioInterruptionBehavior.KEEP_TIMING_MUTE_CUES -> stringResource(R.string.audio_interrupt_continue)
    AudioInterruptionBehavior.KEEP_TIMING_DUCK -> stringResource(R.string.audio_interrupt_duck)
}

@Composable
private fun HeadphoneBehavior.label(): String = when (this) {
    HeadphoneBehavior.PAUSE -> stringResource(R.string.notif_action_pause)
    HeadphoneBehavior.KEEP_GOING -> stringResource(R.string.headphone_continue)
}

// ---------------------------------------------------------------------------------------------
// Voice & audio studio
// ---------------------------------------------------------------------------------------------

@Composable
fun VoiceStudioScreen(container: AppContainer, onBack: () -> Unit) {
    val strings = LocalContext.current.resources
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val speechInfo by container.speech.info.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assets by container.audioAssets.assets.collectAsStateWithLifecycle(initialValue = emptyList())
    val musicPlaying by container.music.playing.collectAsStateWithLifecycle()
    val musicUnavailable by container.music.unavailable.collectAsStateWithLifecycle()
    val duckState by container.music.duckState.collectAsStateWithLifecycle()
    var recording by remember { mutableStateOf<ActiveRecording?>(null) }
    /** True when *this screen* started the background audio, so leaving stops only the preview. */
    var previewingHere by remember { mutableStateOf(false) }
    val current = prefs

    // A preview started here must not outlive the screen — but a workout's own audio must survive it.
    DisposableEffect(Unit) {
        onDispose { if (previewingHere) container.music.stop() }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startRecording(context, container) { recorder -> recording = recorder }
        }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                container.preferences.setMusic(uri.toString())
            }
        }
    }

    PulseScaffold { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = LocalPulseDimens.current.pagePadding,
                end = LocalPulseDimens.current.pagePadding,
                top = LocalPulseDimens.current.cardGap,
                bottom = LocalPulseDimens.current.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.cardGap),
        ) {
            item {
                PulsePageHeader(
                    title = strings.getString(R.string.voice_title),
                    onBack = onBack,
                )
            }

            if (current == null) return@LazyColumn

            item { SectionHeader(strings.getString(R.string.voice_engine)) }
            item {
                Column {
                    val availability = speechInfo.availability
                    Text(
                        when (availability) {
                            is SpeechAvailability.Ready -> strings.getString(R.string.voice_ready)
                            is SpeechAvailability.Initialising -> strings.getString(R.string.voice_checking)
                            is SpeechAvailability.NoEngine -> strings.getString(R.string.voice_engine_missing)
                            is SpeechAvailability.MissingLanguageData -> strings.getString(R.string.voice_missing_data, availability.languageTag)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (availability is SpeechAvailability.Ready) colors.work else colors.prepare,
                    )
                    Spacer(Modifier.height(LocalPulseDimens.current.xs))
                    speechInfo.engineLabel?.let {
                        Text(strings.getString(R.string.voice_engine_name, it), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                    Text(
                        when {
                            speechInfo.offlineVoiceInstalled -> strings.getString(R.string.voice_offline_installed)
                            availability is SpeechAvailability.NoEngine -> strings.getString(R.string.voice_none_installed)
                            else -> strings.getString(R.string.voice_install_offline)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(LocalPulseDimens.current.m))
                    PulseActionMenu(
                        text = strings.getString(R.string.voice_actions),
                        items = listOf(
                            PulseMenuAction(strings.getString(R.string.voice_settings)) { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) },
                            PulseMenuAction(strings.getString(R.string.voice_preview_label)) { container.speech.speakPreview(strings.getString(R.string.voice_test_line)) },
                        ),
                    )

                }
            }

            item { SectionHeader(strings.getString(R.string.voice_coaching_style)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.m)) {
                    PulseSliderRow(
                        label = strings.getString(R.string.voice_rate),
                        value = current.voiceRate,
                        valueRange = 0.5f..2.0f,
                        valueLabel = "${"%.2f".format(current.voiceRate)}×",
                        onValueChange = { scope.launch { container.preferences.setVoiceRate(it) } },
                        onValueChangeFinished = { container.speech.rate = current.voiceRate },
                    )
                    PulseSliderRow(
                        label = strings.getString(R.string.voice_pitch),
                        value = current.voicePitch,
                        valueRange = 0.5f..2.0f,
                        valueLabel = "${"%.2f".format(current.voicePitch)}×",
                        onValueChange = { scope.launch { container.preferences.setVoicePitch(it) } },
                        onValueChangeFinished = { container.speech.pitch = current.voicePitch },
                    )
                    OptionRow(
                        label = stringResource(R.string.voice_verbosity),
                        options = VoiceVerbosity.entries.map { it to it.label() },
                        selected = current.verbosity,
                        onSelect = { verbosity ->
                            scope.launch {
                                container.preferences.setVerbosity(verbosity)
                                container.speech.verbose = verbosity
                            }
                        },
                    )
                    ToggleRow(strings.getString(R.string.voice_countdown), current.spokenCountdown) { scope.launch { container.preferences.setSpokenCountdown(it) } }
                    ToggleRow(strings.getString(R.string.voice_halfway), current.halfwayAnnouncements) { scope.launch { container.preferences.setHalfway(it) } }
                    ToggleRow(strings.getString(R.string.voice_announce_next), current.announceNext) { scope.launch { container.preferences.setAnnounceNext(it) } }
                    ToggleRow(strings.getString(R.string.builder_haptic), current.vibrationEnabled) { scope.launch { container.preferences.setVibration(it) } }

                }
            }

            item { SectionHeader(strings.getString(R.string.voice_sounds)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.m)) {
                    PulseSliderRow(
                        label = strings.getString(R.string.voice_sound_volume),
                        value = current.cueVolume,
                        valueRange = 0f..1f,
                        valueLabel = "${(current.cueVolume * 100).toInt()}%",
                        onValueChange = { scope.launch { container.preferences.setCueVolume(it) } },
                        onValueChangeFinished = { container.cueSoundPlayer.volume = current.cueVolume },
                    )
                    PulseActionMenu(
                        text = strings.getString(R.string.voice_sound_preview),
                        items = listOf(SoundCue.BEEP, SoundCue.BELL, SoundCue.CHIME).map { cue ->
                            PulseMenuAction(strings.soundLabel(cue)) {
                                container.cueSoundPlayer.volume = current.cueVolume
                                container.cueSoundPlayer.play(cue)
                            }
                        },
                    )
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    if (!container.haptics.isAvailable) {
                        Text(strings.getString(R.string.voice_no_vibration), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }

                }
            }

            item { SectionHeader(stringResource(R.string.voice_music_title)) }
            item {
                Column {
                    Text(
                        current.musicUri?.let { strings.getString(R.string.voice_selected, it.substringAfterLast('/')) }
                            ?: stringResource(R.string.voice_music_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    musicUnavailable?.let { reason ->
                        Spacer(Modifier.height(LocalPulseDimens.current.xs))
                        Text(
                            stringResource(R.string.voice_music_unavailable, reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.destructive,
                        )
                    }
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    PulseSliderRow(
                        label = stringResource(R.string.voice_music_volume),
                        value = current.musicVolume,
                        valueRange = 0f..1f,
                        valueLabel = (current.musicVolume * 100).toInt().toString() + "%",
                        onValueChange = { scope.launch { container.preferences.setMusicVolume(it) } },
                        onValueChangeFinished = { container.music.setVolume(current.musicVolume) },
                    )
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    PulseActionMenu(
                        text = stringResource(R.string.voice_music_title),
                        items = listOf(
                            PulseMenuAction(stringResource(R.string.voice_music_pick)) { musicPicker.launch(arrayOf("audio/*")) },
                            PulseMenuAction(stringResource(R.string.voice_music_clear)) {
                                container.music.stop()
                                previewingHere = false
                                scope.launch { container.preferences.setMusic(null) }
                            },
                        ),
                    )
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    SecondaryActionButton(
                        text = if (musicPlaying) {
                            stringResource(R.string.voice_music_stop)
                        } else {
                            stringResource(R.string.voice_music_play)
                        },
                        onClick = {
                            if (musicPlaying) {
                                container.music.stop()
                                previewingHere = false
                            } else {
                                container.music.start()
                                previewingHere = true
                            }
                        },
                        enabled = current.musicUri != null,
                        modifier = Modifier.fillMaxWidth(),
                    )

                }
            }

            item { SectionHeader(stringResource(R.string.voice_duck_title)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.m)) {
                    ToggleRow(
                        label = stringResource(R.string.voice_duck_music),
                        hint = stringResource(R.string.voice_duck_hint),
                        checked = current.duckMusicDuringCues,
                        onCheckedChange = { enabled -> scope.launch { container.preferences.setDuckMusic(enabled) } },
                    )
                    PulseSliderRow(
                        label = stringResource(R.string.voice_duck_level),
                        value = current.duckLevel,
                        valueRange = 0f..1f,
                        valueLabel = duckLevelLabel(current.duckLevel),
                        onValueChange = { scope.launch { container.preferences.setDuckLevel(it) } },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeutralChip(
                            text = if (duckState.isDucked) {
                                stringResource(R.string.voice_duck_active)
                            } else {
                                stringResource(R.string.voice_duck_idle)
                            },
                            icon = Icons.Filled.VolumeDown,
                        )
                        Spacer(Modifier.width(LocalPulseDimens.current.s))
                        Text(
                            stringResource(R.string.voice_duck_phase, duckState.phase.label()),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    // Hoisted: stringResource is a composable call and onClick is not a
                    // composable lambda, so the sample line has to be read out here.
                    val duckTestLine = stringResource(R.string.voice_test_line)
                    SecondaryActionButton(
                        text = stringResource(R.string.voice_duck_test),
                        onClick = {
                            if (!container.speech.speakPreview(duckTestLine)) {
                                container.music.previewDuck()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                }
            }

            item { SectionHeader(strings.getString(R.string.voice_recording_title)) }
            item {
                Text(
                    stringResource(R.string.privacy_microphone_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            item {
                Column {
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    if (recording == null) {
                        SecondaryActionButton(
                            text = strings.getString(R.string.voice_record_cue),
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) startRecording(context, container) { recording = it } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        PrimaryActionButton(
                            text = stringResource(R.string.voice_record_stop),
                            onClick = {
                                stopRecording(recording, container)
                                recording = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (assets.isNotEmpty()) {
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        assets.forEach { asset ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(asset.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                    Text(
                                        if (asset.missingDetectedAt != null) strings.getString(R.string.voice_file_missing) else strings.getString(if (asset.source == "RECORDING") R.string.voice_source_recording else R.string.voice_source_import),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (asset.missingDetectedAt != null) colors.destructive else colors.textSecondary,
                                    )
                                }
                                PulseIconButton(
                                    icon = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tooltip = stringResource(R.string.action_delete),
                                    onClick = {
                                        scope.launch {
                                            if (!container.audioAssets.remove(asset.id)) {
                                                android.widget.Toast.makeText(
                                                    context, R.string.recording_delete_failed, android.widget.Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    },
                                    tint = colors.destructive,
                                )
                            }
                        }
                    }

                }
            }
        }
    }
}

/** A cue being recorded, with the file it is written to. */
private class ActiveRecording(val recorder: MediaRecorder, val file: File)

private fun startRecording(context: Context, container: AppContainer, onStarted: (ActiveRecording) -> Unit) {
    val dir = File(context.filesDir, "recordings").apply { mkdirs() }
    val file = File(dir, "cue-${System.currentTimeMillis()}.m4a")
    val recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOutputFile(file.absolutePath)
        prepare()
        start()
    }
    onStarted(ActiveRecording(recorder, file))
}

private fun stopRecording(recording: ActiveRecording?, container: AppContainer) {
    val active = recording ?: return
    val output = runCatching { active.recorder.stop() }
    runCatching { active.recorder.release() }
    output.onSuccess {
        val file = active.file
        container.appScope.launch {
            container.audioAssets.add(
                AudioAssetEntity(
                    id = file.nameWithoutExtension,
                    name = file.nameWithoutExtension,
                    uri = Uri.fromFile(file).toString(),
                    source = "RECORDING",
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }
}

/** Plain words for the duck multiplier: 30 % means the music sits at 30 % of its own volume. */
@Composable
private fun duckLevelLabel(level: Float): String = when {
    level <= 0.001f -> stringResource(R.string.voice_muted)
    level < 0.2f -> stringResource(R.string.duck_strong, (level * 100).toInt())
    level < 0.45f -> stringResource(R.string.duck_balanced, (level * 100).toInt())
    level < 0.8f -> stringResource(R.string.duck_subtle, (level * 100).toInt())
    else -> stringResource(R.string.duck_barely, (level * 100).toInt())
}

@Composable
private fun DuckPhase.label(): String = when (this) {
    DuckPhase.NONE -> stringResource(R.string.duck_full)
    DuckPhase.FADING_DOWN -> stringResource(R.string.duck_lowering)
    DuckPhase.HELD -> stringResource(R.string.duck_lowered)
    DuckPhase.FADING_UP -> stringResource(R.string.duck_restoring)
}

@Composable
private fun VoiceVerbosity.label(): String = when (this) {
    VoiceVerbosity.MINIMAL -> stringResource(R.string.voice_verbosity_minimal)
    VoiceVerbosity.STANDARD -> stringResource(R.string.voice_verbosity_standard)
    VoiceVerbosity.CHATTY -> stringResource(R.string.voice_verbosity_chatty)
}

// ---------------------------------------------------------------------------------------------
// Help
// ---------------------------------------------------------------------------------------------

@Composable
fun HelpScreen(container: AppContainer, onBack: () -> Unit) {
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current

    PulseScaffold { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = LocalPulseDimens.current.pagePadding,
                end = LocalPulseDimens.current.pagePadding,
                top = LocalPulseDimens.current.cardGap,
                bottom = LocalPulseDimens.current.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.cardGap),
        ) {
            item {
                PulsePageHeader(
                    title = stringResource(R.string.help_title),
                    onBack = onBack,
                )
            }

            val sections = listOf(
                R.string.help_voice to R.string.help_voice_body,
                R.string.help_health to R.string.help_health_body,
                R.string.help_background to R.string.help_background_body,
                R.string.help_audio to R.string.help_audio_body,
                R.string.help_gestures to R.string.help_gestures_body,
                R.string.help_accessibility to R.string.help_accessibility_body,
                R.string.settings_permissions to R.string.help_permissions_body,
            )
            sections.forEach { (title, body) ->
                item { SectionHeader(stringResource(title)) }
                item {
                    PulseCard {
                        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
            }
            item {
                val helpContext = LocalContext.current
                SecondaryActionButton(
                    text = strings.getString(R.string.settings_open_android),
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + BuildConfig.APPLICATION_ID),
                        )
                        helpContext.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
