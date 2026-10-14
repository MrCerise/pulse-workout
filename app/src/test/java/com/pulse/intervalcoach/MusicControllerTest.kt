package com.pulse.intervalcoach

import android.content.Context
import android.os.SystemClock
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.coEvery
import io.mockk.verify
import com.pulse.intervalcoach.audio.DuckPhase
import com.pulse.intervalcoach.data.PreferencesRepository
import com.pulse.intervalcoach.data.UserPreferences
import com.pulse.intervalcoach.session.MusicController
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicControllerTest {
    @Test fun `ducking runs without a local file and stop cancels every pending release`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        mockkStatic(SystemClock::class)
        try {
            every { SystemClock.elapsedRealtime() } answers { testScheduler.currentTime }
            val context = mockk<Context>()
            every { context.getSystemService(Context.AUDIO_SERVICE) } returns null
            val music = MusicController(context, mockk<PreferencesRepository>(), backgroundScope)
            music.configure(UserPreferences(duckLevel = 0.2f))
            music.onSpeechStarted(1000)
            runCurrent()
            advanceTimeBy(200)
            runCurrent()
            assertEquals(DuckPhase.HELD, music.duckState.value.phase)
            assertEquals(0.2f, music.duckState.value.scale, 0.001f)
            music.onSpeechFinished()
            runCurrent()
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(DuckPhase.NONE, music.duckState.value.phase)
            music.onSpeechStarted(2000)
            runCurrent()
            music.stop()
            runCurrent()
            advanceTimeBy(5000)
            runCurrent()
            assertEquals(DuckPhase.NONE, music.duckState.value.phase)
            assertFalse(music.playing.value)
        } finally {
            unmockkStatic(SystemClock::class)
            Dispatchers.resetMain()
        }
    }

    @Test fun `disabling ducking restores state immediately and watchdog recovers missing callbacks`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        mockkStatic(SystemClock::class)
        try {
            every { SystemClock.elapsedRealtime() } answers { testScheduler.currentTime }
            val context = mockk<Context>()
            every { context.getSystemService(Context.AUDIO_SERVICE) } returns null
            val music = MusicController(context, mockk<PreferencesRepository>(), backgroundScope)
            music.onSpeechStarted(100)
            runCurrent()
            advanceTimeBy(200)
            runCurrent()
            assertTrue(music.duckState.value.isDucked)
            music.configure(UserPreferences(duckMusicDuringCues = false))
            runCurrent()
            assertFalse(music.duckState.value.isDucked)
            music.configure(UserPreferences())
            music.onSpeechStarted(100)
            runCurrent()
            advanceTimeBy(3000)
            runCurrent()
            assertFalse(music.duckState.value.isDucked)
            music.stop()
            runCurrent()
        } finally {
            unmockkStatic(SystemClock::class)
            Dispatchers.resetMain()
        }
    }
    @Test fun `player volume follows ducking during startup and returns to the chosen level`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        mockkStatic(SystemClock::class)
        mockkStatic(Uri::class)
        try {
            every { SystemClock.elapsedRealtime() } answers { testScheduler.currentTime }
            every { Uri.parse(any()) } returns mockk<Uri>(relaxed = true)
            val context = mockk<Context>()
            every { context.getSystemService(Context.AUDIO_SERVICE) } returns null
            val preferences = mockk<PreferencesRepository>()
            coEvery { preferences.current() } returns UserPreferences(musicUri = "content://test/song", musicVolume = 0.6f, duckLevel = 0.3f)
            val player = mockk<ExoPlayer>(relaxed = true)
            val volumes = mutableListOf<Float>()
            every { player.volume = capture(volumes) } returns Unit
            val music = MusicController(context, preferences, backgroundScope, createPlayer = { player })
            music.onSpeechStarted(2000)
            music.start()
            runCurrent()
            advanceTimeBy(400)
            runCurrent()
            assertNull(music.unavailable.value)
            assertTrue(volumes.isNotEmpty())
            assertEquals(0.18f, volumes.last(), 0.001f)
            assertTrue("Fade-in must not override the duck", volumes.all { it <= 0.181f })
            music.onSpeechFinished()
            runCurrent()
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(0.6f, volumes.last(), 0.001f)
            music.stop()
            runCurrent()
            verify(exactly = 1) { player.release() }
            val writesAfterStop = volumes.size
            advanceTimeBy(4000)
            runCurrent()
            assertEquals(writesAfterStop, volumes.size)
        } finally {
            unmockkStatic(Uri::class)
            unmockkStatic(SystemClock::class)
            Dispatchers.resetMain()
        }
    }
}
