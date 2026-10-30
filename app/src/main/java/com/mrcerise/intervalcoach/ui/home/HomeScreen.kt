package com.mrcerise.intervalcoach.ui.home

import com.mrcerise.intervalcoach.locale.categoryTitle
import androidx.compose.ui.platform.LocalResources
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.DailyActivity
import com.mrcerise.intervalcoach.data.QuickWorkoutFactory
import com.mrcerise.intervalcoach.data.WorkoutCategories
import com.mrcerise.intervalcoach.data.WorkoutCategory
import com.mrcerise.intervalcoach.data.WorkoutSummary
import com.mrcerise.intervalcoach.data.category
import com.mrcerise.intervalcoach.data.categoryOrCustom
import com.mrcerise.intervalcoach.data.db.ReminderEntity
import com.mrcerise.intervalcoach.ui.components.ActivityBar
import com.mrcerise.intervalcoach.ui.components.ActivityChart
import com.mrcerise.intervalcoach.ui.components.CategoryChip
import com.mrcerise.intervalcoach.ui.components.DurationStepper
import com.mrcerise.intervalcoach.ui.components.EmptyState
import com.mrcerise.intervalcoach.ui.components.GhostActionButton
import com.mrcerise.intervalcoach.ui.components.InfoBanner
import com.mrcerise.intervalcoach.ui.components.LoadingBox
import com.mrcerise.intervalcoach.ui.components.NumberStepper
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.ProgressBar
import com.mrcerise.intervalcoach.ui.components.PulseCard
import com.mrcerise.intervalcoach.ui.components.PulseDivider
import com.mrcerise.intervalcoach.ui.components.PulseHeroCard
import com.mrcerise.intervalcoach.ui.components.PulseListRow
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.SecondaryActionButton
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.pulse.library.StarterWorkouts
import com.mrcerise.intervalcoach.ui.components.StatTile
import com.mrcerise.intervalcoach.ui.components.PulseIconButton
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseType
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
    val todaySessions: Int = 0,
    val todayMillis: Long = 0L,
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
        // Reminders stay enabled after they fire, so the oldest one is usually in the past.
        val now = System.currentTimeMillis()
        val nextReminder = reminders.firstOrNull { it.scheduledAt >= now }
        val activities = container.sessions.weeklyActivity(7)
        HomeUiState(
            favorites = summaries.filter { it.isFavorite }.take(8),
            recent = recent,
            nextPlanned = nextReminder?.let { PlannedInfo(it, byId[it.workoutId]?.name ?: "") },
            week = activities,
            weekSessions = activities.sumOf { it.sessionCount },
            weekMillis = activities.sumOf { it.activeMillis },
            todaySessions = activities.lastOrNull()?.sessionCount ?: 0,
            todayMillis = activities.lastOrNull()?.activeMillis ?: 0L,
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
    fun buildQuickPlan(resources: android.content.res.Resources): WorkoutPlan = QuickWorkoutFactory.build(
        workMillis = _quickState.value.work,
        restMillis = _quickState.value.rest,
        rounds = _quickState.value.rounds,
        preparationMillis = _quickState.value.prepMillis,
        name = resources.getString(R.string.quick_workout_name),
        workName = resources.getString(R.string.builder_work),
        restName = resources.getString(R.string.builder_rest),
        preparationName = resources.getString(R.string.phase_prepare),
        cooldownName = resources.getString(R.string.builder_cooldown),
        roundName = resources.getString(R.string.builder_group),
    )

    suspend fun planFor(id: String): WorkoutPlan? = container.workouts.plan(id)
    suspend fun favorite(id: String, favorite: Boolean) = container.workouts.setFavorite(id, favorite)
    suspend fun saveTemplate(template: StarterWorkouts.Template) =
        container.workouts.save(template.plan, tags = template.tags)
}

/** The categories offered as one-tap starts in the category rail. */
val homeStartableCategories: List<WorkoutCategory>
    get() = listOf("HIIT", "TABATA", "EMOM", "AMRAP", "STRENGTH", "RUN", "STRETCHING", "BREATHING", "FOCUS")
        .map { WorkoutCategories.byKey(it) }

/** The WHO weekly guideline in active minutes — the default for the goal picker. */
const val WHO_WEEKLY_MINUTES = 150

/** Today puts the current workout first, followed by discovery and weekly progress. */
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
    val strings = LocalResources.current
    val viewModel: HomeViewModel = viewModel(initializer = { HomeViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quick by viewModel.quickState.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val activeSession by container.sessionController.active.collectAsStateWithLifecycle()
    val snapshot by container.sessionController.snapshot.collectAsStateWithLifecycle()
    val watchSession by container.wearSync.remoteWatchSession.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val goalMillis = state.goalMinutes.coerceAtLeast(1).toLong() * 60L * 1000L
    val quickPlan = remember(quick, strings) { viewModel.buildQuickPlan(strings) }
    val quickDuration = remember(quickPlan) {
        runCatching { com.pulse.engine.TimelineExpander.expand(quickPlan).knownMillis }.getOrNull()
    }
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
                        .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", strings.configuration.locales[0])) +
                        weekSummarySuffix(state.weekSessions, state.weekMillis),
                )
            }

            // A running workout takes priority over creating another timer.
            if (activeSession != null) {
                item {
                    PulseHeroCard {
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.m)) {
                            Text(strings.getString(R.string.home_in_progress),
                                style = MaterialTheme.typography.labelLarge, color = colors.heroMuted)
                            Text(activeSession?.plan?.name.orEmpty(),
                                style = MaterialTheme.typography.headlineSmall, color = colors.heroText)
                            Text(
                                text = listOfNotNull(snapshot.step?.name,
                                    strings.getString(R.string.time_left, formatDuration(snapshot.stepRemainingMillis)))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.heroMuted,
                            )
                            PrimaryActionButton(
                                text = strings.getString(R.string.home_return_to_workout),
                                onClick = onResumeSession,
                                icon = Icons.Filled.PlayArrow,
                                modifier = Modifier.fillMaxWidth(),
                            )
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
                                            strings.getString(R.string.time_left, formatDuration(watch.stepRemainingMillis))
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

            if (activeSession == null) {
                item {
                    QuickStartCard(
                        quick = quick,
                        durationMillis = quickDuration,
                        onChange = viewModel::updateQuick,
                        onStart = { onStartWorkout(quickPlan) },
                        onBuild = onQuickStart,
                    )
                }
            }

            // --- Start by category ---------------------------------------------------------------
            item { SectionHeader(strings.getString(R.string.home_by_category)) }
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
                        text = strings.getString(R.string.home_all_workouts),
                        onClick = onOpenLibrary,
                        icon = Icons.Filled.FitnessCenter,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // --- Favorites rail -------------------------------------------------------------------
            if (state.favorites.isNotEmpty()) {
                item { SectionHeader(strings.getString(R.string.home_favorites)) }
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

            // --- Weekly activity -----------------------------------------------------------------
            item { SectionHeader(text = strings.getString(R.string.home_week_summary), trailing = strings.getQuantityString(R.plurals.session_count, state.weekSessions, state.weekSessions)) }
            item {
                WeeklyGoalCard(state.weekMillis, state.weekSessions, goalMillis)
            }
            item {
                PulseCard {
                    ActivityChart(
                        bars = state.week.map {
                            ActivityBar(
                                label = it.date.format(DateTimeFormatter.ofPattern("EEEEE", strings.configuration.locales[0])),
                                value = it.activeMillis,
                            )
                        },
                        height = dimens.chartHeight - 48.dp,
                        emptyLabel = strings.getString(R.string.home_week_empty),
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
                                    text = strings.getString(R.string.nav_progress),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = strings.getString(R.string.home_history_charts),
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
                                    text = strings.getString(R.string.goals_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = strings.getString(R.string.home_weekly_target),
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

            // --- Templates rail: the starters, inline — never a separate page ----------------------
            item { SectionHeader(strings.getString(R.string.home_templates)) }
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
                item { SectionHeader(strings.getString(R.string.home_recent_activity)) }
                item {
                    PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.xs)) {
                        Column(Modifier.fillMaxWidth()) {
                            state.nextPlanned?.let { planned ->
                                PulseListRow(
                                    title = planned.workoutName.ifBlank { strings.getString(R.string.deleted_workout) },
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
                                    subtitle = strings.getString(R.string.interval_count, workout.intervalCount) + " · " +
                                        (if (workout.hasOpenEnded) strings.getString(R.string.player_open_ended) else formatDuration(workout.knownDurationMillis)),
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
                        text = strings.getString(R.string.home_full_history),
                        onClick = onOpenProgress,
                        icon = Icons.Filled.Insights,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.loading) {
                item { LoadingBox(label = strings.getString(R.string.loading), modifier = Modifier.height(160.dp)) }
            }

            if (state.favorites.isEmpty() && state.recent.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        title = strings.getString(R.string.home_nothing),
                        body = strings.getString(R.string.home_no_favorites),
                        icon = Icons.Filled.FitnessCenter,
                        actionLabel = if (onOpenLibrary != null) strings.getString(R.string.home_browse) else null,
                        onAction = onOpenLibrary,
                    )
                }
            }
        }
    }
}

/** Show exactly what Start will run; reveal the timer fields only when the user wants to edit. */
@Composable
private fun QuickStartCard(
    quick: HomeViewModel.QuickState,
    durationMillis: Long?,
    onChange: ((HomeViewModel.QuickState) -> HomeViewModel.QuickState) -> Unit,
    onStart: () -> Unit,
    onBuild: () -> Unit,
) {
    val strings = LocalResources.current
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    var editing by rememberSaveable { mutableStateOf(false) }
    val canStart = durationMillis != null && durationMillis > 0L

    PulseCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.m)) {
            Text(strings.getString(R.string.quick_workout_name),
                style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            if (canStart) {
                Column {
                    Text(formatDuration(durationMillis!!), style = PulseType.NumericLarge, color = colors.textPrimary)
                    Text(strings.getString(R.string.total_time_label),
                        style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                }
            }
            Text(
                strings.getString(R.string.home_timer_summary, quick.rounds, formatDuration(quick.work), formatDuration(quick.rest)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (quick.prepMillis > 0L) {
                Text(strings.getString(R.string.home_preparation_summary, formatDuration(quick.prepMillis)),
                    style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            PulseListRow(
                title = strings.getString(if (editing) R.string.home_hide_timer else R.string.home_adjust_timer),
                leadingIcon = Icons.Filled.Tune,
                onClick = { editing = !editing },
                trailing = {
                    Icon(if (editing) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null, tint = colors.textSecondary)
                },
            )
            if (editing) {
                DurationStepper(strings.getString(R.string.builder_work), quick.work, allowZero = false,
                    onChange = { value -> onChange { it.copy(work = value) } })
                DurationStepper(strings.getString(R.string.builder_rest), quick.rest,
                    onChange = { value -> onChange { it.copy(rest = value) } })
                NumberStepper(strings.getString(R.string.builder_rounds), quick.rounds,
                    onChange = { value -> onChange { it.copy(rounds = value) } })
                DurationStepper(strings.getString(R.string.builder_preparation), quick.prepMillis,
                    onChange = { value -> onChange { it.copy(prepMillis = value) } })
            }
            if (!canStart) InfoBanner(strings.getString(R.string.home_timing_invalid))
            PrimaryActionButton(
                text = strings.getString(R.string.home_start_workout),
                onClick = onStart,
                enabled = canStart,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth(),
            )
            GhostActionButton(
                text = strings.getString(R.string.home_create_saved),
                onClick = onBuild,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Weekly progress stays below workout actions and saved favorites. */
@Composable
private fun WeeklyGoalCard(
    weekMillis: Long,
    weekSessions: Int,
    goalMillis: Long,
) {
    val strings = LocalResources.current
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val goalMinutes = (goalMillis / 60000L).coerceAtLeast(1)
    PulseHeroCard {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.getString(R.string.home_week_summary),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.heroMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (weekSessions == 0) strings.getString(R.string.home_not_started) else strings.getQuantityString(R.plurals.session_count, weekSessions, weekSessions),
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
                    text = strings.getString(R.string.home_goal_progress, goalMinutes) +
                        if (goalMinutes == WHO_WEEKLY_MINUTES.toLong()) strings.getString(R.string.home_who) else "",
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
    val strings = LocalResources.current
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
                text = strings.categoryTitle(category),
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
    val strings = LocalResources.current
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
                    text = strings.categoryTitle(category),
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
                    contentDescription = strings.getString(R.string.action_start_named, workout.name),
                    tooltip = strings.getString(R.string.home_start),
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
                text = if (workout.hasOpenEnded) strings.getString(R.string.player_open_ended) else formatDuration(workout.knownDurationMillis),
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

@Composable
private fun greeting(): String = when (java.time.LocalTime.now(ZoneId.systemDefault()).hour) {
    in 5..11 -> stringResource(R.string.greeting_morning)
    in 12..17 -> stringResource(R.string.greeting_afternoon)
    else -> stringResource(R.string.greeting_evening)
}

/** The parenthetical after the date: only shown once there is something to report. */
@Composable
private fun weekSummarySuffix(sessions: Int, millis: Long): String =
    if (sessions == 0) "" else stringResource(R.string.home_trained_week, formatDuration(millis))

private fun presetDuration(plan: WorkoutPlan): String = runCatching {
    com.pulse.engine.TimelineExpander.expand(plan).let {
        if (it.hasOpenEnded) "—" else formatDuration(it.knownMillis)
    }
}.getOrDefault("—")

/** The duration a one-tap start from a category produces, from that category's own defaults. */
private fun presetTotalMillis(category: WorkoutCategory): String {
    return presetDuration(planForCategory(category))
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

@Composable
private fun formatPlanned(millis: Long): String {
    val strings = LocalResources.current
    val zone = ZoneId.systemDefault()
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val today = LocalDateTime.now(zone).toLocalDate()
    return when (dateTime.toLocalDate()) {
        today -> strings.getString(R.string.date_today_time, dateTime.format(DateTimeFormatter.ofPattern("HH:mm", strings.configuration.locales[0])))
        today.plusDays(1) -> strings.getString(R.string.date_tomorrow_time, dateTime.format(DateTimeFormatter.ofPattern("HH:mm", strings.configuration.locales[0])))
        else -> dateTime.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", strings.configuration.locales[0]))
    }
}
