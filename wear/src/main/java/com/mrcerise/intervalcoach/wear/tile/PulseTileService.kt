package com.mrcerise.intervalcoach.wear.tile

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.StoredWorkout
import com.mrcerise.intervalcoach.wear.WatchActivity
import com.mrcerise.intervalcoach.wear.WearDeepLink
import com.mrcerise.intervalcoach.wear.WearMainActivity
import com.mrcerise.intervalcoach.wear.pulseWearContainer
import com.mrcerise.intervalcoach.wear.ui.theme.PulseWearPalette
import com.mrcerise.intervalcoach.wear.watchActivity
import com.pulse.engine.TimelineExpander
import com.pulse.engine.formatDuration
import kotlinx.coroutines.Dispatchers
import java.text.DateFormat
import java.util.Date

/** What the tile's main button opens: a workout (resumed, or its details), or the app itself. */
internal data class TileTarget(val workoutId: String?, val resume: Boolean)

/**
 * Picks the tile's one main action, most urgent first: the workout running now, then the next
 * workout planned for today and not yet done, then the first favourite, then the first workout.
 * Pure, so the order is testable.
 */
internal fun tileTarget(
    activeWorkoutId: String?,
    activity: WatchActivity,
    workouts: List<StoredWorkout>,
): TileTarget {
    if (activeWorkoutId != null) return TileTarget(activeWorkoutId, resume = true)
    val known = workouts.map { it.id }.toSet()
    activity.today.firstOrNull { !it.done && it.workoutId in known }?.let { return TileTarget(it.workoutId, resume = false) }
    // The store lists favourites first, so the first workout is the first favourite when there is one.
    return TileTarget(workouts.firstOrNull()?.id, resume = false)
}

/**
 * The watch tile: one swipe from the watch face to the workout that matters now, with the week's
 * progress underneath. It opens the app on that workout — running it if it already runs,
 * otherwise on its details, so a stray tap never starts a session.
 */
class PulseTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val container = applicationContext.pulseWearContainer
            // Read the stores directly: the tile may be asked to render before the app's flows load.
            val prefs = container.preferences.current()
            val workouts = container.workouts.current()
            val activity = watchActivity(prefs, container.history.current())
            val target = tileTarget(container.session.active.value?.plan?.id, activity, workouts)
            val layout = tileLayout(
                context = this@PulseTileService,
                device = requestParams.deviceConfiguration,
                target = target,
                workout = workouts.firstOrNull { it.id == target.workoutId },
                activity = activity,
            )
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                // "Today" and the rolling week change at midnight even when nothing else does.
                .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val FRESHNESS_MILLIS = 30L * 60L * 1000L
    }
}

private fun tileLayout(
    context: Context,
    device: DeviceParameters,
    target: TileTarget,
    workout: StoredWorkout?,
    activity: WatchActivity,
): LayoutElement = materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = pulseTileColors()) {
    val subtitle = when {
        workout == null -> null
        target.resume -> context.getString(R.string.player_resume)
        activity.today.any { it.workoutId == workout.id && !it.done } -> {
            val at = activity.today.first { it.workoutId == workout.id && !it.done }.atMillis
            context.getString(R.string.tile_planned, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at)))
        }
        else -> context.getString(
            R.string.library_row_summary,
            formatDuration(runCatching { TimelineExpander.knownDurationMillis(workout.plan) }.getOrDefault(0L)),
            workout.plan.allIntervals().size,
        )
    }
    val week = context.getString(R.string.home_week_minutes, activity.weekMinutes, activity.goalMinutes) +
        if (activity.streakDays > 0) {
            " · " + context.resources.getQuantityString(R.plurals.home_streak_days, activity.streakDays, activity.streakDays)
        } else {
            ""
        }
    primaryLayout(
        titleSlot = { text(context.getString(R.string.app_name).layoutString) },
        mainSlot = {
            // The week sits under the main action: the layout drops its bottom-slot label when the
            // bottom slot is an edge button, and the week is worth more than the label space.
            LayoutElementBuilders.Column.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    if (workout == null) {
                        text(context.getString(R.string.tile_empty).layoutString, maxLines = 3, typography = Typography.BODY_MEDIUM)
                    } else {
                        button(
                            onClick = launch(context, target, id = "main"),
                            labelContent = { text(workout.plan.name.layoutString, maxLines = 2) },
                            secondaryLabelContent = subtitle?.let { line -> { text(line.layoutString) } },
                            width = expand(),
                            height = expand(),
                        )
                    },
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
                .addContent(
                    text(week.layoutString, typography = Typography.LABEL_MEDIUM, color = colorScheme.onSurfaceVariant, maxLines = 2),
                )
                .build()
        },
        bottomSlot = {
            textEdgeButton(
                onClick = launch(context, TileTarget(workoutId = null, resume = false), id = "open"),
                labelContent = { text(context.getString(R.string.tile_open).layoutString) },
            )
        },
    )
}

/** Opens the app — on [target]'s workout when there is one. */
private fun launch(context: Context, target: TileTarget, id: String): Clickable {
    val activity = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(context.packageName)
        .setClassName(WearMainActivity::class.java.name)
        .apply {
            target.workoutId?.let { workoutId ->
                addKeyToExtraMapping(WearDeepLink.EXTRA_WORKOUT_ID, ActionBuilders.AndroidStringExtra.Builder().setValue(workoutId).build())
                addKeyToExtraMapping(WearDeepLink.EXTRA_RESUME, ActionBuilders.AndroidBooleanExtra.Builder().setValue(target.resume).build())
            }
        }
        .build()
    return clickable(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build(), id = id)
}

/** The app's palette on the tile, so it reads as the same product as the app it opens. */
private fun pulseTileColors(): ColorScheme {
    val p = PulseWearPalette
    return ColorScheme(
        primary = p.Accent.toArgb().argb,
        primaryDim = p.Accent.toArgb().argb,
        primaryContainer = p.AccentTint.toArgb().argb,
        onPrimary = p.OnAccent.toArgb().argb,
        onPrimaryContainer = p.AccentAlt.toArgb().argb,
        secondary = p.Rest.toArgb().argb,
        secondaryDim = p.Rest.toArgb().argb,
        secondaryContainer = p.RestTint.toArgb().argb,
        onSecondary = p.Canvas.toArgb().argb,
        onSecondaryContainer = p.Rest.toArgb().argb,
        tertiary = p.Work.toArgb().argb,
        tertiaryDim = p.Work.toArgb().argb,
        tertiaryContainer = p.WorkTint.toArgb().argb,
        onTertiary = p.Canvas.toArgb().argb,
        onTertiaryContainer = p.Work.toArgb().argb,
        surfaceContainerLow = p.Surface.toArgb().argb,
        surfaceContainer = p.SurfaceRaised.toArgb().argb,
        surfaceContainerHigh = p.SurfaceOverlay.toArgb().argb,
        onSurface = p.TextPrimary.toArgb().argb,
        onSurfaceVariant = p.TextSecondary.toArgb().argb,
        outline = p.Outline.toArgb().argb,
        outlineVariant = p.Border.toArgb().argb,
        background = p.BlackCanvas.toArgb().argb,
        onBackground = p.TextPrimary.toArgb().argb,
        error = p.Destructive.toArgb().argb,
        errorDim = p.Destructive.toArgb().argb,
        errorContainer = p.DestructiveTint.toArgb().argb,
        onError = p.Canvas.toArgb().argb,
        onErrorContainer = p.Destructive.toArgb().argb,
    )
}
