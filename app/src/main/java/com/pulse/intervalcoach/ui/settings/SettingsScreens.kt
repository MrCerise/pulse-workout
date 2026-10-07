package com.pulse.intervalcoach.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.DurationStepper
import com.pulse.intervalcoach.ui.components.GradientActionButton
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.NeutralChip
import com.pulse.intervalcoach.ui.components.NumberStepper
import com.pulse.intervalcoach.ui.components.OptionRow
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.ToggleRow
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.ThemeMode
import com.pulse.intervalcoach.audio.SpeechAvailability
import com.pulse.intervalcoach.data.AudioInterruptionBehavior
import com.pulse.intervalcoach.data.BackupRepository
import com.pulse.intervalcoach.data.HeadphoneBehavior
import com.pulse.intervalcoach.data.PlayerDensity
import com.pulse.intervalcoach.data.VoiceVerbosity
import com.pulse.intervalcoach.data.WeightUnit
import com.pulse.intervalcoach.data.db.AudioAssetEntity
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.pulse.intervalcoach.R

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenVoiceStudio: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWelcome: () -> Unit,
    onOpenParser: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val colors = LocalPulseColors.current
    val scope = rememberCoroutineScope()
    val healthSnapshot by container.health.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDeleteSessions by remember { mutableStateOf(false) }
    val current = prefs

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text("Everything applies instantly", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Health Connect & Google Fit", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            }
                            healthSnapshot.source?.let { NeutralChip(it) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                healthSnapshot.source == null -> "Connect in the Health setup screen to share finished workouts, and to show live heart rate and daily steps."
                                else -> "Connected via ${healthSnapshot.source}." +
                                    (if (healthSnapshot.lastSyncAt > 0) " Last sync " + formatSync(healthSnapshot.lastSyncAt) + "." else "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        ToggleRow(
                            label = "Share finished workouts to health apps",
                            hint = "Writes workout sessions with per-interval segments and calories burned. Off = nothing is written.",
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
                        OptionRow(
                            label = stringResource(R.string.settings_theme),
                            options = ThemeMode.entries.map { it to it.label() },
                            selected = current.themeMode,
                            onSelect = { mode -> scope.launch { container.preferences.setTheme(mode) } },
                        )
                        ToggleRow(
                            label = stringResource(R.string.settings_dynamic_color),
                            hint = stringResource(R.string.settings_dynamic_color_hint),
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
                            hint = "Mirrors the main controls for one-handed use.",
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
                            hint = "Runs the workout in a foreground service with a partial wake lock.",
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
                        TextButton(onClick = onOpenVoiceStudio) { Text("Open Voice & audio studio") }
                    }
                }
            }

            item { SectionHeader("Data") }
            item {
                PulseCard {
                    Column {
                        Text(
                            "Everything stays on this device. No account, no ads, no analytics, no cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        SecondaryActionButton("Import / export & backups", onOpenBackup, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        SecondaryActionButton("Delete all sessions…", { confirmDeleteSessions = true }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        ToggleRow(
                            label = stringResource(R.string.settings_backup_auto),
                            hint = "A dated JSON backup is written weekly with WorkManager.",
                            checked = current.autoBackupEnabled,
                            onCheckedChange = { scope.launch { container.preferences.setAutoBackup(it) } },
                        )
                        Text(
                            if (current.lastAutoBackupAt > 0) {
                                "Last backup: " + LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(current.lastAutoBackupAt),
                                    ZoneId.systemDefault(),
                                ).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))
                            } else {
                                "No automatic backup has run yet."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            item { SectionHeader("Create workouts") }
            item {
                PulseCard {
                    SecondaryActionButton("Describe a workout in words", onOpenParser, Modifier.fillMaxWidth())
                }
            }

            item { SectionHeader("Help & about") }
            item {
                PulseCard {
                    Column {
                        SecondaryActionButton("Help, voice setup & shortcuts", onOpenHelp, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        SecondaryActionButton("Show the welcome tour again", onOpenWelcome, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Text("PULSE Interval Coach ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        Text(
                            "Offline interval timer · Health Connect first, Google Fit best-effort (deprecated by Google as of 2026) · package ${BuildConfig.APPLICATION_ID}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }

    if (confirmDeleteSessions) {
        ConfirmDialog(
            title = "Delete all sessions?",
            body = "This permanently removes every saved session and their events. Workouts are kept.",
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

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.TRUE_BLACK -> "True black"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceStudioScreen(container: AppContainer, onBack: () -> Unit) {
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val speechInfo by container.speech.info.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assets by container.audioAssets.assets.collectAsStateWithLifecycle(initialValue = emptyList())
    var recording by remember { mutableStateOf<MediaRecorder?>(null) }
    val current = prefs

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice & audio") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        Spacer(Modifier.height(6.dp))
                        speechInfo.engineLabel?.let {
                            Text("Engine: $it", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                        Text(
                            when {
                                speechInfo.offlineVoiceInstalled -> "Offline voice installed — coaching works in airplane mode."
                                availability is SpeechAvailability.NoEngine -> "PULSE falls back to sound cues. Install a voice in Android Settings → System → Languages & input → Text-to-speech."
                                else -> "No offline voice found yet. Install voice data for offline coaching — the app still works online or with sound cues only."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text("Speaking rate ${"%.2f".format(current.voiceRate)}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        Slider(
                            value = current.voiceRate,
                            onValueChange = { scope.launch { container.preferences.setVoiceRate(it) } },
                            valueRange = 0.5f..2.0f,
                            onValueChangeFinished = { container.speech.rate = current.voiceRate },
                        )
                        Text("Pitch ${"%.2f".format(current.voicePitch)}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        Slider(
                            value = current.voicePitch,
                            onValueChange = { scope.launch { container.preferences.setVoicePitch(it) } },
                            valueRange = 0.5f..2.0f,
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
                        Text("Cue volume ${(current.cueVolume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        Slider(
                            value = current.cueVolume,
                            onValueChange = { scope.launch { container.preferences.setCueVolume(it) } },
                            valueRange = 0f..1f,
                            onValueChangeFinished = { container.cueSoundPlayer.volume = current.cueVolume },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (container.haptics.isAvailable) "Vibration is available on this device." else "This device has no vibrator — cues stay audible only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            item { SectionHeader("Background audio") }
            item {
                PulseCard {
                    Column {
                        Text(
                            current.musicUri?.let { "Selected: ${it.substringAfterLast('/')}" } ?: "No background audio selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            "PULSE plays a file you pick; it never bundles or streams music, and it ducks your audio during cues.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryActionButton("Choose audio…", { musicPicker.launch(arrayOf("audio/*")) }, Modifier.weight(1f))
                            SecondaryActionButton("Remove", { scope.launch { container.preferences.setMusic(null) } }, Modifier.weight(0.6f))
                        }
                    }
                }
            }

            item { SectionHeader("Your own cue recordings") }
            item {
                PulseCard {
                    Column {
                        Text(
                            "Record short spoken cues (for example \u201Cswitch sides\u201D) and PULSE stores them as private app files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
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
                            Spacer(Modifier.height(8.dp))
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
                                    TextButton(onClick = { scope.launch { container.audioAssets.remove(asset.id) } }) { Text(stringResource(R.string.action_delete)) }
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

private fun VoiceVerbosity.label(): String = when (this) {
    VoiceVerbosity.MINIMAL -> "Minimal"
    VoiceVerbosity.STANDARD -> "Standard"
    VoiceVerbosity.CHATTY -> "Chatty"
}

// ---------------------------------------------------------------------------------------------
// Import / export / backups
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val colors = LocalPulseColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { BackupRepository(context, container) }
    var preview by remember { mutableStateOf<com.pulse.intervalcoach.data.BackupPreview?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var backups by remember { mutableStateOf(repository.automaticBackups()) }
    var error by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { repository.writeTo(uri) }
                    .onSuccess { onMessage("Exported to the selected file") }
                    .onFailure { error = it.message ?: "Export failed" }
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            if (stream == null) {
                error = "The selected file could not be read (permission revoked, or the file was removed)."
                return@launch
            }
            val result = runCatching { stream.use { repository.preview(it).getOrThrow() } }
            result
                .onSuccess { info ->
                    preview = info
                    pendingUri = uri
                }
                .onFailure { failure -> error = failure.message ?: "This file is not a valid PULSE backup." }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backups & import") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Export") }
            item {
                PulseCard {
                    Column {
                        Text(
                            "Exports a plain JSON file with your workouts, sessions, tags and settings. It contains no secrets and no media files — only references.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = stringResource(R.string.settings_export_all),
                            onClick = { createDocument.launch("pulse-backup.json") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { SectionHeader("Import") }
            item {
                PulseCard {
                    Column {
                        Text(
                            "Import shows a preview first and never overwrites existing workouts: colliding entries get new IDs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        SecondaryActionButton(
                            text = stringResource(R.string.settings_import),
                            onClick = { openDocument.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { SectionHeader("Automatic backups") }
            item {
                PulseCard {
                    Column {
                        if (backups.isEmpty()) {
                            Text("No automatic backups yet. They run weekly while the app is installed.", color = colors.textSecondary)
                        } else {
                            backups.take(5).forEach { file ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                        Text(
                                            "${file.length() / 1024} KB · " + LocalDateTime.ofInstant(
                                                Instant.ofEpochMilli(file.lastModified()),
                                                ZoneId.systemDefault(),
                                            ).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            repository.readAutomaticBackup(file).use { stream ->
                                                repository.import(stream)
                                                    .onSuccess { (w, s) -> onMessage("Imported $w workouts and $s sessions") }
                                                    .onFailure { error = it.message }
                                            }
                                        }
                                    }) { Text("Restore") }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SecondaryActionButton(
                            text = "Write a backup now",
                            onClick = {
                                scope.launch {
                                    runCatching { repository.writeAutomaticBackup() }
                                        .onSuccess { backups = repository.automaticBackups(); onMessage("Backup written") }
                                        .onFailure { error = it.message }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            error?.let { message ->
                item {
                    InfoBanner("Import failed: $message Nothing was changed.", tone = colors.destructive, warning = true)
                }
            }
        }
    }

    preview?.let { info ->
        ConfirmDialog(
            title = stringResource(R.string.import_preview_title),
            body = "The file contains ${info.workouts} workouts, ${info.sessions} sessions and ${info.tags} tags. Nothing is written until you confirm." +
                info.warnings.takeIf { it.isNotEmpty() }?.joinToString(" ") { " ⚠ $it" }.orEmpty(),
            confirmLabel = stringResource(R.string.import_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                val uri = pendingUri
                preview = null
                pendingUri = null
                if (uri != null) {
                    scope.launch {
                        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                        if (stream == null) {
                            error = "The file is no longer readable."
                        } else {
                            runCatching { stream.use { repository.import(it).getOrThrow() } }
                                .onSuccess { (workoutsImported, sessionsImported) ->
                                    onMessage("Imported $workoutsImported workouts and $sessionsImported sessions")
                                }
                                .onFailure { failure -> error = failure.message }
                        }
                    }
                }
            },
            onDismiss = { preview = null; pendingUri = null },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Help
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(container: AppContainer, onBack: () -> Unit) {
    val colors = LocalPulseColors.current
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Voice setup") }
            item {
                PulseCard {
                    Text(
                        "PULSE uses the text-to-speech engine installed on your device. For coaching that works in flight mode, install an offline voice: " +
                            "Android Settings → System → Languages & input → Text-to-speech → your engine → Install voice data. If no engine is present, PULSE falls " +
                            "back to sound cues and says so in Voice Studio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            item { SectionHeader("Health Connect & Google Fit") }
            item {
                PulseCard {
                    Text(
                        "Health Connect is the primary integration: finished workouts (with per-interval segments and calories) are written to it, and live " +
                            "heart rate plus daily steps are read for the player and Today screen. Google Fit is best-effort on top — Google has deprecated the " +
                            "on-device Fit API as of 2026 and no longer accepts new signups, so treat Fit extras as optional. Nothing is ever sent to the cloud " +
                            "by PULSE itself; only the Google services you connect to.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            item { SectionHeader("Background operation") }
            item {
                PulseCard {
                    Text(
                        "A workout runs in a foreground service with a media notification, so timing and cues continue with the screen off. Android can still restart " +
                            "the process in extreme low-memory situations; if that happens PULSE marks the session as interrupted instead of pretending it completed. " +
                            "This build keeps cues running with the screen off: " + if (prefs?.continueCuesScreenOff == true) "yes" else "no (you turned it off in Settings)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            item { SectionHeader("Audio troubleshooting") }
            item {
                PulseCard {
                    Text(
                        "Cues are mixed at the volume set in Voice Studio. If you cannot hear them: raise media volume, check that Do Not Disturb is not blocking media, " +
                            "and confirm PULSE is not muted in the notification shade. Other apps are ducked, not stopped. Bluetooth cues can arrive a little later on " +
                            "some devices — the timer itself is never affected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            item { SectionHeader("Imported files") }
            item {
                PulseCard {
                    Text(
                        "Exports are plain JSON. Importing previews the file, never overwrites workouts and re-assigns colliding IDs. If a referenced image or audio " +
                            "file was deleted, the workout still opens and the app says the media is missing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            item { SectionHeader("Accessibility") }
            item {
                PulseCard {
                    Text(
                        "Every drag action has Move up / Move down alternatives, controls are at least 48 dp, phase colours always come with a label, and TalkBack " +
                            "announces interval changes rather than every tick. Reduced motion and high contrast are in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            item { SectionHeader("Permissions used") }
            item {
                PulseCard {
                    Text(
                        "• Notifications (optional): shows the running-workout controls.\n" +
                            "• Vibration: interval haptics.\n" +
                            "• Microphone (optional): only while you record your own cue in Voice Studio.\n" +
                            "• Foreground service: keeps timing and cues alive with the screen off.\n" +
                            "• Health Connect (optional): read heart rate & steps, write finished workouts.\n" +
                            "• Google Fit (optional): the same, as a secondary source where available.\n" +
                            "PULSE asks for no location, no contacts, no cloud account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
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
