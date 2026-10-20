package com.mrcerise.intervalcoach.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.mrcerise.intervalcoach.wear.ui.PulseWearRoot

/** A request to open the app on one workout — sent by the tile. */
data class WearDeepLink(val workoutId: String, val resume: Boolean) {
    companion object {
        const val EXTRA_WORKOUT_ID = "com.mrcerise.intervalcoach.wear.WORKOUT_ID"
        const val EXTRA_RESUME = "com.mrcerise.intervalcoach.wear.RESUME"

        fun from(intent: Intent?): WearDeepLink? {
            val id = intent?.getStringExtra(EXTRA_WORKOUT_ID)?.takeIf { it.isNotBlank() } ?: return null
            return WearDeepLink(id, intent.getBooleanExtra(EXTRA_RESUME, false))
        }
    }
}

/**
 * Single-activity host for the watch app.
 *
 * The activity owns no session state: the container keeps the workout running, so lowering the
 * wrist (screen off) pauses nothing — the engine ticks under a wake lock and the UI catches up
 * from the snapshot when the wrist comes back up. It is single-task, so the tile reopens it rather
 * than stacking a second copy; [onNewIntent] carries the tile's workout to the running screen.
 */
class WearMainActivity : ComponentActivity() {

    private val deepLink = mutableStateOf<WearDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch follows the link: a recreated activity has already handled it.
        if (savedInstanceState == null) deepLink.value = WearDeepLink.from(intent)
        setContent {
            PulseWearRoot(
                container = pulseWearContainer,
                deepLink = deepLink.value,
                onDeepLinkHandled = { deepLink.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = WearDeepLink.from(intent)
    }
}
