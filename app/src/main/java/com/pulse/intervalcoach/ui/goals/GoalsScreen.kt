package com.pulse.intervalcoach.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.STATUS_RUNNING
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.ProgressBar
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// ---------------------------------------------------------------------------------------------
// Goals screen
// ---------------------------------------------------------------------------------------------

/** Monthly target, in active minutes. */
private const val MONTHLY_GOAL_MINUTES = 1000

data class GoalsUiState(
    val totalWorkouts: Int = 0,
    val totalMinutes: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val goalMinutes: Int = MONTHLY_GOAL_MINUTES,
    val minutesThisMonth: Int = 0,
)

class GoalsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<GoalsUiState> = container.sessions.history
        .map { all ->
            // A RUNNING row is a workout still in progress (or about to be recovered as
            // interrupted) — it has not earned any minutes yet.
            val sessions = all.filter { it.status != STATUS_RUNNING }
            val zone = ZoneId.systemDefault()
            val days = sessions.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
            val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            GoalsUiState(
                totalWorkouts = sessions.size,
                totalMinutes = (sessions.sumOf { it.activeMillis } / 60_000L).toInt(),
                currentStreak = container.sessions.currentStreak(zone),
                longestStreak = longestStreak(days),
                goalMinutes = MONTHLY_GOAL_MINUTES,
                minutesThisMonth = (sessions.filter { it.startedAt >= monthStart }.sumOf { it.activeMillis } / 60_000L).toInt(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

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
                    description = "Track your progress against a monthly target",
                    onBack = onBack,
                )
            }

            if (state.totalWorkouts == 0) {
                item {
                    EmptyState(
                        title = "Start your first workout",
                        body = "Finish a workout and your progress toward your goals shows up here.",
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
                item {
                    PulseCard {
                        Column {
                            Text(
                                text = "Goal: ${state.goalMinutes} minutes this month",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.height(dimens.m))
                            ProgressBar(
                                progress = state.minutesThisMonth.toFloat() / state.goalMinutes.coerceAtLeast(1).toFloat(),
                            )
                            Spacer(Modifier.height(dimens.s))
                            Text(
                                text = "${state.minutesThisMonth} / ${state.goalMinutes} minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}
