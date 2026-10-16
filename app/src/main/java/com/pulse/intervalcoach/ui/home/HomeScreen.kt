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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.PhaseKind
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.CategoryDefaults
import com.pulse.intervalcoach.data.DailyActivity
import com.pulse.intervalcoach.data.QuickWorkoutFactory
import com.pulse.intervalcoach.data.WorkoutCategories
import com.pulse.intervalcoach.data.WorkoutCategory
import com.pulse.intervalcoach.data.WorkoutSummary
import com.pulse.intervalcoach.data.category
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
import com.pulse.intervalcoach.ui.components.ProgressBar
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseDivider
import com.pulse.intervalcoach.ui.components.PulseHeroCard
import com.pulse.intervalcoach.ui.components.PulseListRow
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.library.StarterWorkouts
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val favorites: List<WorkoutSummary> = emptyList(),
    val recent: List<WorkoutSummary> = emptyList(),
    val nextPlanned: PlannedInfo? = null,
    val week: List<DailyActivity> = emptyList(),
    val weekSessions: Int = 0,
    val weekMillis: Long = 0L,
    /** The user's own weekly target in active minutes — the hero and rings divide by this. */
    val goalMinutes: Int = 150,
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
        container.preferences.flow,
    ) { summaries, recentSessions, reminders, active, prefs ->
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
            goalMinutes = prefs.weeklyGoalMinutes,
            activePlanName = active?.plan?.name,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Freshen the health snapshot so "Today's health" is current the moment the dashboard
        // appears — the hub only refreshes itself on app start and in the Health screen.
        container.health.refresh()
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
    suspend fun saveTemplate(template: StarterWorkouts.Template) =
        container.workouts.save(template.plan, tags = template.tags)
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

/** The WHO weekly guideline in active minutes — the default for the goal picker. */
const val WHO_WEEKLY_MINUTES = 150

/**
 * Today — the dashboard's overview page.
 *
 * The page answers one question — "what do I do right now?" — so it is ordered by urgency and every
 * section is a decision rather than a report:
 *
 * 1. a **page intro** that names the page and dates it;
 * 2. a **hero panel** (ink in both themes) with the week against the 150-minute guideline and the
 *    one primary action on the page;
 * 3. a **live session** row when a workout is running;
 * 4. a **category rail**, because most people know the *kind* of session they want before they know
 *    its name;
 * 5. **quick start** with the four presets and the custom work/rest/rounds steppers;
 * 6. the week's chart, health, favourites and a **table-like recent list**.
 *
 * Density is deliberate: rows are 56 dp, sections are 12 dp apart, and a list of what you did last
 * week fits in one screen without a scroll. Every number on it is a recorded one; nothing is
 * estimated.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenWorkout: (String) -> Unit,
    onStartWorkout: (WorkoutPlan) -> Unit,
    onQuickStart: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenGoals: () -> Unit,
    onResumeSession: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenCategory: ((WorkoutCategory) -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
    onOpenWatchSession: (() -> Unit)? = null,
) {
    val viewModel: HomeViewModel = viewModel(initializer = { HomeViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quick by viewModel.quickState.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val activeSession by container.sessionController.active.collectAsStateWithLifecycle()
    val snapshot by container.sessionController.snapshot.collectAsStateWithLifecycle()
    val watchSession by container.wearSync.remoteWatchSession.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val healthSnapshot by container.health.snapshot.collectAsStateWithLifecycle()
    val goalMillis = state.goalMinutes.coerceAtLeast(1).toLong() * 60L * 1000L
    val presets = remember { buildCategoryPresets() }
    val categories = remember { homeStartableCategories }
    val templates = remember { StarterWorkouts.templates }

    PulseScaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.s,
                bottom = dimens.huge,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            // --- Page heading -------------------------------------------------------------------
            item {
                PulsePageHeader(
                    title = greeting(),
                    description = LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) +
                        weekSummarySuffix(state.weekSessions, state.weekMillis),
                )
            }

            // --- Hero ----------------------------------------------------------------------------
            item {
                TodayHero(
                    weekMillis = state.weekMillis,
                    weekSessions = state.weekSessions,
                    goalMillis = goalMillis,
                    onStart = { onStartWorkout(viewModel.buildQuickPlan()) },
                )
            }

            // --- Live session --------------------------------------------------------------------
            if (activeSession != null) {
                item {
                    PulseCard(onClick = onResumeSession) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(icon = Icons.Filled.PlayArrow, tint = colors.accent, container = colors.accentTint)
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

            // --- Live watch session --------------------------------------------------------------
            // Shown only when nothing runs on the phone: a phone session always wins the screen.
            if (activeSession == null && watchSession != null && onOpenWatchSession != null) {
                item {
                    PulseCard(onClick = onOpenWatchSession) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(icon = Icons.Filled.Watch, tint = colors.work, container = colors.phaseTint(PhaseKind.WORK))
                            Spacer(Modifier.width(dimens.m))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(dimens.microGap),
                            ) {
                                Text(
                                    text = stringResource(R.string.watch_banner_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = watchSession?.let { watch ->
                                        watch.planName + " · " + watch.stepName + " · " +
                                            formatDuration(watch.stepRemainingMillis) + " left"
                                    } ?: "",
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
            item { SectionHeader("Start by category") }
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
            // The library is a destination, so it gets a destination-sized control — the same
            // full-width outlined button every other page ends its list with — not a floating
            // two-word link in the section header.
            if (onOpenLibrary != null) {
                item {
                    SecondaryActionButton(
                        text = "All workouts",
                        onClick = onOpenLibrary,
                        icon = Icons.Filled.FitnessCenter,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // --- Quick start ---------------------------------------------------------------------
            // Deliberately *not* a card: presets and steppers are already raised surfaces, so a
            // container around them would be a container inside a container. The section sits
            // directly on the canvas and the tiles carry the visual weight themselves.
            item {
                Column(Modifier.fillMaxWidth()) {
                    SectionHeader("Quick start")
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

                    SectionHeader(text = "Custom", trailing = formatDuration(quickTotal(quick)))
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.m)) {
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
                    Spacer(Modifier.height(dimens.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.m)) {
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
                    Spacer(Modifier.height(dimens.l))
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                        PrimaryActionButton(
                            text = "Start",
                            onClick = { onStartWorkout(viewModel.buildQuickPlan()) },
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryActionButton(
                            text = "Customize",
                            onClick = onQuickStart,
                            icon = Icons.Filled.Tune,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // --- Weekly activity -----------------------------------------------------------------
            // The hero already answers "where am I against my goal", so this section is only the
            // day-by-day chart — one container, one job, and no second copy of the same numbers.
            item { SectionHeader(text = "This week", trailing = "${state.weekSessions} session${if (state.weekSessions == 1) "" else "s"}") }
            item {
                PulseCard {
                    ActivityChart(
                        bars = state.week.map {
                            ActivityBar(
                                label = it.date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())),
                                value = it.activeMillis,
                            )
                        },
                        height = dimens.chartHeight - 48.dp,
                        emptyLabel = "No sessions recorded this week yet.",
                        color = colors.accent,
                    )
                }
            }

            // --- Progress & Goals: two equal nav tiles, so neither reads as a stray text link ----
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.cardGap)) {
                    PulseCard(onClick = onOpenProgress, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(icon = Icons.Filled.Insights, tint = colors.accent, container = colors.accentTint)
                            Spacer(Modifier.width(dimens.s))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Progress",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "History & charts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textMuted)
                        }
                    }
                    PulseCard(onClick = onOpenGoals, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(icon = Icons.Filled.Flag, tint = colors.prepare, container = colors.warningTint)
                            Spacer(Modifier.width(dimens.s))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Goals",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Weekly target",
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

            // --- Health snapshot (only when something is actually connected) ----------------------
            // Flat tiles on the canvas: the tiles are already surfaces, so a card around them was
            // a container inside a container with nothing to say.
            if (healthSnapshot.source != null) {
                item { SectionHeader(text = "Today's health", trailing = healthSnapshot.source.orEmpty()) }
                item {
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

            // --- Templates rail: the starters, inline — never a separate page ----------------------
            item { SectionHeader("Templates") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.cardGap)) {
                    items(templates, key = { it.plan.id }) { template ->
                        TemplateCard(
                            template = template,
                            onUse = {
                                scope.launch {
                                    viewModel.saveTemplate(template)
                                    onOpenWorkout(template.plan.id)
                                }
                            },
                        )
                    }
                }
            }

            // --- Next planned & recent: one table, two sections ------------------------------------
            if (state.nextPlanned != null || state.recent.isNotEmpty()) {
                item { SectionHeader("Recent activity") }
                item {
                    PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.xs)) {
                        Column(Modifier.fillMaxWidth()) {
                            state.nextPlanned?.let { planned ->
                                PulseListRow(
                                    title = planned.workoutName,
                                    subtitle = formatPlanned(planned.reminder.scheduledAt),
                                    leadingIcon = Icons.Filled.Schedule,
                                    leadingTint = colors.prepare,
                                    leadingContainer = colors.warningTint,
                                    onClick = { onOpenWorkout(planned.reminder.workoutId) },
                                    trailing = {
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textMuted,
                                        )
                                    },
                                )
                                if (state.recent.isNotEmpty()) PulseDivider()
                            }
                            state.recent.forEachIndexed { index, workout ->
                                val category = workout.categoryOrCustom()
                                PulseListRow(
                                    title = workout.name,
                                    subtitle = "${workout.intervalCount} intervals · " +
                                        (if (workout.hasOpenEnded) "open-ended" else formatDuration(workout.knownDurationMillis)),
                                    leadingIcon = category.icon,
                                    leadingTint = colors.categoryColor(category.hue),
                                    leadingContainer = colors.categoryTint(category.hue),
                                    onClick = { onOpenWorkout(workout.id) },
                                    trailing = {
                                        CategoryChip(category = category, showTitle = false)
                                    },
                                )
                                if (index != state.recent.lastIndex) PulseDivider()
                            }
                        }
                    }
                }
                item {
                    SecondaryActionButton(
                        text = "Full history",
                        onClick = onOpenProgress,
                        icon = Icons.Filled.Insights,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.loading) {
                item { LoadingBox(label = "Loading…", modifier = Modifier.height(160.dp)) }
            }

            if (state.favorites.isEmpty() && state.recent.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        title = "Nothing here yet",
                        body = "Star a workout to pin it here.",
                        icon = Icons.Filled.FitnessCenter,
                        actionLabel = if (onOpenLibrary != null) "Browse workouts" else null,
                        onAction = onOpenLibrary,
                    )
                }
            }
        }
    }
}

/**
 * The hero: the week against the 150-minute guideline, on an ink panel.
 *
 * It is deliberately dark in both themes — the one panel on the page that is not a card — so the
 * single primary action sits on a surface nothing else can imitate. No gradient, no glow: ink, a
 * hairline, and the accent used exactly once.
 */
@Composable
private fun TodayHero(
    weekMillis: Long,
    weekSessions: Int,
    goalMillis: Long,
    onStart: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val goalMinutes = (goalMillis / 60000L).coerceAtLeast(1)
    PulseHeroCard {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "This week",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.heroMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (weekSessions == 0) "Not started" else "$weekSessions session${if (weekSessions == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.heroMuted,
                )
            }
            Spacer(Modifier.height(dimens.m))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${(weekMillis / 60000L).toInt()}",
                    style = PulseType.NumericLarge,
                    color = colors.heroText,
                )
                Spacer(Modifier.width(dimens.s))
                Text(
                    text = "of $goalMinutes active minutes" +
                        if (goalMinutes == WHO_WEEKLY_MINUTES.toLong()) " · WHO guideline" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.heroMuted,
                    modifier = Modifier.padding(bottom = dimens.xs),
                )
            }
            Spacer(Modifier.height(dimens.m))
            ProgressBar(
                progress = (weekMillis.toFloat() / goalMillis.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
                height = 4.dp,
                trackColor = colors.heroMuted.copy(alpha = 0.28f),
            )
            Spacer(Modifier.height(dimens.l))
            PrimaryActionButton(
                text = if (weekSessions == 0) "Start your first session" else "Quick start",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.PlayArrow,
            )
        }
    }
}

/**
 * A one-tap category tile: the category's glyph in its own hue on its own tint.
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
        modifier = modifier.width(132.dp),
        shape = LocalPulseShapes.current.card,
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
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = category.title,
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

/** A favourite in the horizontal rail, with its category hue on the icon tile. */
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
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    icon = category.icon,
                    tint = colors.categoryColor(category.hue),
                    container = colors.categoryTint(category.hue),
                )
                Spacer(Modifier.width(dimens.s))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                )
                // The start affordance is a control inside the card's own row, not a button
                // block inside the button block the card already is.
                PulseIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "Start ${workout.name}",
                    tooltip = "Start",
                    onClick = onStart,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
            }
            Spacer(Modifier.height(dimens.m))
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
        }
    }
}

/** A starter template in the horizontal rail. "Use" saves a copy and opens it. */
@Composable
private fun TemplateCard(
    template: StarterWorkouts.Template,
    onUse: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val category = template.plan.category
    PulseCard(
        modifier = Modifier.width(dimens.carouselCardWidth),
        tint = colors.categoryTint(category.hue),
        onClick = onUse,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryChip(category = category)
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDuration(StarterWorkouts.knownDuration(template.plan)),
                    style = PulseType.NumericSmall,
                    color = colors.textSecondary,
                )
                PulseIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.templates_use),
                    tooltip = stringResource(R.string.templates_use),
                    onClick = onUse,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
            }
            Spacer(Modifier.height(dimens.m))
            Text(
                text = template.plan.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            template.plan.equipment?.let {
                Spacer(Modifier.height(dimens.xs))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The 32 dp icon tile used at the head of a row: a tinted rounded square that identifies the section
 * without introducing decoration. The tint carries meaning (accent for live, warning for scheduled).
 */
@Composable
private fun IconTile(
    icon: ImageVector,
    tint: Color,
    container: Color? = null,
) {
    val colors = LocalPulseColors.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(LocalPulseDimens.current.controlRadius))
            .background(container ?: colors.neutralFill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/** A preset tile in the quick-start grid, coloured by its category. */
@Composable
private fun PresetTile(
    title: String,
    icon: ImageVector,
    hue: Color,
    tint: Color,
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
            Icon(icon, contentDescription = null, tint = hue, modifier = Modifier.size(18.dp))
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

/** The parenthetical after the date: only shown once there is something to report. */
private fun weekSummarySuffix(sessions: Int, millis: Long): String =
    if (sessions == 0) "" else " · ${formatDuration(millis)} trained this week"

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
