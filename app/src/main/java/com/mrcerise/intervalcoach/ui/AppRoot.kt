package com.mrcerise.intervalcoach.ui

import androidx.compose.foundation.background
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.DeepLinks
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.AppLanguage
import com.mrcerise.intervalcoach.data.category
import com.mrcerise.intervalcoach.session.RunTracker
import com.mrcerise.intervalcoach.locale.Localized
import com.mrcerise.intervalcoach.ui.builders.AdvancedBuilderScreen
import com.mrcerise.intervalcoach.ui.builders.QuickBuilderScreen
import com.mrcerise.intervalcoach.ui.goals.GoalsScreen
import com.mrcerise.intervalcoach.ui.components.PulseAppSidebar
import com.mrcerise.intervalcoach.ui.components.PulseBreadcrumbBar
import com.mrcerise.intervalcoach.ui.components.PulseFloatingNavBar
import com.mrcerise.intervalcoach.ui.components.ConfirmDialog
import com.mrcerise.intervalcoach.ui.components.PulseIconButton
import com.mrcerise.intervalcoach.ui.components.PulseLogo
import com.mrcerise.intervalcoach.ui.components.PulseNavGroup
import com.mrcerise.intervalcoach.ui.components.PulseNavItem
import com.mrcerise.intervalcoach.ui.components.PulseNavRail
import com.mrcerise.intervalcoach.ui.components.PulseChipGroup
import com.mrcerise.intervalcoach.ui.components.PulseSnackbar
import com.mrcerise.intervalcoach.ui.health.HealthScreen
import com.mrcerise.intervalcoach.ui.home.HomeScreen
import com.mrcerise.intervalcoach.ui.importer.JsonImportScreen
import com.mrcerise.intervalcoach.ui.importer.SharedWorkoutDialog
import com.mrcerise.intervalcoach.ui.player.PlayerScreen
import com.mrcerise.intervalcoach.ui.progress.HistoryScreen
import com.mrcerise.intervalcoach.ui.progress.ProgressScreen
import com.mrcerise.intervalcoach.ui.progress.ScheduleScreen
import com.mrcerise.intervalcoach.ui.progress.SessionDetailScreen
import com.mrcerise.intervalcoach.ui.settings.HelpScreen
import com.mrcerise.intervalcoach.ui.settings.SettingsScreen
import com.mrcerise.intervalcoach.ui.settings.VoiceStudioScreen
import com.mrcerise.intervalcoach.ui.summary.SessionSummaryScreen
import com.pulse.engine.Validation
import com.pulse.engine.WorkoutPlan
import com.pulse.sync.ShareCode
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseMotion
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseTheme
import com.mrcerise.intervalcoach.ui.theme.PulseType
import com.mrcerise.intervalcoach.ui.theme.pulseColors
import com.mrcerise.intervalcoach.ui.theme.ThemeMode
import com.mrcerise.intervalcoach.ui.watchsession.WatchSessionScreen
import com.mrcerise.intervalcoach.ui.welcome.WelcomeScreen
import com.mrcerise.intervalcoach.ui.workouts.CategoryScreen
import com.mrcerise.intervalcoach.ui.workouts.WorkoutDetailsScreen
import com.mrcerise.intervalcoach.ui.workouts.WorkoutLibraryScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
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
    const val JSON_IMPORT = "json-import"
    const val DETAILS = "workout/{id}"
    const val PLAYER = "player"
    const val SUMMARY = "summary"
    const val HISTORY = "history"
    const val SCHEDULE = "schedule?workoutId={workoutId}"
    private const val SCHEDULE_PATH = "schedule"
    const val SESSION_DETAIL = "session/{id}"
    const val VOICE_STUDIO = "settings/voice"
    const val HELP = "settings/help"
    const val HEALTH = "health"
    const val WELCOME = "welcome"
    const val GOALS = "goals"
    const val WATCH_SESSION = "watch-session"

    fun details(id: String) = "workout/$id"
    fun schedule(workoutId: String?): String =
        if (workoutId == null) SCHEDULE_PATH else "$SCHEDULE_PATH?workoutId=${escapeRouteArg(workoutId)}"
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

/** The destinations the bottom bar, the rail and the sidebar all agree on. */
private val primaryRoutes = listOf(Routes.HOME, Routes.WORKOUTS, Routes.PROGRESS, Routes.SETTINGS)

/**
 * The primary destinations, with their labels read as resources.
 *
 * The shell is deliberately resource-backed rather than literal: it is the first thing a user looks at
 * after choosing a language in Settings → Appearance, and a bar that stays in English underneath a
 * French app makes the setting look broken.
 */
@Composable
private fun primaryDestinations(): List<PulseNavItem> = listOf(
    PulseNavItem(Routes.HOME, stringResource(R.string.nav_today), Icons.Filled.Home),
    PulseNavItem(Routes.WORKOUTS, stringResource(R.string.nav_workouts), Icons.Filled.FitnessCenter),
    PulseNavItem(Routes.PROGRESS, stringResource(R.string.nav_progress), Icons.Filled.Insights),
    PulseNavItem(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Filled.Settings),
)

/**
 * The sidebar's contents: the three destinations a session starts from, then the library and the
 * system pages. The phone bar and the tablet rail keep the three primary ones, so the shell never
 * grows chrome where there is no room for it.
 */
@Composable
private fun sidebarGroups(): List<PulseNavGroup> = listOf(
    PulseNavGroup(
        label = null,
        items = primaryDestinations().take(3),
    ),
    PulseNavGroup(
        label = stringResource(R.string.nav_group_library),
        items = listOf(
            PulseNavItem(Routes.HEALTH, stringResource(R.string.nav_health), Icons.Filled.Healing),
        ),
    ),
    PulseNavGroup(
        label = stringResource(R.string.nav_group_system),
        items = listOf(
            PulseNavItem(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Filled.Settings),
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
 *
 * The entry point has two layers. This one resolves the selected language and re-provides
 * [androidx.compose.ui.platform.LocalContext] for it, so [PulseAppRootContent] — including the
 * placeholder drawn while DataStore warms up, which is why the app never flashes the system language
 * first — reads every string through that context. [com.mrcerise.intervalcoach.locale.AppLocale] explains
 * why the override lives here rather than on the activity.
 */
@Composable
fun PulseAppRoot(
    container: AppContainer,
    deeplinks: StateFlow<String?>,
    onDeeplinkConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: (() -> Unit) -> Unit,
) {
    val languageFlow = remember(container.preferences) {
        container.preferences.flow.map { it.appLanguage }
    }
    val language by languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.SYSTEM)
    Localized(language) {
        PulseAppRootContent(
            container = container,
            deeplinks = deeplinks,
            onDeeplinkConsumed = onDeeplinkConsumed,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}

/** The frame itself: theme, navigation host, deep links and the snackbar that reports failures. */
@Composable
private fun PulseAppRootContent(
    container: AppContainer,
    deeplinks: StateFlow<String?>,
    onDeeplinkConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: (() -> Unit) -> Unit,
) {
    val strings = LocalResources.current
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
        val motion = LocalPulseMotion.current
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val pendingStartPlan = remember { mutableStateOf<WorkoutPlan?>(null) }
        val pendingLocationStart = remember { mutableStateOf<Pair<WorkoutPlan, Boolean>?>(null) }
        // A workout received by QR code or `pulse://import` link, waiting for the user to accept it.
        val pendingShared = remember { mutableStateOf<WorkoutPlan?>(null) }
        val sharedSaving = remember { mutableStateOf(false) }
        val openSharedCode: (String) -> Unit = { text ->
            val plan = ShareCode.decode(text)?.takeIf { Validation.validate(it).isEmpty() }
            if (plan == null) {
                scope.launch { snackbarHostState.showSnackbar(strings.getString(R.string.share_code_invalid)) }
            } else {
                pendingShared.value = plan
            }
        }

        // Starting over an active session is always an explicit user decision. The controller also
        // refuses silent replacement, so alternate callers cannot bypass this UI check.
        val performStart: (WorkoutPlan, Boolean) -> Unit = { plan, replaceActive ->
            scope.launch {
                val result = sessionController.startWorkout(plan, replaceActiveSession = replaceActive)
                if (result.isSuccess) {
                    onRequestNotificationPermission()
                    sessionController.clearSummary()
                    val repeating = navController.currentDestination?.route == Routes.SUMMARY
                    navController.navigate(Routes.PLAYER) {
                        if (repeating) popUpTo(Routes.SUMMARY) { inclusive = true }
                    }
                } else {
                    snackbarHostState.showSnackbar(strings.getString(R.string.workout_start_failed))
                }
            }
        }
        // Explain screen-off tracking before permission and start only after the result, so
        // RunTracker and the foreground service see the grant on the very first run.
        val launchStart: (WorkoutPlan, Boolean) -> Unit = { plan, replaceActive ->
            if (plan.category.capabilities.supportsRaceInfo && !RunTracker.hasLocationPermission(context)) {
                pendingLocationStart.value = plan to replaceActive
            } else {
                performStart(plan, replaceActive)
            }
        }
        val startSession: (WorkoutPlan) -> Unit = { plan ->
            if (activeSession != null) pendingStartPlan.value = plan else launchStart(plan, false)
        }
        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route
        val onPrimaryDestination = route in primaryRoutes
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
                    val plan = container.workouts.plan(id)
                    if (plan == null) {
                        snackbarHostState.showSnackbar(strings.getString(R.string.external_start_unavailable))
                    } else {
                        // pulse:// is an exported entry point; a matching URI is not proof that a
                        // trusted widget or shortcut launched it. Require an in-app confirmation.
                        pendingStartPlan.value = plan
                    }
                }
                link.startsWith("pulse://workout/") -> {
                    val id = link.removePrefix("pulse://workout/")
                    navController.navigate(Routes.details(id))
                }
                // Never saved directly: the shared-workout dialog asks first.
                ShareCode.isShareCode(link) -> openSharedCode(link)
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

        Row(Modifier.fillMaxSize()) {
            if (showSidebar) {
                PulseAppSidebar(
                    groups = sidebarGroups(),
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
                    items = primaryDestinations().take(3),
                    currentRoute = route,
                    onSelect = select,
                    header = { PulseLogo(size = 32.dp) },
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
                        enterTransition = { fadeIn(tween(motion.standard())) },
                        exitTransition = { fadeOut(tween(motion.fast())) },
                        popEnterTransition = { fadeIn(tween(motion.standard())) },
                        popExitTransition = { fadeOut(tween(motion.fast())) },
                        modifier = Modifier.padding(padding),
                    ) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                container = container,
                                onOpenWorkout = { navController.navigate(Routes.details(it)) },
                                onStartWorkout = startSession,
                                onQuickStart = { navController.navigate(Routes.QUICK_BUILDER) },
                                onOpenProgress = { navController.navigate(Routes.PROGRESS) },
                                onOpenGoals = { navController.navigate(Routes.GOALS) },
                                onResumeSession = { navController.navigate(Routes.PLAYER) },
                                onOpenHealth = { navController.navigate(Routes.HEALTH) },
                                onOpenCategory = { navController.navigate(Routes.category(it.key)) },
                                onOpenLibrary = { navController.navigate(Routes.WORKOUTS) },
                                onOpenWatchSession = { navController.navigate(Routes.WATCH_SESSION) },
                            )
                        }
                        composable(Routes.WORKOUTS) {
                            WorkoutLibraryScreen(
                                container = container,
                                onOpenWorkout = { navController.navigate(Routes.details(it)) },
                                onQuickBuilder = { navController.navigate(Routes.QUICK_BUILDER) },
                                onAdvancedBuilder = { navController.navigate(Routes.advancedBuilder(null)) },
                                onStartWorkout = startSession,
                                onEdit = { navController.navigate(Routes.advancedBuilder(it)) },
                                onJsonImport = { navController.navigate(Routes.JSON_IMPORT) },
                                onImportCode = openSharedCode,
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
                        composable(Routes.DETAILS) { entry ->
                            WorkoutDetailsScreen(
                                container = container,
                                workoutId = entry.arguments?.getString("id").orEmpty(),
                                onBack = { navController.popBackStack() },
                                // The id travels with the route, so the builder edits *this* workout
                                // instead of opening a blank one that saves as a new entry.
                                onEdit = { id -> navController.navigate(Routes.advancedBuilder(id)) },
                                onStart = startSession,
                                onSchedule = { id -> navController.navigate(Routes.schedule(id)) },
                                onOpenWorkout = { id ->
                                    navController.navigate(Routes.details(id)) {
                                        popUpTo(Routes.DETAILS) { inclusive = true }
                                    }
                                },
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
                        composable(Routes.PLAYER) {
                            PlayerScreen(
                                container = container,
                                onFinished = { navController.navigate(Routes.SUMMARY) { popUpTo(Routes.PLAYER) { inclusive = true } } },
                                onClose = { navController.popBackStack() },
                            )
                        }
                        composable(Routes.WATCH_SESSION) {
                            WatchSessionScreen(
                                container = container,
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
                                onRepeat = startSession,
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
                                onOpenSchedule = { navController.navigate(Routes.schedule(null)) },
                            )
                        }
                        composable(
                            route = Routes.SCHEDULE,
                            arguments = listOf(
                                navArgument("workoutId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        ) { entry ->
                            ScheduleScreen(
                                container = container,
                                workoutId = entry.arguments?.getString("workoutId"),
                                onBack = { navController.popBackStack() },
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
                                onOpenHelp = { navController.navigate(Routes.HELP) },
                                onOpenWelcome = { navController.navigate(Routes.WELCOME) },
                                onOpenHealth = { navController.navigate(Routes.HEALTH) },
                            )
                        }
                        composable(Routes.VOICE_STUDIO) {
                            VoiceStudioScreen(container = container, onBack = { navController.popBackStack() })
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
                        items = primaryDestinations(),
                        currentRoute = route,
                        onSelect = select,
                    )
                }
            }
        }

        pendingLocationStart.value?.let { (plan, replaceActive) ->
            AlertDialog(
                onDismissRequest = { pendingLocationStart.value = null },
                title = { Text(stringResource(R.string.privacy_location_title)) },
                text = { Text(stringResource(R.string.privacy_location_notice)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingLocationStart.value = null
                        onRequestLocationPermission { performStart(plan, replaceActive) }
                    }) { Text(stringResource(R.string.privacy_location_enable)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingLocationStart.value = null
                        performStart(plan, replaceActive)
                    }) { Text(stringResource(R.string.privacy_location_skip)) }
                },
            )
        }
        pendingShared.value?.let { plan ->
            SharedWorkoutDialog(
                plan = plan,
                busy = sharedSaving.value,
                onAdd = {
                    sharedSaving.value = true
                    scope.launch {
                        val id = try {
                            container.workouts.importShared(plan)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                        sharedSaving.value = false
                        pendingShared.value = null
                        if (id != null) {
                            navController.navigate(Routes.details(id))
                            snackbarHostState.showSnackbar(strings.getString(R.string.share_import_added))
                        } else {
                            snackbarHostState.showSnackbar(strings.getString(R.string.share_import_failed))
                        }
                    }
                },
                onDismiss = { pendingShared.value = null },
            )
        }
        pendingStartPlan.value?.let { plan ->
            val activeName = activeSession?.plan?.name
            ConfirmDialog(
                title = stringResource(R.string.external_start_title),
                body = if (activeName == null) {
                    stringResource(R.string.external_start_body, plan.name)
                } else {
                    stringResource(R.string.external_start_replace_body, plan.name, activeName)
                },
                confirmLabel = stringResource(R.string.details_start),
                dismissLabel = stringResource(R.string.cancel),
                onConfirm = {
                    pendingStartPlan.value = null
                    launchStart(plan, activeSession != null)
                },
                onDismiss = { pendingStartPlan.value = null },
            )
        }
    }
}

/**
 * What the breadcrumb calls the current page. Static per route — no screen has to publish a title — and
 * read from the same resources the screens title themselves with wherever one exists, so the breadcrumb
 * follows the chosen language like the page under it.
 */
@Composable
private fun routeTitle(route: String?): String = when (route) {
    Routes.HOME -> stringResource(R.string.nav_today)
    Routes.WORKOUTS -> stringResource(R.string.workouts_title)
    Routes.PROGRESS -> stringResource(R.string.progress_title)
    Routes.SETTINGS -> stringResource(R.string.settings_title)
    Routes.CATEGORY -> stringResource(R.string.title_category)
    Routes.QUICK_BUILDER -> stringResource(R.string.builder_quick_title)
    Routes.ADVANCED_BUILDER -> stringResource(R.string.title_builder)
    Routes.JSON_IMPORT -> stringResource(R.string.json_title)
    Routes.DETAILS -> stringResource(R.string.title_details)
    Routes.HEALTH -> stringResource(R.string.nav_health)
    Routes.SUMMARY -> stringResource(R.string.summary_title)
    Routes.HISTORY -> stringResource(R.string.history_title)
    Routes.SCHEDULE -> stringResource(R.string.planner_add)
    Routes.SESSION_DETAIL -> stringResource(R.string.session_detail_title)
    Routes.VOICE_STUDIO -> stringResource(R.string.voice_title)
    Routes.HELP -> stringResource(R.string.help_title)
    else -> stringResource(R.string.app_name)
}

/**
 * The section a pushed page belongs to, shown before the leaf so the path is always legible. On a
 * top-level destination the section *is* the leaf, so the bar shows the product name as the root
 * instead — which is also what keeps the breadcrumb and the page heading from saying the same thing
 * twice.
 */
@Composable
private fun routeParent(route: String?): String? = when (route) {
    Routes.CATEGORY, Routes.DETAILS, Routes.JSON_IMPORT,
    Routes.QUICK_BUILDER, Routes.ADVANCED_BUILDER -> stringResource(R.string.workouts_title)

    Routes.HISTORY, Routes.SESSION_DETAIL, Routes.SCHEDULE -> stringResource(R.string.progress_title)
    Routes.VOICE_STUDIO, Routes.HELP -> stringResource(R.string.settings_title)
    Routes.HEALTH -> stringResource(R.string.nav_today)
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
    onOpenSettings: () -> Unit,
) {
    val strings = LocalResources.current
    PulseBreadcrumbBar(
        title = title,
        parent = parent,
        leading = if (showMark) {
            { PulseLogo(size = 20.dp) }
        } else {
            null
        },
        actions = {
            PulseIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = strings.getString(R.string.nav_settings),
                onClick = onOpenSettings,
            )
        },
    )
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
        PulseLogo(size = 24.dp)
        Spacer(Modifier.width(dimens.s))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
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
    val strings = LocalResources.current
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
                text = strings.getString(R.string.settings_appearance),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
        }
        PulseChipGroup(
            options = listOf(
                ThemeMode.SYSTEM to strings.getString(R.string.theme_system),
                ThemeMode.LIGHT to strings.getString(R.string.theme_light),
                ThemeMode.DARK to strings.getString(R.string.theme_dark),
            ),
            selected = if (mode == ThemeMode.TRUE_BLACK) ThemeMode.DARK else mode,
            onSelect = onSelect,
        )
    }
}
