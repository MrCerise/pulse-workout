package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.pulse.sync.LiveSession
import com.pulse.sync.WatchCommand
import com.pulse.sync.WatchCommandKind

/**
 * The phone's session, mirrored on the watch.
 *
 * The screen is the same [SessionView]; every control becomes a command message back to the
 * phone, so pausing from the wrist pauses the phone's engine — and its spoken cues — at the same
 * step. Stopping asks first here too: it ends the workout on the phone as well.
 */
@Composable
fun WearRemotePlayerScreen(
    container: WearAppContainer,
    onClosed: () -> Unit,
) {
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val remote by container.wearSync.remoteSession.collectAsStateWithLifecycle()
    var confirmingStop by rememberSaveable { mutableStateOf(false) }

    KeepScreenOnWhileActive(active = remote != null, enabled = prefs.keepScreenOn)

    val live = remote
    if (live != null) {
        SessionView(
            data = live.toViewData(),
            footer = stringResource(R.string.remote_badge) + " · " + formatDuration(live.sessionElapsedMillis),
            onPauseToggle = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.TOGGLE_PAUSE)) },
            onSkip = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.NEXT)) },
            onAddTime = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.ADD_TIME, 15_000L)) },
            onManualDone = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.COMPLETE_MANUAL)) },
            onStop = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.STOP)) },
            confirmingStop = confirmingStop,
            onConfirmingStopChange = { confirmingStop = it },
        )
    }

    AlertDialog(
        visible = live == null,
        onDismissRequest = onClosed,
        icon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
        title = { Text(stringResource(R.string.remote_ended), textAlign = TextAlign.Center) },
        edgeButton = {
            AlertDialogDefaults.EdgeButton(onClick = onClosed) {
                Text(stringResource(R.string.summary_done))
            }
        },
    )
}

/** Maps a phone snapshot onto the shared session screen's inputs. */
private fun LiveSession.toViewData(): SessionViewData {
    val kind = runCatching { PhaseKind.valueOf(stepKind) }.getOrDefault(PhaseKind.CUSTOM)
    return SessionViewData(
        stepName = stepName.ifBlank { planName },
        phaseKind = kind,
        stepRemainingMillis = stepRemainingMillis,
        stepDurationMillis = stepDurationMillis,
        stepIndex = stepIndex,
        totalSteps = totalSteps,
        sessionElapsedMillis = sessionElapsedMillis,
        paused = status == TimerStatus.PAUSED.name,
        awaitingManual = manualCompletion,
    )
}
