package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import com.pulse.engine.TimelineExpander
import com.pulse.engine.TimerStatus
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.StoredWorkout
import com.mrcerise.intervalcoach.wear.WatchActivity
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors
import com.mrcerise.intervalcoach.wear.ui.theme.wearCategory
import com.mrcerise.intervalcoach.wear.watchActivity
import com.pulse.sync.PlannedWorkout
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The watch's home: what is running, how the week is going, what is planned today, then the
 * workout library.
 *
 * A running session sits on top as the one bright button, so getting back to it is a single tap.
 * The week card is the phone's weekly goal and streak, recomputed on the watch so it stays right
 * after midnight and away from the phone. Library rows open the workout's details; starting
 * happens there, one tap further, where the whole workout can be seen first.
 *
 * The library is whatever this watch has: a phone's mirror once one has synced, or the bundled
 * catalogue when it never has. The footer says which of those is true, so "no phone" is never
 * mistaken for "broken".
 */
@Composable
fun WearHomeScreen(
    container: WearAppContainer,
    onOpenWorkout: (String) -> Unit,
    onResume: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    val colors = LocalWearColors.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val history by container.history.records.collectAsStateWithLifecycle()
    val remote by container.wearSync.remoteSession.collectAsStateWithLifecycle()
    val active by container.session.active.collectAsStateWithLifecycle()
    val snapshot by container.session.snapshot.collectAsStateWithLifecycle()
    val phoneLibrarySeen by container.workouts.phoneLibrarySeen.collectAsStateWithLifecycle()
    val phoneConnected by container.wearSync.phoneConnected.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val activity = remember(prefs, history) { watchActivity(prefs, history) }
    val knownIds = remember(workouts) { workouts.map { it.id }.toSet() }

    PulseListScreen { spec ->
        item(key = "header") { ScreenHeader(stringResource(R.string.app_name), spec) }

        active?.let { session ->
            item(key = "active") {
                Button(
                    onClick = { onResume(session.plan.id) },
                    modifier = Modifier.transformed(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.buttonColors(),
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    // The workout is the title; the action and the clock sit under it, so both
                    // survive the narrow top of a small round screen.
                    label = { Text(session.plan.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        // A manual step has no clock to show; "Resume" alone is the honest answer.
                        val remaining = if (snapshot.status == TimerStatus.AWAITING_MANUAL) {
                            ""
                        } else {
                            " · " + formatDuration(snapshot.stepRemainingMillis.coerceAtLeast(0L))
                        }
                        Text(stringResource(R.string.player_resume) + remaining, maxLines = 1)
                    },
                )
            }
        }
        remote?.let { live ->
            item(key = "remote") {
                Button(
                    onClick = onOpenRemote,
                    modifier = Modifier.transformed(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.work,
                        contentColor = colors.canvas,
                        secondaryContentColor = colors.canvas,
                        iconColor = colors.canvas,
                    ),
                    icon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                    label = { Text(stringResource(R.string.remote_running), maxLines = 1) },
                    secondaryLabel = {
                        Text(live.planName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }

        item(key = "week") {
            WeekCard(
                activity = activity,
                onClick = onOpenHistory,
                modifier = Modifier.transformed(this, spec),
                transformation = SurfaceTransformation(spec),
            )
        }

        // Only plans this watch can actually open: a workout trimmed from the mirror is skipped.
        val planned = activity.today.filter { it.workoutId in knownIds }
        if (planned.isNotEmpty()) {
            item(key = "today-header") { ScreenHeader(stringResource(R.string.home_today), spec) }
            items(planned, key = { "plan-" + it.workoutId + "-" + it.atMillis }) { plan ->
                PlannedButton(
                    plan = plan,
                    onClick = { onOpenWorkout(plan.workoutId) },
                    modifier = Modifier.transformed(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item(key = "library-header") { ScreenHeader(stringResource(R.string.home_library), spec) }
        }

        if (workouts.isEmpty()) {
            item(key = "empty") {
                // Two different emptinesses, two different fixes: a solo watch loads the bundled
                // catalogue, a paired one asks the phone for the library it owns.
                val solo = !phoneLibrarySeen
                Button(
                    onClick = {
                        if (solo) {
                            scope.launch { container.workouts.seedBundledWorkouts() }
                        } else {
                            container.wearSync.requestSync()
                        }
                    },
                    modifier = Modifier.transformed(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = {
                        Icon(
                            if (solo) Icons.Filled.LibraryAdd else Icons.Filled.CloudDownload,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(stringResource(if (solo) R.string.library_empty_solo else R.string.library_empty))
                    },
                    secondaryLabel = {
                        Text(
                            stringResource(if (solo) R.string.library_restore_starters else R.string.library_empty_hint),
                        )
                    },
                )
            }
        }

        items(workouts, key = { it.id }) { workout ->
            WorkoutButton(
                workout = workout,
                modifier = Modifier.transformed(this, spec),
                transformation = SurfaceTransformation(spec),
                onClick = { onOpenWorkout(workout.id) },
            )
        }

        if (prefs.libraryTruncated && workouts.isNotEmpty()) {
            item(key = "truncated") {
                Caption(stringResource(R.string.library_truncated, workouts.size), spec)
            }
        }

        item(key = "footer") {
            Row(
                modifier = Modifier.transformed(this, spec).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                FilledTonalIconButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = stringResource(R.string.library_history))
                }
                FilledTonalIconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }
        }
        item(key = "status") {
            Caption(
                text = watchSyncStatus(
                    phoneLibrarySeen = phoneLibrarySeen,
                    syncEnabled = prefs.syncEnabled,
                    phoneConnected = phoneConnected,
                ),
                spec = spec,
            )
        }
    }
}

/**
 * The weekly goal as a ring, the minutes in numerals, and the streak — the phone's Today card,
 * sized for a wrist. Tapping it opens the history behind the numbers.
 */
@Composable
private fun WeekCard(
    activity: WatchActivity,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val colors = LocalWearColors.current
    // The indicator observes its lambda, so it must read State to follow changes.
    val progress by rememberUpdatedState(activity.goalProgress)
    Card(onClick = onClick, modifier = modifier, transformation = transformation) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(44.dp),
                    colors = ProgressIndicatorDefaults.colors(indicatorColor = colors.accent, trackColor = colors.accentTint),
                    strokeWidth = 5.dp,
                )
                Text(
                    text = "${(activity.goalProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = stringResource(R.string.home_week_minutes, activity.weekMinutes, activity.goalMinutes),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.home_week_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (activity.streakDays > 0) colors.prepare else colors.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (activity.streakDays > 0) {
                            pluralStringResource(R.plurals.home_streak_days, activity.streakDays, activity.streakDays)
                        } else {
                            stringResource(R.string.home_streak_none)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activity.streakDays > 0) colors.prepare else colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A workout planned on the phone for today: its time, or a tick once it has been done. */
@Composable
private fun PlannedButton(
    plan: PlannedWorkout,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val colors = LocalWearColors.current
    val configuration = LocalConfiguration.current
    val time = remember(plan.atMillis, configuration) {
        DateFormat.getTimeInstance(DateFormat.SHORT, configuration.locales[0]).format(Date(plan.atMillis))
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        transformation = transformation,
        colors = if (plan.done) {
            ButtonDefaults.filledTonalButtonColors(iconColor = colors.work, secondaryContentColor = colors.work)
        } else {
            ButtonDefaults.filledVariantButtonColors()
        },
        icon = { Icon(if (plan.done) Icons.Filled.CheckCircle else Icons.Filled.Event, contentDescription = null) },
        label = { Text(plan.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text(
                if (plan.done) stringResource(R.string.home_planned_done, time) else time,
                maxLines = 1,
            )
        },
    )
}

/** One library row: the category mark in its hue, the name, then duration and interval count. */
@Composable
private fun WorkoutButton(
    workout: StoredWorkout,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    val category = remember(workout.plan.type) { wearCategory(workout.plan.type) }
    val durationMillis = remember(workout.plan) {
        runCatching { TimelineExpander.knownDurationMillis(workout.plan) }.getOrDefault(0L)
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(iconColor = category.color),
        icon = { Icon(category.icon, contentDescription = null) },
        label = { Text(workout.plan.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            if (workout.favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.details_favorite),
                    tint = LocalWearColors.current.prepare,
                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                )
            }
            Text(
                stringResource(
                    R.string.library_row_summary,
                    formatDuration(durationMillis),
                    workout.plan.allIntervals().size,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
