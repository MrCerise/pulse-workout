package com.mrcerise.intervalcoach.wear.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pulse.engine.HapticCue

/**
 * Haptic cue output — the watch's counterpart of the phone's tone/TTS coach.
 *
 * A watch has no meaningful speaker scenario mid-workout, so coaching is delivered as vibration
 * patterns mapped from the same [HapticCue] values the phone uses.
 */
interface WatchCues {
    /** The pattern baked into the interval that is starting. */
    fun intervalCue(cue: HapticCue)

    /** One countdown second (3-2-1 before a step change). */
    fun countdownTick()
}

class AndroidWatchCues(context: Context) : WatchCues {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun intervalCue(cue: HapticCue) {
        when (cue) {
            HapticCue.NONE -> Unit
            HapticCue.LIGHT -> wave(longArrayOf(0, 24), intArrayOf(0, 140))
            HapticCue.DOUBLE -> wave(longArrayOf(0, 24, 90, 24), intArrayOf(0, 160, 0, 160))
            HapticCue.STRONG -> wave(longArrayOf(0, 48), intArrayOf(0, 255))
            HapticCue.PATTERN_321 -> wave(longArrayOf(0, 18, 110, 18, 110, 18), intArrayOf(0, 170, 0, 170, 0, 170))
        }
    }

    override fun countdownTick() {
        wave(longArrayOf(0, 14), intArrayOf(0, 110))
    }

    private fun wave(timings: LongArray, amplitudes: IntArray) {
        val vibrator = vibrator ?: return
        runCatching {
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }
}
