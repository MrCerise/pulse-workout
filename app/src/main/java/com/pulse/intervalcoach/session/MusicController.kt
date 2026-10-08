package com.pulse.intervalcoach.session

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pulse.intervalcoach.audio.DuckConfig
import com.pulse.intervalcoach.audio.DuckState
import com.pulse.intervalcoach.audio.DuckingEngine
import com.pulse.intervalcoach.data.PreferencesRepository
import com.pulse.intervalcoach.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Optional background audio chosen by the user (a playlist, an album, a podcast episode), and the
 * ducking that keeps the coach audible over it.
 *
 * PULSE never bundles or streams music itself: it plays a file the user picked through the system
 * file picker. Playback is ducked — not stopped — while the coach speaks or a cue tone sounds, and
 * released when the session ends. If the file has moved or access was revoked, playback fails
 * silently and the workout continues; Voice Studio tells the user the file is unavailable.
 *
 * The ducking curve itself lives in [DuckingEngine] (pure, unit tested). This class owns the player
 * and a short sampling loop that writes the curve to [ExoPlayer.volume]; nothing else in the app
 * touches that volume, so the ramp can never fight a second writer.
 */
class MusicController(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    private var player: ExoPlayer? = null
    private var rampJob: Job? = null
    private var speechWatchdog: Job? = null

    /** Increments on every start/stop so a late fade-out can never release a fresh player. */
    private var generation = 0

    private val ducking = DuckingEngine { SystemClock.elapsedRealtime() }

    /** Last volume written to the player, or -1 before the first write. */
    @Volatile
    private var appliedVolume = -1f

    /** The listener's own music volume, before ducking. Updated whenever the preference changes. */
    @Volatile
    private var baseVolume = 0.6f

    /** True while another app holds a "may duck" focus: a navigation prompt, a call ringing in. */
    @Volatile
    private var externallyDucked = false

    private val _duckState = MutableStateFlow(DuckState.FULL_VOLUME)
    /** Live ducking state, for the player indicator and the Voice Studio preview. */
    val duckState: StateFlow<DuckState> = _duckState.asStateFlow()

    private val _playing = MutableStateFlow(false)
    /** True while background audio is actually playing. */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _unavailable = MutableStateFlow<String?>(null)
    /** Set when the selected file could not be opened, so the UI can say why nothing is playing. */
    val unavailable: StateFlow<String?> = _unavailable.asStateFlow()

    // -------------------------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------------------------

    /** Pushes the user's ducking and volume preferences into the curve. Safe at any time. */
    fun configure(preferences: UserPreferences) {
        ducking.update(
            DuckConfig(
                enabled = preferences.duckMusicDuringCues,
                level = preferences.duckLevel,
            )
        )
        baseVolume = preferences.musicVolume.coerceIn(0f, 1f)
        publishDuckState()
    }

    // -------------------------------------------------------------------------------------------
    // Playback
    // -------------------------------------------------------------------------------------------

    /** Starts the user's background audio, if one is selected and still readable. */
    fun start() {
        val thisGeneration = ++generation
        scope.launch {
            val current = prefs.current()
            val uri = current.musicUri
            if (uri.isNullOrBlank()) {
                _unavailable.value = null
                return@launch
            }
            configure(current)
            val exo = ensurePlayer() ?: return@launch
            val started = runCatching {
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                exo.prepare()
                exo.volume = 0f
                appliedVolume = 0f
                exo.play()
                true
            }.getOrDefault(false)
            if (!started || thisGeneration != generation) return@launch
            _unavailable.value = null
            _playing.value = true
            // Fade in from silence instead of slamming the track in at full volume.
            rampTo(baseVolume, FADE_IN_MS)
            if (thisGeneration == generation) startRampLoop()
        }
    }

    /** Stops and releases playback — session over, or the Voice Studio preview switched off. */
    fun stop() {
        generation++
        rampJob?.cancel()
        rampJob = null
        speechWatchdog?.cancel()
        speechWatchdog = null
        ducking.cancel()
        appliedVolume = -1f
        publishDuckState()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        _playing.value = false
    }

    fun pause() {
        runCatching { player?.pause() }
        _playing.value = player?.isPlaying == true
    }

    fun play() {
        runCatching { player?.play() }
        _playing.value = player?.isPlaying == true
    }

    /** Applies a new base volume immediately (the user moved the slider mid-session). */
    fun setVolume(volume: Float) {
        baseVolume = volume.coerceIn(0f, 1f)
        writeVolume(targetVolume())
    }

    /**
     * Another app asked for room without taking the session away (a `LOSS_TRANSIENT_CAN_DUCK` focus
     * event). The music stays down until focus comes back; coaching keeps running at full volume.
     */
    fun setExternalDuck(ducked: Boolean) {
        if (externallyDucked == ducked) return
        externallyDucked = ducked
        writeVolume(targetVolume())
    }

    private fun ensurePlayer(): ExoPlayer? {
        player?.let { return it }
        return runCatching {
            val exo = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    // PULSE owns the focus dance for the whole session (see AudioFocusController),
                    // so the player must not pause itself behind our back.
                    /* handleAudioFocus = */ false,
                )
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            // Revoked permission, moved file, unsupported codec: the workout goes on.
                            _unavailable.value = error.errorCodeName
                            _playing.value = false
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _playing.value = isPlaying
                        }
                    }
                )
            }
            player = exo
            exo
        }.getOrNull()
    }

    // -------------------------------------------------------------------------------------------
    // Ducking
    // -------------------------------------------------------------------------------------------

    /**
     * The coach started talking. [estimatedMillis] is the cue gate's own estimate and only backs the
     * watchdog: the real end of speech arrives through [onSpeechFinished], but some engines never
     * report it, and music that stays ducked forever is worse than music that comes back early.
     */
    fun onSpeechStarted(estimatedMillis: Long) {
        ducking.beginDuck()
        publishDuckState()
        speechWatchdog?.cancel()
        speechWatchdog = scope.launch {
            delay(estimatedMillis.coerceAtLeast(0L) + SPEECH_WATCHDOG_SLACK_MS)
            ducking.release()
            publishDuckState()
        }
    }

    /** The speech engine is quiet again: hold, then climb back to full volume. */
    fun onSpeechFinished() {
        speechWatchdog?.cancel()
        speechWatchdog = null
        ducking.release()
        publishDuckState()
    }

    /** A cue tone is sounding for [millis]; the music drops for exactly that long (plus the ramp). */
    fun onCue(millis: Long) {
        if (!ducking.config.enabled) return
        ducking.duckFor(millis)
        publishDuckState()
    }

    /**
     * Preview used by Voice Studio: ducks for a moment so the setting can be heard — and seen, since
     * the state is republished while the curve travels, with or without a track playing.
     */
    fun previewDuck(millis: Long = 1_600L) {
        ducking.beginDuck()
        speechWatchdog?.cancel()
        speechWatchdog = scope.launch {
            val releaser = launch {
                delay(millis)
                ducking.release()
            }
            while (releaser.isActive || ducking.isDucking()) {
                publishDuckState()
                delay(RAMP_STEP_MS)
            }
            publishDuckState()
        }
    }

    private fun publishDuckState() {
        _duckState.value = ducking.stateAt()
    }

    // -------------------------------------------------------------------------------------------
    // Volume ramp
    // -------------------------------------------------------------------------------------------

    /** Samples the ducking curve and writes it to the player until playback stops. */
    private fun startRampLoop() {
        rampJob?.cancel()
        rampJob = scope.launch {
            while (true) {
                _duckState.value = ducking.stateAt()
                writeVolume(targetVolume())
                delay(RAMP_STEP_MS)
            }
        }
    }

    /** Short fade used when playback starts, so the first second is not a jump. */
    private suspend fun rampTo(target: Float, durationMs: Long) {
        if (player == null) return
        val from = appliedVolume.coerceAtLeast(0f)
        val steps = (durationMs / RAMP_STEP_MS).coerceAtLeast(1L).toInt()
        repeat(steps) { index ->
            val fraction = (index + 1).toFloat() / steps.toFloat()
            writeVolume(from + (target - from) * fraction)
            delay(RAMP_STEP_MS)
        }
        writeVolume(target)
    }

    /** The volume the player should be at right now: user volume × ducking × external duck. */
    private fun targetVolume(): Float {
        val ducked = ducking.volumeFor(baseVolume)
        return if (externallyDucked) (ducked * EXTERNAL_DUCK_SCALE).coerceIn(0f, 1f) else ducked
    }

    private fun writeVolume(volume: Float) {
        val target = volume.coerceIn(0f, 1f)
        if (appliedVolume >= 0f && !ducking.shouldApply(appliedVolume, target)) return
        appliedVolume = target
        runCatching { player?.volume = target }
    }

    companion object {
        /** How often the ducking curve is sampled. Fine enough that a 140 ms drop looks smooth. */
        const val RAMP_STEP_MS = 40L
        const val FADE_IN_MS = 320L

        /** Slack over the gate's speech estimate before the watchdog releases the duck. */
        const val SPEECH_WATCHDOG_SLACK_MS = 1_500L

        /** How far the music drops when another app only needs to be heard over it. */
        const val EXTERNAL_DUCK_SCALE = 0.4f
    }
}
