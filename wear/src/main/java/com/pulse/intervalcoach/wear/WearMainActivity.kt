package com.pulse.intervalcoach.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pulse.intervalcoach.wear.ui.PulseWearRoot

/**
 * Single-activity host for the watch app.
 *
 * The activity owns no session state: the container keeps the workout running, so lowering the
 * wrist (screen off) pauses nothing — the engine ticks under a wake lock and the UI catches up
 * from the snapshot when the wrist comes back up.
 */
class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseWearRoot(container = pulseWearContainer)
        }
    }
}
