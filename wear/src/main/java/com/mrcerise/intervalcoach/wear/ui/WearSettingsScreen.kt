package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.compose.ui.graphics.vector.ImageVector
import com.mrcerise.intervalcoach.wear.BuildConfig
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.WearAppContainer
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
 * with nothing sent or accepted. The state line under "Sync now" always says which of the possible
 * situations this watch is actually in.
 */
@Composable
fun WearSettingsScreen(
    container: WearAppContainer,
    onOpenPrivacy: () -> Unit,
) {
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val phoneLibrarySeen by container.workouts.phoneLibrarySeen.collectAsStateWithLifecycle()
    val phoneConnected by container.wearSync.phoneConnected.collectAsStateWithLifecycle()
    var syncRequested by remember { mutableStateOf(false) }

    PulseListScreen { spec ->
        item(key = "title") { ScreenHeader(stringResource(R.string.settings_title), spec) }
        item(key = "sync-toggle") {
            SettingSwitch(
                checked = prefs.syncEnabled,
                onCheckedChange = { container.preferences.setSyncEnabled(it) },
                icon = Icons.Filled.PhonelinkRing,
                label = stringResource(R.string.settings_sync_with_phone),
                secondary = stringResource(
                    if (prefs.syncEnabled) R.string.settings_sync_on_hint else R.string.settings_sync_off_hint,
                ),
                spec = spec,
            )
        }
        if (prefs.syncEnabled) {
            item(key = "sync") {
                SettingButton(
                    onClick = {
                        container.wearSync.requestSync()
                        syncRequested = true
                    },
                    icon = Icons.Filled.Sync,
                    label = stringResource(if (syncRequested) R.string.settings_sync_requested else R.string.settings_sync_now),
                    secondary = watchSyncStatus(
                        phoneLibrarySeen = phoneLibrarySeen,
                        syncEnabled = true,
                        phoneConnected = phoneConnected,
                    ),
                    spec = spec,
                )
            }
        }
        item(key = "haptics") {
            SettingSwitch(
                checked = prefs.vibrationEnabled,
                onCheckedChange = { container.preferences.setVibration(it) },
                icon = Icons.Filled.Vibration,
                label = stringResource(R.string.settings_haptics),
                spec = spec,
            )
        }
        item(key = "voice") {
            SettingSwitch(
                checked = prefs.voiceCues,
                onCheckedChange = { container.preferences.setVoiceCues(it) },
                icon = Icons.Filled.RecordVoiceOver,
                label = stringResource(R.string.settings_voice),
                secondary = stringResource(R.string.settings_voice_hint),
                spec = spec,
            )
        }
        item(key = "keep-screen") {
            SettingSwitch(
                checked = prefs.keepScreenOn,
                onCheckedChange = { container.preferences.setKeepScreenOn(it) },
                icon = Icons.Filled.LightMode,
                label = stringResource(R.string.settings_keep_screen_on),
                spec = spec,
            )
        }
        item(key = "theme") {
            val next = when (prefs.themeMode) {
                WearThemeMode.SYSTEM -> WearThemeMode.DARK
                WearThemeMode.DARK -> WearThemeMode.TRUE_BLACK
                WearThemeMode.TRUE_BLACK -> WearThemeMode.SYSTEM
            }
            SettingButton(
                onClick = { container.preferences.setThemeMode(next) },
                icon = Icons.Filled.Palette,
                label = stringResource(R.string.settings_theme),
                secondary = themeLabel(prefs.themeMode),
                spec = spec,
            )
        }
        item(key = "privacy") {
            SettingButton(
                onClick = onOpenPrivacy,
                icon = Icons.Filled.PrivacyTip,
                label = stringResource(R.string.privacy_policy_title),
                spec = spec,
            )
        }
        item(key = "about") {
            Caption(aboutText(prefs.phoneAppVersion, prefs.lastSyncAt), spec)
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.SettingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    label: String,
    spec: TransformationSpec,
    secondary: String? = null,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.transformed(this, spec),
        transformation = SurfaceTransformation(spec),
        icon = { Icon(icon, contentDescription = null) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun TransformingLazyColumnItemScope.SettingButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    spec: TransformationSpec,
    secondary: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.transformed(this, spec),
        transformation = SurfaceTransformation(spec),
        colors = ButtonDefaults.filledTonalButtonColors(),
        icon = { Icon(icon, contentDescription = null) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    )
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
