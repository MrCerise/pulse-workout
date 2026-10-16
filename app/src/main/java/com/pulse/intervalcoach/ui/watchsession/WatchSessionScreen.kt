package com.pulse.intervalcoach.ui.watchsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.PhaseKind
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.DangerActionButton
import com.pulse.intervalcoach.ui.components.ProgressRing
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.PulseStat
import com.pulse.intervalcoach.ui.components.PulseStatGrid
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.sync.LiveSession
import com.pulse.sync.WatchCommand
import com.pulse.sync.WatchCommandKind

/**
 * A workout running on the watch, mirrored on the phone.
 *
 * The watch owns the session (its engine, its haptics); this screen renders the live snapshot at
 * ~1 Hz and turns every control into a command message back to the watch. It is the same
 * relationship in reverse as the watch's remote player: whichever device runs the workout, the
 * other one is its remote control.
 */
@Composable
fun WatchSessionScreen(
    container: AppContainer,
    onClose: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val remote by container.wearSync.remoteWatchSession.collectAsStateWithLifecycle()
    var confirmingStop by remember { mutableStateOf(false) }

    PulseScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimens.pagePadding,
                    end = dimens.pagePadding,
                    top = dimens.cardGap,
                    bottom = dimens.huge,
                ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            PulsePageHeader(
                title = stringResource(R.string.watch_session_title),
                description = stringResource(R.string.watch_session_subtitle),
                onBack = onClose,
            )

            val live = remote
            if (live == null) {
                PulseCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimens.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.m),
                    ) {
                        Icon(
                            Icons.Filled.Watch,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(R.string.watch_session_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                SecondaryActionButton(
                    text = stringResource(R.string.watch_session_close),
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val phase = runCatching { PhaseKind.valueOf(live.stepKind) }.getOrDefault(PhaseKind.CUSTOM)
                val phaseColor = colors.phaseColor(phase)
                val progress = if (live.stepDurationMillis > 0L) {
                    1f - (live.stepRemainingMillis.toFloat() / live.stepDurationMillis.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }

                PulseCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.s),
                    ) {
                        Text(
                            text = live.planName,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = live.stepName,
                            style = MaterialTheme.typography.labelLarge,
                            color = phaseColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(contentAlignment = Alignment.Center) {
                            ProgressRing(
                                progress = progress,
                                color = phaseColor,
                                trackColor = colors.track,
                                strokeWidth = 10.dp,
                                modifier = Modifier.size(180.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = formatDuration(live.stepRemainingMillis.coerceAtLeast(0L)),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.watch_session_step_of,
                                            live.stepIndex + 1,
                                            live.totalSteps,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                    )
                                }
                            }
                        }
                        if (live.status == "PAUSED") {
                            Text(
                                text = stringResource(R.string.watch_session_paused),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.prepare,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.s),
                ) {
                    WatchControl(
                        icon = if (live.status == "PAUSED") Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        label = if (live.status == "PAUSED") {
                            stringResource(R.string.watch_session_resume)
                        } else {
                            stringResource(R.string.watch_session_pause)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        container.wearSync.sendCommand(WatchCommand(WatchCommandKind.TOGGLE_PAUSE))
                    }
                    WatchControl(
                        icon = Icons.Filled.SkipNext,
                        label = stringResource(R.string.watch_session_skip),
                        modifier = Modifier.weight(1f),
                    ) {
                        container.wearSync.sendCommand(WatchCommand(WatchCommandKind.NEXT))
                    }
                    WatchControl(
                        icon = Icons.Filled.Add,
                        label = stringResource(R.string.watch_session_add_time),
                        modifier = Modifier.weight(1f),
                    ) {
                        container.wearSync.sendCommand(WatchCommand(WatchCommandKind.ADD_TIME, 15_000L))
                    }
                }

                PulseStatGrid(
                    listOf(
                        PulseStat(
                            label = stringResource(R.string.watch_session_elapsed),
                            value = formatDuration(live.sessionElapsedMillis),
                        ),
                        PulseStat(
                            label = stringResource(R.string.watch_session_remaining),
                            value = live.sessionRemainingMillis?.let { formatDuration(it) }
                                ?: stringResource(R.string.watch_session_open_ended),
                        ),
                    ),
                )

                Spacer(Modifier.height(dimens.s))
                DangerActionButton(
                    text = stringResource(R.string.watch_session_stop),
                    onClick = { confirmingStop = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryActionButton(
                    text = stringResource(R.string.watch_session_close),
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(dimens.huge))
        }
    }

    if (confirmingStop) {
        ConfirmDialog(
            title = stringResource(R.string.watch_session_stop_confirm),
            body = stringResource(R.string.watch_session_stop_message),
            confirmLabel = stringResource(R.string.watch_session_stop),
            dismissLabel = stringResource(R.string.watch_session_keep_going),
            onConfirm = {
                confirmingStop = false
                container.wearSync.sendCommand(WatchCommand(WatchCommandKind.STOP))
            },
            onDismiss = { confirmingStop = false },
        )
    }
}

@Composable
private fun WatchControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    PulseCard(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = label, tint = colors.accent)
            Spacer(Modifier.height(dimens.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
