package com.pulse.intervalcoach.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.DailyActivity
import com.pulse.intervalcoach.data.QuickWorkoutFactory
import com.pulse.intervalcoach.data.WorkoutSummary
import com.pulse.intervalcoach.data.db.ReminderEntity
import com.pulse.intervalcoach.ui.components.ActivityBar
import com.pulse.intervalcoach.ui.components.ActivityChart
import com.pulse.intervalcoach.ui.components.DurationStepper
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.GhostActionButton
import com.pulse.intervalcoach.ui.components.LoadingBox
import com.pulse.intervalcoach.ui.components.NumberStepper
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.ProgressRing
import com.pulse.intervalcoach.ui.components.PulseCard
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
 * The one-tap presets on the Today hero. Every preset is a real plan built with
 * [QuickWorkoutFactory], and the tile shows its *real* computed duration — never a guess.
 */
data class QuickPreset(val id: String, val label: String, val plan: WorkoutPlan)

fun buildQuickPresets(): List<QuickPreset> = listOf(
    QuickPreset(
        id = "tabata",
        label = "Tabata",
        plan = QuickWorkoutFactory.tabata(preparationMillis = 0L, includeFinalRest = true),
    ),
    QuickPreset(
        id = "hiit",
        label = "HIIT",
        plan = QuickWorkoutFactory.build(
            workMillis = 40_000,
            restMillis = 20_000,
            rounds = 10,
            preparationMillis = 10_000,
            includeFinalRest = true,
            name = "HIIT",
            type = WorkoutType.HIIT,
        ),
    ),
    QuickPreset(
        id = "runwalk",
        label = "Run / walk",
        plan = QuickWorkoutFactory.build(
            workMillis = 60_000,
            restMillis = 30_000,
            rounds = 10,
            preparationMillis = 0L,
            includeFinalRest = true,
            name = "Run / walk",
            type = WorkoutType.RUN_WALK,
            workName = "Run",
            restName = "Walk",
        ),
    ),
    QuickPreset(
        id = "strength",
        label = "Strength",
        plan = QuickWorkoutFactory.build(
            workMillis = 45_000,
            restMillis = 75_000,
            rounds = 6,
            preparationMillis = 15_000,
            includeFinalRest = true,
            name = "Strength",
            type = WorkoutType.STRENGTH,
            workName = "Lift",
            restName = "Rest",
        ),
    ),
)

private const val WEEKLY_GOAL_MILLIS = 150L * 60L * 1000L // WHO 150 min/week guideline

/**
 * Today.
 *
 * The screen answers one question — "what do I do right now?" — so it is ordered by urgency: the
 * running session first, then this week's progress, then one-tap starts, then the library rails.
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
    val presets = remember { buildQuickPresets() }

    LazyColumn(
        // The root Scaffold no longer applies system-bar insets (see PulseAppRoot), so the two
        // screens without their own Scaffold apply them here.
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.pagePadding,
            end = dimens.pagePadding,
            top = dimens.m,
            bottom = dimens.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
    ) {
        // --- Greeting -------------------------------------------------------------------------
        item {
            Column(Modifier.padding(top = dimens.s, bottom = dimens.xs)) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(LocalPulseDimens.current.microGap))
                Text(
                    text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }

        // --- Active session banner ------------------------------------------------------------
        if (activeSession != null) {
            item {
                PulseCard(onClick = onResumeSession) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon = Icons.Filled.PlayArrow, tint = colors.accent, container = colors.accentTint)
                        Spacer(Modifier.width(dimens.m))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
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

        // --- Weekly activity ------------------------------------------------------------------
        item {
            PulseCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(
                            progress = (state.weekMillis.toFloat() / WEEKLY_GOAL_MILLIS).coerceIn(0f, 1f),
                            color = colors.work,
                            trackColor = colors.track,
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(76.dp),
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
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
                            Text(
                                text = "This week",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "${formatDuration(state.weekMillis)} of the 150 min goal · " +
                                    "${state.weekSessions} session${if (state.weekSessions == 1) "" else "s"}",
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
                        height = LocalPulseDimens.current.barHeightRegular,
                        emptyLabel = "No sessions recorded this week yet.",
                    )
                }
            }
        }

        // --- Quick start ----------------------------------------------------------------------
        item {
            PulseCard {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon = Icons.Filled.Bolt, tint = colors.accent, container = colors.accentTint)
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
                                label = preset.label,
                                duration = presetDuration(preset),
                                onClick = { onStartWorkout(preset.plan) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(dimens.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                        presets.drop(2).forEach { preset ->
                            PresetTile(
                                label = preset.label,
                                duration = presetDuration(preset),
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
                    GhostActionButton(
                        text = "Open the quick builder instead",
                        onClick = onQuickStart,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // --- Health snapshot (only when something is actually connected) ------------------------
        if (healthSnapshot.source != null) {
            item {
                PulseCard(onClick = onOpenHealth) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Today's health",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = healthSnapshot.source.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                        }
                        Spacer(Modifier.height(dimens.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
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
        }

        // --- Favorites rail ---------------------------------------------------------------------
        if (state.favorites.isNotEmpty()) {
            item { SectionHeader("Favorites") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.cardGap)) {
                    items(state.favorites, key = { it.id }) { workout ->
                        PulseCard(modifier = Modifier.width(LocalPulseDimens.current.carouselCardWidth), onClick = { onOpenWorkout(workout.id) }) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = workout.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis),
                                    style = PulseType.NumericSmall,
                                    color = colors.textMuted,
                                )
                                Spacer(Modifier.height(dimens.m))
                                SecondaryStartButton(
                                    text = "Start",
                                    // One tap from Today starts the favourite immediately.
                                    onClick = { scope.launch { viewModel.planFor(workout.id)?.let(onStartWorkout) } },
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Next planned -------------------------------------------------------------------------
        state.nextPlanned?.let { planned ->
            item { SectionHeader("Next planned") }
            item {
                PulseCard(onClick = { onOpenWorkout(planned.reminder.workoutId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon = Icons.Filled.Schedule, tint = colors.prepare, container = colors.warningTint)
                        Spacer(Modifier.width(dimens.m))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
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

        // --- Recent --------------------------------------------------------------------------------
        if (state.recent.isNotEmpty()) {
            item { SectionHeader("Recent") }
            items(state.recent, key = { it.id }) { workout ->
                PulseCard(onClick = { onOpenWorkout(workout.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
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

/**
 * The 28 dp icon tile used at the head of a card: a tinted square that identifies the section
 * without introducing decoration. The tint carries meaning (accent for live, prepare for scheduled).
 */
@Composable
private fun IconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = LocalPulseColors.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(LocalPulseShapes.current.chip)
            .background(container ?: colors.neutralFill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PresetTile(
    label: String,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.s),
            verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap),
        ) {
            Text(
                text = label,
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

/** A compact start affordance for the favourite rail; a full-width button would dominate the card. */
@Composable
private fun SecondaryStartButton(text: String, onClick: () -> Unit) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        border = BorderStroke(dimens.borderWidth, colors.outline),
        contentColor = colors.textPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun greeting(): String = when (java.time.LocalTime.now(ZoneId.systemDefault()).hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

private fun presetDuration(preset: QuickPreset): String = runCatching {
    com.pulse.engine.TimelineExpander.expand(preset.plan).let {
        if (it.hasOpenEnded) "—" else formatDuration(it.knownMillis)
    }
}.getOrDefault("—")

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
