package com.pulse.intervalcoach.session

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pulse.intervalcoach.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Optional background audio chosen by the user (a playlist, an album, a podcast episode).
 *
 * PULSE never bundles or streams music itself: it plays a file the user picked through the system
 * file picker. Playback is ducked — not stopped — while the coach speaks, and released when the
 * session ends. If the file has moved or access was revoked, playback fails silently and the
 * workout continues; the Voice Studio tells the user the file is unavailable.
 */
class MusicController(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    private var player: ExoPlayer? = null

    @Volatile
    private var ducked = false

    private fun ensurePlayer(): ExoPlayer? {
        player?.let { return it }
        return runCatching {
            val exo = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ false,
                )
                repeatMode = Player.REPEAT_MODE_ALL
            }
            player = exo
            exo
        }.getOrNull()
    }

    /** Starts the user's background audio, if one is selected and still readable. */
    fun start() {
        scope.launch {
            val current = prefs.current()
            val uri = current.musicUri ?: return@launch
            val exo = ensurePlayer() ?: return@launch
            runCatching {
                exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                exo.prepare()
                exo.volume = if (ducked) duckedVolume(current.musicVolume) else current.musicVolume
                exo.play()
            }
        }
    }

    fun setDucked(ducked: Boolean) {
        this.ducked = ducked
        val exo = player ?: return
        scope.launch {
            val current = prefs.current()
            runCatching { exo.volume = if (ducked) duckedVolume(current.musicVolume) else current.musicVolume }
        }
    }

    fun setVolume(volume: Float) {
        val exo = player ?: return
        runCatching { exo.volume = if (ducked) duckedVolume(volume) else volume }
    }

    fun pause() = runCatching { player?.pause() }.let { }
    fun play() = runCatching { player?.play() }.let { }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        ducked = false
    }

    private fun duckedVolume(base: Float) = (base * 0.25f).coerceIn(0f, 1f)
}
