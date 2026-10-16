package com.pulse.intervalcoach.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.categoryOrCustom
import com.pulse.intervalcoach.ui.components.pageContentPadding
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCalendar
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseDivider
import com.pulse.intervalcoach.ui.components.PulseListRow
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.PulseTextField
import com.pulse.intervalcoach.ui.components.NumberStepper
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Schedule a workout — a full page rather than a chain of links and confirm dialogs.
 *
 * One column, three decisions in the order they are made:
 *
 *  1. **When** — the app's own month calendar ([PulseCalendar], drawn from the same tokens as every
 *     other control) plus an hour/minute pair, with the resolved moment spelled out underneath so
 *     the choice is never ambiguous;
 *  2. **Which workout** — the library as a searchable, tappable list; the chosen row shows an
 *     accent check instead of a chevron;
 *  3. the **primary action**, which only lights up once both decisions exist.
 *
 * Replaces the old flow (an expanding stack of "Tomorrow · workout" text links behind a secondary
 * button, confirmed by a dialog that fixed the time at 18:00 and said so after the fact).
 */
@Composable
fun ScheduleScreen(
    container: AppContainer,
    workoutId: String?,
    onBack: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val scope = rememberCoroutineScope()
    val summaries by container.workouts.summaries.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedId by rememberSaveable { mutableStateOf(workoutId) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1)) }
    var hour by rememberSaveable { mutableStateOf(18) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(summaries, query) {
        if (query.isBlank()) summaries else summaries.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val selectedName = remember(selectedId, summaries) { summaries.firstOrNull { it.id == selectedId }?.name }
    val at = remember(selectedDate, hour, minute) {
        selectedDate.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }
    val atMillis = remember(at) { at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }

    PulseScaffold { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = pageContentPadding(),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            item {
                PulsePageHeader(
                    title = stringResource(R.string.planner_add),
                    onBack = onBack,
                )
            }

            // --- When --------------------------------------------------------------------------
            item { SectionHeader("When") }
            item {
                PulseCalendar(selectedDate = selectedDate, onSelectDate = { selectedDate = it })
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimens.m),
                    verticalAlignment = Alignment.Top,
                ) {
                    NumberStepper(
                        label = "Hour",
                        value = hour,
                        min = 0,
                        max = 23,
                        onChange = { hour = it },
                        modifier = Modifier.weight(1f),
                    )
                    NumberStepper(
                        label = "Minute",
                        value = minute,
                        min = 0,
                        max = 59,
                        onChange = { minute = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseIconTile(
                        icon = Icons.Filled.Schedule,
                        tint = colors.accent,
                        container = colors.accentTint,
                    )
                    Text(
                        text = at.format(DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", Locale.getDefault()))
                            .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // --- Which workout ------------------------------------------------------------------
            item { SectionHeader("Which workout", trailing = "${filtered.size}") }
            item {
                PulseTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search workouts",
                    leadingIcon = Icons.Filled.Search,
                    singleLine = true,
                )
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = if (summaries.isEmpty()) "No workouts yet" else "Nothing matches",
                        body = if (summaries.isEmpty()) {
                            stringResource(R.string.workouts_empty_body)
                        } else {
                            "Try another search."
                        },
                    )
                }
            } else {
                item {
                    PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.xs)) {
                        Column {
                            filtered.forEachIndexed { index, workout ->
                                val isSelected = workout.id == selectedId
                                val category = workout.categoryOrCustom()
                                PulseListRow(
                                    title = workout.name,
                                    subtitle = "${workout.intervalCount} intervals · " +
                                        (if (workout.hasOpenEnded) "open-ended" else formatDuration(workout.knownDurationMillis)),
                                    leadingIcon = category.icon,
                                    leadingTint = colors.categoryColor(category.hue),
                                    leadingContainer = colors.categoryTint(category.hue),
                                    onClick = { selectedId = workout.id },
                                    trailing = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = colors.accent,
                                            )
                                        }
                                    },
                                )
                                if (index != filtered.lastIndex) PulseDivider()
                            }
                        }
                    }
                }
            }

            // --- The action ---------------------------------------------------------------------
            item {
                Column {
                    Spacer(Modifier.height(dimens.s))
                    PrimaryActionButton(
                        text = if (selectedName == null) "Pick a workout" else "Schedule \"$selectedName\"",
                        onClick = {
                            val id = selectedId
                            if (id != null) {
                                scope.launch {
                                    container.reminders.schedule(id, atMillis)
                                    onBack()
                                }
                            }
                        },
                        enabled = selectedId != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
