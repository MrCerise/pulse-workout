package com.mrcerise.intervalcoach.wear.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.WearDeepLink
import com.mrcerise.intervalcoach.wear.ui.theme.PulseWearTheme

/**
 * The watch's small screen graph: home, a workout's details, a local player, a remote player, the
 * summary, the watch's own history and settings. Every screen works with or without a phone.
 */
private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val DETAILS = "workout/{workoutId}"
    const val PLAYER = "player/{workoutId}"
    const val REMOTE = "remote"
    const val SUMMARY = "summary"

    fun player(workoutId: String) = "player/" + Uri.encode(workoutId)
    fun details(workoutId: String) = "workout/" + Uri.encode(workoutId)
}

/**
 * Application frame: theme, navigation, and the session-scoped device keep-alive (screen on while
 * a session is visible, partial wake lock while any session runs).
 *
 * Navigation is the Wear swipe-to-dismiss host: a swipe from the left edge goes back one screen,
 * the way every Wear OS app behaves, and only closes the app from the home screen. Leaving a
 * player that way keeps its session running; home offers to resume it.
 */
@Composable
fun PulseWearRoot(
    container: WearAppContainer,
    deepLink: WearDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
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

        val navController = rememberSwipeDismissableNavController()
        // The tile opens a workout: running ones straight into the player, others on their details,
        // always on top of home so a swipe back lands where the app normally starts.
        LaunchedEffect(deepLink) {
            val link = deepLink ?: return@LaunchedEffect
            navController.popBackStack(Routes.HOME, inclusive = false)
            navController.navigate(if (link.resume) Routes.player(link.workoutId) else Routes.details(link.workoutId))
            onDeepLinkHandled()
        }
        AppScaffold {
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    WearHomeScreen(
                        container = container,
                        onOpenWorkout = { navController.navigate(Routes.details(it)) },
                        onResume = { navController.navigate(Routes.player(it)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onOpenRemote = { navController.navigate(Routes.REMOTE) },
                    )
                }
                composable(Routes.DETAILS) { entry ->
                    WearDetailsScreen(
                        container = container,
                        workoutId = entry.arguments?.getString("workoutId").orEmpty(),
                        onStart = { id ->
                            // The player replaces the details: swiping back from a running workout
                            // lands on home, where Resume is, not on a Start button.
                            navController.navigate(Routes.player(id)) {
                                popUpTo(Routes.HOME) { inclusive = false }
                            }
                        },
                    )
                }
                composable(Routes.HISTORY) {
                    WearHistoryScreen(container = container)
                }
                composable(Routes.PLAYER) { entry ->
                    WearPlayerScreen(
                        container = container,
                        workoutId = entry.arguments?.getString("workoutId").orEmpty(),
                        onFinished = {
                            navController.navigate(Routes.SUMMARY) {
                                popUpTo(Routes.PLAYER) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.REMOTE) {
                    WearRemotePlayerScreen(
                        container = container,
                        onClosed = { navController.popBackStack() },
                    )
                }
                composable(Routes.SUMMARY) {
                    // However the summary is left (Done, or a swipe back), it is consumed: a stale
                    // summary must never end the next workout's player the moment it opens.
                    DisposableEffect(Unit) {
                        onDispose { container.session.clearSummary() }
                    }
                    WearSummaryScreen(
                        container = container,
                        onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }
                composable(Routes.SETTINGS) {
                    WearSettingsScreen(
                        container = container,
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    )
                }
                composable(Routes.PRIVACY) {
                    WearPrivacyScreen()
                }
            }
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
