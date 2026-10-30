package com.mrcerise.intervalcoach.journey

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.session.SessionSummaryData
import com.mrcerise.intervalcoach.ui.components.*
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.engine.TimelineExpander
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun french(): Boolean = LocalResources.current.configuration.locales[0].language == "fr"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyScreen(
    container: AppContainer,
    onStart: (WorkoutPlan, JourneyContext) -> Unit,
    onResume: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val observed by container.journey.state.collectAsStateWithLifecycle(initialValue = null)
    val active by container.sessionController.active.collectAsStateWithLifecycle()
    val fr = french()
    val scope = rememberCoroutineScope()
    val colors = LocalPulseColors.current
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = Instant.now(); delay(30_000) }
        }
    }
    val state = observed
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.journey_loading), color = colors.textPrimary)
        }
        return
    }
    if (state.enrollment == null || editing) {
        JourneySetup(state, onCancel = { editing = false }, onSave = { level, target, days, minute ->
            error = false
            scope.launch {
                try {
                    container.journey.configure(level, target, days, minute)
                    runCatching { com.mrcerise.intervalcoach.session.Notifications.manager(context).cancel(4202) }
                    if (minute != null) onRequestNotifications()
                    editing = false
                    error = false
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { error = true }
            }
        }, error = error)
        return
    }
    val completed = remember(state.credits) { state.completed }
    val next = remember(state.level, completed) { JourneyCatalogue.recommended(state.level, completed) }
    var additionalChapters by rememberSaveable(state.level.name) { mutableIntStateOf(0) }
    val chapterCount = maxOf(4, next.chapter + 2) + additionalChapters
    val sessionCount = chapterCount * JourneyCatalogue.SESSIONS_PER_CHAPTER
    PulseScaffold { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PulsePageHeader(stringResource(R.string.journey_title), description = stringResource(R.string.journey_intro))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JourneyLevel.entries.forEach { level ->
                            FilterChip(selected = state.level == level, label = { Text(level.title.localized(fr)) },
                                onClick = { scope.launch {
                                    try { container.journey.selectLevel(level); error = false }
                                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (_: Exception) { error = true }
                                } })
                        }
                    }
                    JourneyWeekCard(state, now)
                    Text(stringResource(R.string.journey_progress, state.completedCount()),
                        color = colors.textPrimary)
                    Text(stringResource(R.string.journey_foundation_progress, state.foundationCompleted),
                        color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    if (active != null) {
                        PrimaryActionButton(stringResource(R.string.journey_resume), onResume, Modifier.fillMaxWidth())
                        SecondaryActionButton(stringResource(R.string.journey_continue, next.title.localized(fr)),
                            { selectedId = next.id }, Modifier.fillMaxWidth())
                    } else {
                        PrimaryActionButton(stringResource(R.string.journey_continue, next.title.localized(fr)),
                            { selectedId = next.id }, Modifier.fillMaxWidth())
                    }
                    if (error) InfoBanner(stringResource(R.string.journey_error))
                    if (state.levelComplete) {
                        PulseCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val advanced = state.level.next() == null
                                Text(stringResource(if (advanced) R.string.journey_advanced_done else R.string.journey_ready_title),
                                    style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(if (advanced) R.string.journey_advanced_body else R.string.journey_ready_body))
                                if (!advanced) {
                                    SecondaryActionButton(stringResource(R.string.journey_ready), {
                                        scope.launch {
                                            try { container.journey.readiness(state.level, true); error = false }
                                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                            catch (_: Exception) { error = true }
                                        }
                                    }, Modifier.fillMaxWidth())
                                    if (state.readiness.none { it.level == state.level.name && !it.ready }) {
                                        GhostActionButton(stringResource(R.string.journey_repeat_level), {
                                            scope.launch {
                                                try { container.journey.readiness(state.level, false); error = false }
                                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                                catch (_: Exception) { error = true }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                    SecondaryActionButton(stringResource(R.string.journey_settings), { editing = true }, Modifier.fillMaxWidth())
                }
            }
            items(count = sessionCount, key = { index -> "${state.level.name}-session-$index" }) { index ->
                val node = remember(state.level, index) { JourneyCatalogue.at(state.level, index) }
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    if (node.position == 0) {
                        Text(stringResource(R.string.journey_chapter, node.chapter + 1,
                            JourneyCatalogue.chapterTitle(node.chapter).localized(fr)),
                            modifier = Modifier.padding(top = 28.dp, bottom = 20.dp).semantics { heading() },
                            style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    }
                    val isCompleted = node.id in completed
                    val recommended = !isCompleted && node.id == next.id
                    val status = stringResource(when {
                        isCompleted -> R.string.journey_completed
                        recommended -> R.string.journey_recommended
                        else -> R.string.journey_available
                    })
                    Box(Modifier.fillMaxWidth(), contentAlignment = when (index % 4) {
                        0 -> Alignment.CenterStart
                        2 -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }) {
                        PulseCard(
                            modifier = Modifier.fillMaxWidth(0.82f),
                            onClick = { selectedId = node.id },
                            tint = if (recommended) colors.brand else null,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(if (isCompleted) Icons.Filled.CheckCircle else if (node.checkpoint) Icons.Filled.Flag
                                    else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = colors.textPrimary)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.journey_node, index + 1, node.title.localized(fr), status),
                                        style = MaterialTheme.typography.titleMedium)
                                    if (node.checkpoint) Text(stringResource(R.string.journey_checkpoint),
                                        style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    if (index != sessionCount - 1) Canvas(Modifier.fillMaxWidth().height(32.dp)) {
                        fun center(i: Int) = when (i % 4) { 0 -> .41f; 2 -> .59f; else -> .5f } * size.width
                        val start = center(index)
                        val end = center(index + 1)
                        val curve = Path().apply {
                            moveTo(start, 0f)
                            cubicTo(start, size.height * .5f, end, size.height * .5f, end, size.height)
                        }
                        drawPath(curve, colors.borderStrong, style = Stroke(width = 3.dp.toPx()))
                    }
                }
            }
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.journey_ongoing), color = colors.textPrimary)
                    SecondaryActionButton(stringResource(R.string.journey_more), { additionalChapters++ },
                        Modifier.fillMaxWidth())
                }
            }
        }
    }
    selectedId?.let { id ->
        JourneyCatalogue.node(id)?.let { node ->
            JourneyPreview(node, node.id in completed, onDismiss = { selectedId = null }, onStart = {
                selectedId = null
                onStart(node.plan(fr), JourneyContext(node.id))
            })
        }
    }
}

@Composable
private fun JourneyWeekCard(state: JourneyState, now: Instant) {
    state.weekly(now)?.let { weekly ->
        PulseCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.journey_week, weekly.days, weekly.target), style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(progress = { (weekly.days.toFloat() / weekly.target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.journey_streak, weekly.streak))
                Text(stringResource(if (weekly.practiceWeek) R.string.journey_practice else R.string.journey_recovery),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun JourneyPreview(node: JourneyNode, completed: Boolean, onDismiss: () -> Unit, onStart: () -> Unit) {
    val fr = french()
    val plan = remember(node, fr) { node.plan(fr) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(node.title.localized(fr)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.journey_duration, formatDuration(TimelineExpander.expand(plan).knownMillis),
                    node.rounds, node.workSeconds, node.restSeconds))
                Text(stringResource(R.string.journey_effort))
                node.exercises.distinct().forEach { exercise ->
                    Text(exercise.title.localized(fr), style = MaterialTheme.typography.titleMedium)
                    Text(exercise.guidance.localized(fr))
                    Text(exercise.easier.localized(fr), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onStart) { Text(stringResource(if (completed) R.string.journey_replay else R.string.journey_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun defaultDays(target: Int): Int = when (target) {
    2 -> 9 // Monday, Thursday
    4 -> 43 // Monday, Tuesday, Thursday, Saturday
    5 -> 91 // Monday, Tuesday, Thursday, Friday, Sunday
    else -> 21 // Monday, Wednesday, Friday
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JourneySetup(
    state: JourneyState,
    onCancel: () -> Unit,
    onSave: (JourneyLevel, Int, Int, Int?) -> Unit,
    error: Boolean,
) {
    val enrollment = state.enrollment
    var levelName by rememberSaveable { mutableStateOf(state.level.name) }
    val plannedTarget = state.targets.maxByOrNull { it.week }?.days ?: 3
    var target by rememberSaveable { mutableIntStateOf(plannedTarget) }
    var days by rememberSaveable { mutableIntStateOf(enrollment?.trainingDays ?: defaultDays(target)) }
    var reminders by rememberSaveable { mutableStateOf(enrollment?.reminderMinute != null) }
    var minute by rememberSaveable { mutableIntStateOf(enrollment?.reminderMinute ?: 540) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(error) { if (error) saving = false }
    val fr = french()
    val context = LocalContext.current
    val locale = LocalResources.current.configuration.locales[0]
    val colors = LocalPulseColors.current
    PulseScaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PulsePageHeader(stringResource(R.string.journey_setup),
                    description = stringResource(R.string.journey_intro),
                    onBack = if (enrollment == null) null else onCancel)
                Text(stringResource(R.string.journey_level), color = colors.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JourneyLevel.entries.forEach { level ->
                        FilterChip(selected = levelName == level.name, onClick = { levelName = level.name },
                            label = { Text(level.title.localized(fr)) })
                    }
                }
                Text(stringResource(R.string.journey_target), color = colors.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..5).forEach { value ->
                        FilterChip(selected = target == value, onClick = { target = value; days = defaultDays(value) },
                            label = { Text(value.toString()) })
                    }
                }
                Text(stringResource(R.string.journey_days), color = colors.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val bit = 1 shl (day.value - 1)
                        FilterChip(selected = days and bit != 0, onClick = { days = days xor bit },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) })
                    }
                }
                Text(stringResource(R.string.journey_days_hint, target), color = colors.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminders, onCheckedChange = { reminders = it })
                    Text(stringResource(R.string.journey_reminder), color = colors.textPrimary)
                }
                if (reminders) {
                    SecondaryActionButton(stringResource(R.string.journey_time,
                        String.format(locale, "%02d:%02d", minute / 60, minute % 60)), {
                        TimePickerDialog(context, { _, hour, min -> minute = hour * 60 + min },
                            minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
                    }, Modifier.fillMaxWidth())
                    Text(stringResource(R.string.journey_reminder_hint), color = colors.textPrimary)
                }
                if (enrollment != null) Text(stringResource(R.string.journey_changes), color = colors.textPrimary)
                if (error) InfoBanner(stringResource(R.string.journey_error))
                PrimaryActionButton(stringResource(if (enrollment == null) R.string.journey_begin else R.string.journey_save),
                    onClick = {
                        saving = true
                        onSave(JourneyLevel.valueOf(levelName), target, days, if (reminders) minute else null)
                    }, enabled = !saving && Integer.bitCount(days) == target, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun JourneySummaryScreen(container: AppContainer, summary: SessionSummaryData, onDone: () -> Unit, onRepeat: () -> Unit) {
    val state by container.journey.state.collectAsStateWithLifecycle(initialValue = null)
    val earned = JourneyProgress.qualifies(summary.status, summary.completedIntervals, summary.totalIntervals, summary.skippedIntervals)
    PulseScaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                PulsePageHeader(summary.planName, onBack = onDone)
                Text(stringResource(if (earned) R.string.journey_earned else R.string.journey_not_earned),
                    style = MaterialTheme.typography.headlineSmall, color = LocalPulseColors.current.textPrimary)
                Text(formatDuration(summary.activeMillis), style = MaterialTheme.typography.headlineLarge,
                    color = LocalPulseColors.current.textPrimary)
                state?.let { value ->
                    JourneyWeekCard(value, Instant.now())
                    val node = summary.journey?.nodeId?.let(JourneyCatalogue::node)
                    if (node != null) Text(stringResource(R.string.journey_progress,
                        value.completedCount(node.level)),
                        color = LocalPulseColors.current.textPrimary)
                }
                Text(stringResource(R.string.journey_result_hint), color = LocalPulseColors.current.textPrimary)
                PrimaryActionButton(stringResource(R.string.journey_back), onDone, Modifier.fillMaxWidth())
                SecondaryActionButton(stringResource(R.string.journey_replay), onRepeat, Modifier.fillMaxWidth())
            }
        }
    }
}
