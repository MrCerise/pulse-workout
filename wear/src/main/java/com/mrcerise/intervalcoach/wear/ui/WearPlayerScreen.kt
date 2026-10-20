package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.pulse.engine.PhaseKind
import com.pulse.engine.TimerStatus
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors

/**
 * The local player: loads the tapped workout into the watch's own engine (the same engine the
 * phone runs) and shows the session screen. One tap in the library starts the workout — watch
 * interactions are short by design. The screen is stateless between frames: the controller under
 * the container keeps timing alive across wrist turns, and a swipe back leaves it running.
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
    var startFailed by remember { mutableStateOf(false) }
    var confirmingStop by rememberSaveable { mutableStateOf(false) }

    // Load once per workout id: a session already running this plan is resumed, not restarted.
    LaunchedEffect(workoutId) {
        if (active?.plan?.id != workoutId) {
            val plan = container.workouts.plan(workoutId)
            startFailed = plan == null || container.session.startWorkout(plan).isFailure
        }
    }
    LaunchedEffect(summary) {
        if (summary != null) onFinished()
    }

    KeepScreenOnWhileActive(active = snapshot.isActive, enabled = prefs.keepScreenOn)

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
            nextStepName = snapshot.nextStep?.name,
        ),
        footer = stringResource(R.string.player_elapsed, formatDuration(snapshot.sessionElapsedMillis)),
        onPauseToggle = { container.session.togglePause() },
        onSkip = { container.session.next() },
        onAddTime = { container.session.addTime(15_000L) },
        onManualDone = { container.session.completeManual() },
        onStop = { container.session.stop() },
        confirmingStop = confirmingStop,
        onConfirmingStopChange = { confirmingStop = it },
    )

    AlertDialog(
        visible = startFailed,
        onDismissRequest = onBack,
        icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = colors.destructive) },
        title = { Text(stringResource(R.string.player_load_failed), textAlign = TextAlign.Center) },
        edgeButton = {
            AlertDialogDefaults.EdgeButton(onClick = onBack) {
                Text(stringResource(R.string.summary_done))
            }
        },
    )
}
