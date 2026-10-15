package com.pulse.intervalcoach.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pulse.engine.PhaseKind
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.wear.R
import com.pulse.intervalcoach.wear.ui.theme.LocalWearColors

/** What a session view needs to draw — the local engine and the phone mirror provide the same shape. */
data class SessionViewData(
    val stepName: String,
    val phaseKind: PhaseKind,
    val stepRemainingMillis: Long,
    val stepDurationMillis: Long,
    val stepIndex: Int,
    val totalSteps: Int,
    val sessionElapsedMillis: Long,
    val paused: Boolean,
    val awaitingManual: Boolean,
)

/**
 * The shared session dial: phase-coloured progress ring, remaining time, step counter and the
 * control row. Drawn identically whether the engine runs on the watch or the session is mirrored
 * from the phone.
 */
@Composable
fun SessionView(
    data: SessionViewData,
    accentLabel: String,
    onPauseToggle: () -> Unit,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    onManualDone: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalWearColors.current
    val phaseColor = colors.phaseColor(data.phaseKind)
    val progress = if (data.stepDurationMillis > 0L) {
        1f - (data.stepRemainingMillis.toFloat() / data.stepDurationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = data.stepName,
            style = MaterialTheme.typography.caption1,
            color = phaseColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(100.dp),
                foregroundColor = phaseColor,
                trackColor = colors.track,
                strokeWidth = 7.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatDuration(data.stepRemainingMillis.coerceAtLeast(0L)),
                    style = MaterialTheme.typography.display2.copy(fontSize = 26.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.player_step_of, data.stepIndex + 1, data.totalSteps),
                    style = MaterialTheme.typography.caption1,
                    color = colors.textMuted,
                )
            }
        }

        if (data.awaitingManual) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(
                    icon = Icons.Filled.Check,
                    description = stringResource(R.string.player_done),
                    background = phaseColor,
                    content = colors.canvas,
                    onClick = onManualDone,
                )
                ControlButton(
                    icon = Icons.Filled.SkipNext,
                    description = stringResource(R.string.player_skip),
                    background = colors.surfaceRaised,
                    content = colors.textPrimary,
                    onClick = onSkip,
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterHorizontally,
            ) {
                ControlButton(
                    icon = if (data.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    description = if (data.paused) {
                        stringResource(R.string.player_resume)
                    } else {
                        stringResource(R.string.player_pause)
                    },
                    background = colors.accent,
                    content = colors.onAccent,
                    onClick = onPauseToggle,
                )
                ControlButton(
                    icon = Icons.Filled.SkipNext,
                    description = stringResource(R.string.player_skip),
                    background = colors.surfaceRaised,
                    content = colors.textPrimary,
                    onClick = onSkip,
                )
                ControlButton(
                    icon = Icons.Filled.Add,
                    description = stringResource(R.string.player_add_time),
                    background = colors.surfaceRaised,
                    content = colors.textPrimary,
                    onClick = onAddTime,
                )
            }
        }

        Text(
            text = accentLabel,
            style = MaterialTheme.typography.caption1,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = onStop,
            colors = ButtonDefaults.secondaryButtonColors(
                backgroundColor = colors.surface,
                contentColor = colors.destructive,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.player_stop),
                style = MaterialTheme.typography.button2,
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    background: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = ButtonDefaults.primaryButtonColors(backgroundColor = background, contentColor = content),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
    }
}
