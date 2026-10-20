package com.mrcerise.intervalcoach.wear.session

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import com.mrcerise.intervalcoach.wear.R
import com.pulse.engine.FinishReason
import com.pulse.engine.PhaseKind
import com.pulse.engine.TimerEvent
import java.util.Locale

/**
 * Spoken coaching on the watch — the counterpart of the phone's voice coach, trimmed to what a
 * wrist needs: the step that starts, the last three seconds, halfway, pause and the finish.
 *
 * It speaks only when the wearer turned it on, and only through an output that can be heard: the
 * watch's speaker or connected earbuds. A watch with neither stays on haptics, silently.
 */
interface WatchVoice {
    fun announce(event: TimerEvent)

    /** Frees the speech engine once a session is over; the next session starts it again. */
    fun release()

    object None : WatchVoice {
        override fun announce(event: TimerEvent) = Unit
        override fun release() = Unit
    }
}

/** The localized words the voice uses. An interface so the wording is testable on the JVM. */
interface VoicePhrases {
    fun side(label: String): String
    val halfway: String
    val paused: String
    val resumed: String
    val finished: String
    val stopped: String
}

/** What to say for [event], or null for events the voice stays quiet on. Pure. */
fun spokenLine(event: TimerEvent, phrases: VoicePhrases): String? = when (event) {
    is TimerEvent.StepStarted -> if (event.resumed) {
        null
    } else {
        val step = event.step
        buildList {
            add(step.name.ifBlank { PhaseKind.defaultLabel(step.kind) }.trim().trimEnd('.') + ".")
            step.sideLabel?.takeIf { it.isNotBlank() }?.let { add(phrases.side(it) + ".") }
            step.speechText?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        }.joinToString(" ")
    }
    is TimerEvent.Countdown -> event.secondsRemaining.toString()
    is TimerEvent.Halfway -> phrases.halfway
    TimerEvent.SessionPaused -> phrases.paused
    TimerEvent.SessionResumed -> phrases.resumed
    is TimerEvent.SessionFinished -> if (event.reason == FinishReason.TIMELINE_COMPLETED) phrases.finished else phrases.stopped
    else -> null
}

class AndroidWatchVoice(private val context: Context) : WatchVoice {

    private val phrases = object : VoicePhrases {
        override fun side(label: String) = context.getString(R.string.voice_side, label)
        override val halfway get() = context.getString(R.string.voice_halfway)
        override val paused get() = context.getString(R.string.player_paused)
        override val resumed get() = context.getString(R.string.voice_resumed)
        override val finished get() = context.getString(R.string.voice_finished)
        override val stopped get() = context.getString(R.string.voice_stopped)
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    /** A line asked for before the engine finished starting; spoken as soon as it is ready. */
    private var pending: String? = null

    @Synchronized
    override fun announce(event: TimerEvent) {
        val line = spokenLine(event, phrases) ?: return
        if (!hasAudibleOutput()) return
        val engine = tts ?: TextToSpeech(context) { status -> onReady(status == TextToSpeech.SUCCESS) }.also { tts = it }
        if (!ready) {
            pending = line
            return
        }
        // A countdown digit must not queue behind a long step name: the newest line always wins.
        engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "pulse-" + System.nanoTime())
        if (event is TimerEvent.SessionFinished) scheduleRelease()
    }

    @Synchronized
    private fun onReady(success: Boolean) {
        val engine = tts ?: return
        if (!success) {
            release()
            return
        }
        ready = true
        val locale = Locale.getDefault()
        if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) engine.language = locale
        engine.setAudioAttributes(
            android.media.AudioAttributes.Builder()
                // Navigation guidance ducks music instead of pausing it, like the phone's coach.
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        pending?.let { engine.speak(it, TextToSpeech.QUEUE_FLUSH, null, "pulse-pending") }
        pending = null
    }

    /** The finish line is still being spoken; release the engine a moment later. */
    private fun scheduleRelease() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ release() }, RELEASE_DELAY_MILLIS)
    }

    @Synchronized
    override fun release() {
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }

    /** True when something can play the voice: the watch's speaker or Bluetooth audio. */
    private fun hasAudibleOutput(): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val audible = buildSet {
            add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in audible }
    }

    private companion object {
        const val RELEASE_DELAY_MILLIS = 6_000L
    }
}
