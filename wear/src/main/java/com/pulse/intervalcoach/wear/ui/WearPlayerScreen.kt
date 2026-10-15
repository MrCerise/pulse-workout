package com.pulse.intervalcoach.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.pulse.engine.PhaseKind
import com.pulse.engine.TimerStatus
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.wear.R
import com.pulse.intervalcoach.wear.WearAppContainer
import com.pulse.intervalcoach.wear.ui.theme.LocalWearColors

/**
 * The local player: loads the tapped workout into the watch's own engine (the same engine the
 * phone runs) and shows the session dial. One tap in the library starts the workout — watch
 * interactions are short by design. The screen is stateless between frames: the controller under
 * the container keeps timing alive across wrist turns.
 */
@Composable
fun WearPlayerScreen(
    container: WearAppContainer,
    workoutId: String,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWearColors.current
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val snapshot by container.session.snapshot.collectAsStateWithLifecycle()
    val active by container.session.active.collectAsStateWithLifecycle()
    val summary by container.session.summary.collectAsStateWithLifecycle()
    var startError by remember { mutableStateOf<String?>(null) }

    // Load once per workout id: a session already running this plan is resumed, not restarted.
    LaunchedEffect(workoutId) {
        if (active?.plan?.id != workoutId) {
            val plan = container.workouts.plan(workoutId)
            if (plan == null) {
                startError = ""
            } else {
                val result = container.session.startWorkout(plan)
                if (result.isFailure) startError = result.exceptionOrNull()?.message ?: ""
            }
        }
    }
    LaunchedEffect(summary) {
        if (summary != null) onFinished()
    }

    KeepScreenOnWhileActive(active = snapshot.isActive, enabled = prefs.keepScreenOn)

    Scaffold(timeText = { TimeText() }) {
        if (startError != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.player_load_failed),
                        style = MaterialTheme.typography.body1,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.secondaryButtonColors(
                            backgroundColor = colors.surface,
                            contentColor = colors.textPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.summary_done), style = MaterialTheme.typography.button)
                    }
                }
            }
        } else {
            var confirmingStop by remember { mutableStateOf(false) }
            if (confirmingStop) {
                StopConfirmView(
                    onStop = {
                        confirmingStop = false
                        container.session.stop()
                    },
                    onCancel = { confirmingStop = false },
                )
            } else {
                SessionView(
                    data = SessionViewData(
                        stepName = snapshot.step?.name ?: snapshot.planName,
                        phaseKind = snapshot.step?.kind ?: PhaseKind.CUSTOM,
                        stepRemainingMillis = snapshot.stepRemainingMillis,
                        stepDurationMillis = snapshot.stepDurationMillis,
                        stepIndex = snapshot.stepIndex,
                        totalSteps = snapshot.totalSteps,
                        sessionElapsedMillis = snapshot.sessionElapsedMillis,
                        paused = snapshot.isPaused,
                        awaitingManual = snapshot.status == TimerStatus.AWAITING_MANUAL,
                    ),
                    accentLabel = formatDuration(snapshot.sessionElapsedMillis) +
                        if (snapshot.isPaused) " · " + stringResource(R.string.player_paused) else "",
                    onPauseToggle = { container.session.togglePause() },
                    onSkip = { container.session.next() },
                    onAddTime = { container.session.addTime(15_000L) },
                    onManualDone = { container.session.completeManual() },
                    onStop = { confirmingStop = true },
                )
            }
        }
    }
}

@Composable
private fun StopConfirmView(onStop: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalWearColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.player_stop_confirm),
                style = MaterialTheme.typography.title3,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onStop,
                colors = ButtonDefaults.primaryButtonColors(
                    backgroundColor = colors.destructive,
                    contentColor = colors.canvas,
                ),
            ) {
                Text(stringResource(R.string.player_stop), style = MaterialTheme.typography.button)
            }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.secondaryButtonColors(
                    backgroundColor = colors.surface,
                    contentColor = colors.textPrimary,
                ),
            ) {
                Text(stringResource(R.string.player_keep_going), style = MaterialTheme.typography.button)
            }
        }
    }
}
