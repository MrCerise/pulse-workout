package com.pulse.intervalcoach.ui.player

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.TimerStatus
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.PlayerDensity
import com.pulse.intervalcoach.session.formatClock
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.ProgressRing
import com.pulse.intervalcoach.ui.components.TimelineBar
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseMotion
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.R

/**
 * Full-screen workout player.
 *
 * The remaining time dominates the screen and is readable across a room; every control is at least
 * 48 dp, colour is never the only signal for a phase, and the layout follows the user's density,
 * left-handed and reduced-motion preferences.
 */
@Composable
fun PlayerScreen(
    container: AppContainer,
    onFinished: () -> Unit,
    onClose: () -> Unit,
) {
    val controller = container.sessionController
    val snapshot by controller.snapshot.collectAsStateWithLifecycle()
    val active by controller.active.collectAsStateWithLifecycle()
    val summary by controller.summary.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val cuesMuted by controller.cuesMuted.collectAsStateWithLifecycle()
    val lastCue by controller.lastCueText.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val motion = LocalPulseMotion.current
    val context = LocalContext.current
    val view = LocalView.current
    var confirmEnd by remember { mutableStateOf(false) }
    var instructor by remember { mutableStateOf(false) }

    // Keep the screen awake only while the user asked for it, and only in this screen.
    DisposableEffect(prefs?.keepScreenOn, active) {
        val window = (context as? Activity)?.window
        if (prefs?.keepScreenOn == true && active != null) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Leaving the player when the session ended (and was recorded) shows the summary.
    LaunchedEffect(summary, active) {
        if (active == null && summary != null) onFinished()
    }

    if (active == null) {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No active workout",
                body = "Start a workout from Home or your library and the player will appear here.",
                actionLabel = "Close",
                onAction = onClose,
            )
        }
        return
    }

    val step = snapshot.step
    val accent = step?.let { colors.phaseColor(it.kind) } ?: colors.work
    val density = prefs?.playerDensity ?: PlayerDensity.STANDARD
    val timerSize = when (density) {
        PlayerDensity.COMPACT -> MaterialTheme.typography.displayMedium.fontSize
        PlayerDensity.STANDARD -> MaterialTheme.typography.displayLarge.fontSize
        PlayerDensity.LARGE -> MaterialTheme.typography.displayLarge.fontSize * 1.35f
    }
    val leftHanded = prefs?.leftHandedPlayer == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(dimens.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- Top bar: session name, mute, close ---
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(active?.plan?.name ?: "", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (cuesMuted) "Cues muted" else "Voice & sound on",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            IconButton(onClick = { controller.toggleCuesMuted() }) {
                Icon(
                    if (cuesMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (cuesMuted) "Unmute cues" else "Mute cues",
                    tint = colors.textPrimary,
                )
            }
            IconButton(onClick = { confirmEnd = true }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.player_end_session), tint = colors.textPrimary)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Elapsed ${formatClock(snapshot.sessionElapsedMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                snapshot.sessionRemainingMillis?.let { "Remaining ${formatClock(it)}" } ?: "Open-ended",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(dimens.l))

        // --- Phase + round ---
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            step?.let { PhaseChip(kind = it.kind, name = it.name) }
            step?.roundInGroup?.let { round ->
                Text(
                    "Round $round of ${step.roundsInGroup ?: 0}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            }
            step?.sideLabel?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        }

        Spacer(Modifier.height(dimens.l))

        // --- The timer itself ---
        val showing = if (snapshot.status == TimerStatus.AWAITING_MANUAL) snapshot.stepElapsedMillis else snapshot.stepRemainingMillis
        ProgressRing(
            progress = if (snapshot.status == TimerStatus.AWAITING_MANUAL) 0f else snapshot.intervalProgress,
            color = accent,
            trackColor = colors.outline,
            strokeWidth = 10.dp,
            modifier = Modifier.size(if (density == PlayerDensity.COMPACT) 220.dp else 280.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatClock(showing),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = timerSize),
                    color = colors.textPrimary,
                )
                Text(
                    text = when (snapshot.status) {
                        TimerStatus.PAUSED -> "PAUSED"
                        TimerStatus.AWAITING_MANUAL -> "Tap when done"
                        else -> "of ${formatClock(snapshot.stepDurationMillis)}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (snapshot.status == TimerStatus.PAUSED) colors.prepare else colors.textSecondary,
                )
                if (lastCue != null && !cuesMuted) {
                    Text(
                        lastCue!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(dimens.l))

        // --- Next interval ---
        snapshot.nextStep?.let { next ->
            Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(
                "${next.name} · ${if (next.isIndefinite) "manual" else formatDuration(next.durationMillis)}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(dimens.l))

        TimelineBar(
            kinds = buildList { repeat(snapshot.totalSteps) { add(com.pulse.engine.PhaseKind.CUSTOM) } },
            currentIndex = snapshot.stepIndex,
            progressInStep = snapshot.intervalProgress,
        )

        Spacer(Modifier.weight(1f))

        // --- Controls (mirrored for left-handed use) ---
        val mainControls: @Composable () -> Unit = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { controller.previous() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous interval", modifier = Modifier.size(36.dp), tint = colors.textPrimary)
                }
                Button(
                    onClick = {
                        if (snapshot.status == TimerStatus.AWAITING_MANUAL) controller.completeManual() else controller.togglePause()
                    },
                    modifier = Modifier.size(96.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ) {
                    Icon(
                        when (snapshot.status) {
                            TimerStatus.PAUSED -> Icons.Filled.PlayArrow
                            TimerStatus.AWAITING_MANUAL -> Icons.Filled.Add
                            else -> Icons.Filled.Pause
                        },
                        contentDescription = when (snapshot.status) {
                            TimerStatus.PAUSED -> "Resume"
                            TimerStatus.AWAITING_MANUAL -> "Complete this interval"
                            else -> "Pause"
                        },
                        modifier = Modifier.size(42.dp),
                    )
                }
                IconButton(onClick = { controller.next() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(36.dp), tint = colors.textPrimary)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leftHanded) {
                SecondaryControls(container, onAddTime = { controller.addTime(15_000) }, onLap = { controller.lap() })
                mainControls()
            } else {
                mainControls()
                SecondaryControls(container, onAddTime = { controller.addTime(15_000) }, onLap = { controller.lap() })
            }
        }

        Spacer(Modifier.height(dimens.m))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
            TextButton(onClick = { controller.addTime(15_000) }) { Text(stringResource(R.string.player_add_time)) }
            if (active?.plan?.type == com.pulse.engine.WorkoutType.STOPWATCH || active?.plan?.type == com.pulse.engine.WorkoutType.AMRAP) {
                TextButton(onClick = { controller.lap() }) { Text("Lap / round (${snapshot.laps})") }
            }
            TextButton(onClick = { instructor = !instructor }) { Text(if (instructor) "Standard view" else "Instructor view") }
        }
    }

    if (instructor) {
        InstructorOverlay(
            planName = active?.plan?.name ?: "",
            currentStep = step?.name ?: "",
            remaining = snapshot.stepRemainingMillis,
            nextStep = snapshot.nextStep?.name ?: "",
            nextDuration = snapshot.nextStep?.durationMillis,
            round = step?.roundInGroup,
            rounds = step?.roundsInGroup,
            accent = accent,
            onClose = { instructor = false },
        )
    }

    if (confirmEnd) {
        ConfirmDialog(
            title = stringResource(R.string.player_exit_title),
            body = "You can finish early and still save what you did — the summary will show the intervals you completed.",
            confirmLabel = stringResource(R.string.player_end_session),
            dismissLabel = stringResource(R.string.player_keep_going),
            destructive = true,
            onConfirm = {
                confirmEnd = false
                controller.endSession(stoppedEarly = true)
            },
            onDismiss = { confirmEnd = false },
        )
    }
}

@Composable
private fun SecondaryControls(container: AppContainer, onAddTime: () -> Unit, onLap: () -> Unit) {
    val colors = LocalPulseColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = onAddTime, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Add 15 seconds", tint = colors.textSecondary)
        }
        IconButton(onClick = onLap, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Record a lap", tint = colors.textSecondary)
        }
    }
}

/** Oversized display for tablets, gym TVs and group training. */
@Composable
private fun InstructorOverlay(
    planName: String,
    currentStep: String,
    remaining: Long,
    nextStep: String,
    nextDuration: Long?,
    round: Int?,
    rounds: Int?,
    accent: androidx.compose.ui.graphics.Color,
    onClose: () -> Unit,
) {
    val colors = LocalPulseColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(32.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(planName.uppercase(), style = MaterialTheme.typography.titleLarge, color = colors.textSecondary)
            Spacer(Modifier.height(24.dp))
            Text(
                formatClock(remaining),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 2.4f),
                color = accent,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                buildString {
                    append(currentStep)
                    round?.let { append("   ·   round $it of ${rounds ?: 0}") }
                },
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                "Next: $nextStep" + (nextDuration?.let { " · ${formatDuration(it)}" } ?: ""),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textSecondary,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Filled.Close, contentDescription = "Exit instructor view", tint = colors.textPrimary)
        }
    }
}
