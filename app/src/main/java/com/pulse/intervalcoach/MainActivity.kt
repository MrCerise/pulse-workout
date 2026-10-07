package com.pulse.intervalcoach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.pulse.intervalcoach.session.Notifications
import com.pulse.intervalcoach.ui.PulseAppRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-activity host.
 *
 * The activity owns no session state: it forwards deep links (from shortcuts, widgets and
 * notification taps) into the Compose tree and lets [AppContainer] keep the workout running.
 */
class MainActivity : ComponentActivity() {

    private val deeplinks = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        deeplinks.value = intent?.data?.toString()
        setContent {
            PulseAppRoot(
                container = (application as PulseApp).container,
                deeplinks = deeplinks.asStateFlow(),
                onDeeplinkConsumed = { deeplinks.value = null },
                onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deeplinks.value = intent.data?.toString()
    }

    /**
     * Asks for POST_NOTIFICATIONS the first time a workout starts so the foreground service can
     * show its controls. Denial is handled by the app: the workout still runs, without controls.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        runCatching {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Deep-link helper shared with widgets, shortcuts and reminders. */
object DeepLinks {
    const val SCHEME = "pulse"
    const val QUICK_BUILDER = "pulse://quick-builder"
    const val RECENT = "pulse://recent"
    const val PLAYER = "pulse://player"
    const val WORKOUTS = "pulse://workouts"

    fun start(workoutId: String) = "pulse://start/$workoutId"
    fun details(workoutId: String) = "pulse://workout/$workoutId"
}
