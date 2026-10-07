package com.pulse.intervalcoach.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pulse.engine.TimerSnapshot
import com.pulse.engine.TimerStatus
import com.pulse.intervalcoach.MainActivity
import com.pulse.intervalcoach.PulseApp
import com.pulse.intervalcoach.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground service that keeps a workout alive with the screen off.
 *
 * It deliberately owns no timing logic: [SessionController] runs the engine and publishes state,
 * this service only (a) keeps the process alive with a media-playback foreground type, (b) mirrors
 * player state into a notification with controls, and (c) reacts to the system events that matter
 * mid-workout (headphones unplugged, audio focus, task removal).
 */
class WorkoutService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private var noisyReceiver: BroadcastReceiver? = null

    private val controller: SessionController
        get() = (application as PulseApp).container.sessionController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        registerNoisyReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> controller.togglePause()
            ACTION_NEXT -> controller.next()
            ACTION_STOP -> controller.endSession(stoppedEarly = true)
            ACTION_ADD_TIME -> controller.addTime(30_000)
            ACTION_START -> Unit // the session is already running; the service only needs to exist
        }
        startForegroundCompat(buildNotification(controller.snapshot.value))
        observeSession()
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
    }

    private fun observeSession() {
        if (notificationJob?.isActive == true) return
        notificationJob = serviceScope.launch {
            var lastUpdate = 0L
            controller.snapshot.collectLatest { snapshot ->
                if (!snapshot.isActive) {
                    stopForegroundCompat()
                    stopSelf()
                    return@collectLatest
                }
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastUpdate >= 500) {
                    lastUpdate = now
                    notify(buildNotification(snapshot))
                }
            }
        }
    }

    private fun notify(notification: Notification) {
        runCatching { Notifications.manager(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun buildNotification(snapshot: TimerSnapshot): Notification {
        val step = snapshot.step
        val title = if (snapshot.isPaused) {
            getString(R.string.player_paused) + " · " + controller.lastCueText.value.orEmpty().takeIf { it.isNotBlank() }.orEmpty()
        } else {
            step?.let { CueScript.notificationLine(it) } ?: getString(R.string.app_name)
        }.trim().ifBlank { snapshot.planName.ifBlank { getString(R.string.app_name) } }

        val remaining = snapshot.sessionRemainingMillis
        val timeText = if (snapshot.status == TimerStatus.AWAITING_MANUAL) {
            getString(R.string.player_awaiting_manual)
        } else if (remaining != null) {
            "${formatClock(snapshot.stepRemainingMillis)} · ${getString(R.string.player_session_remaining, formatClock(remaining))}"
        } else {
            formatClock(snapshot.stepElapsedMillis)
        }

        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle(title)
            .setContentText(timeText)
            .setSubText(snapshot.planName)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent())
            .setProgress(if (snapshot.stepDurationMillis > 0) 1_000 else 0, (snapshot.intervalProgress * 1_000).toInt(), false)

        builder.addAction(
            0,
            getString(if (snapshot.isPaused) R.string.notif_action_resume else R.string.notif_action_pause),
            actionPendingIntent(if (snapshot.isPaused) ACTION_TOGGLE_PAUSE else ACTION_TOGGLE_PAUSE),
        )
        builder.addAction(0, getString(R.string.notif_action_next), actionPendingIntent(ACTION_NEXT))
        builder.addAction(0, getString(R.string.notif_action_stop), actionPendingIntent(ACTION_STOP))
        return builder.build()
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("pulse://player")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, WorkoutService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    controller.onHeadphonesDisconnected()
                }
            }
        }
        noisyReceiver = receiver
        registerReceiver(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        noisyReceiver?.let { runCatching { unregisterReceiver(it) } }
        noisyReceiver = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    companion object {
        const val CHANNEL_ID = "pulse.workout"
        const val NOTIFICATION_ID = 4101
        const val ACTION_START = "com.pulse.intervalcoach.action.START"
        const val ACTION_TOGGLE_PAUSE = "com.pulse.intervalcoach.action.TOGGLE_PAUSE"
        const val ACTION_NEXT = "com.pulse.intervalcoach.action.NEXT"
        const val ACTION_STOP = "com.pulse.intervalcoach.action.STOP"
        const val ACTION_ADD_TIME = "com.pulse.intervalcoach.action.ADD_TIME"

        fun start(context: Context) {
            val intent = Intent(context, WorkoutService::class.java).setAction(ACTION_START)
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WorkoutService::class.java)) }
        }
    }
}

internal fun formatClock(millis: Long): String {
    val total = (millis.coerceAtLeast(0) + 999) / 1_000
    val h = total / 3_600
    val m = (total % 3_600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

/** Notification channels are created once, on app start and again defensively by the service. */
object Notifications {
    const val CHANNEL_WORKOUT = WorkoutService.CHANNEL_ID
    const val CHANNEL_REMINDERS = "pulse.reminders"
    const val CHANNEL_BACKUP = "pulse.backup"

    fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels(context: Context) {
        val manager = manager(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WORKOUT,
                context.getString(R.string.notif_channel_workout_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_workout_desc)
                setShowBadge(false)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.notif_channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_reminders_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BACKUP,
                context.getString(R.string.notif_backup_channel),
                NotificationManager.IMPORTANCE_MIN,
            ).apply { description = context.getString(R.string.notif_backup_channel_desc) }
        )
    }
}
