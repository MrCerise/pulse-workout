package com.pulse.intervalcoach.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.pulse.intervalcoach.MainActivity
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.WorkoutSummary
import com.pulse.intervalcoach.pulseContainer
import com.pulse.intervalcoach.session.formatClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widgets built with RemoteViews.
 *
 * Both widgets are strictly read-only views over real saved data: the favourites widget shows the
 * user's starred workouts and starts one on tap; the quick-start widget opens the builder. Nothing
 * is faked — an empty library shows an instruction instead of placeholder workouts.
 */
object Widgets {

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val favorites = ComponentName(context, FavoritesWidgetProvider::class.java)
        manager.getAppWidgetIds(favorites).forEach { id ->
            FavoritesWidgetProvider.updateWidget(context, manager, id)
        }
        val quick = ComponentName(context, QuickStartWidgetProvider::class.java)
        manager.getAppWidgetIds(quick).forEach { id ->
            QuickStartWidgetProvider.updateWidget(context, manager, id)
        }
        // Shortcut publishing is a suspended read of the library; fire and forget.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { ShortcutHelper.publish(context) }
    }
}

class FavoritesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {
        private const val MAX_ROWS = 4

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_favorites)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope.launch {
                val container = context.pulseContainer
                val favorites = runCatching {
                    container.workouts.summaries.first().filter { it.isFavorite }.take(MAX_ROWS)
                }.getOrDefault(emptyList())
                val remoteViews = build(context, views, favorites)
                with(Dispatchers.Main) { runCatching { manager.updateAppWidget(widgetId, remoteViews) } }
            }
        }

        private fun build(context: Context, views: RemoteViews, favorites: List<WorkoutSummary>): RemoteViews {
            views.removeAllViews(R.id.widget_rows)
            if (favorites.isEmpty()) {
                views.addView(
                    R.id.widget_rows,
                    RemoteViews(context.packageName, R.layout.widget_empty_row).apply {
                        setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_empty))
                        setOnClickPendingIntent(R.id.widget_empty_text, openApp(context, "pulse://workouts"))
                    },
                )
                return views
            }
            favorites.forEach { workout ->
                val row = RemoteViews(context.packageName, R.layout.widget_favorite_row)
                row.setTextViewText(R.id.widget_row_title, workout.name)
                val duration = if (workout.hasOpenEnded) context.getString(R.string.player_open_ended) else formatClock(workout.knownDurationMillis)
                row.setTextViewText(R.id.widget_row_subtitle, duration)
                row.setOnClickPendingIntent(R.id.widget_row_root, openApp(context, "pulse://workout/${workout.id}"))
                views.addView(R.id.widget_rows, row)
            }
            return views
        }
    }
}

class QuickStartWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_start)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, "pulse://quick-builder"))
            runCatching { manager.updateAppWidget(widgetId, views) }
        }
    }
}

private fun openApp(context: Context, deeplink: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(deeplink)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    return PendingIntent.getActivity(
        context,
        deeplink.hashCode(),
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
