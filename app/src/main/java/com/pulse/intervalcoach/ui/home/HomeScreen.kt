package com.pulse.intervalcoach.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.pulse.intervalcoach.ui.components.DurationStepper
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.GradientActionButton
import com.pulse.intervalcoach.ui.components.NumberStepper
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.ProgressRing
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
 * [QuickWorkoutFactory], and the chip shows its *real* computed duration — never a guess.
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // --- Greeting ---------------------------------------------------------------------
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors.work, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("PULSE", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    greeting(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        // --- Active session banner ----------------------------------------------------------
        if (activeSession != null) {
            item {
                PulseCard(onClick = onResumeSession) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(colors.activeGlow, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.work)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Workout in progress",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${activeSession?.plan?.name} · ${snapshot.step?.name ?: ""} · " +
                                    formatDuration(snapshot.stepRemainingMillis) + " left",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSecondary)
                    }
                }
            }
        }

        // --- Weekly activity: ring + stats ---------------------------------------------------
        item {
            PulseCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = (state.weekMillis.toFloat() / WEEKLY_GOAL_MILLIS).coerceIn(0f, 1f),
                        color = colors.work,
                        trackColor = colors.track,
                        strokeWidth = 10.dp,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(state.weekMillis / 60000L).toInt()}",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                "min",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("This week", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Goal 150 min · ${state.weekSessions} session${if (state.weekSessions == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        WeekChart(state.week)
                    }
                }
            }
        }

        // --- Quick start hero -----------------------------------------------------------------
        item {
            PulseCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(colors.work.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = colors.work, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Quick start", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    // Preset chips — real plans, real durations.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        presets.take(2).forEach { preset ->
                            PresetChip(
                                label = preset.label,
                                duration = presetDuration(preset),
                                colors = colors,
                                modifier = Modifier.weight(1f),
                                onClick = { onStartWorkout(preset.plan) },
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        presets.drop(2).forEach { preset ->
                            PresetChip(
                                label = preset.label,
                                duration = presetDuration(preset),
                                colors = colors,
                                modifier = Modifier.weight(1f),
                                onClick = { onStartWorkout(preset.plan) },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Custom — total ${formatDuration(quickTotal(quick))}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Spacer(Modifier.height(14.dp))
                    GradientActionButton(
                        text = "Start now",
                        onClick = { onStartWorkout(viewModel.buildQuickPlan()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onQuickStart) { Text("Open the quick builder instead") }
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
                                "Today's health",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(colors.work, CircleShape)
                                    .semantics { contentDescription = healthSnapshot.source.orEmpty() },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                "Steps",
                                healthSnapshot.stepsToday?.toString() ?: "—",
                                accent = colors.rest,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                "Heart rate",
                                healthSnapshot.latestHeartRate?.let { "$it bpm" } ?: "—",
                                accent = colors.prepare,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                "Source",
                                healthSnapshot.source ?: "—",
                                modifier = Modifier.weight(1.4f),
                            )
                        }
                    }
                }
            }
        }

        // --- Favorites rail ----------------------------------------------------------------------
        if (state.favorites.isNotEmpty()) {
            item { SectionHeader("Favorites") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.favorites, key = { it.id }) { workout ->
                        PulseCard(modifier = Modifier.width(220.dp), onClick = { onOpenWorkout(workout.id) }) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = colors.work, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        workout.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(10.dp))
                                PrimaryActionButton(
                                    text = "Start",
                                    // One tap from Today starts the favourite immediately.
                                    onClick = { scope.launch { viewModel.planFor(workout.id)?.let(onStartWorkout) } },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Next planned -------------------------------------------------------------------------
        if (state.nextPlanned != null) {
            item { SectionHeader("Next planned") }
            item {
                val planned = state.nextPlanned!!
                PulseCard(onClick = { onOpenWorkout(planned.reminder.workoutId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(colors.prepare.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.prepare)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(planned.workoutName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text(formatPlanned(planned.reminder.scheduledAt), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSecondary)
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
                        Column(Modifier.weight(1f)) {
                            Text(workout.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${workout.intervalCount} intervals · ${if (workout.hasOpenEnded) "open-ended" else formatDuration(workout.knownDurationMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSecondary)
                    }
                }
            }
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

@Composable
private fun PresetChip(
    label: String,
    duration: String,
    colors: com.pulse.intervalcoach.ui.theme.PulseColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                duration,
                style = MaterialTheme.typography.labelSmall,
                color = colors.work,
            )
        }
    }
}

/**
 * Seven-day activity chart.
 *
 * Bars sit in equal-width slots so they line up with the weekday labels underneath — the previous
 * version spaced them by a fixed gap, so the bars drifted out of alignment with nothing to read
 * them against anyway.
 */
@Composable
private fun WeekChart(activities: List<DailyActivity>) {
    val colors = LocalPulseColors.current
    val max = (activities.maxOfOrNull { it.activeMillis } ?: 0L).coerceAtLeast(1L)
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = "Last 7 days of activity" },
        ) {
            if (activities.isEmpty()) return@Canvas
            val slot = size.width / activities.size
            val barWidth = (slot - 6.dp.toPx()).coerceAtLeast(2.dp.toPx())
            activities.forEachIndexed { index, day ->
                val fraction = day.activeMillis.toFloat() / max.toFloat()
                val barHeight = (size.height * fraction).coerceAtLeast(if (day.activeMillis > 0) 6.dp.toPx() else 2.dp.toPx())
                val color: Color = if (day.activeMillis > 0) colors.work else colors.outline.copy(alpha = 0.4f)
                val x = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            activities.forEach { day ->
                Text(
                    day.date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.activeMillis > 0) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

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
