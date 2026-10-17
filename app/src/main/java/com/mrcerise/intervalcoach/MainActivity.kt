package com.mrcerise.intervalcoach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.mrcerise.intervalcoach.session.Notifications
import com.mrcerise.intervalcoach.session.WorkoutService
import com.mrcerise.intervalcoach.ui.PulseAppRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-activity host.
 *
 * The activity owns no session state: it forwards deep links (from shortcuts, widgets and
 * notification taps) into the Compose tree and lets [AppContainer] keep the workout running.
 */
class MainActivity : ComponentActivity() {

    private val deeplinks = MutableStateFlow<String?>(null)

    // Result launchers must be registered before the activity reaches STARTED, so they live at
    // property level — calling registerForActivityResult from a runtime callback would throw.
    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    private var afterLocationPermission: (() -> Unit)? = null
    private val locationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
            val action = afterLocationPermission
            afterLocationPermission = null
            action?.invoke()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hardware volume buttons always control media, including between spoken cues.
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        deeplinks.value = intent?.data?.toString()
        setContent {
            PulseAppRoot(
                container = (application as PulseApp).container,
                deeplinks = deeplinks.asStateFlow(),
                onDeeplinkConsumed = { deeplinks.value = null },
                onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                onRequestLocationPermission = ::requestLocationPermissionIfNeeded,
            )
        }
        val container = (application as PulseApp).container
        // Refresh the health-platform state whenever the app comes back to the foreground.
        container.health.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deeplinks.value = intent.data?.toString()
    }

    override fun onResume() {
        super.onResume()
        val tracker = (application as PulseApp).container.runTracker
        if (tracker.isTracking) {
            // Returning from location/app settings can restore permission or providers mid-run.
            // Refresh the foreground type too when location access was previously denied.
            WorkoutService.start(this)
            tracker.refreshLocationAccess()
        }
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
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Asks for location when a running workout starts, so the running system can measure real
     * speed and distance with GPS. Denial is handled by the app: race info falls back to the
     * plan's target pace and is labelled "Estimated".
     */
    private fun requestLocationPermissionIfNeeded(onResult: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            onResult()
            return
        }
        afterLocationPermission = onResult
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
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
