package com.pulse.intervalcoach.audio

import kotlin.math.abs

/**
 * Audio ducking: how far background audio drops while the coach speaks, and how the volume travels
 * down and back up again.
 *
 * The whole thing is a pure state machine over a monotonic clock — no Android types, no player —
 * so the exact volume curve is covered by unit tests instead of being discovered mid-workout. The
 * player ([com.pulse.intervalcoach.session.MusicController]) samples it on a short interval and
 * writes the result to the media player.
 *
 * @property enabled master switch. When false the scale is always 1.0 and nothing is ducked.
 * @property level volume multiplier applied while ducked: `0.30` means music sits at 30 % of the
 *   user's volume, `0.0` mutes it completely, `1.0` is effectively "no ducking".
 * @property fadeDownMs how long the drop takes. A hard cut at the first word sounds like a fault;
 *   ~140 ms reads as intentional without letting the coach talk over the music.
 * @property holdMs how long the duck is held after the last utterance ends, so a trailing cue or a
 *   second sentence does not make the music pump up and down.
 * @property fadeUpMs how long the recovery takes. Slower than the drop: music returning is more
 *   noticeable than music leaving.
 */
data class DuckConfig(
    val enabled: Boolean = true,
    val level: Float = DEFAULT_LEVEL,
    val fadeDownMs: Long = 140L,
    val holdMs: Long = 450L,
    val fadeUpMs: Long = 420L,
) {
    /** [level] as a usable multiplier, whatever the caller stored. */
    val clampedLevel: Float get() = level.coerceIn(0f, 1f)

    /** Time from "speech stopped" to full volume again. */
    val releaseMillis: Long get() = holdMs + fadeUpMs

    companion object {
        const val DEFAULT_LEVEL = 0.30f
    }
}

/** Where the duck currently sits, which is what the UI and the player both need to know. */
enum class DuckPhase { NONE, FADING_DOWN, HELD, FADING_UP }

/**
 * A sampled point on the ducking curve.
 *
 * @property scale multiplier for the background audio, `1.0` = untouched.
 */
data class DuckState(val phase: DuckPhase, val scale: Float) {
    val isDucked: Boolean get() = phase != DuckPhase.NONE

    companion object {
        val FULL_VOLUME = DuckState(DuckPhase.NONE, 1f)
    }
}

/**
 * Decides the background-audio volume multiplier at any instant.
 *
 * Two kinds of request feed it, and they compose:
 *  - **open-ended** ([beginDuck] / [release]): speech. It lasts as long as the engine is talking,
 *    plus a hold, and a second utterance simply extends it;
 *  - **fixed window** ([duckFor]): a cue tone, whose length is known up front. A tone that lands
 *    inside an open-ended duck never shortens it.
 *
 * Re-ducking while the volume is still climbing back starts the next drop from wherever the volume
 * actually is, so back-to-back cues ramp instead of snapping.
 *
 * Not thread-safe by contract: one owner (the playback controller) drives it, from a single
 * coroutine. The state is trivially inspectable through [stateAt], which never mutates anything.
 */
class DuckingEngine(private val nowMillis: () -> Long) {

    @Volatile
    var config: DuckConfig = DuckConfig()
        private set

    private var active = false
    private var startedAt = 0L

    /** `null` while speech is still talking; otherwise the instant the hold started counting. */
    private var releasedAt: Long? = null

    /** End of a fixed-window duck (a cue tone) that is still sounding. */
    private var windowUntil: Long? = null

    /** Volume the current drop starts from — 1.0 unless a fade-up was interrupted. */
    private var fromScale = 1f

    /** Replaces the configuration. Disabling cancels any duck in progress immediately. */
    fun update(config: DuckConfig) {
        val wasEnabled = this.config.enabled
        this.config = config
        if (!config.enabled && wasEnabled) cancel()
    }

    /** Starts (or extends) an open-ended duck: something is speaking until [release] is called. */
    fun beginDuck() {
        val now = nowMillis()
        settleIfFinished(now)
        if (!active) {
            active = true
            startedAt = now
            fromScale = 1f
        } else if (stateAt(now).phase == DuckPhase.FADING_UP) {
            // Interrupted recovery: drop again from the current volume rather than from full.
            fromScale = stateAt(now).scale
            startedAt = now
        }
        releasedAt = null
    }

    /** Ducks for a known duration, for example the length of a cue tone. */
    fun duckFor(millis: Long) {
        if (millis <= 0L) return
        val now = nowMillis()
        settleIfFinished(now)
        val until = now + millis
        if (!active) {
            active = true
            startedAt = now
            fromScale = 1f
            releasedAt = until
        } else if (releasedAt == null) {
            // Speech holds the duck open; the tone only lengthens the tail that follows it.
            windowUntil = maxOf(windowUntil ?: 0L, until)
        } else {
            // Never shorten an in-flight duck: two overlapping tones cover each other.
            releasedAt = maxOf(releasedAt!!, until)
        }
    }

    /** Speech stopped: start the hold, after which the volume climbs back. */
    fun release() {
        if (!active) return
        val now = nowMillis()
        val window = windowUntil
        windowUntil = null
        releasedAt = maxOf(now, window ?: 0L)
    }

    /**
     * Forgets a duck that has already climbed all the way back, so the next one ramps from full
     * volume instead of snapping: nothing else in the machine notices the recovery on its own.
     */
    private fun settleIfFinished(now: Long) {
        val released = releasedAt ?: return
        if (now >= released + config.holdMs + config.fadeUpMs) cancel()
    }

    /** Back to full volume at once — session ended, playback stopped, ducking switched off. */
    fun cancel() {
        active = false
        releasedAt = null
        windowUntil = null
        fromScale = 1f
    }

    /** The volume multiplier and phase at [now]. Pure: it never mutates the machine. */
    fun stateAt(now: Long = nowMillis()): DuckState {
        val current = config
        if (!current.enabled || !active) return DuckState.FULL_VOLUME
        val target = current.clampedLevel
        val downEnd = startedAt + current.fadeDownMs
        val released = releasedAt
        val upStart = if (released == null) Long.MAX_VALUE else released + current.holdMs
        val upEnd = if (upStart == Long.MAX_VALUE) Long.MAX_VALUE else upStart + current.fadeUpMs
        return when {
            now < downEnd -> DuckState(
                DuckPhase.FADING_DOWN,
                lerp(fromScale, target, progress(now, startedAt, downEnd)),
            )
            now < upStart -> DuckState(DuckPhase.HELD, target)
            now < upEnd -> DuckState(
                DuckPhase.FADING_UP,
                lerp(target, 1f, progress(now, upStart, upEnd)),
            )
            else -> DuckState.FULL_VOLUME
        }
    }

    /** True while the volume is anywhere below full — used for the player indicator. */
    fun isDucking(now: Long = nowMillis()): Boolean = stateAt(now).isDucked

    /**
     * The next instant the curve changes on its own, or `null` when nothing is scheduled — either
     * the volume has settled or open-ended speech is holding it down and only [release] moves it.
     *
     * A caller that samples on a timer can use it to skip pointless wake-ups; it also makes the
     * ramps testable without a real clock.
     */
    fun nextChangeAt(now: Long = nowMillis()): Long? {
        val current = config
        if (!current.enabled || !active) return null
        val downEnd = startedAt + current.fadeDownMs
        val released = releasedAt
        val upStart = if (released == null) Long.MAX_VALUE else released + current.holdMs
        val upEnd = if (upStart == Long.MAX_VALUE) Long.MAX_VALUE else upStart + current.fadeUpMs
        return listOf(downEnd, upStart, upEnd)
            .filter { it != Long.MAX_VALUE }
            .firstOrNull { it > now }
    }

    /** Volume to hand the player: the duck scale applied to the user's own music volume. */
    fun volumeFor(baseVolume: Float, now: Long = nowMillis()): Float =
        (baseVolume.coerceIn(0f, 1f) * stateAt(now).scale).coerceIn(0f, 1f)

    /** Whether writing [candidate] over [applied] would be audible. Avoids touching the player 20×/s. */
    fun shouldApply(applied: Float, candidate: Float): Boolean = abs(applied - candidate) > APPLY_EPSILON

    private fun progress(now: Long, from: Long, to: Long): Float {
        if (to <= from) return 1f
        return ((now - from).toFloat() / (to - from).toFloat()).coerceIn(0f, 1f)
    }

    private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

    companion object {
        /** Smallest volume step worth pushing to the player (~0.4 % of full scale). */
        const val APPLY_EPSILON = 0.004f
    }
}
