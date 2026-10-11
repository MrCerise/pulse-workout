package com.pulse.intervalcoach.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.CategoryDefaults
import com.pulse.intervalcoach.data.DailyActivity
import com.pulse.intervalcoach.data.QuickWorkoutFactory
import com.pulse.intervalcoach.data.WorkoutCategories
import com.pulse.intervalcoach.data.WorkoutCategory
import com.pulse.intervalcoach.data.WorkoutSummary
import com.pulse.intervalcoach.data.categoryOrCustom
import com.pulse.intervalcoach.data.db.ReminderEntity
import com.pulse.intervalcoach.ui.components.ActivityBar
import com.pulse.intervalcoach.ui.components.ActivityChart
import com.pulse.intervalcoach.ui.components.CategoryChip
import com.pulse.intervalcoach.ui.components.DurationStepper
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.LoadingBox
import com.pulse.intervalcoach.ui.components.NumberStepper
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.ProgressRing
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseGradientCard
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val favorites: List<WorkoutSummary> = emptyList(),
    val recent: List<WorkoutSummary> = emptyList(),
    val nextPlanned: PlannedInfo? = null,
    val week: List<DailyActivity> = emptyList(),
    val weekSessions: Int = 0,
    val weekMillis: Long = 0L,
    val activePlanName: String? = null,
    val loading: Boolean = true,
)

data class PlannedInfo(val reminder: ReminderEntity, val workoutName: String)

class HomeViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _quickState = MutableStateFlow(QuickState())
    val quickState: StateFlow<QuickState> = _quickState.asStateFlow()

    data class QuickState(val work: Long = 40_000, val rest: Long = 20_000, val rounds: Int = 8, val prepMillis: Long = 10_000)

    val state: StateFlow<HomeUiState> = combine(
        container.workouts.summaries,
        container.sessions.observeRecent(6),
        container.reminders.enabled,
        container.sessionController.active,
        combine(_quickState, container.preferences.flow) { quick, _ -> quick },
    ) { summaries, recentSessions, reminders, active, _ ->
        val byId = summaries.associateBy { it.id }
        val recent = recentSessions.mapNotNull { session -> byId[session.workoutId] }
            .distinctBy { it.id }
            .take(5)
        val nextReminder = reminders.firstOrNull()
        val activities = container.sessions.weeklyActivity(7)
        HomeUiState(
            favorites = summaries.filter { it.isFavorite }.take(8),
            recent = recent,
            nextPlanned = nextReminder?.let { PlannedInfo(it, byId[it.workoutId]?.name ?: "Deleted workout") },
            week = activities,
            weekSessions = activities.sumOf { it.sessionCount },
            weekMillis = activities.sumOf { it.activeMillis },
            activePlanName = active?.plan?.name,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            val prefs = container.preferences.current()
            _quickState.value = QuickState(
                work = prefs.defaultWorkMillis,
                rest = prefs.defaultRestMillis,
                rounds = prefs.defaultRounds,
                prepMillis = prefs.defaultPreparationMillis,
            )
        }
    }

    fun updateQuick(transform: (QuickState) -> QuickState) {
        _quickState.value = transform(_quickState.value)
    }

    /** Builds the ad-hoc work/rest workout used by Quick Start. */
    fun buildQuickPlan(): WorkoutPlan = QuickWorkoutFactory.build(
        workMillis = _quickState.value.work,
        restMillis = _quickState.value.rest,
        rounds = _quickState.value.rounds,
        preparationMillis = _quickState.value.prepMillis,
    )

    suspend fun planFor(id: String): WorkoutPlan? = container.workouts.plan(id)
    suspend fun favorite(id: String, favorite: Boolean) = container.workouts.setFavorite(id, favorite)
}

/**
 * A one-tap start tile built straight from a category's [CategoryDefaults].
 *
 * This is what the category registry buys the Today screen: the presets are no longer four
 * hand-written blocks of arguments, they are four categories asked for their own starting point, so
 * a new category can appear here without this screen being edited.
 */
data class CategoryPreset(val category: WorkoutCategory, val plan: WorkoutPlan)

/** The presets shown on Today, in the order they are trained. */
fun buildCategoryPresets(): List<CategoryPreset> {
    val keys = listOf("TABATA", "HIIT", "RUN_WALK", "STRENGTH")
    return keys.mapNotNull { key ->
        val category = WorkoutCategories.byKey(key)
        val d: CategoryDefaults = category.defaults
        val plan = if (key == "TABATA") {
            QuickWorkoutFactory.tabata()
        } else {
            QuickWorkoutFactory.build(
                workMillis = d.workMillis,
                restMillis = d.restMillis,
                rounds = d.rounds,
                preparationMillis = d.preparationMillis,
                includeFinalRest = d.includeFinalRest,
                name = category.title,
                type = category.type,
                workName = d.workName,
                restName = d.restName,
            )
        }
        CategoryPreset(category, plan)
    }
}

/** The categories offered as one-tap starts in the category rail. */
val homeStartableCategories: List<WorkoutCategory>
    get() = listOf("HIIT", "TABATA", "EMOM", "AMRAP", "STRENGTH", "RUN_WALK", "STRETCHING", "BREATHING", "FOCUS")
        .map { WorkoutCategories.byKey(it) }

private const val WEEKLY_GOAL_MILLIS = 150L * 60L * 1000L // WHO 150 min/week guideline

/**
 * Today.
 *
 * The screen answers one question — "what do I do right now?" — so it is ordered by urgency and
 * every section is a decision rather than a report:
 *
 * 1. a **hero** with the greeting, the week's progress and the one button that starts something;
 * 2. a **live session** card when a workout is running;
 * 3. a **category rail**, because most people know the *kind* of session they want before they know
 *    its name;
 * 4. **quick start** with the four presets and the custom work/rest/rounds steppers;
 * 5. the week's chart, health, favourites, what is planned next and what you did last.
 *
 * Every number on it is a recorded one; nothing is estimated.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenWorkout: (String) -> Unit,
    onStartWorkout: (WorkoutPlan) -> Unit,
    onQuickStart: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenTemplates: () -> Unit,
    onResumeSession: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenCategory: ((WorkoutCategory) -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
) {
    val viewModel: HomeViewModel = viewModel(initializer = { HomeViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quick by viewModel.quickState.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val activeSession by container.sessionController.active.collectAsStateWithLifecycle()
    val snapshot by container.sessionController.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val healthSnapshot by container.health.snapshot.collectAsStateWithLifecycle()
    val presets = remember { buildCategoryPresets() }
    val categories = remember { homeStartableCategories }

    PulseScaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.s,
                bottom = dimens.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            // --- Hero ---------------------------------------------------------------------------
            item {
                TodayHero(
                    weekMillis = state.weekMillis,
                    weekSessions = state.weekSessions,
                    onStart = { onStartWorkout(viewModel.buildQuickPlan()) },
                )
            }

            // --- Live session -------------------------------------------------------------------
            if (activeSession != null) {
                item {
                    PulseCard(onClick = onResumeSession) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeroIconTile(icon = Icons.Filled.PlayArrow, tint = colors.accent, container = colors.accentTint)
                            Spacer(Modifier.width(dimens.m))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(dimens.microGap),
                            ) {
                                Text(
                                    text = "Workout in progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${activeSession?.plan?.name} · ${snapshot.step?.name ?: ""} · " +
                                        formatDuration(snapshot.stepRemainingMillis) + " left",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textMuted)
                        }
                    }
                }
            }

            // --- Start by category ---------------------------------------------------------------
            item {
                SectionHeader(
                    text = "Start by category",
                    actionLabel = if (onOpenLibrary != null) "All" else null,
                    onAction = onOpenLibrary,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    items(categories, key = { it.key }) { category ->
                        CategoryStartTile(
                            category = category,
                            duration = remember(category) { presetTotalMillis(category) },
                            onClick = {
                                if (onOpenCategory != null) {
                                    onOpenCategory(category)
                                } else {
                                    onStartWorkout(planForCategory(category))
                                }
                            },
                        )
                    }
                }
            }

            // --- Quick start ---------------------------------------------------------------------
            item {
                PulseCard {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeroIconTile(icon = Icons.Filled.Bolt, tint = colors.accent, container = colors.accentTint)
                            Spacer(Modifier.width(dimens.m))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Quick start",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Presets start immediately with their real duration.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(dimens.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                            presets.take(2).forEach { preset ->
                                PresetTile(
                                    title = preset.category.title,
                                    icon = preset.category.icon,
                                    hue = colors.categoryColor(preset.category.hue),
                                    tint = colors.categoryTint(preset.category.hue),
                                    duration = presetDuration(preset.plan),
                                    onClick = { onStartWorkout(preset.plan) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(dimens.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                            presets.drop(2).forEach { preset ->
                                PresetTile(
                                    title = preset.category.title,
                                    icon = preset.category.icon,
                                    hue = colors.categoryColor(preset.category.hue),
                                    tint = colors.categoryTint(preset.category.hue),
                                    duration = presetDuration(preset.plan),
                                    onClick = { onStartWorkout(preset.plan) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Spacer(Modifier.height(dimens.l))
                        SectionHeader(text = "Custom · ${formatDuration(quickTotal(quick))}")
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                            DurationStepper(
                                label = "Work",
                                millis = quick.work,
                                onChange = { viewModel.updateQuick { s -> s.copy(work = it) } },
                                modifier = Modifier.weight(1f),
                            )
                            DurationStepper(
                                label = "Rest",
                                millis = quick.rest,
                                onChange = { viewModel.updateQuick { s -> s.copy(rest = it) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(dimens.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                            NumberStepper(
                                label = "Rounds",
                                value = quick.rounds,
                                onChange = { viewModel.updateQuick { s -> s.copy(rounds = it) } },
                                modifier = Modifier.weight(1f),
                            )
                            DurationStepper(
                                label = "Prep",
                                millis = quick.prepMillis,
                                onChange = { viewModel.updateQuick { s -> s.copy(prepMillis = it) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(dimens.m))
                        PrimaryActionButton(
                            text = "Start now",
                            onClick = { onStartWorkout(viewModel.buildQuickPlan()) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Filled.PlayArrow,
                        )
                        Spacer(Modifier.height(dimens.s))
                        SecondaryActionButton(
                            text = "Open the quick builder instead",
                            onClick = onQuickStart,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // --- Weekly activity -----------------------------------------------------------------
            item { SectionHeader(text = "This week", actionLabel = "Progress", onAction = onOpenProgress) }
            item {
                PulseCard {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(
                                progress = (state.weekMillis.toFloat() / WEEKLY_GOAL_MILLIS).coerceIn(0f, 1f),
                                color = colors.work,
                                trackColor = colors.track,
                                strokeWidth = 7.dp,
                                gradient = true,
                                modifier = Modifier.size(84.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${(state.weekMillis / 60000L).toInt()}",
                                        style = PulseType.NumericMedium,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = "min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textMuted,
                                    )
                                }
                            }
                            Spacer(Modifier.width(dimens.l))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(dimens.microGap),
                            ) {
                                Text(
                                    text = "${state.weekSessions} session${if (state.weekSessions == 1) "" else "s"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "${formatDuration(state.weekMillis)} of the 150 min goal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(dimens.l))
                        ActivityChart(
                            bars = state.week.map {
                                ActivityBar(
                                    label = it.date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())),
                                    value = it.activeMillis,
                                )
                            },
                            height = dimens.chartHeight - 40.dp,
                            emptyLabel = "No sessions recorded this week yet.",
                        )
                    }
                }
            }

            // --- Health snapshot (only when something is actually connected) ----------------------
            if (healthSnapshot.source != null) {
                item { SectionHeader(text = "Today's health", trailing = healthSnapshot.source.orEmpty()) }
                item {
                    PulseCard(onClick = onOpenHealth) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.m)) {
                            StatTile(
                                label = "Steps",
                                value = healthSnapshot.stepsToday?.toString() ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Heart rate",
                                value = healthSnapshot.latestHeartRate?.let { "$it bpm" } ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // --- Favorites rail -------------------------------------------------------------------
            if (state.favorites.isNotEmpty()) {
                item { SectionHeader("Favorites") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.cardGap)) {
                        items(state.favorites, key = { it.id }) { workout ->
                            FavoriteCard(
                                workout = workout,
                                onOpen = { onOpenWorkout(workout.id) },
                                onStart = { scope.launch { viewModel.planFor(workout.id)?.let(onStartWorkout) } },
                            )
                        }
                    }
                }
            }

            // --- Next planned ----------------------------------------------------------------------
            state.nextPlanned?.let { planned ->
                item { SectionHeader("Next planned") }
                item {
                    PulseCard(onClick = { onOpenWorkout(planned.reminder.workoutId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeroIconTile(
                                icon = Icons.Filled.Schedule,
                                tint = colors.prepare,
                                container = colors.warningTint,
                            )
                            Spacer(Modifier.width(dimens.m))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(dimens.microGap),
                            ) {
                                Text(
                                    text = planned.workoutName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatPlanned(planned.reminder.scheduledAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textMuted)
                        }
                    }
                }
            }

            // --- Recent ----------------------------------------------------------------------------
            if (state.recent.isNotEmpty()) {
                item { SectionHeader("Recent") }
                items(state.recent, key = { it.id }) { workout ->
                    PulseCard(onClick = { onOpenWorkout(workout.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryChip(
                                category = workout.categoryOrCustom(),
                                showTitle = false,
                            )
                            Spacer(Modifier.width(dimens.m))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(dimens.microGap),
                            ) {
                                Text(
                                    text = workout.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${workout.intervalCount} intervals · " +
                                        (if (workout.hasOpenEnded) "open-ended" else formatDuration(workout.knownDurationMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textMuted)
                        }
                    }
                }
            }

            if (state.loading) {
                item { LoadingBox(label = "Loading your training log…", modifier = Modifier.height(160.dp)) }
            }

            if (state.favorites.isEmpty() && state.recent.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        title = "Nothing here yet",
                        body = "Star a workout to pin it here, or press Start on Quick start above. Your finished sessions will appear once you complete one.",
                        icon = Icons.Filled.FitnessCenter,
                        actionLabel = "Browse templates",
                        onAction = onOpenTemplates,
                    )
                }
            }
        }
    }
}

/**
 * The gradient hero: the greeting, the week's progress against the 150-minute guideline, and the one
 * button on the screen that starts a session.
 *
 * It is the only place the accent gradient is used at this scale, which is deliberate — on a screen
 * with a dozen cards, the hero is the one that should be impossible to miss.
 */
@Composable
private fun TodayHero(
    weekMillis: Long,
    weekSessions: Int,
    onStart: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    PulseGradientCard {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = greeting(),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onAccentGradient,
            )
            Spacer(Modifier.height(dimens.microGap))
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onAccentGradient.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(dimens.l))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${(weekMillis / 60000L).toInt()}",
                    style = PulseType.NumericLarge,
                    color = colors.onAccentGradient,
                )
                Spacer(Modifier.width(dimens.xs))
                Text(
                    text = "/ 150 min this week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onAccentGradient.copy(alpha = 0.82f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(dimens.s))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.barHeightCompact)
                    .background(
                        colors.onAccentGradient.copy(alpha = 0.28f),
                        LocalPulseShapes.current.chip,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((weekMillis.toFloat() / WEEKLY_GOAL_MILLIS).coerceIn(0f, 1f))
                        .height(dimens.barHeightCompact)
                        .background(colors.onAccentGradient, LocalPulseShapes.current.chip),
                )
            }
            Spacer(Modifier.height(dimens.l))
            Surface(
                onClick = onStart,
                shape = LocalPulseShapes.current.chip,
                color = colors.onAccentGradient,
                contentColor = colors.accent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = dimens.m),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.background,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(dimens.s))
                    Text(
                        text = if (weekSessions == 0) "Start your first session" else "Quick start",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.background,
                    )
                }
            }
        }
    }
}

/**
 * A one-tap category tile: the category's glyph in its own hue.
 *
 * The rail exists because most people decide "intervals" or "a run" before they decide "HIIT
 * Starter 3", and because the registry makes the tile free to build for any category.
 */
@Composable
private fun CategoryStartTile(
    category: WorkoutCategory,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val hue = colors.categoryColor(category.hue)
    Surface(
        onClick = onClick,
        modifier = modifier.width(112.dp),
        shape = LocalPulseShapes.current.panel,
        color = colors.categoryTint(category.hue),
        contentColor = hue,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.m),
            verticalArrangement = Arrangement.spacedBy(dimens.s),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = hue,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = category.title,
                style = MaterialTheme.typography.labelMedium,
                color = hue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = duration,
                style = PulseType.NumericSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

/** A favourite in the horizontal rail, with its category hue on the card. */
@Composable
private fun FavoriteCard(
    workout: WorkoutSummary,
    onOpen: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val category = workout.categoryOrCustom()
    PulseCard(
        modifier = Modifier.width(dimens.carouselCardWidth),
        onClick = onOpen,
        tint = colors.categoryColor(category.hue),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = colors.categoryColor(category.hue),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(dimens.xs))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(dimens.xs))
            Text(
                text = workout.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(dimens.xs))
            Text(
                text = if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis),
                style = PulseType.NumericSmall,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(dimens.m))
            SecondaryActionButton(
                text = "Start",
                onClick = onStart,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The 40 dp icon tile used at the head of a card: a tinted rounded square that identifies the
 * section without introducing decoration. The tint carries meaning (accent for live, prepare for
 * scheduled).
 */
@Composable
private fun HeroIconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = LocalPulseColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container ?: colors.neutralFill, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** A preset tile in the quick-start grid, coloured by its category. */
@Composable
private fun PresetTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hue: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = LocalPulseShapes.current.panel,
        color = tint,
        contentColor = hue,
        border = null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.m),
            verticalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(hue.copy(alpha = 0.18f), LocalPulseShapes.current.chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = hue, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = duration,
                style = PulseType.NumericSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

private fun greeting(): String = when (java.time.LocalTime.now(ZoneId.systemDefault()).hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

private fun presetDuration(plan: WorkoutPlan): String = runCatching {
    com.pulse.engine.TimelineExpander.expand(plan).let {
        if (it.hasOpenEnded) "—" else formatDuration(it.knownMillis)
    }
}.getOrDefault("—")

/** The duration a one-tap start from a category produces, from that category's own defaults. */
private fun presetTotalMillis(category: WorkoutCategory): String {
    val d = category.defaults
    return formatDuration(d.preparationMillis + d.rounds * (d.workMillis + d.restMillis) + d.cooldownMillis)
}

/** Builds a runnable plan straight from a category's defaults, without going through a builder. */
private fun planForCategory(category: WorkoutCategory): WorkoutPlan {
    val d = category.defaults
    return QuickWorkoutFactory.build(
        workMillis = d.workMillis,
        restMillis = d.restMillis,
        rounds = d.rounds,
        preparationMillis = d.preparationMillis,
        cooldownMillis = d.cooldownMillis,
        includeFinalRest = d.includeFinalRest,
        name = category.title,
        type = category.type,
        workName = d.workName,
        restName = d.restName,
    )
}

private fun quickTotal(quick: HomeViewModel.QuickState): Long =
    quick.prepMillis + quick.rounds * (quick.work + quick.rest)

private fun formatPlanned(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val today = LocalDateTime.now(zone).toLocalDate()
    return when (dateTime.toLocalDate()) {
        today -> "Today, " + dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        today.plusDays(1) -> "Tomorrow, " + dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        else -> dateTime.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault()))
    }
}
