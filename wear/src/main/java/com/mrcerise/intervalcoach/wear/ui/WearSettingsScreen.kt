package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.mrcerise.intervalcoach.wear.BuildConfig
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors
import com.mrcerise.intervalcoach.wear.ui.theme.WearThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Watch settings: the handful of things worth changing on a wrist. Appearance follows the phone's
 * PULSE settings by default (synced automatically); the toggles here override it until the next
 * sync.
 *
 * The phone link has its own switch, because the watch is not a companion device first: turning it
 * off makes this a standalone app — the bundled library, its own sessions and its own history,
 * with nothing sent or accepted. The state line next to "Sync now" always says which of the
 * possible situations this watch is actually in.
 */
@Composable
fun WearSettingsScreen(
    container: WearAppContainer,
    onBack: () -> Unit,
) {
    val colors = LocalWearColors.current
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val phoneLibrarySeen by container.workouts.phoneLibrarySeen.collectAsStateWithLifecycle()
    val phoneConnected by container.wearSync.phoneConnected.collectAsStateWithLifecycle()
    var syncRequested by remember { mutableStateOf(false) }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "sync-toggle") {
                ToggleChip(
                    checked = prefs.syncEnabled,
                    onCheckedChange = { container.preferences.setSyncEnabled(it) },
                    label = { Text(stringResource(R.string.settings_sync_with_phone)) },
                    toggleControl = { Switch(checked = prefs.syncEnabled) },
                )
            }
            item(key = "sync-hint") {
                Text(
                    text = stringResource(
                        if (prefs.syncEnabled) R.string.settings_sync_on_hint
                        else R.string.settings_sync_off_hint,
                    ),
                    style = MaterialTheme.typography.caption1,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
            if (prefs.syncEnabled) {
                item(key = "sync") {
                    Chip(
                        onClick = {
                            container.wearSync.requestSync()
                            syncRequested = true
                        },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                        label = {
                            Text(
                                if (syncRequested) stringResource(R.string.settings_sync_requested)
                                else stringResource(R.string.settings_sync_now),
                            )
                        },
                        secondaryLabel = {
                            Text(
                                watchSyncStatus(
                                    phoneLibrarySeen = phoneLibrarySeen,
                                    syncEnabled = true,
                                    phoneConnected = phoneConnected,
                                ),
                            )
                        },
                    )
                }
            }
            item(key = "haptics") {
                ToggleChip(
                    checked = prefs.vibrationEnabled,
                    onCheckedChange = { container.preferences.setVibration(it) },
                    label = { Text(stringResource(R.string.settings_haptics)) },
                    toggleControl = { Switch(checked = prefs.vibrationEnabled) },
                )
            }
            item(key = "keep-screen") {
                ToggleChip(
                    checked = prefs.keepScreenOn,
                    onCheckedChange = { container.preferences.setKeepScreenOn(it) },
                    label = { Text(stringResource(R.string.settings_keep_screen_on)) },
                    toggleControl = { Switch(checked = prefs.keepScreenOn) },
                )
            }
            item(key = "theme") {
                val next = when (prefs.themeMode) {
                    WearThemeMode.SYSTEM -> WearThemeMode.DARK
                    WearThemeMode.DARK -> WearThemeMode.TRUE_BLACK
                    WearThemeMode.TRUE_BLACK -> WearThemeMode.SYSTEM
                }
                Chip(
                    onClick = { container.preferences.setThemeMode(next) },
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = colors.surface,
                        contentColor = colors.textPrimary,
                    ),
                    label = { Text(stringResource(R.string.settings_theme)) },
                    secondaryLabel = { Text(themeLabel(prefs.themeMode)) },
                )
            }
            item(key = "about") {
                Text(
                    text = aboutText(prefs.phoneAppVersion, prefs.lastSyncAt),
                    style = MaterialTheme.typography.caption1,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item(key = "back") {
                Chip(
                    onClick = onBack,
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = colors.surface,
                        contentColor = colors.textPrimary,
                    ),
                    label = { Text(stringResource(R.string.settings_back)) },
                )
            }
        }
    }
}

@Composable
private fun themeLabel(mode: WearThemeMode): String = when (mode) {
    WearThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    WearThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    WearThemeMode.TRUE_BLACK -> stringResource(R.string.settings_theme_true_black)
}

@Composable
private fun aboutText(phoneVersion: String, lastSyncAt: Long): String {
    val synced = if (lastSyncAt <= 0L) {
        stringResource(R.string.library_never_synced)
    } else {
        val formatter = remember { DateTimeFormatter.ofPattern("d MMM HH:mm") }
        remember(lastSyncAt) {
            formatter.format(Instant.ofEpochMilli(lastSyncAt).atZone(ZoneId.systemDefault()))
        }
    }
    val phone = if (phoneVersion.isBlank()) "" else " · " + phoneVersion
    return stringResource(R.string.settings_about, BuildConfig.VERSION_NAME, synced) + phone
}
