package com.pulse.intervalcoach.ui.progress

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.DailyActivity
import com.pulse.intervalcoach.data.STATUS_COMPLETED
import com.pulse.intervalcoach.data.STATUS_INTERRUPTED
import com.pulse.intervalcoach.data.STATUS_STOPPED
import com.pulse.intervalcoach.data.db.ReminderEntity
import com.pulse.intervalcoach.data.db.SessionEntity
import com.pulse.intervalcoach.data.db.SessionEventEntity
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.R

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val summaries by container.workouts.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val healthSnapshot by container.health.snapshot.collectAsStateWithLifecycle()
    var scheduleFor by remember { mutableStateOf<String?>(null) }
    val weekMillis = remember(state.sessions) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        state.sessions.filter { it.startedAt >= weekAgo }.sumOf { it.activeMillis }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Progress", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        Text("Real numbers from your saved sessions", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Streak", "${state.streak}d", accent = colors.work, modifier = Modifier.weight(1f))
                    StatTile("This week", "${(weekMillis / 60000L).toInt()}m", accent = colors.rest, modifier = Modifier.weight(1f))
                    StatTile("All time", formatDuration(state.totals), modifier = Modifier.weight(1f))
                }
            }
            item {
                PulseCard {
                    Column {
                        Text(
                            "Active minutes — last 14 days",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        ActivityChart(state.activities)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.sessions.isEmpty()) "No sessions yet — this chart stays empty until you finish a workout."
                            else "Built from your saved sessions on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            // --- Health sync status ---
            item {
                PulseCard(onClick = onOpenHealth) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Healing,
                                    contentDescription = null,
                                    tint = if (healthSnapshot.source != null) colors.work else colors.textSecondary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Health sync", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    healthSnapshot.source == null -> "Health Connect & Google Fit — connect to share workouts and see heart rate"
                                    healthSnapshot.lastSyncAt > 0 ->
                                        "Connected via ${healthSnapshot.source} · last sync " +
                                            formatLastSync(healthSnapshot.lastSyncAt)
                                    else -> "Connected via ${healthSnapshot.source} · not synced yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 2,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSecondary)
                    }
                }
            }

            item { SectionHeader("Planner") }
            item {
                PulseCard {
                    Column {
                        if (state.reminders.isEmpty()) {
                            Text(
                                "No planned sessions. Schedule one and it appears on Today.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        } else {
                            state.reminders.forEach { reminder ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            state.planned.firstOrNull { it.first == reminder.id }?.second ?: "Workout",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.textPrimary,
                                        )
                                        Text(
                                            LocalDateTime.ofInstant(Instant.ofEpochMilli(reminder.scheduledAt), ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                    TextButton(onClick = { viewModel.deleteReminder(reminder.id) }) {
                                        Text(stringResource(R.string.planner_delete))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
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
                    SecondaryActionButton("Open full history", onOpenHistory, Modifier.fillMaxWidth())
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

/**
 * Daily-minutes chart. Bars sit in equal-width slots so the date labels below line up with them;
 * only every other day is labelled, otherwise 14 labels would collide on a phone.
 */
@Composable
private fun ActivityChart(activities: List<DailyActivity>) {
    val colors = LocalPulseColors.current
    val max = (activities.maxOfOrNull { it.activeMillis } ?: 0L).coerceAtLeast(1L)
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = "Active minutes over the last ${activities.size} days" },
        ) {
            if (activities.isEmpty()) return@Canvas
            val slot = size.width / activities.size
            val barWidth = (slot - 3.dp.toPx()).coerceAtLeast(2.dp.toPx())
            activities.forEachIndexed { index, day ->
                val fraction = day.activeMillis.toFloat() / max.toFloat()
                val barHeight = (size.height * fraction).coerceAtLeast(if (day.activeMillis > 0) 8.dp.toPx() else 2.dp.toPx())
                val x = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (day.activeMillis > 0) colors.work else colors.outline.copy(alpha = 0.35f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3),
                )
            }
        }
        if (activities.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                activities.forEachIndexed { index, day ->
                    val label = if (index % 2 == 0 || index == activities.lastIndex) {
                        day.date.format(DateTimeFormatter.ofPattern("d/M", Locale.getDefault()))
                    } else {
                        ""
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionEntity, onClick: () -> Unit) {
    val colors = LocalPulseColors.current
    PulseCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.workoutName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(formatDuration(session.activeMillis))
                    append(" · ")
                    append("${session.completedIntervals}/${session.totalIntervals} intervals")
                    append(" · ")
                    append(session.status.humanStatus())
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(session.startedAt), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm")),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
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

// ---------------------------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(container: AppContainer, onBack: () -> Unit, onOpenSession: (String) -> Unit) {
    val sessions by container.sessions.history.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_history)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (sessions.isEmpty()) {
                item { EmptyState(title = "No sessions", body = "Finished workouts are listed here with their real timings.") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    container: AppContainer,
    sessionId: String,
    onBack: () -> Unit,
    onRepeat: (WorkoutPlan) -> Unit,
) {
    val colors = LocalPulseColors.current
    val session by container.sessions.observeSession(sessionId).collectAsStateWithLifecycle(initialValue = null)
    val events by container.sessions.observeEvents(sessionId).collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.session_detail_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val current = session
        if (current == null) {
            Column(Modifier.padding(padding)) { EmptyState(title = "Session not found", body = "It may have been deleted.") }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(current.workoutName, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Text(
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(current.startedAt), ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Active", formatDuration(current.activeMillis), accent = colors.work, modifier = Modifier.weight(1f))
                    StatTile("Total", formatDuration(current.wallMillis), modifier = Modifier.weight(1f))
                    StatTile("Skipped", current.skippedIntervals.toString(), modifier = Modifier.weight(1f))
                }
            }
            if (current.status == STATUS_INTERRUPTED) {
                item {
                    InfoBanner("This session was interrupted (the app stopped before it finished), so its numbers show what was actually recorded.")
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
                SecondaryActionButton("Delete session", { confirmDelete = true }, Modifier.fillMaxWidth())
            }
            item {
                SectionHeader("Workout as it was run")
            }
            item {
                val snapshotPlan = remember(current.id) {
                    runCatching { com.pulse.intervalcoach.data.PlanCodec.decode(current.workoutSnapshotJson) }.getOrNull()
                }
                PulseCard {
                    Column {
                        Text(
                            "Saved snapshot (revision ${snapshotPlan?.revision ?: 1})",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            snapshotPlan?.let { "${it.name} · ${it.type.displayName}" } ?: "Snapshot unavailable",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            "Editing the workout later never changes these historical numbers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatOffset(event.atMillis),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(event.kind.humanEvent(), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            if (event.stepName.isNotBlank()) {
                Text(event.stepName, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        if (event.durationMillis > 0) {
            Text(formatDuration(event.durationMillis), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
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
