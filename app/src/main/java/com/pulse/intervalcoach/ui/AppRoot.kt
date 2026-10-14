package com.pulse.intervalcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.DeepLinks
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.ui.builders.AdvancedBuilderScreen
import com.pulse.intervalcoach.ui.builders.QuickBuilderScreen
import com.pulse.intervalcoach.ui.goals.GoalsScreen
import com.pulse.intervalcoach.ui.components.PulseAppSidebar
import com.pulse.intervalcoach.ui.components.PulseBreadcrumbBar
import com.pulse.intervalcoach.ui.components.PulseFloatingNavBar
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.PulseNavGroup
import com.pulse.intervalcoach.ui.components.PulseNavItem
import com.pulse.intervalcoach.ui.components.PulseNavRail
import com.pulse.intervalcoach.ui.components.PulseSegmented
import com.pulse.intervalcoach.ui.components.PulseSnackbar
import com.pulse.intervalcoach.ui.health.HealthScreen
import com.pulse.intervalcoach.ui.home.HomeScreen
import com.pulse.intervalcoach.ui.parser.ParserScreen
import com.pulse.intervalcoach.ui.importer.JsonImportScreen
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
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseTheme
import com.pulse.intervalcoach.ui.theme.PulseType
import com.pulse.intervalcoach.ui.theme.pulseColors
import com.pulse.intervalcoach.ui.theme.ThemeMode
import com.pulse.intervalcoach.ui.welcome.WelcomeScreen
import com.pulse.intervalcoach.ui.workouts.CategoryScreen
import com.pulse.intervalcoach.ui.workouts.TemplateGalleryScreen
import com.pulse.intervalcoach.ui.workouts.WorkoutDetailsScreen
import com.pulse.intervalcoach.ui.workouts.WorkoutLibraryScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val TEMPLATES = "templates"
    const val CATEGORY = "category/{key}"
    const val QUICK_BUILDER = "builder/quick"

    /**
     * The advanced builder, with the workout to edit as an optional query argument.
     *
     * `?workoutId={workoutId}` rather than a path segment because the same destination serves two
     * jobs — "build a new workout" (no argument) and "edit this one" (the id) — and an optional
     * argument is what lets one route express both. Without it the builder had no way to know which
     * workout it was opened for, so *Edit* on a details screen always produced a new workout.
     */
    const val ADVANCED_BUILDER = "builder/advanced?workoutId={workoutId}"

    /** The builder without its optional argument: where "new workout" navigates. */
    private const val ADVANCED_BUILDER_PATH = "builder/advanced"
    const val PARSER = "parser"
    const val JSON_IMPORT = "json-import"
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
    const val GOALS = "goals"

    fun details(id: String) = "workout/$id"
    fun session(id: String) = "session/$id"
    fun category(key: String) = "category/$key"

    /** The builder for a new workout when [workoutId] is null, or the editor for that workout. */
    fun advancedBuilder(workoutId: String?): String =
        if (workoutId == null) {
            ADVANCED_BUILDER_PATH
        } else {
            "$ADVANCED_BUILDER_PATH?workoutId=${escapeRouteArg(workoutId)}"
        }
}

/**
 * Percent-encodes what a URI query cannot carry.
 *
 * Ids are UUIDs and registry slugs in practice, so this is only a guard for a workout whose id
 * arrived in a hand-edited import file — but an unescaped space or `&` in that id would otherwise
 * split the route and silently drop the argument, which is precisely the "it opened a new workout"
 * failure the argument exists to avoid.
 */
private fun escapeRouteArg(value: String): String {
    return buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            val unreserved = code in 0x30..0x39 || code in 0x41..0x5A || code in 0x61..0x7A ||
                char == '-' || char == '_' || char == '.' || char == '~'
            if (unreserved) {
                append(char)
            } else {
                append('%').append(code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}

private val primaryDestinations = listOf(
    PulseNavItem(Routes.HOME, "Today", Icons.Filled.Home),
    PulseNavItem(Routes.WORKOUTS, "Workouts", Icons.Filled.FitnessCenter),
    PulseNavItem(Routes.PROGRESS, "Progress", Icons.Filled.Insights),
    PulseNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

/**
 * The sidebar's contents: the three destinations a session starts from, then the library and the
 * system pages. The phone bar and the tablet rail keep the three primary ones, so the shell never
 * grows chrome where there is no room for it.
 */
private val sidebarGroups = listOf(
    PulseNavGroup(
        label = null,
        items = primaryDestinations.take(3),
    ),
    PulseNavGroup(
        label = "Library",
        items = listOf(
            PulseNavItem(Routes.TEMPLATES, "Templates", Icons.Filled.GridView),
            PulseNavItem(Routes.HEALTH, "Health", Icons.Filled.Healing),
        ),
    ),
    PulseNavGroup(
        label = "System",
        items = listOf(
            PulseNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
        ),
    ),
)

/** Destinations that take the whole window: the running session and onboarding. */
private val immersiveRoutes = setOf(Routes.PLAYER, Routes.WELCOME)

/**
 * The application frame.
 *
 * One navigation model, three shapes, and the shape follows the window:
 *
 *  - **phone** (< 600 dp) — a 52 dp chrome bar with the wordmark, the section name and the account
 *    controls, and a hairline-separated bottom bar with an indicator behind the active glyph;
 *  - **tablet portrait** (>= 600 dp) — the same destinations as a 72 dp icon rail;
 *  - **desktop / tablet landscape** (>= 1000 dp) — the full sidebar: wordmark, grouped destinations
 *    with a tinted active row and a 2 dp accent edge, and the appearance control in its footer.
 *
 * Every shape reads the same [PulseNavItem] list, and none of them casts a shadow.
 */
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
            themeMode = ThemeMode.SYSTEM,
            dynamicColor = false,
            highContrast = false,
            reducedMotion = false,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(pulseColors(dark = true, trueBlack = false, highContrast = false).background),
            )
        }
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
        val onPrimaryDestination = route in primaryDestinations.map { it.route }
        val immersive = route in immersiveRoutes
        // One breakpoint ladder, one navigation model: bar, rail, sidebar.
        val width = LocalConfiguration.current.screenWidthDp
        val showSidebar = width >= 1000 && !immersive
        val showRail = !showSidebar && width >= 600 && !immersive

        val select: (String) -> Unit = { destination ->
            navController.navigate(destination) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

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

        val currentIsDark = current.themeMode == ThemeMode.DARK || current.themeMode == ThemeMode.TRUE_BLACK
        val toggleTheme: () -> Unit = {
            scope.launch {
                container.preferences.setTheme(if (currentIsDark) ThemeMode.LIGHT else ThemeMode.DARK)
            }
        }

        Row(Modifier.fillMaxSize()) {
            if (showSidebar) {
                PulseAppSidebar(
                    groups = sidebarGroups,
                    currentRoute = route,
                    onSelect = select,
                    header = { SidebarHeader() },
                    footer = {
                        AppearanceControl(
                            mode = current.themeMode,
                            onSelect = { mode -> scope.launch { container.preferences.setTheme(mode) } },
                        )
                    },
                )
            } else if (showRail) {
                PulseNavRail(
                    items = primaryDestinations.take(3),
                    currentRoute = route,
                    onSelect = select,
                    header = { PulseMark(size = 32.dp) },
                    footer = {
                        PulseIconButton(
                            icon = if (currentIsDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (currentIsDark) "Switch to light theme" else "Switch to dark theme",
                            onClick = toggleTheme,
                        )
                    },
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                if (!immersive) {
                    ShellBreadcrumb(
                        title = routeTitle(route),
                        parent = routeParent(route) ?: stringResource(R.string.app_name),
                        showMark = !showSidebar && !showRail,
                        isDark = currentIsDark,
                        onToggleTheme = toggleTheme,
                        onOpenSettings = { select(Routes.SETTINGS) },
                    )
                }
                Scaffold(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    containerColor = LocalPulseColors.current.background,
                    // Zero here on purpose: screens own their own insets, and the chrome bar above
                    // has already consumed the status bar.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data -> PulseSnackbar(data) }
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
                                onOpenGoals = { navController.navigate(Routes.GOALS) },
                                onResumeSession = { navController.navigate(Routes.PLAYER) },
                                onOpenHealth = { navController.navigate(Routes.HEALTH) },
                                onOpenCategory = { navController.navigate(Routes.category(it.key)) },
                                onOpenLibrary = { navController.navigate(Routes.WORKOUTS) },
                            )
                        }
                        composable(Routes.WORKOUTS) {
                            WorkoutLibraryScreen(
                                container = container,
                                onOpenWorkout = { navController.navigate(Routes.details(it)) },
                                onOpenTemplates = { navController.navigate(Routes.TEMPLATES) },
                                onQuickBuilder = { navController.navigate(Routes.QUICK_BUILDER) },
                                onAdvancedBuilder = { navController.navigate(Routes.advancedBuilder(null)) },
                                onEdit = { navController.navigate(Routes.advancedBuilder(it)) },
                                onParser = { navController.navigate(Routes.PARSER) },
                                onJsonImport = { navController.navigate(Routes.JSON_IMPORT) },
                            )
                        }
                        composable(Routes.CATEGORY) { entry ->
                            CategoryScreen(
                                container = container,
                                categoryKey = entry.arguments?.getString("key").orEmpty(),
                                onBack = { navController.popBackStack() },
                                onOpenWorkout = { navController.navigate(Routes.details(it)) },
                                onStart = startSession,
                                onBuild = { navController.navigate(Routes.QUICK_BUILDER) },
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
                            WorkoutDetailsScreen(
                                container = container,
                                workoutId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { navController.popBackStack() },
                                // The id travels with the route, so the builder edits *this* workout
                                // instead of opening a blank one that saves as a new entry.
                                onEdit = { id -> navController.navigate(Routes.advancedBuilder(id)) },
                                onStart = startSession,
                            )
                        }
                        composable(Routes.QUICK_BUILDER) {
                            QuickBuilderScreen(
                                container = container,
                                onBack = { navController.popBackStack() },
                                onSaved = { id ->
                                    navController.navigate(Routes.details(id)) {
                                        popUpTo(Routes.QUICK_BUILDER) { inclusive = true }
                                    }
                                },
                                onSavedAndStart = startSession,
                            )
                        }
                        composable(
                            route = Routes.ADVANCED_BUILDER,
                            arguments = listOf(
                                navArgument("workoutId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        ) { entry ->
                            val editingId = entry.arguments?.getString("workoutId")
                            AdvancedBuilderScreen(
                                container = container,
                                workoutId = editingId,
                                onBack = { navController.popBackStack() },
                                onSaved = { id ->
                                    // An edit belongs back on the details page it was opened from,
                                    // which re-renders itself from the observed row — so that page is
                                    // returned to rather than stacked a second time. A new workout has
                                    // nowhere to go back to, so it opens its details instead.
                                    val origin = navController.previousBackStackEntry
                                    if (editingId != null && origin?.destination?.route == Routes.DETAILS &&
                                        origin.arguments?.getString("id") == id
                                    ) {
                                        navController.popBackStack()
                                    } else {
                                        navController.navigate(Routes.details(id)) {
                                            popUpTo(Routes.ADVANCED_BUILDER) { inclusive = true }
                                        }
                                    }
                                },
                            )
                        }
                        composable(Routes.JSON_IMPORT) {
                            JsonImportScreen(
                                container = container,
                                onBack = { navController.popBackStack() },
                                onOpenWorkout = { id ->
                                    navController.navigate(Routes.details(id)) {
                                        popUpTo(Routes.JSON_IMPORT) { inclusive = true }
                                    }
                                },
                                onOpenLibrary = {
                                    navController.navigate(Routes.WORKOUTS) {
                                        popUpTo(Routes.WORKOUTS) { inclusive = true }
                                        launchSingleTop = true
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
                        composable(Routes.GOALS) {
                            GoalsScreen(
                                container = container,
                                onBack = { navController.popBackStack() },
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
                                onJsonImport = { navController.navigate(Routes.JSON_IMPORT) },
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
                if (onPrimaryDestination && !showSidebar && !showRail) {
                    PulseFloatingNavBar(
                        items = primaryDestinations,
                        currentRoute = route,
                        onSelect = select,
                    )
                }
            }
        }
    }
}

/** What the breadcrumb calls the current page. Static per route: no screen has to publish a title. */
private fun routeTitle(route: String?): String = when (route) {
    Routes.HOME -> "Today"
    Routes.WORKOUTS -> "Workouts"
    Routes.PROGRESS -> "Progress"
    Routes.SETTINGS -> "Settings"
    Routes.TEMPLATES -> "Templates"
    Routes.CATEGORY -> "Category"
    Routes.QUICK_BUILDER -> "Quick builder"
    Routes.ADVANCED_BUILDER -> "Builder"
    Routes.PARSER -> "Text import"
    Routes.JSON_IMPORT -> "JSON / AI import"
    Routes.DETAILS -> "Workout"
    Routes.HEALTH -> "Health"
    Routes.SUMMARY -> "Session summary"
    Routes.HISTORY -> "History"
    Routes.SESSION_DETAIL -> "Session"
    Routes.VOICE_STUDIO -> "Voice studio"
    Routes.BACKUP -> "Backup"
    Routes.HELP -> "Help"
    else -> "Pulse"
}

/**
 * The section a pushed page belongs to, shown before the leaf so the path is always legible. On a
 * top-level destination the section *is* the leaf, so the bar shows the product name as the root
 * instead — which is also what keeps the breadcrumb and the page heading from saying the same thing
 * twice.
 */
private fun routeParent(route: String?): String? = when (route) {
    Routes.TEMPLATES, Routes.CATEGORY, Routes.DETAILS, Routes.PARSER, Routes.JSON_IMPORT,
    Routes.QUICK_BUILDER, Routes.ADVANCED_BUILDER -> "Workouts"
    Routes.HISTORY, Routes.SESSION_DETAIL -> "Progress"
    Routes.VOICE_STUDIO, Routes.BACKUP, Routes.HELP -> "Settings"
    Routes.HEALTH -> "Today"
    else -> null
}

/**
 * The shell's global bar: a breadcrumb on the left, the account controls on the right.
 *
 * It is mounted once, above the navigation graph, so the back affordance and the global controls are
 * in the same place on all eighteen destinations — the shell knows the stack, a page only has to say
 * what it is (see `PulsePageHeader`). The status bar inset belongs to this bar, which is why no
 * screen ever has to reason about insets.
 */
@Composable
private fun ShellBreadcrumb(
    title: String,
    parent: String?,
    showMark: Boolean,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    PulseBreadcrumbBar(
        title = title,
        parent = parent,
        leading = if (showMark) {
            { PulseMark(size = 20.dp) }
        } else {
            null
        },
        actions = {
            PulseIconButton(
                icon = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = if (isDark) "Switch to light theme" else "Switch to dark theme",
                onClick = onToggleTheme,
            )
            PulseIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings,
            )
        },
    )
}

/** The mark: a small brand block. The only place the accent is allowed to be a solid square. */
@Composable
private fun PulseMark(size: androidx.compose.ui.unit.Dp) {
    val colors = LocalPulseColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(LocalPulseShapes.current.control)
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            tint = colors.onBrand,
            modifier = Modifier.size(size * 0.58f),
        )
    }
}

/** The sidebar's head: the mark and the wordmark, on one 32 dp line. */
@Composable
private fun SidebarHeader() {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseMark(size = 24.dp)
        Spacer(Modifier.width(dimens.s))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = PulseType.SectionLabel,
                color = colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = "Interval coach",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * The appearance control in the sidebar footer. The Settings screen remains the place where the
 * theme is *configured*; this is the shortcut every dashboard has for the one setting people change
 * twice a day.
 */
@Composable
private fun AppearanceControl(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimens.s),
        verticalArrangement = Arrangement.spacedBy(dimens.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (mode == ThemeMode.LIGHT) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(dimens.s))
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
        }
        PulseSegmented(
            options = listOf(
                ThemeMode.SYSTEM to "Auto",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
            ),
            selected = if (mode == ThemeMode.TRUE_BLACK) ThemeMode.DARK else mode,
            onSelect = onSelect,
        )
    }
}
