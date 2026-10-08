package com.pulse.intervalcoach.ui.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.TimerStatus
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.PlayerDensity
import com.pulse.intervalcoach.health.HrZones
import com.pulse.intervalcoach.session.formatClock
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.GhostActionButton
import com.pulse.intervalcoach.ui.components.NeutralChip
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.ProgressRing
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.StatusBadge
import com.pulse.intervalcoach.ui.components.PulseTone
import com.pulse.intervalcoach.ui.components.TimelineBar
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseType
import kotlinx.coroutines.delay

/**
 * The workout player.
 *
 * This screen is looked at from a metre away, mid-effort, so the hierarchy is: the remaining time,
 * the phase, the next interval, everything else. The phase colour is carried by three functional
 * elements — the progress strip along the top edge, the ring, and the phase chip — rather than by
 * washing the whole background, which kept the text legible in theory and unreadable in practice.
 *
 * Every control is at least 48 dp, colour is never the only signal for a phase, and the layout
 * follows the user's density, left-handed and reduced-motion preferences.
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
    val musicPlaying by container.music.playing.collectAsStateWithLifecycle()
    val musicDucked by container.music.duckState.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val context = LocalContext.current
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

    // Live heart rate: poll the health hub while a session is running. No health platform?
    // The chip simply never appears — no fake numbers.
    var liveHr by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(active) {
        if (active == null) {
            liveHr = null
            return@LaunchedEffect
        }
        while (true) {
            liveHr = runCatching { container.health.latestHeartRate() }.getOrNull()
            delay(3_000)
        }
    }

    if (active == null) {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No active workout",
                body = "Start a workout from Today or your library and the player will appear here.",
                actionLabel = "Close",
                onAction = onClose,
            )
        }
        return
    }

    val step = snapshot.step
    val accent = step?.let { colors.phaseColor(it.kind) } ?: colors.work
    // The bar used to be built from `repeat(totalSteps) { CUSTOM }`, which painted every segment in
    // the neutral colour and threw the phase coding away. Expand the plan instead — memoised, and
    // only trusted when it agrees with the engine's own step count.
    val expandedKinds = remember(active?.plan) {
        active?.plan
            ?.let { plan -> runCatching { com.pulse.engine.TimelineExpander.expand(plan).steps.map { it.kind } }.getOrNull() }
            .orEmpty()
    }
    val timelineKinds = if (expandedKinds.size == snapshot.totalSteps) {
        expandedKinds
    } else {
        List(snapshot.totalSteps) { com.pulse.engine.PhaseKind.CUSTOM }
    }
    val density = prefs?.playerDensity ?: PlayerDensity.STANDARD
    val timerStyle = when (density) {
        PlayerDensity.COMPACT -> PulseType.NumericDisplay.copy(fontSize = PulseType.NumericDisplay.fontSize * 0.75f)
        PlayerDensity.STANDARD -> PulseType.NumericDisplay
        PlayerDensity.LARGE -> PulseType.NumericDisplay.copy(fontSize = PulseType.NumericDisplay.fontSize * 1.3f)
    }
    val leftHanded = prefs?.leftHandedPlayer == true

    // Wrapped in a Box so the instructor overlay and the end-session dialog always stack on top
    // of the player instead of relying on the order siblings happen to be emitted in.
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- Interval progress, readable from across the room -----------------------------
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(LocalPulseDimens.current.xs)
                    .background(colors.surfaceHover),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(
                            if (snapshot.status == TimerStatus.AWAITING_MANUAL) 1f
                            else snapshot.intervalProgress.coerceIn(0f, 1f),
                        )
                        .height(LocalPulseDimens.current.xs)
                        .background(accent)
                        .semantics {
                            contentDescription = "Interval progress ${(snapshot.intervalProgress * 100).toInt()} percent"
                        },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.l, vertical = dimens.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // --- Top bar: session name, heart rate, mute, close ---
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = active?.plan?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (cuesMuted) "Cues muted" else "Voice & sound on",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                    liveHr?.let { bpm ->
                        HeartRateBadge(bpm = bpm, modifier = Modifier.padding(end = dimens.s))
                    }
                    PulseIconButton(
                        icon = if (cuesMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (cuesMuted) "Unmute cues" else "Mute cues",
                        tooltip = if (cuesMuted) "Unmute cues" else "Mute cues",
                        onClick = { controller.toggleCuesMuted() },
                        tint = colors.textSecondary,
                    )
                    PulseIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.player_end_session),
                        tooltip = stringResource(R.string.player_end_session),
                        onClick = { confirmEnd = true },
                        tint = colors.textSecondary,
                    )
                }

                Spacer(Modifier.height(dimens.s))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Elapsed ${formatClock(snapshot.sessionElapsedMillis)}",
                        style = PulseType.NumericSmall,
                        color = colors.textMuted,
                    )
                    Text(
                        text = snapshot.sessionRemainingMillis?.let { "Remaining ${formatClock(it)}" } ?: "Open-ended",
                        style = PulseType.NumericSmall,
                        color = colors.textMuted,
                    )
                }

                Spacer(Modifier.height(dimens.l))

                // --- Phase + round + side label ---
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    step?.let { PhaseChip(kind = it.kind, name = it.name) }
                    step?.roundInGroup?.let { round ->
                        val total = step.roundsInGroup
                        Text(
                            text = if (total != null && total > 0) "Round $round of $total" else "Round $round",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    step?.sideLabel?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    // Only shown when the listener picked a background track: the chip says whether
                    // it is playing, and drops to "lowered" for as long as the coach is talking.
                    if (musicPlaying) {
                        NeutralChip(
                            text = if (musicDucked.isDucked) {
                                stringResource(R.string.player_music_ducked)
                            } else {
                                stringResource(R.string.player_music_on)
                            },
                            icon = if (musicDucked.isDucked) Icons.Filled.VolumeDown else Icons.Filled.VolumeUp,
                        )
                    }
                }

                // --- The timer itself ---
                val showing = if (snapshot.status == TimerStatus.AWAITING_MANUAL) snapshot.stepElapsedMillis else snapshot.stepRemainingMillis
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val available = minOf(maxWidth, maxHeight)
                    val preferred = when (density) {
                        PlayerDensity.COMPACT -> LocalPulseDimens.current.playerPanelCompact
                        PlayerDensity.STANDARD -> LocalPulseDimens.current.playerPanelStandard
                        PlayerDensity.LARGE -> LocalPulseDimens.current.playerPanelLarge
                    }
                    ProgressRing(
                        progress = if (snapshot.status == TimerStatus.AWAITING_MANUAL) 0f else snapshot.intervalProgress,
                        color = accent,
                        trackColor = colors.track,
                        strokeWidth = 8.dp,
                        modifier = Modifier.size(minOf(available, preferred)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatClock(showing),
                                style = timerStyle,
                                color = colors.textPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = when (snapshot.status) {
                                    TimerStatus.PAUSED -> "PAUSED"
                                    TimerStatus.AWAITING_MANUAL -> "Tap when done"
                                    else -> "of ${formatClock(snapshot.stepDurationMillis)}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (snapshot.status == TimerStatus.PAUSED) colors.prepare else colors.textMuted,
                            )
                        }
                    }
                }

                // The spoken cue lives outside the ring: two lines of body text inside a 260 dp circle
                // pushed the countdown off-centre and clipped.
                if (lastCue != null && !cuesMuted) {
                    Text(
                        text = lastCue!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = dimens.xl, vertical = dimens.s),
                    )
                }

                // --- Next interval ---
                snapshot.nextStep?.let { next ->
                    Surface(
                        shape = LocalPulseShapes.current.control,
                        color = colors.surface,
                        border = BorderStroke(dimens.borderWidth, colors.border),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.player_up_next).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                modifier = Modifier.padding(end = dimens.m),
                            )
                            Text(
                                text = next.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(dimens.m))
                            Text(
                                text = if (next.isIndefinite) "manual" else formatDuration(next.durationMillis),
                                style = PulseType.NumericSmall,
                                color = colors.phaseColor(next.kind),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(dimens.m))

                TimelineBar(
                    kinds = timelineKinds,
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
                        PulseIconButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous interval",
                            tooltip = "Previous interval",
                            onClick = { controller.previous() },
                            tint = colors.textSecondary,
                            modifier = Modifier.size(LocalPulseDimens.current.minTouchTarget),
                        )
                        TransportButton(
                            paused = snapshot.status == TimerStatus.PAUSED,
                            manual = snapshot.status == TimerStatus.AWAITING_MANUAL,
                            onClick = {
                                if (snapshot.status == TimerStatus.AWAITING_MANUAL) controller.completeManual() else controller.togglePause()
                            },
                        )
                        PulseIconButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.player_next),
                            tooltip = stringResource(R.string.player_next),
                            onClick = { controller.next() },
                            tint = colors.textSecondary,
                            modifier = Modifier.size(LocalPulseDimens.current.minTouchTarget),
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val secondary: @Composable () -> Unit = {
                        SecondaryControls(
                            onAddTime = { controller.addTime(15_000) },
                            onLap = { controller.lap() },
                        )
                    }
                    if (leftHanded) {
                        secondary()
                        mainControls()
                    } else {
                        mainControls()
                        secondary()
                    }
                }

                Spacer(Modifier.height(dimens.s))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    if (snapshot.laps > 0) {
                        Text(
                            text = "Laps ${snapshot.laps}",
                            style = PulseType.NumericSmall,
                            color = colors.textMuted,
                        )
                    }
                    GhostActionButton(
                        text = if (instructor) "Standard view" else "Instructor view",
                        onClick = { instructor = !instructor },
                    )
                }
            }
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

/**
 * The one round control in the interface. It is filled with the solid action surface so it is
 * unmistakably the transport, and it is the only element on the screen with a circular shape.
 */
@Composable
private fun TransportButton(paused: Boolean, manual: Boolean, onClick: () -> Unit) {
    val colors = LocalPulseColors.current
    val description = when {
        manual -> "Complete this interval"
        paused -> "Resume"
        else -> "Pause"
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = colors.actionFill,
        contentColor = colors.actionText,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when {
                    manual -> Icons.Filled.Check
                    paused -> Icons.Filled.PlayArrow
                    else -> Icons.Filled.Pause
                },
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Live heart rate, shown only when a connected health platform actually reported one. */
@Composable
private fun HeartRateBadge(bpm: Int, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier,
        shape = LocalPulseShapes.current.chip,
        color = colors.warningTint,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.s, vertical = LocalPulseDimens.current.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = colors.prepare,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$bpm",
                style = PulseType.NumericSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = HrZones.zone(bpm),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun SecondaryControls(onAddTime: () -> Unit, onLap: () -> Unit) {
    val colors = LocalPulseColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.xs), verticalAlignment = Alignment.CenterVertically) {
        PulseIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add 15 seconds",
            tooltip = "Add 15 seconds",
            onClick = onAddTime,
            tint = colors.textSecondary,
            modifier = Modifier.size(LocalPulseDimens.current.minTouchTarget),
        )
        // A play triangle for "record a lap" read as a second start button — hence the flag.
        PulseIconButton(
            icon = Icons.Filled.Flag,
            contentDescription = "Record a lap",
            tooltip = "Record a lap",
            onClick = onLap,
            tint = colors.textSecondary,
            modifier = Modifier.size(LocalPulseDimens.current.minTouchTarget),
        )
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
    accent: Color,
    onClose: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(dimens.xxl),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusBadge(text = planName, tone = PulseTone.NEUTRAL)
            Spacer(Modifier.height(dimens.xl))
            Text(
                text = formatClock(remaining),
                style = PulseType.NumericDisplay.copy(fontSize = PulseType.NumericDisplay.fontSize * 2.2f),
                color = accent,
            )
            Spacer(Modifier.height(dimens.m))
            Text(
                text = buildString {
                    append(currentStep)
                    round?.let { append("   ·   round $it of ${rounds ?: 0}") }
                },
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(dimens.xl))
            Text(
                text = "Next: $nextStep" + (nextDuration?.let { " · ${formatDuration(it)}" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textMuted,
            )
        }
        PulseIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Exit instructor view",
            tooltip = "Exit instructor view",
            onClick = onClose,
            tint = colors.textSecondary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(LocalPulseShapes.current.control),
        )
    }
}
