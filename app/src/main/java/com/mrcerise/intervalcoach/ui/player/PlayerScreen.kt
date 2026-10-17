package com.mrcerise.intervalcoach.ui.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.engine.RaceInfo
import com.pulse.engine.RaceMath
import com.pulse.engine.TimerStatus
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.PlayerDensity
import com.mrcerise.intervalcoach.data.category
import com.mrcerise.intervalcoach.health.HrZones
import com.mrcerise.intervalcoach.session.RunTrackingStatus
import com.mrcerise.intervalcoach.session.formatClock
import com.mrcerise.intervalcoach.ui.components.CategoryChip
import com.mrcerise.intervalcoach.ui.components.ConfirmDialog
import com.mrcerise.intervalcoach.ui.components.EmptyState
import com.mrcerise.intervalcoach.ui.components.GhostActionButton
import com.mrcerise.intervalcoach.ui.components.NeutralChip
import com.mrcerise.intervalcoach.ui.components.PhaseChip
import com.mrcerise.intervalcoach.ui.components.ProgressRing
import com.mrcerise.intervalcoach.ui.components.PulseIconButton
import com.mrcerise.intervalcoach.ui.components.StatusBadge
import com.mrcerise.intervalcoach.ui.components.PulseTone
import com.mrcerise.intervalcoach.ui.components.TimelineBar
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseElevation
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseType
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
            liveHr = runCatching { container.health.latestLiveHeartRate() }.getOrNull()
            delay(3_000)
        }
    }

    if (active == null) {
        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No active workout",
                body = "Choose a workout from your library.",
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
    // The countdown is the page, so it is sized like one: 74 sp on standard density, with the
    // density preference scaling it and long clocks (h:mm:ss) stepping down to stay inside the ring.
    val timerStyle = when (density) {
        PlayerDensity.COMPACT -> PulseType.NumericDisplay.copy(fontSize = PulseType.NumericDisplay.fontSize * 0.9f)
        PlayerDensity.STANDARD -> PulseType.NumericDisplay.copy(fontSize = PulseType.NumericDisplay.fontSize * 1.15f)
        PlayerDensity.LARGE -> PulseType.NumericDisplay.copy(fontSize = PulseType.NumericDisplay.fontSize * 1.5f)
    }
    val leftHanded = prefs?.leftHandedPlayer == true

    // Wrapped in a Box so the instructor overlay and the end-session dialog always stack on top
    // of the player instead of relying on the order siblings happen to be emitted in.
    Box(Modifier.fillMaxSize().background(colors.background)) {
        // One column, one padding: the old structure wrapped a full-size padded column inside the
        // safe-drawing column, which made the horizontal insets two different numbers on one screen.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = dimens.pagePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- Interval progress, readable from across the room -----------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.s)
                    .height(dimens.barHeightCompact)
                    .background(colors.track, LocalPulseShapes.current.chip),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(
                            if (snapshot.status == TimerStatus.AWAITING_MANUAL) 1f
                            else snapshot.intervalProgress.coerceIn(0f, 1f),
                        )
                        .height(dimens.barHeightCompact)
                        .background(accent, LocalPulseShapes.current.chip)
                        .semantics {
                            contentDescription = "Interval progress ${(snapshot.intervalProgress * 100).toInt()} percent"
                        },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = dimens.xs, bottom = dimens.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // --- Top bar: the workout's name centred between the two session controls -----
                // The name is the one piece of identity on the screen, so it sits centred at the
                // top; exiting and muting flank it symmetrically.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PulseIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.player_end_session),
                        tooltip = stringResource(R.string.player_end_session),
                        onClick = { confirmEnd = true },
                        tint = colors.textSecondary,
                    )
                    Text(
                        text = active?.plan?.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = dimens.s),
                    )
                    PulseIconButton(
                        icon = if (cuesMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (cuesMuted) "Unmute cues" else "Mute cues",
                        tooltip = if (cuesMuted) "Unmute cues" else "Mute cues",
                        onClick = { controller.toggleCuesMuted() },
                        tint = colors.textSecondary,
                    )
                }

                // --- The hero: the countdown is the page ------------------------------------
                // One dominant ring with the interval's own colour as a soft wash rising behind
                // it. Everything that used to crowd above the timer — phase, round, side — now
                // reads inside the ring, under the digits, in one micro line.
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
                    val ringSize = minOf(available, preferred)
                    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                        // The phase wash: the interval's colour as light, not as a filled panel.
                        Box(
                            Modifier
                                .size(ringSize * 1.28f)
                                .background(
                                    Brush.radialGradient(
                                        listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                                    ),
                                ),
                        )
                        val showing = if (snapshot.status == TimerStatus.AWAITING_MANUAL) snapshot.stepElapsedMillis else snapshot.stepRemainingMillis
                        val showingText = formatClock(showing)
                        val fittedStyle = if (showingText.length > 5) {
                            timerStyle.copy(fontSize = timerStyle.fontSize * 0.7f)
                        } else {
                            timerStyle
                        }
                        ProgressRing(
                            progress = if (snapshot.status == TimerStatus.AWAITING_MANUAL) 0f else snapshot.intervalProgress,
                            color = accent,
                            trackColor = colors.track,
                            strokeWidth = 12.dp,
                            showRemainder = true,
                            modifier = Modifier.size(ringSize),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = showingText,
                                    style = fittedStyle,
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
                                step?.let { current ->
                                    Spacer(Modifier.height(dimens.xs))
                                    Text(
                                        text = buildString {
                                            append(current.name)
                                            current.roundInGroup?.let { round ->
                                                val total = current.roundsInGroup
                                                append(if (total != null && total > 0) " · Round $round of $total" else " · Round $round")
                                            }
                                            current.sideLabel?.let { append(" · $it") }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // --- The spoken cue, micro --------------------------------------------------
                if (lastCue != null && !cuesMuted) {
                    Text(
                        text = lastCue!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = dimens.xl, vertical = dimens.xs),
                    )
                }

                // --- One micro band of session facts -----------------------------------------
                // Elapsed, remaining, heart rate and the music state, one centred line — the
                // numbers you glance at, never the numbers you read.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.s, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${formatClock(snapshot.sessionElapsedMillis)} elapsed",
                        style = PulseType.NumericSmall,
                        color = colors.textMuted,
                    )
                    Text(text = "·", style = MaterialTheme.typography.bodySmall, color = colors.textDisabled)
                    Text(
                        text = snapshot.sessionRemainingMillis?.let { "${formatClock(it)} left" }
                            ?: stringResource(R.string.player_open_ended),
                        style = PulseType.NumericSmall,
                        color = colors.textMuted,
                    )
                    liveHr?.let { bpm -> HeartRateBadge(bpm = bpm) }
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

                // --- Race info (the running system): live speed and distance ----------------
                // Only for workouts whose category runs the system; placeholders stand in until
                // the tracker has something honest to say — never fake numbers.
                if (active?.plan?.category?.capabilities?.supportsRaceInfo == true) {
                    Spacer(Modifier.height(dimens.xs))
                    val race by container.runTracker.raceInfo.collectAsStateWithLifecycle()
                    val trackingStatus by container.runTracker.trackingStatus.collectAsStateWithLifecycle()
                    RaceInfoBand(
                        race = race,
                        trackingStatus = trackingStatus,
                        onLocationAction = {
                            when (trackingStatus) {
                                RunTrackingStatus.LOCATION_DISABLED ->
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                RunTrackingStatus.PERMISSION_REQUIRED, RunTrackingStatus.PRECISE_LOCATION_REQUIRED ->
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                else -> container.runTracker.refreshLocationAccess()
                            }
                        },
                    )
                }

                // --- Next interval, timeline and controls: anchored to the bottom -----------
                snapshot.nextStep?.let { next ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimens.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.s, Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = stringResource(R.string.player_up_next).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                        Text(
                            text = next.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (next.isIndefinite) "manual" else formatDuration(next.durationMillis),
                            style = PulseType.NumericSmall,
                            color = colors.phaseColor(next.kind),
                        )
                    }
                }

                Spacer(Modifier.height(dimens.m))

                TimelineBar(
                    kinds = timelineKinds,
                    currentIndex = snapshot.stepIndex,
                    progressInStep = snapshot.intervalProgress,
                    height = dimens.barHeightRegular,
                )

                Spacer(Modifier.height(dimens.l))

                // --- Controls: one centred transport cluster --------------------------------
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimens.m),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimens.l, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerRoundButton(
                            icon = Icons.Filled.SkipPrevious,
                            description = "Previous interval",
                            onClick = { controller.previous() },
                        )
                        TransportButton(
                            paused = snapshot.status == TimerStatus.PAUSED,
                            manual = snapshot.status == TimerStatus.AWAITING_MANUAL,
                            accent = accent,
                            onClick = {
                                if (snapshot.status == TimerStatus.AWAITING_MANUAL) controller.completeManual() else controller.togglePause()
                            },
                        )
                        PlayerRoundButton(
                            icon = Icons.Filled.SkipNext,
                            description = stringResource(R.string.player_next),
                            onClick = { controller.next() },
                        )
                    }
                    SecondaryControls(
                        onAddTime = { controller.addTime(15_000) },
                        onLap = { controller.lap() },
                    )
                }

                Spacer(Modifier.height(dimens.s))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.s, Alignment.CenterHorizontally),
                ) {
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
            body = "Your progress will be saved.",
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
private fun TransportButton(
    paused: Boolean,
    manual: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val description = when {
        manual -> "Complete this interval"
        paused -> "Resume"
        else -> "Pause"
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(88.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        // The transport is the phase: one round control, filled with the colour of the interval it
        // is controlling, which is the only place Prism lets a big saturated shape carry meaning.
        color = accent,
        contentColor = colors.onAccent,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when {
                    manual -> Icons.Filled.Check
                    paused -> Icons.Filled.PlayArrow
                    else -> Icons.Filled.Pause
                },
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

/** A modern round secondary transport button: raised circle, border, centred glyph. */
@Composable
private fun PlayerRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = Modifier.size(56.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        border = BorderStroke(dimens.borderWidth, colors.borderStrong),
        shadowElevation = LocalPulseElevation.current.level1,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * The running system's race info: speed, distance, and the race's state — drawn as three labelled
 * micro-stats in the same rhythm as the elapsed/remaining row. When a target distance is set the
 * third column is what is left of the race; otherwise it is the live pace.
 */
@Composable
private fun RaceInfoBand(
    race: RaceInfo?,
    trackingStatus: RunTrackingStatus,
    onLocationAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RaceStat(
                label = stringResource(R.string.player_race_speed).uppercase(),
                value = race?.takeIf { it.speedMps > 0.0 }?.let { "${RaceMath.formatSpeedKph(it.speedMps)} km/h" } ?: "—",
            )
            RaceStat(
                label = stringResource(R.string.player_race_distance).uppercase(),
                value = race?.let { "${RaceMath.formatDistanceKm(it.distanceMeters)} km" } ?: "—",
                alignment = Alignment.CenterHorizontally,
            )
            if (race?.targetDistanceMeters != null) {
                RaceStat(
                    label = stringResource(R.string.player_race_left).uppercase(),
                    value = "${RaceMath.formatDistanceKm(race.remainingMeters ?: 0.0)} km",
                    alignment = Alignment.End,
                )
            } else {
                RaceStat(
                    label = stringResource(R.string.player_race_pace).uppercase(),
                    value = race?.let { "${RaceMath.formatPace(it.paceSecondsPerKm)} /km" } ?: "—",
                    alignment = Alignment.End,
                )
            }
        }
        Spacer(Modifier.height(dimens.microGap))
        val statusText = when (trackingStatus) {
            RunTrackingStatus.INACTIVE -> stringResource(R.string.player_race_starting)
            RunTrackingStatus.SEARCHING -> stringResource(R.string.player_race_waiting_gps)
            RunTrackingStatus.TRACKING -> stringResource(R.string.player_race_gps_live)
            RunTrackingStatus.SIGNAL_LOST -> stringResource(R.string.player_race_signal_lost)
            RunTrackingStatus.PERMISSION_REQUIRED -> stringResource(R.string.player_race_permission_required)
            RunTrackingStatus.PRECISE_LOCATION_REQUIRED -> stringResource(R.string.player_race_precise_required)
            RunTrackingStatus.LOCATION_DISABLED -> stringResource(R.string.player_race_location_disabled)
            RunTrackingStatus.UNAVAILABLE -> stringResource(R.string.player_race_location_unavailable)
        }
        val estimated = race != null && !race.hasFix && trackingStatus != RunTrackingStatus.SEARCHING
        Text(
            text = if (estimated) {
                statusText + "\n" + stringResource(R.string.player_race_estimated, RaceMath.formatPace(race?.racePaceSecondsPerKm?.toDouble()))
            } else statusText,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        val actionLabel = when (trackingStatus) {
            RunTrackingStatus.PERMISSION_REQUIRED, RunTrackingStatus.PRECISE_LOCATION_REQUIRED -> R.string.player_race_location_permissions
            RunTrackingStatus.LOCATION_DISABLED -> R.string.player_race_enable_location
            RunTrackingStatus.UNAVAILABLE, RunTrackingStatus.SIGNAL_LOST -> R.string.retry
            else -> null
        }
        if (actionLabel != null) {
            GhostActionButton(
                text = stringResource(actionLabel),
                onClick = onLocationAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RaceStat(
    label: String,
    value: String,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    val colors = LocalPulseColors.current
    Column(horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
        )
        Text(
            text = value,
            style = PulseType.NumericSmall,
            color = colors.textSecondary,
        )
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
    val dimens = LocalPulseDimens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        listOf(
            Triple(Icons.Filled.Add, "Add 15 seconds", onAddTime),
            Triple(Icons.Filled.Flag, "Record a lap", onLap),
        ).forEach { (icon, label, action) ->
            Surface(
                onClick = action,
                shape = LocalPulseShapes.current.pill,
                color = colors.surfaceRaised,
                contentColor = colors.textPrimary,
                border = BorderStroke(dimens.borderWidth, colors.borderStrong),
            ) {
                Row(Modifier.padding(horizontal = dimens.m, vertical = dimens.s), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = label, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(dimens.xs))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                }
            }
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
                style = PulseType.NumericDisplay.copy(fontSize = PulseType.NumericDisplay.fontSize * 2.4f),
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
