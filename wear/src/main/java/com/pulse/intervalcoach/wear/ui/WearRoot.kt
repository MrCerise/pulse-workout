package com.pulse.intervalcoach.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.intervalcoach.wear.WearAppContainer
import com.pulse.intervalcoach.wear.ui.theme.PulseWearTheme

/**
 * The watch's small screen graph: home, a local player, a remote player, the summary, the watch's
 * own history and settings. Every screen works with or without a phone.
 */
sealed interface WearScreen {
    data object Home : WearScreen
    data object History : WearScreen
    data object Settings : WearScreen
    data class Player(val workoutId: String) : WearScreen
    data object RemotePlayer : WearScreen
    data object Summary : WearScreen
}

/**
 * Application frame: theme, back stack, and the session-scoped device keep-alive (screen on while
 * a session is visible, partial wake lock while any session runs).
 */
@Composable
fun PulseWearRoot(container: WearAppContainer) {
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val snapshot by container.session.snapshot.collectAsStateWithLifecycle()
    val remote by container.wearSync.remoteSession.collectAsStateWithLifecycle()

    PulseWearTheme(themeMode = prefs.themeMode, highContrast = prefs.highContrast) {
        // A partial wake lock keeps the engine ticking while the wrist is down; the screen flag
        // keeps it readable while it is up. Both are session-scoped.
        LaunchedEffect(snapshot.isActive, remote != null) {
            container.wakeLock.set(snapshot.isActive || remote != null)
        }
        DisposableEffect(Unit) {
            onDispose { container.wakeLock.set(false) }
        }

        val backStack = remember { mutableStateListOf<WearScreen>(WearScreen.Home) }
        val screen = backStack.lastOrNull() ?: WearScreen.Home
        BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }

        when (val current = screen) {
            is WearScreen.Home -> WearHomeScreen(
                container = container,
                onOpenWorkout = { backStack.add(WearScreen.Player(it)) },
                onOpenSettings = { backStack.add(WearScreen.Settings) },
                onOpenHistory = { backStack.add(WearScreen.History) },
                onOpenRemote = { backStack.add(WearScreen.RemotePlayer) },
                onOpenActive = { backStack.add(WearScreen.Player(it)) },
            )
            WearScreen.History -> WearHistoryScreen(
                container = container,
                onBack = { backStack.removeAt(backStack.lastIndex) },
            )
            is WearScreen.Player -> WearPlayerScreen(
                container = container,
                workoutId = current.workoutId,
                onFinished = {
                    backStack[backStack.lastIndex] = WearScreen.Summary
                },
                onBack = { backStack.removeAt(backStack.lastIndex) },
            )
            WearScreen.RemotePlayer -> WearRemotePlayerScreen(
                container = container,
                onClosed = { backStack.removeAt(backStack.lastIndex) },
            )
            WearScreen.Summary -> WearSummaryScreen(
                container = container,
                onDone = {
                    container.session.clearSummary()
                    backStack.clear()
                    backStack.add(WearScreen.Home)
                },
            )
            WearScreen.Settings -> WearSettingsScreen(
                container = container,
                onBack = { backStack.removeAt(backStack.lastIndex) },
            )
        }
    }
}

/** Keeps the display readable while a session screen is showing and the preference allows it. */
@Composable
fun KeepScreenOnWhileActive(active: Boolean, enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(active, enabled) {
        view.keepScreenOn = active && enabled
        onDispose { view.keepScreenOn = false }
    }
}
