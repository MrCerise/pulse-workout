package com.pulse.engine

/**
 * PULSE running system — pure race math shared by the player, the tracker and the tests.
 *
 * The engine never talks to a GPS: the Android layer feeds it location fixes and moving time, and
 * everything here — distance accumulation, pace/speed conversion and the user-facing formatting —
 * is deterministic arithmetic that a unit test can pin down.
 */

/** Where the current race numbers came from. */
enum class RaceSource {
    /** Measured from real GPS fixes. */
    GPS,
    /** Projected from the plan's target pace and the session's moving time (no fix available). */
    ESTIMATED,
}

/** One accepted location fix. */
data class RacePoint(
    val latitude: Double,
    val longitude: Double,
    /** Fix accuracy in metres, as reported by the platform. */
    val accuracyMeters: Float,
)

/**
 * A live read-out of a running session: what the player shows as race info.
 *
 * @param distanceMeters metres covered so far (GPS-measured or projected)
 * @param movingMillis time the timer was actually running (excludes pauses)
 * @param speedMps current speed in metres per second
 * @param targetDistanceMeters the race distance, or null for an open-ended run
 * @param racePaceSecondsPerKm the plan's planned pace, or null when none was set
 */
data class RaceInfo(
    val source: RaceSource,
    val distanceMeters: Double,
    val movingMillis: Long,
    val speedMps: Double,
    val targetDistanceMeters: Double? = null,
    val racePaceSecondsPerKm: Int? = null,
    /** True once a measured run has taken at least one GPS fix. */
    val hasFix: Boolean = false,
) {
    /** Metres still to cover, or null when the run has no target distance. */
    val remainingMeters: Double?
        get() = targetDistanceMeters?.let { (it - distanceMeters).coerceAtLeast(0.0) }

    /** True once the target distance has been covered. */
    val finished: Boolean
        get() = targetDistanceMeters != null && distanceMeters >= targetDistanceMeters

    /** Current pace in seconds per kilometre, or null when effectively stopped. */
    val paceSecondsPerKm: Double?
        get() = if (speedMps >= RaceMath.MIN_MEANINGFUL_SPEED_MPS) 1000.0 / speedMps else null

    /** Average speed over the moving time, in metres per second. */
    val averageSpeedMps: Double
        get() = if (movingMillis > 0L) distanceMeters / (movingMillis / 1000.0) else speedMps

    /**
     * Milliseconds until the target distance is reached at the current average speed,
     * or null when there is no target, no speed, or the run is already finished.
     */
    val etaMillis: Long?
        get() {
            val remaining = remainingMeters ?: return null
            if (finished || remaining <= 0.0) return 0L
            val speed = averageSpeedMps
            if (speed < RaceMath.MIN_MEANINGFUL_SPEED_MPS) return null
            return (remaining / speed * 1000.0).toLong()
        }
}

object RaceMath {

    /** Below this speed a pace/ETA would read as nonsense (or divide by zero). */
    const val MIN_MEANINGFUL_SPEED_MPS = 0.27 // ≈ 1 km/h

    /** Fixes with worse accuracy than this are ignored — they jitter more than a runner moves. */
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 50f

    /**
     * Largest distance one fix gap may explain. Anything above is a GPS jump (tunnel exit, tall
     * building) rather than movement: at world-record pace a runner covers ≈ 7 m/s.
     */
    const val MAX_PLAUSIBLE_STEP_METERS = 120.0

    /** Fallback planned pace when a run has no pace set and no fix: 6:00 /km. */
    const val DEFAULT_PACE_SECONDS_PER_KM = 360

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Great-circle distance between two fixes, in metres. */
    fun haversineMeters(from: RacePoint, to: RacePoint): Double {
        val latDelta = Math.toRadians(to.latitude - from.latitude)
        val lonDelta = Math.toRadians(to.longitude - from.longitude)
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val a = kotlin.math.sin(latDelta / 2) * kotlin.math.sin(latDelta / 2) +
            kotlin.math.cos(fromLat) * kotlin.math.cos(toLat) *
            kotlin.math.sin(lonDelta / 2) * kotlin.math.sin(lonDelta / 2)
        return 2 * EARTH_RADIUS_METERS * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Distance a new fix contributes, in metres. The first fix contributes nothing (it only seeds
     * the tracker); inaccurate fixes and implausible jumps contribute nothing, so a momentary GPS
     * glitch cannot inflate the race distance. The tracker seeds its previous point with the first
     * fix whose accuracy is acceptable, so an opening bad fix is simply skipped.
     */
    fun stepMeters(previous: RacePoint?, next: RacePoint): Double {
        if (previous == null) return 0.0
        if (next.accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) return 0.0
        val step = haversineMeters(previous, next)
        return if (step > MAX_PLAUSIBLE_STEP_METERS) 0.0 else step
    }

    /** Distance covered at [paceSecondsPerKm] over [movingMillis] — the no-GPS projection. */
    fun projectedDistanceMeters(movingMillis: Long, paceSecondsPerKm: Int): Double {
        if (movingMillis <= 0L || paceSecondsPerKm <= 0) return 0.0
        return movingMillis / 1000.0 / paceSecondsPerKm * 1000.0
    }

    /** `10.2` — speed in km/h, one decimal. */
    fun formatSpeedKph(speedMps: Double): String = "%.1f".format(speedMps * 3.6)

    /** `3.24` — distance in kilometres, two decimals below 10 km and one above. */
    fun formatDistanceKm(meters: Double): String =
        if (meters < 10_000.0) "%.2f".format(meters / 1000.0) else "%.1f".format(meters / 1000.0)

    /** `5:30` — minutes:seconds per kilometre; `h:mm:ss` above an hour; `--:--` when stopped. */
    fun formatPace(secondsPerKm: Double?): String {
        if (secondsPerKm == null || !secondsPerKm.isFinite() || secondsPerKm <= 0.0) return "--:--"
        val totalSeconds = kotlin.math.round(secondsPerKm).toLong()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** `10:04` — projected finish delay as `h:mm:ss` / `m:ss`, or `--:--` when unknown. */
    fun formatEta(etaMillis: Long?): String =
        if (etaMillis == null) "--:--" else formatDuration(etaMillis)
}
