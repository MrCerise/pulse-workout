package com.pulse.intervalcoach.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.DailyActivity
import com.pulse.intervalcoach.data.STATUS_COMPLETED
import com.pulse.intervalcoach.data.STATUS_INTERRUPTED
import com.pulse.intervalcoach.data.STATUS_STOPPED
import com.pulse.intervalcoach.data.db.ReminderEntity
import com.pulse.intervalcoach.data.db.SessionEntity
import com.pulse.intervalcoach.data.db.SessionEventEntity
import com.pulse.intervalcoach.ui.components.ActivityBar
import com.pulse.intervalcoach.ui.components.ActivityChart
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.PulseListRow
import com.pulse.intervalcoach.ui.components.PulseTone
import com.pulse.intervalcoach.ui.components.PulseTopBar
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.components.StatusBadge
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.PulseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------------------------

data class ProgressUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val activities: List<DailyActivity> = emptyList(),
    val totals: Long = 0L,
    val streak: Int = 0,
    val reminders: List<ReminderEntity> = emptyList(),
    val planned: List<Pair<String, String>> = emptyList(),
)

class ProgressViewModel(private val container: AppContainer) : ViewModel() {

    private val refresh = MutableStateFlow(0)

    val state = combine(container.sessions.history, container.reminders.reminders, refresh) { sessions, reminders, _ ->
        val activities = container.sessions.weeklyActivity(14)
        val totals = sessions.sumOf { it.activeMillis }
        val streak = container.sessions.currentStreak()
        val planned = reminders.map { reminder ->
            reminder.id to (container.workouts.plan(reminder.workoutId)?.name ?: "Deleted workout")
        }
        ProgressUiState(sessions.take(30), activities, totals, streak, reminders, planned)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun schedule(workoutId: String, atMillis: Long) = viewModelScope.launch {
        container.reminders.schedule(workoutId, atMillis)
        refresh.value++
    }

    fun deleteReminder(id: String) = viewModelScope.launch {
        container.reminders.delete(id)
        refresh.value++
    }
}

/**
 * Progress.
 *
 * Every figure on this screen comes from a stored session — the streak, the weekly total, the
 * fourteen-day chart and the health sync line. Nothing here is projected, estimated or padded out
 * to make the screen look fuller than the user's real history.
 */
@Composable
fun ProgressScreen(
    container: AppContainer,
    onOpenHistory: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenHealth: () -> Unit,
) {
    val viewModel: ProgressViewModel = viewModel(initializer = { ProgressViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val summaries by container.workouts.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val healthSnapshot by container.health.snapshot.collectAsStateWithLifecycle()
    var scheduleFor by remember { mutableStateOf<String?>(null) }
    val weekMillis = remember(state.sessions) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        state.sessions.filter { it.startedAt >= weekAgo }.sumOf { it.activeMillis }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = "Progress",
                subtitle = "Real numbers from your saved sessions",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.cardGap,
                bottom = dimens.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    StatTile("Streak", "${state.streak}d", accent = colors.success, modifier = Modifier.weight(1f))
                    StatTile("This week", "${(weekMillis / 60000L).toInt()}m", modifier = Modifier.weight(1f))
                    StatTile("All time", formatDuration(state.totals), modifier = Modifier.weight(1f))
                }
            }
            item {
                PulseCard {
                    Column {
                        Text(
                            text = "Active minutes — last 14 days",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(dimens.m))
                        ActivityChart(
                            bars = state.activities.map {
                                ActivityBar(
                                    label = it.date.format(DateTimeFormatter.ofPattern("d/M", Locale.getDefault())),
                                    value = it.activeMillis,
                                )
                            },
                            height = LocalPulseDimens.current.chartHeight,
                            labelEvery = 2,
                            emptyLabel = "No sessions yet — the chart fills in as you finish workouts.",
                        )
                        Spacer(Modifier.height(dimens.s))
                        Text(
                            text = "Built from your saved sessions on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            // --- Health sync status ---
            item {
                PulseCard(onClick = onOpenHealth) {
                    PulseListRow(
                        title = "Health sync",
                        subtitle = when {
                            healthSnapshot.source == null ->
                                "Connect Health Connect or Google Fit to share workouts and read heart rate."
                            healthSnapshot.lastSyncAt > 0 ->
                                "Connected via ${healthSnapshot.source} · last sync ${formatLastSync(healthSnapshot.lastSyncAt)}"
                            else -> "Connected via ${healthSnapshot.source} · not synced yet"
                        },
                        leadingIcon = Icons.Filled.Healing,
                        leadingTint = if (healthSnapshot.source != null) colors.success else colors.textMuted,
                        leadingContainer = if (healthSnapshot.source != null) colors.successTint else colors.neutralFill,
                        trailing = {
                            if (healthSnapshot.source != null) {
                                StatusBadge(text = "connected", tone = PulseTone.SUCCESS)
                            } else {
                                StatusBadge(text = "not connected", tone = PulseTone.NEUTRAL)
                            }
                        },
                    )
                }
            }

            item { SectionHeader("Planner") }
            item {
                PulseCard {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
                        if (state.reminders.isEmpty()) {
                            Text(
                                text = "No planned sessions. Schedule one and it appears on Today.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        } else {
                            state.reminders.forEach { reminder ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
                                        Text(
                                            text = state.planned.firstOrNull { it.first == reminder.id }?.second ?: "Workout",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                        )
                                        Text(
                                            text = LocalDateTime.ofInstant(Instant.ofEpochMilli(reminder.scheduledAt), ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")),
                                            style = PulseType.NumericSmall,
                                            color = colors.textMuted,
                                        )
                                    }
                                    TextButton(onClick = { viewModel.deleteReminder(reminder.id) }) {
                                        Text(stringResource(R.string.planner_delete))
                                    }
                                }
                            }
                        }
                        var expanded by remember { mutableStateOf(false) }
                        SecondaryActionButton(
                            text = stringResource(R.string.planner_add),
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (expanded) {
                            summaries.take(6).forEach { workout ->
                                TextButton(onClick = {
                                    scheduleFor = workout.id
                                    expanded = false
                                }) { Text("Tomorrow · ${workout.name}") }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Recent sessions") }
            if (state.sessions.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.progress_empty_title),
                        body = "Finish your first workout and your real numbers appear here.",
                        icon = Icons.Filled.Healing,
                    )
                }
            } else {
                items(state.sessions.take(6), key = { it.id }) { session ->
                    SessionRow(session, onClick = { onOpenSession(session.id) })
                }
                item {
                    SecondaryActionButton(
                        text = "Open full history",
                        onClick = onOpenHistory,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    scheduleFor?.let { workoutId ->
        val whenTo = remember {
            LocalDateTime.now().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0)
        }
        ConfirmDialog(
            title = "Schedule this workout",
            body = "Reminder for tomorrow at 18:00 (" +
                whenTo.format(DateTimeFormatter.ofPattern("EEE d MMM")) +
                "). You can delete it from the Planner any time.",
            confirmLabel = "Schedule",
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                val at = whenTo.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                viewModel.schedule(workoutId, at)
                scheduleFor = null
            },
            onDismiss = { scheduleFor = null },
        )
    }
}

private fun formatLastSync(millis: Long): String {
    val then = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    val now = LocalDateTime.now(ZoneId.systemDefault())
    return if (then.toLocalDate() == now.toLocalDate()) {
        "today " + then.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        then.format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
    }
}

@Composable
private fun SessionRow(session: SessionEntity, onClick: () -> Unit) {
    val colors = LocalPulseColors.current
    PulseCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
                Text(
                    text = session.workoutName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(formatDuration(session.activeMillis))
                        append(" · ")
                        append("${session.completedIntervals}/${session.totalIntervals} intervals")
                    },
                    style = PulseType.NumericSmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.startedAt), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm")),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
            Spacer(Modifier.width(LocalPulseDimens.current.s))
            StatusBadge(text = session.status.humanStatus(), tone = session.status.tone())
            PulseIconButton(
                icon = Icons.Filled.ChevronRight,
                contentDescription = null,
                onClick = onClick,
            )
        }
    }
}

private fun String.humanStatus(): String = when (this) {
    STATUS_COMPLETED -> "completed"
    STATUS_STOPPED -> "ended early"
    STATUS_INTERRUPTED -> "interrupted"
    else -> lowercase()
}

private fun String.tone(): PulseTone = when (this) {
    STATUS_COMPLETED -> PulseTone.SUCCESS
    STATUS_STOPPED -> PulseTone.WARNING
    STATUS_INTERRUPTED -> PulseTone.DANGER
    else -> PulseTone.NEUTRAL
}

// ---------------------------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------------------------

@Composable
fun HistoryScreen(container: AppContainer, onBack: () -> Unit, onOpenSession: (String) -> Unit) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val sessions by container.sessions.history.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = stringResource(R.string.progress_history),
                subtitle = "${sessions.size} recorded sessions",
                onBack = onBack,
                backDescription = stringResource(R.string.back),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.cardGap,
                bottom = dimens.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.s),
        ) {
            if (sessions.isEmpty()) {
                item {
                    EmptyState(
                        title = "No sessions",
                        body = "Finished workouts are listed here with their real timings.",
                    )
                }
            }
            items(sessions, key = { it.id }) { session ->
                SessionRow(session, onClick = { onOpenSession(session.id) })
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Session detail
// ---------------------------------------------------------------------------------------------

@Composable
fun SessionDetailScreen(
    container: AppContainer,
    sessionId: String,
    onBack: () -> Unit,
    onRepeat: (WorkoutPlan) -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val session by container.sessions.observeSession(sessionId).collectAsStateWithLifecycle(initialValue = null)
    val events by container.sessions.observeEvents(sessionId).collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = stringResource(R.string.session_detail_title),
                subtitle = session?.workoutName,
                onBack = onBack,
                backDescription = stringResource(R.string.back),
            )
        },
    ) { padding ->
        val current = session
        if (current == null) {
            Column(Modifier.padding(padding)) {
                EmptyState(title = "Session not found", body = "It may have been deleted.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.cardGap,
                bottom = dimens.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
                    Text(current.workoutName, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Text(
                        text = LocalDateTime.ofInstant(Instant.ofEpochMilli(current.startedAt), ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm")),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    StatTile("Active", formatDuration(current.activeMillis), accent = colors.success, modifier = Modifier.weight(1f))
                    StatTile("Total", formatDuration(current.wallMillis), modifier = Modifier.weight(1f))
                    StatTile("Skipped", current.skippedIntervals.toString(), modifier = Modifier.weight(1f))
                }
            }
            if (current.status == STATUS_INTERRUPTED) {
                item {
                    InfoBanner(
                        text = "This session was interrupted (the app stopped before it finished), so its numbers show what was actually recorded.",
                        tone = PulseTone.WARNING,
                    )
                }
            }
            current.notes?.takeIf { it.isNotBlank() }?.let { note ->
                item { SectionHeader("Notes") }
                item { PulseCard { Text(note, color = colors.textPrimary) } }
            }

            item { SectionHeader("Timeline of events") }
            items(events) { event ->
                EventRow(event)
            }
            item {
                PrimaryActionButton(
                    text = "Repeat this workout",
                    onClick = {
                        scope.launch { container.workouts.plan(current.workoutId)?.let(onRepeat) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SecondaryActionButton(
                    text = "Delete session",
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SectionHeader("Workout as it was run") }
            item {
                val snapshotPlan = remember(current.id) {
                    runCatching { com.pulse.intervalcoach.data.PlanCodec.decode(current.workoutSnapshotJson) }.getOrNull()
                }
                PulseCard {
                    Column(verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
                        Text(
                            text = "Saved snapshot (revision ${snapshotPlan?.revision ?: 1})",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(dimens.xs))
                        Text(
                            text = snapshotPlan?.let { "${it.name} · ${it.type.displayName}" } ?: "Snapshot unavailable",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Editing the workout later never changes these historical numbers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.session_delete),
            body = "Delete this session from your history? This cannot be undone.",
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.sessions.delete(sessionId)
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun EventRow(event: SessionEventEntity) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatOffset(event.atMillis),
            style = PulseType.NumericSmall,
            color = colors.textMuted,
            modifier = Modifier.padding(end = dimens.m),
        )
        Column(Modifier.weight(1f)) {
            Text(event.kind.humanEvent(), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            if (event.stepName.isNotBlank()) {
                Text(event.stepName, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        if (event.durationMillis > 0) {
            Text(
                text = formatDuration(event.durationMillis),
                style = PulseType.NumericSmall,
                color = colors.textMuted,
            )
        }
    }
}

private fun String.humanEvent(): String = when (this) {
    "INTERVAL_STARTED" -> "Interval started"
    "INTERVAL_COMPLETED" -> "Interval completed"
    "INTERVAL_SKIPPED" -> "Interval skipped"
    "PAUSED" -> "Paused"
    "RESUMED" -> "Resumed"
    "TIME_ADDED" -> "Time added"
    "LAP" -> "Lap recorded"
    "ROUND_STARTED" -> "Round started"
    "PERIODIC_CUE" -> "Cue sounded"
    "COUNTDOWN" -> "Countdown cue"
    "HALFWAY" -> "Halfway cue"
    "FINISHED" -> "Session finished"
    else -> this.replace('_', ' ').lowercase()
}

private fun formatOffset(millis: Long): String {
    val total = millis / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
