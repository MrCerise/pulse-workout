package com.pulse.intervalcoach.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.db.SessionEntity
import com.pulse.intervalcoach.data.db.ReminderEntity
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.PulseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant

// ---------------------------------------------------------------------------------------------
// Goals screen
// ---------------------------------------------------------------------------------------------

data class GoalsUiState(
    val totalWorkouts: Int = 0,
    val totalMinutes: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val goalMinutes: Int = 1000,
    val minutesThisMonth: Int = 0,
)

class GoalsViewModel(container: AppContainer) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    
    val state: StateFlow<GoalsUiState> = combine(
        container.sessions.history,
        refresh.asStateFlow(),
    ) { sessions, _ ->
        val totalWorkouts = sessions.size
        val totalMinutes = sessions.sumOf { it.durationMillis } / 60000
        val allDates = sessions.map { it.startedAt }
        val currentStreak = calculateStreak(allDates)
        val longestStreak = calculateLongestStreak(allDates)
        val now = Instant.now()
        val monthStart = now.minusDays(now.minusMonths(0).toEpochMilli()).toString()
        val minutesThisMonth = sessions.filter { 
            it.startedAt > now.minusSeconds(30 * 24 * 60 * 60).toInstant().toEpochMilli()
        }.sumOf { it.durationMillis } / 60000
        GoalsUiState(
            totalWorkouts = totalWorkouts,
            totalMinutes = totalMinutes,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            goalMinutes = 1000,
            minutesThisMonth = minutesThisMonth,
        )
    }.stateIn(scope = kotlinx.coroutines.context, extra = null, initialValue = GoalsUiState())

    private fun calculateStreak(dates: List<Instant>): Int {
        if (dates.isEmpty()) return 0
        val sorted = dates.sorted()
        var streak = 0
        var lastDate: Instant? = null
        for (date in sorted) {
            if (lastDate == null) {
                streak = 1
            } else {
                val diff = Duration.between(date, lastDate).toMinutes()
                if (diff <= 1440) { // 24 hours
                    streak++
                } else {
                    streak = 1
                }
            }
            lastDate = date
        }
        return streak
    }

    private fun calculateLongestStreak(dates: List<Instant>): Int {
        if (dates.isEmpty()) return 0
        val sorted = dates.sorted()
        var longest = 0
        var current = 1
        for (i in 1 until sorted.size) {
            val diff = Duration.between(sorted[i-1], sorted[i]).toMinutes()
            if (diff <= 1440) {
                current++
                longest = max(longest, current)
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
    val viewModel: GoalsViewModel = viewModel(
        key = "goals",
        initializer = { GoalsViewModel(container) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val scope = rememberCoroutineScope()

    PulseScaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(dimens.xxl),
            horizontalAlignment = Alignment.Start,
        ) {
            PulsePageHeader(
                title = "Goals",
                onBack = onBack,
            )
            
            if (state.totalWorkouts == 0) {
                EmptyState(
                    icon = "target",
                    title = "Start your first workout",
                    description = "Track your progress and set goals",
                )
            } else {
                StatTile(
                    label = "Workouts completed",
                    value = state.totalWorkouts.toString(),
                    icon = "history",
                    color = colors.work,
                )
                StatTile(
                    label = "Total minutes",
                    value = state.totalMinutes.toString(),
                    icon = "timer",
                    color = colors.work,
                )
                StatTile(
                    label = "Current streak",
                    value = "${state.currentStreak} days",
                    icon = "fire",
                    color = colors.accent,
                )
                StatTile(
                    label = "Longest streak",
                    value = "${state.longestStreak} days",
                    icon = "calendar",
                    color = colors.textMuted,
                )
                
                Column(
                    modifier = Modifier.padding(top = dimens.s),
                    verticalArrangement = Arrangement.spacedBy(dimens.s),
                ) {
                    Text(
                        text = "Goal: ${state.goalMinutes} minutes",
                        style = PulseType.BodyLarge,
                        color = colors.textPrimary,
                    )
                    Slider(
                        value = state.minutesThisMonth.toFloat() / state.goalMinutes.toFloat(),
                        onValueChange = { /* TODO: implement */ },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${state.minutesThisMonth}/${state.goalMinutes} minutes this month",
                        style = PulseType.BodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}
