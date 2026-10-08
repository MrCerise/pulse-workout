package com.pulse.intervalcoach.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.DeepLinks
import com.pulse.intervalcoach.ui.builders.AdvancedBuilderScreen
import com.pulse.intervalcoach.ui.builders.QuickBuilderScreen
import com.pulse.intervalcoach.ui.home.HomeScreen
import com.pulse.intervalcoach.ui.health.HealthScreen
import com.pulse.intervalcoach.ui.parser.ParserScreen
import com.pulse.intervalcoach.ui.player.PlayerScreen
import com.pulse.intervalcoach.ui.progress.HistoryScreen
import com.pulse.intervalcoach.ui.progress.ProgressScreen
import com.pulse.intervalcoach.ui.progress.SessionDetailScreen
import com.pulse.intervalcoach.ui.settings.BackupScreen
import com.pulse.intervalcoach.ui.settings.HelpScreen
import com.pulse.intervalcoach.ui.settings.SettingsScreen
import com.pulse.intervalcoach.ui.settings.VoiceStudioScreen
import com.pulse.intervalcoach.ui.summary.SessionSummaryScreen
import com.pulse.engine.WorkoutPlan
import com.pulse.intervalcoach.ui.theme.PulseTheme
import com.pulse.intervalcoach.ui.welcome.WelcomeScreen
import com.pulse.intervalcoach.ui.workouts.TemplateGalleryScreen
import com.pulse.intervalcoach.ui.workouts.WorkoutDetailsScreen
import com.pulse.intervalcoach.ui.components.PulseBottomBar
import com.pulse.intervalcoach.ui.components.PulseNavItem
import com.pulse.intervalcoach.ui.components.PulseSnackbar
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.workouts.WorkoutLibraryScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val TEMPLATES = "templates"
    const val QUICK_BUILDER = "builder/quick"
    const val ADVANCED_BUILDER = "builder/advanced"
    const val PARSER = "parser"
    const val DETAILS = "workout/{id}"
    const val PLAYER = "player"
    const val SUMMARY = "summary"
    const val HISTORY = "history"
    const val SESSION_DETAIL = "session/{id}"
    const val VOICE_STUDIO = "settings/voice"
    const val BACKUP = "settings/backup"
    const val HELP = "settings/help"
    const val HEALTH = "health"
    const val WELCOME = "welcome"

    fun details(id: String) = "workout/$id"
    fun session(id: String) = "session/$id"
}

private val primaryDestinations = listOf(
    PulseNavItem(Routes.HOME, "Today", Icons.Filled.Home),
    PulseNavItem(Routes.WORKOUTS, "Workouts", Icons.Filled.FitnessCenter),
    PulseNavItem(Routes.PROGRESS, "Progress", Icons.Filled.Insights),
    PulseNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun PulseAppRoot(
    container: AppContainer,
    deeplinks: StateFlow<String?>,
    onDeeplinkConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val navController = rememberNavController()
    val current = prefs
    if (current == null) {
        // First frame: DataStore has not answered yet. Draw the platform background, never a blank
        // white screen.
        PulseTheme(
            themeMode = com.pulse.intervalcoach.ui.theme.ThemeMode.SYSTEM,
            dynamicColor = false,
            highContrast = false,
            reducedMotion = false,
        ) { Box(Modifier.fillMaxSize()) }
        return
    }

    val context = LocalContext.current
    val sessionController = container.sessionController
    val activeSession by sessionController.active.collectAsStateWithLifecycle()

    PulseTheme(
        themeMode = current.themeMode,
        dynamicColor = current.dynamicColor,
        highContrast = current.highContrast,
        reducedMotion = current.reducedMotion,
    ) {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        // Single entry point for starting a session: records the session, wakes the service and
        // routes to the player, with a snackbar when the plan cannot run.
        val startSession: (WorkoutPlan) -> Unit = { plan ->
            scope.launch {
                val result = sessionController.startWorkout(plan)
                if (result.isSuccess) {
                    onRequestNotificationPermission()
                    navController.navigate(Routes.PLAYER)
                } else {
                    snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Could not start this workout")
                }
            }
        }
        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route
        val showBottomBar = route in primaryDestinations.map { it.route }
        // With no bottom bar the snackbar would otherwise sit under the gesture bar.
        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // Deep links from shortcuts, widgets, reminders and notification taps.
        val deeplink by deeplinks.collectAsStateWithLifecycle()
        LaunchedEffect(deeplink) {
            val link = deeplink ?: return@LaunchedEffect
            when {
                link.startsWith("pulse://start/") -> {
                    val id = link.removePrefix("pulse://start/")
                    container.workouts.plan(id)?.let { plan ->
                        val result = sessionController.startWorkout(plan)
                        if (result.isSuccess) {
                            onRequestNotificationPermission()
                            navController.navigate(Routes.PLAYER)
                        } else {
                            snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Could not start this workout")
                        }
                    }
                }
                link.startsWith("pulse://workout/") -> {
                    val id = link.removePrefix("pulse://workout/")
                    navController.navigate(Routes.details(id))
                }
                link == DeepLinks.QUICK_BUILDER -> navController.navigate(Routes.QUICK_BUILDER)
                link == DeepLinks.RECENT -> navController.navigate(Routes.WORKOUTS)
                link == DeepLinks.PLAYER -> if (activeSession != null) navController.navigate(Routes.PLAYER)
                else -> navController.navigate(Routes.WORKOUTS)
            }
            onDeeplinkConsumed()
        }

        // Onboarding: shown once, skippable at every step.
        LaunchedEffect(current.onboardingComplete) {
            if (!current.onboardingComplete) navController.navigate(Routes.WELCOME)
        }

        Scaffold(
            containerColor = LocalPulseColors.current.background,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (showBottomBar) 0.dp else navBarInset),
                ) { data -> PulseSnackbar(data) }
            },
            // Zero here on purpose. Most destinations own their own Scaffold + TopAppBar, and
            // Material3's Scaffold does not consume the insets it applies — so an outer Scaffold
            // that applied the system bars would push every inner app bar down a second status-bar
            // height. Screens without their own Scaffold apply the insets themselves.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    PulseBottomBar(
                        items = primaryDestinations,
                        currentRoute = route,
                        onSelect = { destination ->
                            navController.navigate(destination) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        container = container,
                        onOpenWorkout = { navController.navigate(Routes.details(it)) },
                        onStartWorkout = startSession,
                        onQuickStart = { navController.navigate(Routes.QUICK_BUILDER) },
                        onOpenProgress = { navController.navigate(Routes.PROGRESS) },
                        onOpenTemplates = { navController.navigate(Routes.TEMPLATES) },
                        onResumeSession = { navController.navigate(Routes.PLAYER) },
                        onOpenHealth = { navController.navigate(Routes.HEALTH) },
                    )
                }
                composable(Routes.WORKOUTS) {
                    WorkoutLibraryScreen(
                        container = container,
                        onOpenWorkout = { navController.navigate(Routes.details(it)) },
                        onOpenTemplates = { navController.navigate(Routes.TEMPLATES) },
                        onQuickBuilder = { navController.navigate(Routes.QUICK_BUILDER) },
                        onAdvancedBuilder = { navController.navigate(Routes.ADVANCED_BUILDER) },
                        onParser = { navController.navigate(Routes.PARSER) },
                    )
                }
                composable(Routes.TEMPLATES) {
                    TemplateGalleryScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onOpenWorkout = { navController.navigate(Routes.details(it)) },
                    )
                }
                composable(Routes.DETAILS) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    WorkoutDetailsScreen(
                        container = container,
                        workoutId = id,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate("builder/advanced?workoutId=$it") },
                        onStart = startSession,
                    )
                }
                composable(Routes.QUICK_BUILDER) {
                    QuickBuilderScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onSaved = { planId ->
                            scope.launch { snackbarHostState.showSnackbar("Saved workout") }
                            navController.navigate(Routes.details(planId)) {
                                popUpTo(Routes.QUICK_BUILDER) { inclusive = true }
                            }
                        },
                        onSavedAndStart = startSession,
                    )
                }
                composable("builder/advanced?workoutId={workoutId}") { entry ->
                    val workoutId = entry.arguments?.getString("workoutId")?.takeIf { it.isNotBlank() && it != "null" }
                    AdvancedBuilderScreen(
                        container = container,
                        workoutId = workoutId,
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.navigate(Routes.details(id)) {
                                popUpTo(Routes.ADVANCED_BUILDER) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.PARSER) {
                    ParserScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.navigate(Routes.details(id)) {
                                popUpTo(Routes.PARSER) { inclusive = true }
                            }
                        },
                        onStart = startSession,
                    )
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        container = container,
                        onFinished = { navController.navigate(Routes.SUMMARY) { popUpTo(Routes.PLAYER) { inclusive = true } } },
                        onClose = { navController.popBackStack() },
                    )
                }
                composable(Routes.SUMMARY) {
                    SessionSummaryScreen(
                        container = container,
                        onDone = {
                            sessionController.clearSummary()
                            navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                        },
                        onRepeat = { plan ->
                            scope.launch {
                                val result = sessionController.startWorkout(plan)
                                if (result.isSuccess) {
                                    sessionController.clearSummary()
                                    navController.navigate(Routes.PLAYER) { popUpTo(Routes.SUMMARY) { inclusive = true } }
                                }
                            }
                        },
                    )
                }
                composable(Routes.PROGRESS) {
                    ProgressScreen(
                        container = container,
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onOpenSession = { navController.navigate(Routes.session(it)) },
                        onOpenWorkout = { navController.navigate(Routes.details(it)) },
                        onOpenHealth = { navController.navigate(Routes.HEALTH) },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onOpenSession = { navController.navigate(Routes.session(it)) },
                    )
                }
                composable(Routes.SESSION_DETAIL) { entry ->
                    SessionDetailScreen(
                        container = container,
                        sessionId = entry.arguments?.getString("id").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onRepeat = startSession,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        onOpenVoiceStudio = { navController.navigate(Routes.VOICE_STUDIO) },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) },
                        onOpenHelp = { navController.navigate(Routes.HELP) },
                        onOpenWelcome = { navController.navigate(Routes.WELCOME) },
                        onOpenParser = { navController.navigate(Routes.PARSER) },
                        onOpenHealth = { navController.navigate(Routes.HEALTH) },
                    )
                }
                composable(Routes.VOICE_STUDIO) {
                    VoiceStudioScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable(Routes.BACKUP) {
                    BackupScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                    )
                }
                composable(Routes.HELP) {
                    HelpScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable(Routes.HEALTH) {
                    HealthScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        container = container,
                        onDone = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onRequestNotificationPermission = onRequestNotificationPermission,
                    )
                }
            }
        }

    }
}
