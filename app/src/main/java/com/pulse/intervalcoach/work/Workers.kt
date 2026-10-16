package com.pulse.intervalcoach.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pulse.intervalcoach.MainActivity
import com.pulse.intervalcoach.PulseApp
import com.pulse.intervalcoach.pulseContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.session.Notifications
import java.util.concurrent.TimeUnit

/**
 * Deferrable maintenance only — WorkManager never touches interval timing. The worker is
 * best-effort: if the OS defers it, workouts are unaffected.
 */

/** Checks for due planner reminders and posts a notification for each. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PulseApp).container
        val now = System.currentTimeMillis()
        val due = runCatching { container.reminders.due(0, now + 60_000) }.getOrDefault(emptyList())
        due.filter { it.notifiedAt == null }.forEach { reminder ->
            runCatching {
                container.reminders.markNotified(reminder.id)
                val plan = container.workouts.plan(reminder.workoutId)
                val title = plan?.name ?: applicationContext.getString(R.string.app_name)
                val intent = PendingIntent.getActivity(
                    applicationContext,
                    reminder.id.hashCode(),
                    Intent(applicationContext, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("pulse://workout/${reminder.workoutId}")
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_REMINDERS)
                    .setSmallIcon(com.pulse.intervalcoach.R.drawable.ic_stat_pulse)
                    .setContentTitle(applicationContext.getString(R.string.planner_due_today))
                    .setContentText(title)
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .build()
                Notifications.manager(applicationContext).notify(reminder.id.hashCode(), notification)
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "pulse.reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/**
 * Re-arms deferred work after a reboot or an app update, and refreshes widgets. PULSE does not
 * pretend a workout survived a reboot: an interrupted session is reported as interrupted by
 * [com.pulse.intervalcoach.data.SessionRepository.recoverInterruptedSessions].
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ReminderWorker.schedule(context)
                com.pulse.intervalcoach.widget.Widgets.refreshAll(context)
            }
        }
    }
}
