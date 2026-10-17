package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors
import com.pulse.sync.LiveSession
import com.pulse.sync.WatchCommand
import com.pulse.sync.WatchCommandKind

/**
 * The phone's session, mirrored on the watch.
 *
 * The dial is the same [SessionView]; every control becomes a command message back to the phone,
 * so pausing from the wrist pauses the phone's engine — and its spoken cues — at the same step.
 */
@Composable
fun WearRemotePlayerScreen(
    container: WearAppContainer,
    onClosed: () -> Unit,
) {
    val colors = LocalWearColors.current
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val remote by container.wearSync.remoteSession.collectAsStateWithLifecycle()

    KeepScreenOnWhileActive(active = remote != null, enabled = prefs.keepScreenOn)

    Scaffold(timeText = { TimeText() }) {
        val live = remote
        if (live == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.remote_ended),
                        style = MaterialTheme.typography.body1,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onClosed,
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
            SessionView(
                data = live.toViewData(),
                accentLabel = stringResource(R.string.remote_badge) + " · " +
                    formatDuration(live.sessionElapsedMillis),
                onPauseToggle = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.TOGGLE_PAUSE)) },
                onSkip = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.NEXT)) },
                onAddTime = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.ADD_TIME, 15_000L)) },
                onManualDone = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.COMPLETE_MANUAL)) },
                onStop = { container.wearSync.sendCommand(WatchCommand(WatchCommandKind.STOP)) },
            )
        }
    }
}

/** Maps a phone snapshot onto the shared dial's inputs. */
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
