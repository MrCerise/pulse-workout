package com.pulse.intervalcoach.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.DailyActivity
import com.pulse.intervalcoach.data.WorkoutSummary
import com.pulse.intervalcoach.data.db.ReminderEntity
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.NumberStepper
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.components.TimelineBar
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.R

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

    val quickWorkMillis = MutableStateFlow(40_000L)
    val quickRestMillis = MutableStateFlow(20_000L)
    val quickRounds = MutableStateFlow(8)
    val quickPrepMillis = MutableStateFlow(10_000L)

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
            quickWorkMillis.value = prefs.defaultWorkMillis
            quickRestMillis.value = prefs.defaultRestMillis
            quickRounds.value = prefs.defaultRounds
            quickPrepMillis.value = prefs.defaultPreparationMillis
        }
    }

    fun updateQuick(transform: (QuickState) -> QuickState) {
        _quickState.value = transform(_quickState.value)
    }

    /** Builds the ad-hoc work/rest workout used by Quick Start. */
    fun buildQuickPlan(): WorkoutPlan = com.pulse.intervalcoach.data.QuickWorkoutFactory.build(
        workMillis = _quickState.value.work,
        restMillis = _quickState.value.rest,
        rounds = _quickState.value.rounds,
        preparationMillis = _quickState.value.prepMillis,
    )

    suspend fun planFor(id: String): WorkoutPlan? = container.workouts.plan(id)
    suspend fun favorite(id: String, favorite: Boolean) = container.workouts.setFavorite(id, favorite)
    suspend fun runNow() = Unit
}

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenWorkout: (String) -> Unit,
    onStartWorkout: (WorkoutPlan) -> Unit,
    onQuickStart: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(initializer = { HomeViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quick by viewModel.quickState.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val activeSession by container.sessionController.active.collectAsStateWithLifecycle()
    val snapshot by container.sessionController.snapshot.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = colors.work)
                Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
            }
        }

        if (activeSession != null) {
            item {
                PulseCard(onClick = {}) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.work)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Workout in progress — ${activeSession?.plan?.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${snapshot.step?.name ?: ""} · ${formatDuration(snapshot.stepRemainingMillis)} left",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        item {
            PulseCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = colors.work)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_quick_start), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Work / rest timer — adjust and go. Total ${formatDuration(quickTotal(quick))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.pulse.intervalcoach.ui.components.DurationStepper(
                            label = stringResource(R.string.builder_work),
                            millis = quick.work,
                            onChange = { viewModel.updateQuick { s -> s.copy(work = it) } },
                            modifier = Modifier.weight(1f),
                        )
                        com.pulse.intervalcoach.ui.components.DurationStepper(
                            label = stringResource(R.string.builder_rest),
                            millis = quick.rest,
                            onChange = { viewModel.updateQuick { s -> s.copy(rest = it) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.pulse.intervalcoach.ui.components.NumberStepper(
                            label = stringResource(R.string.details_rounds),
                            value = quick.rounds,
                            onChange = { viewModel.updateQuick { s -> s.copy(rounds = it) } },
                            modifier = Modifier.weight(1f),
                        )
                        com.pulse.intervalcoach.ui.components.DurationStepper(
                            label = stringResource(R.string.builder_preparation),
                            millis = quick.prepMillis,
                            onChange = { viewModel.updateQuick { s -> s.copy(prepMillis = it) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryActionButton(
                        text = "Start now",
                        onClick = { onStartWorkout(viewModel.buildQuickPlan()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onQuickStart) { Text("Save it as a workout instead") }
                }
            }
        }

        if (state.favorites.isNotEmpty()) {
            item { SectionHeader("Favorites") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.favorites, key = { it.id }) { workout ->
                        PulseCard(modifier = Modifier.width(220.dp), onClick = { onOpenWorkout(workout.id) }) {
                            Column {
                                Text(workout.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.height(10.dp))
                                PrimaryActionButton(
                                    text = stringResource(R.string.home_start),
                                    // One tap from Home starts the favourite immediately.
                                    onClick = { scope.launch { viewModel.planFor(workout.id)?.let(onStartWorkout) } },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader("This week") }
        item {
            PulseCard {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Sessions", state.weekSessions.toString())
                        StatTile("Active time", formatDuration(state.weekMillis), accent = colors.work)
                    }
                    Spacer(Modifier.height(12.dp))
                    WeekBars(state.week)
                }
            }
        }

        if (state.nextPlanned != null) {
            item { SectionHeader("Next planned") }
            item {
                val planned = state.nextPlanned!!
                PulseCard(onClick = { onOpenWorkout(planned.reminder.workoutId) }) {
                    Column {
                        Text(planned.workoutName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatPlanned(planned.reminder.scheduledAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        if (state.recent.isNotEmpty()) {
            item { SectionHeader("Recent") }
            items(state.recent, key = { it.id }) { workout ->
                PulseCard(onClick = { onOpenWorkout(workout.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(workout.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
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
                    actionLabel = "Browse templates",
                    onAction = onOpenProgress,
                )
            }
        }
    }
}

private fun quickTotal(quick: HomeViewModel.QuickState): Long =
    quick.prepMillis + quick.rounds * (quick.work + quick.rest)

@Composable
private fun WeekBars(activities: List<DailyActivity>) {
    val colors = LocalPulseColors.current
    val max = (activities.maxOfOrNull { it.activeMillis } ?: 0L).coerceAtLeast(1L)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        if (activities.isEmpty()) return@Canvas
        val gap = 6.dp.toPx()
        val barWidth = (size.width - gap * (activities.size - 1)) / activities.size
        activities.forEachIndexed { index, day ->
            val fraction = day.activeMillis.toFloat() / max.toFloat()
            val barHeight = (size.height * fraction).coerceAtLeast(if (day.activeMillis > 0) 6.dp.toPx() else 2.dp.toPx())
            val color: Color = if (day.activeMillis > 0) colors.work else colors.outline.copy(alpha = 0.4f)
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(index * (barWidth + gap), size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 4),
            )
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
