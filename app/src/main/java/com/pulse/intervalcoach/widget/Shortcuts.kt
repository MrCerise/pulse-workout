package com.pulse.intervalcoach.widget

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.pulse.intervalcoach.DeepLinks
import com.pulse.intervalcoach.MainActivity
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.pulseContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Dynamic app shortcuts for pinned (favourite) and recent workouts.
 *
 * Shortcuts are published from real saved data only; on an empty library the launcher shows just the
 * static quick-builder shortcut. Publishing is rate-limited by Android itself (max 5 per app), and
 * failures are ignored — shortcuts are a convenience, never a requirement.
 */
object ShortcutHelper {

    private const val MAX_DYNAMIC = 4

    suspend fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val container = context.pulseContainer
        val summaries = runCatching { container.workouts.summaries.first() }.getOrDefault(emptyList())
        val favorites = summaries.filter { it.isFavorite }.take(2)
        val recent = summaries
            .filter { it.lastUsedAt != null && favorites.none { favorite -> favorite.id == it.id } }
            .sortedByDescending { it.lastUsedAt }
            .take(MAX_DYNAMIC - favorites.size)

        val shortcuts = (favorites + recent).take(MAX_DYNAMIC).map { workout ->
            ShortcutInfo.Builder(context, "workout-${workout.id}")
                .setShortLabel(workout.name.take(18))
                .setLongLabel(workout.name)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_quick))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        // Favourite shortcuts start immediately; recent ones open the detail screen.
                        data = android.net.Uri.parse(
                            if (workout.isFavorite) DeepLinks.start(workout.id) else DeepLinks.details(workout.id),
                        )
                    }
                )
                .build()
        }
        withContext(Dispatchers.Default) {
            runCatching { manager.dynamicShortcuts = shortcuts }
        }
    }
}
