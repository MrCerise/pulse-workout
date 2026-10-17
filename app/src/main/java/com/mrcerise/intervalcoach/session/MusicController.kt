package com.mrcerise.intervalcoach.session

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mrcerise.intervalcoach.audio.AudioFocusController
import com.mrcerise.intervalcoach.audio.DuckConfig
import com.mrcerise.intervalcoach.audio.DuckState
import com.mrcerise.intervalcoach.audio.DuckingEngine
import com.mrcerise.intervalcoach.audio.FocusEvent
import com.mrcerise.intervalcoach.data.PreferencesRepository
import com.mrcerise.intervalcoach.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns local-file playback and cue-scoped audio focus. ALL player/curve operations are confined
 * to Main, including TTS binder callbacks and the session reducer's background-thread commands.
 * Other apps are asked to duck via Android audio focus; their attenuation is system-controlled.
 * Never changes STREAM_MUSIC volume (which would also lower the coach and fight hardware keys).
 */
class MusicController(
    private val context: Context,
    private val prefs: PreferencesRepository,
    scope: CoroutineScope,
    private val createPlayer: () -> ExoPlayer = { ExoPlayer.Builder(context).build() },
) {
    private val playbackScope = CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var startJob: Job? = null
    private var rampJob: Job? = null
    private var speechWatchdog: Job? = null
    private val ducking = DuckingEngine { SystemClock.elapsedRealtime() }
    private var baseVolume = 0.6f
    private var externallyDucked = false
    private var fadeStartedAt = 0L
    private var focusAttempted = false
    private var focusInterrupted = false
    var onFocusEvent: ((FocusEvent) -> Unit)? = null
    private val focus = AudioFocusController(context, ::handleFocusEvent)

    private fun handleFocusEvent(event: FocusEvent) {
        focusInterrupted = event == FocusEvent.LOSS_TRANSIENT
        if (event == FocusEvent.LOSS || event == FocusEvent.LOSS_TRANSIENT) pause()
        onFocusEvent?.invoke(event)
        applyState()
    }

    private val _duckState = MutableStateFlow(DuckState.FULL_VOLUME)
    val duckState: StateFlow<DuckState> = _duckState.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _unavailable = MutableStateFlow<String?>(null)
    val unavailable: StateFlow<String?> = _unavailable.asStateFlow()

    fun configure(preferences: UserPreferences) {
        playbackScope.launch {
            ducking.update(DuckConfig(enabled = preferences.duckMusicDuringCues, level = preferences.duckLevel))
            baseVolume = preferences.musicVolume.coerceIn(0f, 1f)
            applyState()
        }
    }

    fun start() {
        playbackScope.launch {
            startJob?.cancel()
            startJob = playbackScope.launch load@{
                val current = prefs.current()
                val uri = current.musicUri
                releasePlayer()
                _unavailable.value = null
                if (uri.isNullOrBlank()) return@load
                configure(current)
                runCatching {
                    val exo = createPlayer()
                    player = exo
                    exo.setAudioAttributes(
                        AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .setUsage(C.USAGE_MEDIA).build(), false,
                    )
                    exo.repeatMode = Player.REPEAT_MODE_ALL
                    exo.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            _unavailable.value = error.errorCodeName
                            releasePlayer()
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _playing.value = isPlaying
                        }
                    })
                    exo.volume = 0f
                    fadeStartedAt = SystemClock.elapsedRealtime()
                    exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                    exo.prepare()
                    exo.play()
                    ensureRampLoop()
                }.onFailure {
                    _unavailable.value = "Playback unavailable"
                    releasePlayer()
                }
            }
        }
    }

    fun stop() {
        playbackScope.launch {
            startJob?.cancel()
            startJob = null
            speechWatchdog?.cancel()
            speechWatchdog = null
            rampJob?.cancel()
            rampJob = null
            ducking.cancel()
            externallyDucked = false
            focusInterrupted = false
            releasePlayer()
            applyState()
        }
    }

    private fun releasePlayer() {
        val previous = player
        player = null
        previous?.release()
        _playing.value = false
    }

    fun pause() { playbackScope.launch { player?.pause() } }
    fun play() { playbackScope.launch { player?.play() } }
    fun setVolume(volume: Float) {
        playbackScope.launch { baseVolume = volume.coerceIn(0f, 1f); applyState() }
    }
    fun setExternalDuck(ducked: Boolean) {
        playbackScope.launch { externallyDucked = ducked; applyState() }
    }

    fun onSpeechStarted(estimatedMillis: Long) {
        playbackScope.launch {
            ducking.beginDuck()
            ensureRampLoop()
            speechWatchdog?.cancel()
            speechWatchdog = playbackScope.launch {
                // Also covers engines that never send completion callbacks.
                delay(estimatedMillis.coerceAtLeast(0L) + SPEECH_WATCHDOG_SLACK_MS)
                ducking.release()
            }
        }
    }

    fun onSpeechFinished() {
        playbackScope.launch {
            speechWatchdog?.cancel()
            speechWatchdog = null
            ducking.release()
            ensureRampLoop()
        }
    }

    fun onCue(millis: Long) {
        playbackScope.launch {
            ducking.duckFor(millis)
            ensureRampLoop()
        }
    }

    fun previewDuck(millis: Long = 1_600L) = onCue(millis)

    /** Runs even without a local track: Spotify and other players need cue-scoped focus too. */
    private fun ensureRampLoop() {
        applyState()
        if (rampJob?.isActive == true) return
        rampJob = playbackScope.launch {
            do {
                applyState()
                delay(RAMP_STEP_MS)
            } while (player != null || ducking.isDucking())
            applyState()
        }
    }

    private fun applyState() {
        val state = ducking.stateAt()
        _duckState.value = state
        if (state.isDucked && !focusAttempted) {
            focusAttempted = true
            focus.request()
        } else if (!state.isDucked && focusAttempted && !focusInterrupted) {
            focus.abandon()
            focusAttempted = false
            externallyDucked = false
        }
        val fade = ((SystemClock.elapsedRealtime() - fadeStartedAt).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
        val external = if (externallyDucked) EXTERNAL_DUCK_SCALE else 1f
        // Fade-in samples the current duck multiplier, never a stale full-volume target.
        player?.volume = (baseVolume * state.scale * external * fade).coerceIn(0f, 1f)
    }

    companion object {
        const val RAMP_STEP_MS = 40L
        const val FADE_IN_MS = 320L
        const val SPEECH_WATCHDOG_SLACK_MS = 1_500L
        const val EXTERNAL_DUCK_SCALE = 0.4f
    }
}
