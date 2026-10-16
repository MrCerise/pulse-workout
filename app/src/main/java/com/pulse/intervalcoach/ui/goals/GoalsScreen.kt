package com.pulse.intervalcoach.ui.goals

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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.STATUS_RUNNING
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.ProgressBar
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.PulseSliderRow
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.home.WHO_WEEKLY_MINUTES
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Goals screen
// ---------------------------------------------------------------------------------------------

/** Step size of the goal slider, in active minutes. */
private const val GOAL_STEP_MINUTES = 15
private const val GOAL_MIN_MINUTES = 30
private const val GOAL_MAX_MINUTES = 600

data class GoalsUiState(
    val totalWorkouts: Int = 0,
    val totalMinutes: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    /** The user's own weekly target, in active minutes. */
    val goalMinutes: Int = WHO_WEEKLY_MINUTES,
    val minutesThisWeek: Int = 0,
)

class GoalsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<GoalsUiState> = combine(
        container.sessions.history,
        container.preferences.flow,
    ) { all, prefs ->
        // A RUNNING row is a workout still in progress (or about to be recovered as
        // interrupted) — it has not earned any minutes yet.
        val sessions = all.filter { it.status != STATUS_RUNNING }
        val zone = ZoneId.systemDefault()
        val days = sessions.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
        val weekStart = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        GoalsUiState(
            totalWorkouts = sessions.size,
            totalMinutes = (sessions.sumOf { it.activeMillis } / 60_000L).toInt(),
            currentStreak = container.sessions.currentStreak(zone),
            longestStreak = longestStreak(days),
            goalMinutes = prefs.weeklyGoalMinutes,
            minutesThisWeek = (sessions.filter { it.startedAt >= weekStart }.sumOf { it.activeMillis } / 60_000L).toInt(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun setGoal(minutes: Int) {
        viewModelScope.launch { container.preferences.setWeeklyGoalMinutes(minutes) }
    }

    /** Longest run of consecutive calendar days with at least one session. */
    private fun longestStreak(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var longest = 1
        var current = 1
        for (i in 1 until sorted.size) {
            if (sorted[i - 1].plusDays(1) == sorted[i]) {
                current++
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }
        return longest
    }
}

@Composable
fun GoalsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: GoalsViewModel = viewModel(initializer = { GoalsViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current

    PulseScaffold { padding ->
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
                PulsePageHeader(
                    title = "Goals",
                    onBack = onBack,
                )
            }

            if (state.totalWorkouts == 0) {
                item {
                    EmptyState(
                        title = "Start your first workout",
                        body = "No sessions yet.",
                        icon = Icons.Filled.Flag,
                    )
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                        StatTile("Workouts", state.totalWorkouts.toString(), modifier = Modifier.weight(1f))
                        StatTile("Total minutes", state.totalMinutes.toString(), modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                        StatTile(
                            "Current streak",
                            "${state.currentStreak}d",
                            accent = colors.success,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile("Longest streak", "${state.longestStreak}d", modifier = Modifier.weight(1f))
                    }
                }
            }

            // The goal card is always here — it is the one thing on the page a user can *do*.
            item {
                PulseCard {
                    Column {
                        Text(
                            text = "Weekly goal: ${state.goalMinutes} active minutes",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(dimens.s))
                        ProgressBar(
                            progress = state.minutesThisWeek.toFloat() / state.goalMinutes.coerceAtLeast(1).toFloat(),
                        )
                        Spacer(Modifier.height(dimens.s))
                        Text(
                            text = "${state.minutesThisWeek} / ${state.goalMinutes} minutes this week" +
                                if (state.goalMinutes == WHO_WEEKLY_MINUTES) " · WHO guideline" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(dimens.l))
                        PulseSliderRow(
                            label = stringResource(R.string.settings_weekly_goal),
                            value = state.goalMinutes.toFloat(),
                            onValueChange = { minutes ->
                                val snapped = (Math.round(minutes / GOAL_STEP_MINUTES.toFloat()) * GOAL_STEP_MINUTES)
                                    .coerceIn(GOAL_MIN_MINUTES, GOAL_MAX_MINUTES)
                                if (snapped != state.goalMinutes) viewModel.setGoal(snapped)
                            },
                            valueRange = GOAL_MIN_MINUTES.toFloat()..GOAL_MAX_MINUTES.toFloat(),
                            // No fixed tick marks: the value snaps to 15-minute steps in the
                            // handler, and 38 slider dots would have been visual noise.
                            valueLabel = "${((Math.round(state.goalMinutes.toFloat() / GOAL_STEP_MINUTES)) * GOAL_STEP_MINUTES)} min",
                        )
                    }
                }
            }
        }
    }
}
