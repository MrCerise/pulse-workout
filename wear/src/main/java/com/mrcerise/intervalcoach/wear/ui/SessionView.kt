package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonColors
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.pulse.engine.PhaseKind
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors

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
    /** The step after this one, when the source knows it (the phone mirror does not). */
    val nextStepName: String? = null,
)

/**
 * The shared session screen, drawn identically whether the engine runs on the watch or the
 * session is mirrored from the phone.
 *
 * The whole bezel is the step's progress ring in its phase colour, so the state reads from across
 * a room. Inside: the step name, the time left in large numerals, where the session is and what
 * comes next, then three controls sized for a moving wrist — the primary one in the middle.
 *
 * Stopping is deliberately two steps: pause, and the left control becomes Stop, which still asks
 * before ending. A brushed sleeve can pause a workout; it cannot end one.
 */
@Composable
fun SessionView(
    data: SessionViewData,
    footer: String,
    onPauseToggle: () -> Unit,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    onManualDone: () -> Unit,
    onStop: () -> Unit,
    confirmingStop: Boolean,
    onConfirmingStopChange: (Boolean) -> Unit,
) {
    val colors = LocalWearColors.current
    val phaseColor = colors.phaseColor(data.phaseKind)
    // A manual step has no clock, so its ring is simply full; a step not loaded yet starts empty.
    val progress = when {
        data.stepDurationMillis > 0L ->
            1f - (data.stepRemainingMillis.toFloat() / data.stepDurationMillis.toFloat()).coerceIn(0f, 1f)
        data.awaitingManual -> 1f
        else -> 0f
    }
    // The indicator observes its lambda with snapshotFlow, so the lambda must read State: a plain
    // captured Float would be drawn once and never again.
    val progressState = rememberUpdatedState(progress)
    // A paused session breathes, so "paused" is visible at a glance and not only in words.
    val pulse = rememberInfiniteTransition(label = "paused")
    val pausedAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pausedAlpha",
    )
    val timeAlpha = if (data.paused) pausedAlpha else 1f

    ScreenScaffold {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progressState.value },
                modifier = Modifier.fillMaxSize().padding(2.dp),
                // The ring opens around the time at twelve o'clock and fills clockwise from just
                // right of it, so the first seconds of a step are never drawn under the clock.
                startAngle = RING_START_ANGLE,
                endAngle = RING_END_ANGLE,
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = if (data.paused) phaseColor.copy(alpha = 0.45f) else phaseColor,
                    trackColor = colors.phaseTint(data.phaseKind),
                ),
                strokeWidth = 8.dp,
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = data.stepName,
                    style = MaterialTheme.typography.titleSmall,
                    color = phaseColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (data.awaitingManual) {
                    Text(
                        text = stringResource(R.string.player_manual_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                } else {
                    Text(
                        text = formatDuration(data.stepRemainingMillis.coerceAtLeast(0L)),
                        style = MaterialTheme.typography.numeralMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.alpha(timeAlpha),
                    )
                }
                Text(
                    text = if (data.paused) {
                        stringResource(R.string.player_paused)
                    } else {
                        statusLine(data)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (data.paused) phaseColor else colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Controls(
                    data = data,
                    onPauseToggle = onPauseToggle,
                    onSkip = onSkip,
                    onAddTime = onAddTime,
                    onManualDone = onManualDone,
                    onStopRequest = { onConfirmingStopChange(true) },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    AlertDialog(
        visible = confirmingStop,
        onDismissRequest = { onConfirmingStopChange(false) },
        icon = { Icon(Icons.Filled.StopCircle, contentDescription = null, tint = colors.destructive) },
        title = { Text(stringResource(R.string.player_stop_confirm), textAlign = TextAlign.Center) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    onConfirmingStopChange(false)
                    onStop()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.destructive,
                    contentColor = colors.canvas,
                ),
            )
        },
    )
}

/** Degrees clockwise from three o'clock: a 60° opening centred on twelve o'clock (270°). */
private const val RING_START_ANGLE = 300f
private const val RING_END_ANGLE = 240f

/** "3 of 12 · Next: Rest" — position always, the next step when it is known. */
@Composable
private fun statusLine(data: SessionViewData): String {
    val position = stringResource(R.string.player_step_of, data.stepIndex + 1, data.totalSteps)
    val next = data.nextStepName?.takeIf { it.isNotBlank() } ?: return position
    return position + " · " + stringResource(R.string.player_next, next)
}

@Composable
private fun Controls(
    data: SessionViewData,
    onPauseToggle: () -> Unit,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    onManualDone: () -> Unit,
    onStopRequest: () -> Unit,
) {
    val colors = LocalWearColors.current
    // Sized for the narrowest round screen (192 dp): the row must clear the bezel ring at the
    // height it sits. Material keeps the 48 dp touch target around the smaller visual.
    val sideSize = 42.dp
    val mainSize = IconButtonDefaults.DefaultButtonSize
    val stopColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = colors.destructiveTint,
        contentColor = colors.destructive,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: +15 s while the clock runs; Stop once the user has paused (or for a manual step,
        // where there is no clock to extend).
        if (data.paused || data.awaitingManual) {
            SideButton(Icons.Filled.Stop, stringResource(R.string.player_stop), sideSize, stopColors, onStopRequest)
        } else {
            // Shows "+15", announces "Add 15 seconds".
            val description = stringResource(R.string.player_add_time)
            FilledTonalIconButton(
                onClick = onAddTime,
                modifier = Modifier.size(sideSize).semantics { contentDescription = description },
            ) {
                Text(
                    text = stringResource(R.string.player_add_time_short),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }

        if (data.awaitingManual) {
            FilledIconButton(
                onClick = onManualDone,
                modifier = Modifier.size(mainSize),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.phaseColor(data.phaseKind),
                    contentColor = colors.canvas,
                ),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.player_done),
                    modifier = Modifier.size(IconButtonDefaults.DefaultIconSize),
                )
            }
        } else {
            FilledIconButton(
                onClick = onPauseToggle,
                modifier = Modifier.size(mainSize),
            ) {
                Icon(
                    if (data.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (data.paused) R.string.player_resume else R.string.player_pause),
                    modifier = Modifier.size(IconButtonDefaults.DefaultIconSize),
                )
            }
        }

        SideButton(
            Icons.Filled.SkipNext,
            stringResource(R.string.player_skip),
            sideSize,
            IconButtonDefaults.filledTonalIconButtonColors(),
            onSkip,
        )
    }
}

@Composable
private fun SideButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    colors: IconButtonColors,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(size), colors = colors) {
        Icon(icon, contentDescription = description)
    }
}
