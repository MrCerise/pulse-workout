package com.pulse.intervalcoach.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.pulse.engine.formatDuration
import com.pulse.engine.TimelineExpander
import com.pulse.intervalcoach.wear.R
import com.pulse.intervalcoach.wear.StoredWorkout
import com.pulse.intervalcoach.wear.WearAppContainer
import com.pulse.intervalcoach.wear.ui.theme.LocalWearColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The watch's home: the synced workout library.
 *
 * Favorites first, then by name — the same shape as the phone's library, distilled to what a
 * 200 dp round screen renders well: name, duration, interval count.
 */
@Composable
fun WearHomeScreen(
    container: WearAppContainer,
    onOpenWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenActive: (String) -> Unit,
) {
    val colors = LocalWearColors.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val remote by container.wearSync.remoteSession.collectAsStateWithLifecycle()
    val active by container.session.active.collectAsStateWithLifecycle()

    // First tap while a session runs asks for confirmation; the second starts (and replaces) it.
    var pendingReplaceId by remember { mutableStateOf<String?>(null) }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (active != null) {
                item(key = "active") {
                    Chip(
                        onClick = { onOpenActive(active!!.plan.id) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                        label = { Text(stringResource(R.string.library_resume)) },
                        secondaryLabel = {
                            Text(
                                active!!.plan.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            if (remote != null) {
                item(key = "remote") {
                    Chip(
                        onClick = onOpenRemote,
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = colors.work,
                            contentColor = colors.canvas,
                        ),
                        label = { Text(stringResource(R.string.remote_running)) },
                        secondaryLabel = {
                            Text(
                                remote!!.planName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            if (workouts.isEmpty()) {
                item(key = "empty") {
                    Chip(
                        onClick = { container.wearSync.requestSync() },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = colors.surface,
                            contentColor = colors.textPrimary,
                        ),
                        label = { Text(stringResource(R.string.library_empty)) },
                        secondaryLabel = { Text(stringResource(R.string.library_empty_hint)) },
                    )
                }
            }
            items(workouts, key = { it.id }) { workout ->
                WorkoutRow(
                    workout = workout,
                    replacing = pendingReplaceId == workout.id,
                    anyActive = active != null,
                    onClick = {
                        if (active != null && pendingReplaceId != workout.id) {
                            pendingReplaceId = workout.id
                        } else {
                            pendingReplaceId = null
                            onOpenWorkout(workout.id)
                        }
                    },
                )
            }
            if (prefs.libraryTruncated && workouts.isNotEmpty()) {
                item(key = "truncated") {
                    Text(
                        text = stringResource(R.string.library_truncated, workouts.size),
                        style = MaterialTheme.typography.caption1,
                        color = colors.textMuted,
                    )
                }
            }
            item(key = "settings") {
                Chip(
                    onClick = onOpenSettings,
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = colors.surface,
                        contentColor = colors.textPrimary,
                    ),
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text(stringResource(R.string.settings_title)) },
                    secondaryLabel = {
                        Text(formatSyncTime(prefs.lastSyncAt))
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkoutRow(
    workout: StoredWorkout,
    replacing: Boolean,
    anyActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalWearColors.current
    val durationMillis = remember(workout.plan) {
        runCatching { TimelineExpander.knownDurationMillis(workout.plan) }.getOrDefault(0L)
    }
    Chip(
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = if (replacing) colors.surfaceRaised else colors.surface,
            contentColor = colors.textPrimary,
        ),
        label = {
            Text(workout.plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            val summary = stringResource(
                R.string.library_row_summary,
                formatDuration(durationMillis),
                workout.plan.allIntervals().size,
            )
            Text(
                if (replacing) stringResource(R.string.library_replace_confirm) else summary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (replacing) colors.accent else colors.textSecondary,
            )
        },
    )
}

@Composable
private fun formatSyncTime(lastSyncAt: Long): String {
    if (lastSyncAt <= 0L) return stringResource(R.string.library_never_synced)
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM HH:mm") }
    val instant = remember(lastSyncAt) { Instant.ofEpochMilli(lastSyncAt) }
    return remember(lastSyncAt) { formatter.format(instant.atZone(ZoneId.systemDefault())) }
}
