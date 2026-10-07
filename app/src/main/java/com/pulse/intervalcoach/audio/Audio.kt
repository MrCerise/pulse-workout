package com.pulse.intervalcoach.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import com.pulse.engine.HapticCue
import com.pulse.engine.SoundCue
import com.pulse.intervalcoach.data.VoiceVerbosity

/**
 * Audio, speech, haptics and audio-focus management.
 *
 * Everything here degrades gracefully: a missing TTS engine, an absent vibrator or a device without
 * media output never crashes a workout — the session keeps running with whatever feedback exists.
 */

// ---------------------------------------------------------------------------------------------
// Sound cues
// ---------------------------------------------------------------------------------------------

/**
 * Plays the short cue tones.
 *
 * Renders tiny PCM buffers directly with [AudioTrack], which gives us consistent, original tones
 * (no bundled audio files, no licences to track) and predictable latency. The generator is opened
 * lazily on first use and reused for the whole workout.
 */
class CueSoundPlayer(private val context: Context) {

    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44_100

    @Volatile
    var volume: Float = 0.8f

    private fun ensureTrack(): AudioTrack? {
        audioTrack?.let { if (it.state == AudioTrack.STATE_INITIALIZED) return it }
        return runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(sampleRate / 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { it.play() }
            audioTrack = track
            track
        }.getOrNull()
    }

    fun play(cue: SoundCue) {
        if (cue == SoundCue.NONE) return
        val track = ensureTrack() ?: return
        val samples = render(cue)
        runCatching { track.write(samples, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING) }
    }

    /** Releases the underlying track. Called when a session ends. */
    fun release() {
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
    }

    @VisibleForTesting
    internal fun render(cue: SoundCue): FloatArray = when (cue) {
        SoundCue.NONE -> FloatArray(0)
        SoundCue.TICK -> beep(880.0, 90)
        SoundCue.BEEP -> beep(1_046.0, 160)
        SoundCue.DOUBLE_BEEP -> beep(1_046.0, 120) + silence(70) + beep(1_320.0, 120)
        SoundCue.BELL -> bell(660.0, 900)
        SoundCue.WHISTLE -> sweep(1_400.0, 2_200.0, 320)
        SoundCue.CHIME -> bell(990.0, 700) + bell(1_320.0, 600)
        SoundCue.BUZZ -> buzz(180.0, 260)
    }

    private fun beep(frequency: Double, millis: Int): FloatArray {
        val count = sampleRate * millis / 1000
        return FloatArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = envelope(i, count, attackMs = 5, releaseMs = min(40, millis / 2))
            (kotlin.math.sin(2 * Math.PI * frequency * t) * envelope * volume).toFloat()
        }
    }

    private fun bell(frequency: Double, millis: Int): FloatArray {
        val count = sampleRate * millis / 1000
        return FloatArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val decay = kotlin.math.exp(-3.2 * t)
            val tone = kotlin.math.sin(2 * Math.PI * frequency * t) * 0.65 +
                kotlin.math.sin(2 * Math.PI * frequency * 2.76 * t) * 0.25 +
                kotlin.math.sin(2 * Math.PI * frequency * 5.4 * t) * 0.10
            (tone * decay * volume).toFloat()
        }
    }

    private fun sweep(from: Double, to: Double, millis: Int): FloatArray {
        val count = sampleRate * millis / 1000
        var phase = 0.0
        return FloatArray(count) { i ->
            val progress = i.toDouble() / count
            val frequency = from + (to - from) * progress
            phase += 2 * Math.PI * frequency / sampleRate
            val envelope = envelope(i, count, 5, 60)
            (kotlin.math.sin(phase) * envelope * volume).toFloat()
        }
    }

    private fun buzz(frequency: Double, millis: Int): FloatArray {
        val count = sampleRate * millis / 1000
        return FloatArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = envelope(i, count, 5, 60)
            val square = if (kotlin.math.sin(2 * Math.PI * frequency * t) >= 0) 1.0 else -1.0
            (square * envelope * volume * 0.5).toFloat()
        }
    }

    private fun silence(millis: Int) = FloatArray(sampleRate * millis / 1000)

    private fun envelope(index: Int, total: Int, attackMs: Int, releaseMs: Int): Double {
        val attack = sampleRate * attackMs / 1000
        val release = sampleRate * releaseMs / 1000
        return when {
            index < attack -> index.toDouble() / attack
            index > total - release -> ((total - index).toDouble() / release).coerceIn(0.0, 1.0)
            else -> 1.0
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Haptics
// ---------------------------------------------------------------------------------------------

/** Vibration with capability detection. Devices without a vibrator simply do nothing. */
class Haptics(private val context: Context) {

    @Volatile
    var enabled: Boolean = true

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    val isAvailable: Boolean get() = vibrator?.hasVibrator() == true

    fun vibrate(cue: HapticCue) {
        if (!enabled || cue == HapticCue.NONE) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val effect = when (cue) {
            HapticCue.NONE -> return
            HapticCue.LIGHT -> VibrationEffect.createOneShot(40, 90)
            HapticCue.DOUBLE -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1)
            HapticCue.STRONG -> VibrationEffect.createOneShot(180, 200)
            HapticCue.PATTERN_321 -> VibrationEffect.createWaveform(longArrayOf(0, 30, 200, 30, 200, 30), -1)
        }
        runCatching { vib.vibrate(effect) }
    }

    fun cancel() = runCatching { vibrator?.cancel() }.let { }
}

// ---------------------------------------------------------------------------------------------
// Cue anti-backlog gate (pure, unit-testable)
// ---------------------------------------------------------------------------------------------

enum class CuePriority { INTERVAL_CHANGE, COUNTDOWN, INFO }

/**
 * Decides whether a spoken cue should be spoken, dropped, or should interrupt what is being said.
 *
 * Rationale: during a Tabata block the app emits far more cues than a voice can finish. This gate
 * keeps speech useful by (a) never queueing more than one utterance behind the current one and
 * (b) always letting interval changes interrupt countdown chatter. It is deliberately pure so the
 * behaviour is covered by unit tests instead of being discovered in the gym.
 */
class CueGate(
    private val nowMillis: () -> Long,
    /** Characters per second the engine speaks at the current rate. */
    private val speakingRate: Double = 14.0,
) {
    private var busyUntil: Long = 0L
    private var queued = 0

    data class Decision(val speak: Boolean, val flush: Boolean, val droppedForBacklog: Boolean)

    fun decide(text: String, priority: CuePriority): Decision {
        val now = nowMillis()
        if (now >= busyUntil) {
            queued = 0
        }
        val estimated = estimateDuration(text)
        return when {
            priority == CuePriority.INTERVAL_CHANGE -> {
                busyUntil = now + estimated
                queued = 0
                Decision(speak = true, flush = true, droppedForBacklog = false)
            }
            now >= busyUntil -> {
                busyUntil = now + estimated
                queued++
                Decision(speak = true, flush = false, droppedForBacklog = false)
            }
            else -> {
                // Something is still being said. Never stack more than one follow-up cue.
                val remaining = busyUntil - now
                val wouldRunPastNextBoundary = priority == CuePriority.COUNTDOWN && remaining > 1_500
                if (queued >= 1 || wouldRunPastNextBoundary) {
                    Decision(speak = false, flush = false, droppedForBacklog = true)
                } else {
                    queued++
                    busyUntil += estimated
                    Decision(speak = true, flush = false, droppedForBacklog = false)
                }
            }
        }
    }

    fun estimateDuration(text: String): Long {
        val seconds = text.length / speakingRate
        // Floor at 750 ms: even the shortest cue ("Go") occupies the engine for roughly that long
        // once TTS onset latency is included. Without a realistic floor a burst of tiny cues slides
        // through the gate and the coach talks over itself.
        return (seconds * 1000).toLong().coerceAtLeast(MIN_CUE_MILLIS)
    }

    fun reset() {
        busyUntil = 0
        queued = 0
    }

    companion object {
        /** Shortest time any spoken cue may occupy the engine. */
        const val MIN_CUE_MILLIS: Long = 750
    }
}

// ---------------------------------------------------------------------------------------------
// Speech
// ---------------------------------------------------------------------------------------------

sealed interface SpeechAvailability {
    data object Ready : SpeechAvailability
    data object Initialising : SpeechAvailability
    data object NoEngine : SpeechAvailability
    data class MissingLanguageData(val languageTag: String) : SpeechAvailability
}

/** Snapshot of what the installed speech engine can do, shown in Voice Studio. */
data class SpeechEngineInfo(
    val availability: SpeechAvailability,
    val engineLabel: String?,
    val currentVoice: String?,
    val languageTag: String?,
    val offlineVoiceInstalled: Boolean,
    val voiceCount: Int,
)

/**
 * Text-to-speech coaching.
 *
 * Handles the three failure modes explicitly instead of silently doing nothing:
 *  - no engine installed at all → [SpeechAvailability.NoEngine], callers fall back to sound cues;
 *  - engine present but no data for the language → reported with an install hint;
 *  - engine present and working but no *offline* voice → still used, with a note that a network is
 *    required until the user installs offline voice data.
 */
class SpeechCoach(
    context: Context,
    private val gate: CueGate,
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    private val _state = MutableStateFlow<SpeechAvailability>(SpeechAvailability.Initialising)
    val state: StateFlow<SpeechAvailability> = _state.asStateFlow()

    private val _info = MutableStateFlow(
        SpeechEngineInfo(
            availability = SpeechAvailability.Initialising,
            engineLabel = null,
            currentVoice = null,
            languageTag = null,
            offlineVoiceInstalled = false,
            voiceCount = 0,
        )
    )
    val info: StateFlow<SpeechEngineInfo> = _info.asStateFlow()

    @Volatile
    var rate: Float = 1.0f

    @Volatile
    var pitch: Float = 1.0f

    @Volatile
    var verbose: VoiceVerbosity = VoiceVerbosity.STANDARD

    private var initialised = false

    fun initialise() {
        if (initialised) return
        initialised = true
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    configure()
                } else {
                    _state.value = SpeechAvailability.NoEngine
                    _info.value = _info.value.copy(availability = SpeechAvailability.NoEngine)
                }
            }
        }.onFailure {
            _state.value = SpeechAvailability.NoEngine
            _info.value = _info.value.copy(availability = SpeechAvailability.NoEngine)
        }
    }

    private fun configure() {
        val engine = tts ?: return
        runCatching {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = Unit
            })
            val locale = localeFor(appContext)
            val result = engine.setLanguage(locale)
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)
            val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            val languageVoices = voices.filter { it.locale.language == locale.language }
            val offline = languageVoices.filter { !it.isNetworkConnectionRequired }
            val current = runCatching { engine.voice }.getOrNull()
            val availability = when {
                result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED ->
                    SpeechAvailability.MissingLanguageData(locale.toLanguageTag())
                else -> SpeechAvailability.Ready
            }
            _state.value = availability
            _info.value = SpeechEngineInfo(
                availability = availability,
                engineLabel = runCatching { engine.defaultEngine }.getOrNull(),
                currentVoice = current?.name,
                languageTag = locale.toLanguageTag(),
                offlineVoiceInstalled = offline.isNotEmpty(),
                voiceCount = voices.size,
            )
            if (offline.isNotEmpty() && current?.isNetworkConnectionRequired == true) {
                runCatching { engine.voice = offline.first() }
            }
        }.onFailure {
            _state.value = SpeechAvailability.NoEngine
        }
    }

    /** Speaks a cue if the gate allows it. Returns false when the cue was not spoken. */
    fun speak(text: String, priority: CuePriority = CuePriority.INFO): Boolean {
        val engine = tts ?: return false
        if (_state.value !is SpeechAvailability.Ready) return false
        if (text.isBlank()) return false
        val decision = gate.decide(text, priority)
        if (!decision.speak) return false
        runCatching {
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)
            if (decision.flush) engine.stop()
            val mode = if (decision.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, UUID.randomUUID().toString())
        }.onFailure { return false }
        return true
    }

    fun stop() = runCatching { tts?.stop() }.let { }

    /** Preview used by Voice Studio and onboarding; bypasses the gate. */
    fun speakPreview(text: String): Boolean {
        val engine = tts ?: return false
        return runCatching {
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)
            engine.stop()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview-${System.currentTimeMillis()}")
            true
        }.getOrDefault(false)
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        initialised = false
        gate.reset()
    }

    fun resetGate() = gate.reset()

    companion object {
        fun localeFor(context: Context): Locale {
            val tag = context.resources.configuration.locales[0]
            return Locale.forLanguageTag(tag.toLanguageTag()).takeIf { it.language.isNotEmpty() } ?: Locale.getDefault()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Audio focus + music playback
// ---------------------------------------------------------------------------------------------

enum class FocusEvent { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

/** Wraps the audio-focus dance for the whole session (cues + optional background audio). */
class AudioFocusController(
    context: Context,
    private val onFocusEvent: (FocusEvent) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onFocusEvent(FocusEvent.GAIN)
            AudioManager.AUDIOFOCUS_LOSS -> onFocusEvent(FocusEvent.LOSS)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusEvent(FocusEvent.LOSS_TRANSIENT)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onFocusEvent(FocusEvent.LOSS_TRANSIENT_CAN_DUCK)
        }
    }

    fun request(): Boolean {
        val manager = audioManager ?: return false
        // minSdk 26, so the AudioFocusRequest API is always available.
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(listener)
            .build()
            .also { focusRequest = it }
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        val manager = audioManager ?: return
        focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}

/** Reports whether headphones are currently attached, for the unplug behaviour preference. */
fun Context.isHeadsetConnected(): Boolean {
    val manager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    @Suppress("DEPRECATION")
    return !manager.isSpeakerphoneOn && manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
    }
}
