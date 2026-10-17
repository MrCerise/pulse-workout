package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.StoredWorkout
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors
import com.pulse.sync.WatchCommand
import com.pulse.sync.WatchCommandKind
import kotlinx.coroutines.launch

/**
 * The watch's home: the workout library.
 *
 * Favorites first, then by name — the same shape as the phone's library, distilled to what a
 * 200 dp round screen renders well: name, duration, interval count.
 *
 * The library is whatever this watch has: a phone's mirror once one has synced, or the bundled
 * catalogue when it never has. The footer says which of those is true, so "no phone" is never
 * mistaken for "broken".
 */
@Composable
fun WearHomeScreen(
    container: WearAppContainer,
    onOpenWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenActive: (String) -> Unit,
) {
    val colors = LocalWearColors.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val remote by container.wearSync.remoteSession.collectAsStateWithLifecycle()
    val active by container.session.active.collectAsStateWithLifecycle()
    val phoneLibrarySeen by container.workouts.phoneLibrarySeen.collectAsStateWithLifecycle()
    val phoneConnected by container.wearSync.phoneConnected.collectAsStateWithLifecycle()
    val history by container.history.records.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // First tap while a session runs asks for confirmation; the second starts (and replaces) it.
    // "A session" means either one: the watch's own, or one running on the phone, which this
    // watch takes over from — two devices must never coach the same workout at once.
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
                    // Two different emptinesses, two different fixes: a solo watch loads the
                    // bundled catalogue, a paired one asks the phone for the library it owns.
                    val solo = !phoneLibrarySeen
                    Chip(
                        onClick = {
                            if (solo) {
                                scope.launch { container.workouts.seedBundledWorkouts() }
                            } else {
                                container.wearSync.requestSync()
                            }
                        },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = colors.surface,
                            contentColor = colors.textPrimary,
                        ),
                        label = {
                            Text(
                                stringResource(
                                    if (solo) R.string.library_empty_solo else R.string.library_empty,
                                ),
                            )
                        },
                        secondaryLabel = {
                            Text(
                                stringResource(
                                    if (solo) R.string.library_restore_starters else R.string.library_empty_hint,
                                ),
                            )
                        },
                    )
                }
            }
            items(workouts, key = { it.id }) { workout ->
                WorkoutRow(
                    workout = workout,
                    replacing = pendingReplaceId == workout.id,
                    remoteActive = remote != null,
                    onClick = {
                        if ((active != null || remote != null) && pendingReplaceId != workout.id) {
                            pendingReplaceId = workout.id
                        } else {
                            if (remote != null) {
                                // Taking over means the phone stops: its engine, its spoken cues and
                                // its history entry all end where the user can see them.
                                container.wearSync.sendCommand(WatchCommand(WatchCommandKind.STOP))
                            }
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
            if (history.isNotEmpty()) {
                item(key = "history") {
                    Chip(
                        onClick = onOpenHistory,
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = colors.surface,
                            contentColor = colors.textPrimary,
                        ),
                        icon = { Icon(Icons.Filled.History, null) },
                        label = { Text(stringResource(R.string.library_history)) },
                        secondaryLabel = {
                            Text(stringResource(R.string.library_history_count, history.size))
                        },
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
                        Text(
                            watchSyncStatus(
                                phoneLibrarySeen = phoneLibrarySeen,
                                syncEnabled = prefs.syncEnabled,
                                phoneConnected = phoneConnected,
                            ),
                        )
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
    remoteActive: Boolean,
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
            val confirm = if (remoteActive) R.string.library_takeover_confirm else R.string.library_replace_confirm
            Text(
                if (replacing) stringResource(confirm) else summary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (replacing) colors.accent else colors.textSecondary,
            )
        },
    )
}
