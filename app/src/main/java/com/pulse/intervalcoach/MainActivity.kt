package com.pulse.intervalcoach

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.FitnessOptions
import com.pulse.intervalcoach.health.FitAuthBridge
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
 * It also hosts the Google Fit authorisation round-trip, because its result always lands in
 * [onActivityResult] — [FitAuthBridge] is how the health layer reaches it.
 */
class MainActivity : ComponentActivity() {

    private val deeplinks = MutableStateFlow<String?>(null)
    private var fitResultCallback: ((Boolean) -> Unit)? = null

    // Result launchers must be registered before the activity reaches STARTED, so they live at
    // property level — calling registerForActivityResult from a runtime callback would throw.
    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        FitAuthBridge.launcher = { activity, options, onResult -> requestFitAuthorization(activity, options, onResult) }
        deeplinks.value = intent?.data?.toString()
        setContent {
            PulseAppRoot(
                container = (application as PulseApp).container,
                deeplinks = deeplinks.asStateFlow(),
                onDeeplinkConsumed = { deeplinks.value = null },
                onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
            )
        }
        // Refresh the health-platform state whenever the app comes back to the foreground.
        (application as PulseApp).container.health.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deeplinks.value = intent.data?.toString()
    }

    /**
     * Starts the Google Fit authorisation dialog for the given [options]. The outcome is delivered
     * to [onResult] in [onActivityResult] — never assume it fires synchronously.
     */
    private fun requestFitAuthorization(activity: Activity, options: FitnessOptions, onResult: (Boolean) -> Unit) {
        if (activity !is MainActivity || activity.isFinishing) {
            onResult(false)
            return
        }
        fitResultCallback = onResult
        val started = runCatching {
            val account = GoogleSignIn.getAccountForExtension(activity, options)
            if (GoogleSignIn.hasPermissions(account, options)) {
                fitResultCallback = null
                onResult(true)
                false
            } else {
                GoogleSignIn.requestPermissions(activity, FIT_AUTH_REQUEST_CODE, account, options)
                true
            }
        }.getOrDefault(false)
        if (!started) {
            // Already authorised, or the call failed before launching the dialog.
            if (fitResultCallback != null) {
                fitResultCallback = null
                onResult(false)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FIT_AUTH_REQUEST_CODE) {
            val callback = fitResultCallback
            fitResultCallback = null
            callback?.invoke(resultCode == Activity.RESULT_OK)
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

    private companion object {
        const val FIT_AUTH_REQUEST_CODE = 1301
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
