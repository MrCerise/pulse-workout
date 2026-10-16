package com.pulse.intervalcoach.ui.settings

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
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.BuildConfig
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.audio.DuckPhase
import com.pulse.intervalcoach.audio.SpeechAvailability
import com.pulse.intervalcoach.data.AppLanguage
import com.pulse.intervalcoach.data.AudioInterruptionBehavior
import com.pulse.intervalcoach.data.HeadphoneBehavior
import com.pulse.intervalcoach.data.PlayerDensity
import com.pulse.intervalcoach.data.VoiceVerbosity
import com.pulse.intervalcoach.data.WeightUnit
import com.pulse.intervalcoach.data.db.AudioAssetEntity
import com.pulse.intervalcoach.locale.AppLocale
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.DangerActionButton
import com.pulse.intervalcoach.ui.components.DurationStepper
import com.pulse.intervalcoach.ui.components.NeutralChip
import com.pulse.intervalcoach.ui.components.NumberStepper
import com.pulse.intervalcoach.ui.components.OptionRow
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.PulseSliderRow
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.ToggleRow
import com.pulse.intervalcoach.ui.components.ThemePicker
import com.pulse.intervalcoach.ui.components.LanguagePicker
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.ThemeMode
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
            item { SectionHeader("Health") }
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
                                healthSnapshot.source == null -> "Not connected"
                                else -> if (healthSnapshot.lastSyncAt > 0) "Last sync " + formatSync(healthSnapshot.lastSyncAt) else "Not synced yet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(LocalPulseDimens.current.m))
                        ToggleRow(
                            label = "Sync workouts",
                            checked = current.healthSyncSessions,
                            onCheckedChange = { scope.launch { container.preferences.setHealthSyncSessions(it) } },
                        )
                    }
                }
            }

            item { SectionHeader("Appearance") }
            item {
                PulseCard {
                    Column {
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
            }

            // Language gets its own section: it is about content, not chrome, so it sat oddly
            // inside Appearance — and its picker deserves to be findable in one glance.
            item { SectionHeader("Language") }
            item {
                PulseCard {
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
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        Text(
                            text = stringResource(R.string.settings_language_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            item { SectionHeader("Workout defaults") }
            item {
                PulseCard {
                    Column {
                        DurationStepper("Default work", current.defaultWorkMillis) { scope.launch { container.preferences.setDefaultWork(it) } }
                        DurationStepper("Default rest", current.defaultRestMillis) { scope.launch { container.preferences.setDefaultRest(it) } }
                        NumberStepper("Default rounds", current.defaultRounds, onChange = { scope.launch { container.preferences.setDefaultRounds(it) } })
                        DurationStepper("Default preparation", current.defaultPreparationMillis) { scope.launch { container.preferences.setDefaultPreparation(it) } }
                        ToggleRow(
                            label = "Include final rest by default",
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
            }

            item { SectionHeader("During workouts") }
            item {
                PulseCard {
                    Column {
                        ToggleRow(
                            label = "Keep the screen on",
                            checked = current.keepScreenOn,
                            onCheckedChange = { scope.launch { container.preferences.setKeepScreenOn(it) } },
                        )
                        ToggleRow(
                            label = stringResource(R.string.settings_screen_off_cues),
                            checked = current.continueCuesScreenOff,
                            onCheckedChange = { scope.launch { container.preferences.setContinueCuesScreenOff(it) } },
                        )
                        OptionRow(
                            label = "When audio is interrupted",
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
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        SecondaryActionButton(
                            text = "Voice & audio",
                            onClick = onOpenVoiceStudio,
                            icon = Icons.Filled.VolumeDown,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { SectionHeader("Help & about") }
            item {
                PulseCard {
                    Column {
                        SecondaryActionButton("Help", onOpenHelp, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        SecondaryActionButton("Welcome tour", onOpenWelcome, Modifier.fillMaxWidth())
                    }
                }
            }

            item { SectionHeader("Danger zone") }
            item {
                PulseCard {
                    DangerActionButton("Delete all sessions…", { confirmDeleteSessions = true }, Modifier.fillMaxWidth())
                }
            }

            // The build identity, always available and never more than a glance away: version and
            // the build number that produced it, bottom-right in the quietest ink on the page.
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
            title = "Delete all sessions?",
            body = "Session history will be permanently deleted. Workouts are kept.",
            confirmLabel = "Delete sessions",
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

private fun formatSync(millis: Long): String {
    val then = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    return if (then.toLocalDate() == LocalDateTime.now(ZoneId.systemDefault()).toLocalDate()) {
        "today " + then.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        then.format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
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

private fun PlayerDensity.label(): String = when (this) {
    PlayerDensity.COMPACT -> "Compact"
    PlayerDensity.STANDARD -> "Standard"
    PlayerDensity.LARGE -> "Large"
}

private fun WeightUnit.label(): String = when (this) {
    WeightUnit.KG -> "kg"
    WeightUnit.LB -> "lb"
}

private fun AudioInterruptionBehavior.label(): String = when (this) {
    AudioInterruptionBehavior.PAUSE -> "Pause"
    AudioInterruptionBehavior.KEEP_TIMING_MUTE_CUES -> "Keep timing, mute"
    AudioInterruptionBehavior.KEEP_TIMING_DUCK -> "Keep timing, duck"
}

private fun HeadphoneBehavior.label(): String = when (this) {
    HeadphoneBehavior.PAUSE -> "Pause"
    HeadphoneBehavior.KEEP_GOING -> "Keep going"
}

// ---------------------------------------------------------------------------------------------
// Voice & audio studio
// ---------------------------------------------------------------------------------------------

@Composable
fun VoiceStudioScreen(container: AppContainer, onBack: () -> Unit) {
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val speechInfo by container.speech.info.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assets by container.audioAssets.assets.collectAsStateWithLifecycle(initialValue = emptyList())
    val musicPlaying by container.music.playing.collectAsStateWithLifecycle()
    val musicUnavailable by container.music.unavailable.collectAsStateWithLifecycle()
    val duckState by container.music.duckState.collectAsStateWithLifecycle()
    var recording by remember { mutableStateOf<MediaRecorder?>(null) }
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
                    title = "Voice & audio",
                    onBack = onBack,
                )
            }

            if (current == null) return@LazyColumn

            item { SectionHeader("Speech engine") }
            item {
                PulseCard {
                    Column {
                        val availability = speechInfo.availability
                        Text(
                            when (availability) {
                                is SpeechAvailability.Ready -> "Speech engine ready"
                                is SpeechAvailability.Initialising -> "Checking speech engine…"
                                is SpeechAvailability.NoEngine -> "No speech engine available"
                                is SpeechAvailability.MissingLanguageData -> "No voice data for ${availability.languageTag}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (availability is SpeechAvailability.Ready) colors.work else colors.prepare,
                        )
                        Spacer(Modifier.height(LocalPulseDimens.current.xs))
                        speechInfo.engineLabel?.let {
                            Text("Engine: $it", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                        Text(
                            when {
                                speechInfo.offlineVoiceInstalled -> "Offline voice installed"
                                availability is SpeechAvailability.NoEngine -> "No voice installed. Sound cues will be used."
                                else -> "Install an offline voice for coaching without a connection."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(LocalPulseDimens.current.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.s)) {
                            SecondaryActionButton(
                                text = "Text-to-speech settings",
                                onClick = { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryActionButton(
                                text = "Preview voice",
                                onClick = { container.speech.speakPreview("Round one. Work, twenty seconds. Three, two, one, go.") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Coaching style") }
            item {
                PulseCard {
                    Column {
                        PulseSliderRow(
                            label = "Speaking rate",
                            value = current.voiceRate,
                            valueRange = 0.5f..2.0f,
                            valueLabel = "${"%.2f".format(current.voiceRate)}×",
                            onValueChange = { scope.launch { container.preferences.setVoiceRate(it) } },
                            onValueChangeFinished = { container.speech.rate = current.voiceRate },
                        )
                        PulseSliderRow(
                            label = "Pitch",
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
                        ToggleRow("Spoken 3-2-1 countdown", current.spokenCountdown) { scope.launch { container.preferences.setSpokenCountdown(it) } }
                        ToggleRow("Halfway announcements (1 min+)", current.halfwayAnnouncements) { scope.launch { container.preferences.setHalfway(it) } }
                        ToggleRow("Announce the next interval", current.announceNext) { scope.launch { container.preferences.setAnnounceNext(it) } }
                        ToggleRow("Vibration", current.vibrationEnabled) { scope.launch { container.preferences.setVibration(it) } }
                    }
                }
            }

            item { SectionHeader("Sound cues") }
            item {
                PulseCard {
                    Column {
                        PulseSliderRow(
                            label = "Cue volume",
                            value = current.cueVolume,
                            valueRange = 0f..1f,
                            valueLabel = "${(current.cueVolume * 100).toInt()}%",
                            onValueChange = { scope.launch { container.preferences.setCueVolume(it) } },
                            onValueChangeFinished = { container.cueSoundPlayer.volume = current.cueVolume },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.s)) {
                            listOf(SoundCue.BEEP, SoundCue.BELL, SoundCue.CHIME).forEach { cue ->
                                SecondaryActionButton(
                                    text = cue.name.lowercase().replaceFirstChar { it.uppercase() },
                                    onClick = {
                                        container.cueSoundPlayer.volume = current.cueVolume
                                        container.cueSoundPlayer.play(cue)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        if (!container.haptics.isAvailable) {
                            Text("Vibration unavailable", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.voice_music_title)) }
            item {
                PulseCard {
                    Column {
                        Text(
                            current.musicUri?.let { "Selected: " + it.substringAfterLast('/') }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.s)) {
                            SecondaryActionButton(
                                stringResource(R.string.voice_music_pick),
                                { musicPicker.launch(arrayOf("audio/*")) },
                                Modifier.weight(1f),
                            )
                            SecondaryActionButton(
                                stringResource(R.string.voice_music_clear),
                                {
                                    container.music.stop()
                                    previewingHere = false
                                    scope.launch { container.preferences.setMusic(null) }
                                },
                                Modifier.weight(0.6f),
                            )
                        }
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
            }

            item { SectionHeader(stringResource(R.string.voice_duck_title)) }
            item {
                PulseCard {
                    Column {
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
            }

            item { SectionHeader("Your own cue recordings") }
            item {
                PulseCard {
                    Column {
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        if (recording == null) {
                            SecondaryActionButton(
                                text = "Record a cue",
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
                                    stopRecording(recording, context, container)
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
                                            if (asset.missingDetectedAt != null) "File unavailable — re-record or re-import" else asset.source.lowercase(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (asset.missingDetectedAt != null) colors.destructive else colors.textSecondary,
                                        )
                                    }
                                    PulseIconButton(
                                        icon = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tooltip = stringResource(R.string.action_delete),
                                        onClick = { scope.launch { container.audioAssets.remove(asset.id) } },
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
}

private fun startRecording(context: Context, container: AppContainer, onStarted: (MediaRecorder) -> Unit) {
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
    onStarted(recorder)
}

private fun stopRecording(recorder: MediaRecorder?, context: Context, container: AppContainer) {
    val output = runCatching {
        recorder?.stop()
        recorder?.release()
    }
    output.onSuccess {
        val dir = File(context.filesDir, "recordings")
        val newest = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            container.audioAssets.add(
                AudioAssetEntity(
                    id = newest.nameWithoutExtension,
                    name = newest.nameWithoutExtension,
                    uri = Uri.fromFile(newest).toString(),
                    source = "RECORDING",
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }
}

/** Plain words for the duck multiplier: 30 % means the music sits at 30 % of its own volume. */
private fun duckLevelLabel(level: Float): String = when {
    level <= 0.001f -> "Muted"
    level < 0.2f -> (level * 100).toInt().toString() + "% · strong"
    level < 0.45f -> (level * 100).toInt().toString() + "% · balanced"
    level < 0.8f -> (level * 100).toInt().toString() + "% · subtle"
    else -> (level * 100).toInt().toString() + "% · barely"
}

private fun DuckPhase.label(): String = when (this) {
    DuckPhase.NONE -> "full volume"
    DuckPhase.FADING_DOWN -> "lowering"
    DuckPhase.HELD -> "lowered"
    DuckPhase.FADING_UP -> "restoring"
}

private fun VoiceVerbosity.label(): String = when (this) {
    VoiceVerbosity.MINIMAL -> "Minimal"
    VoiceVerbosity.STANDARD -> "Standard"
    VoiceVerbosity.CHATTY -> "Chatty"
}

// ---------------------------------------------------------------------------------------------
// Help
// ---------------------------------------------------------------------------------------------

@Composable
fun HelpScreen(container: AppContainer, onBack: () -> Unit) {
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
                    text = "Open Android app settings",
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
