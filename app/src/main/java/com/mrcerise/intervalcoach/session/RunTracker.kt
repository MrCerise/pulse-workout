package com.mrcerise.intervalcoach.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.pulse.engine.RaceInfo
import com.pulse.engine.RaceMath
import com.pulse.engine.RacePoint
import com.pulse.engine.RaceSource
import com.pulse.engine.WorkoutPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The running system's live measurement: GPS-backed speed and distance for a running workout.
 *
 * Honest by construction:
 *  - real fixes are measured ([RaceSource.GPS]); a treadmill run without permission or a fix is
 *    projected from the plan's target pace ([RaceSource.ESTIMATED]) and labelled as such;
 *  - pauses stop both the distance and the moving clock — a coffee light is not part of the race;
 *  - inaccurate fixes and GPS jumps are dropped by [RaceMath], so a glitch cannot inflate the
 *    distance;
 *  - when the tracker stops, [finalInfo] freezes the last read-out for the session summary.
 *
 * Every mutation is confined to the main looper (location callbacks arrive there, and the ticker
 * posts there), so the counters have one thread and no locks. The tracker holds no wakelock and no
 * service of its own: [WorkoutService] keeps the process alive and promotes its foreground type to
 * include location while this is running.
 */
class RunTracker internal constructor(
    private val locationSource: RunLocationSource,
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val elapsedRealtime: () -> Long,
) {
    constructor(context: Context, scope: CoroutineScope) : this(
        AndroidRunLocationSource(context), scope, Dispatchers.Main.immediate, SystemClock::elapsedRealtime,
    )

    private val _raceInfo = MutableStateFlow<RaceInfo?>(null)
    val raceInfo: StateFlow<RaceInfo?> = _raceInfo.asStateFlow()
    private val _trackingStatus = MutableStateFlow(RunTrackingStatus.INACTIVE)
    val trackingStatus: StateFlow<RunTrackingStatus> = _trackingStatus.asStateFlow()

    /** The last read-out, frozen when the session ends — what the summary screen shows. */
    var finalInfo: RaceInfo? = null
        private set

    private val routePoints = mutableListOf<RacePoint>()
    /** GPS route taken (accepted fixes only), frozen with [finalInfo]. */
    var finalRoute: List<RacePoint> = emptyList()
        private set
    val route: List<RacePoint> get() = routePoints.toList()

    /** True between [start] and [stop]: the tracker wants location updates. */
    var isTracking: Boolean = false
        private set

    /** True once at least one usable GPS fix has been accepted this session. */
    var hasFix: Boolean = false
        private set

    private var targetDistanceMeters: Double? = null
    private var racePaceSecondsPerKm: Int = RaceMath.DEFAULT_PACE_SECONDS_PER_KM

    private var paused = true
    private var movingMillis = 0L
    private var lastTickAt = 0L
    private var distanceMeters = 0.0
    private var lastPoint: RacePoint? = null
    private var speedMps = 0.0
    private var lastFixMillis: Long? = null
    private var startedAt = 0L
    private var lastAccessCheck = 0L
    private var lastAccess: RunLocationAccess? = null
    private var subscribed = false
    private var generation = 0L

    private var tickerJob: Job? = null

    /** Starts tracking a run described by [plan]. Safe to call without location permission. */
    fun start(plan: WorkoutPlan) = onMain {
        stopInternal()
        isTracking = true
        finalInfo = null
        finalRoute = emptyList()
        routePoints.clear()
        hasFix = false
        paused = true
        movingMillis = 0L
        startedAt = elapsedRealtime()
        lastTickAt = startedAt
        distanceMeters = 0.0
        lastPoint = null
        speedMps = 0.0
        lastFixMillis = null
        lastAccess = null
        subscribed = false
        targetDistanceMeters = plan.raceDistanceMeters?.takeIf { it > 0.0 }
        racePaceSecondsPerKm = plan.racePaceSecondsPerKm?.takeIf { it > 0 } ?: RaceMath.DEFAULT_PACE_SECONDS_PER_KM
        publish()
        refreshAccess(force = true)
        tickerJob = scope.launch(mainDispatcher) {
            while (isActive && isTracking) {
                delay(1_000L)
                onMain { tick() }
            }
        }
    }

    /** Stops tracking and freezes [finalInfo] for the summary. */
    fun stop() = onMain { stopInternal() }

    /** Returning from Android settings or retrying a failed subscription does not reset a run. */
    fun refreshLocationAccess() = onMain {
        if (isTracking) refreshAccess(force = true)
    }

    /** Pausing the timer pauses the race: no distance, no moving time, no stale speed. */
    fun setPaused(value: Boolean) = onMain {
        if (paused == value) return@onMain
        advanceMovingClock()
        paused = value
        if (value) {
            // Movement while paused is not race movement: reseed at the next fix.
            lastPoint = null
            speedMps = 0.0
        } else {
            lastPoint = null
            lastTickAt = elapsedRealtime()
        }
        publish()
    }

    companion object {
        /** Whether the app may read the device's location right now. */
        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        /** New speed evidence blends with the previous estimate. */
        private const val SPEED_SMOOTHING = 0.7f

        /** Without fresh fixes, decay the shown speed toward stopped each second. */
        private const val SPEED_DECAY_PER_TICK = 0.92
        private const val ACCESS_RECHECK_MILLIS = 5_000L
        private const val MAX_FIX_AGE_MILLIS = 15_000L
        private const val SIGNAL_TIMEOUT_MILLIS = 30_000L
        private const val MAX_RUNNING_SPEED_MPS = 15.0
    }

    // -------------------------------------------------------------------------------------------
    // Internals (main thread only)
    // -------------------------------------------------------------------------------------------

    private fun onMain(block: () -> Unit) {
        scope.launch(mainDispatcher) { block() }
    }

    private fun stopInternal() {
        if (!isTracking) return
        tickerJob?.cancel()
        tickerJob = null
        generation++
        locationSource.stop()
        subscribed = false
        advanceMovingClock()
        publish()
        finalInfo = _raceInfo.value
        finalRoute = routePoints.toList()
        isTracking = false
        _trackingStatus.value = RunTrackingStatus.INACTIVE
    }

    private fun refreshAccess(force: Boolean) {
        lastAccessCheck = elapsedRealtime()
        val access = locationSource.access()
        if (!force && access == lastAccess && subscribed && !locationSource.providersChanged()) return
        generation++
        val requestGeneration = generation
        locationSource.stop()
        subscribed = false
        lastPoint = null
        lastAccess = access
        _trackingStatus.value = when (access) {
            RunLocationAccess.DENIED -> RunTrackingStatus.PERMISSION_REQUIRED
            RunLocationAccess.APPROXIMATE -> RunTrackingStatus.PRECISE_LOCATION_REQUIRED
            RunLocationAccess.DISABLED -> RunTrackingStatus.LOCATION_DISABLED
            RunLocationAccess.UNAVAILABLE -> RunTrackingStatus.UNAVAILABLE
            RunLocationAccess.AVAILABLE -> RunTrackingStatus.SEARCHING
        }
        if (access == RunLocationAccess.AVAILABLE) {
            subscribed = locationSource.start { fix ->
                onMain { if (isTracking && generation == requestGeneration) onLocation(fix) }
            }
            if (!subscribed) _trackingStatus.value = RunTrackingStatus.UNAVAILABLE
        }
    }

    private fun onLocation(fix: RunLocationFix) {
        val now = elapsedRealtime()
        val point = fix.point
        if (now - fix.elapsedRealtimeMillis !in 0L..MAX_FIX_AGE_MILLIS || !RaceMath.isUsablePoint(point)) return
        val previousFixAt = lastFixMillis
        if (previousFixAt != null && fix.elapsedRealtimeMillis <= previousFixAt) return
        // Never bridge a paused session, a lost signal or an old warm-start position.
        val previous = lastPoint.takeIf {
            !paused && previousFixAt != null && now - previousFixAt < SIGNAL_TIMEOUT_MILLIS
        }
        val step = RaceMath.stepMeters(previous, point)
        if (previous != null) {
            val dt = (fix.elapsedRealtimeMillis - previousFixAt!!) / 1_000.0
            // A rejected jump must not become the seed for the next measurement.
            if (RaceMath.haversineMeters(previous, point) > RaceMath.MAX_PLAUSIBLE_STEP_METERS ||
                dt <= 0.0 || step / dt > MAX_RUNNING_SPEED_MPS) return
            distanceMeters += step
            val sampled = fix.speedMps?.takeIf { it.isFinite() && it in 0.0..MAX_RUNNING_SPEED_MPS } ?: step / dt
            speedMps = SPEED_SMOOTHING * speedMps + (1 - SPEED_SMOOTHING) * sampled
        } else {
            speedMps = 0.0
        }
        if (!paused && fix.elapsedRealtimeMillis >= startedAt) {
            if (routePoints.lastOrNull() != point) routePoints.add(point)
            lastPoint = point
        } else {
            lastPoint = null
        }
        hasFix = true
        lastFixMillis = fix.elapsedRealtimeMillis
        _trackingStatus.value = RunTrackingStatus.TRACKING
        publish()
    }

    private fun advanceMovingClock() {
        val now = elapsedRealtime()
        if (!paused && isTracking) {
            movingMillis += (now - lastTickAt).coerceAtLeast(0L)
        }
        lastTickAt = now
    }

    private fun tick() {
        if (!isTracking) return
        advanceMovingClock()
        val now = elapsedRealtime()
        if (now - lastAccessCheck >= ACCESS_RECHECK_MILLIS) refreshAccess(force = false)
        if (subscribed && lastAccess == RunLocationAccess.AVAILABLE) {
            _trackingStatus.value = when {
                now - (lastFixMillis ?: startedAt) >= SIGNAL_TIMEOUT_MILLIS -> RunTrackingStatus.SIGNAL_LOST
                hasFix -> RunTrackingStatus.TRACKING
                else -> RunTrackingStatus.SEARCHING
            }
        }
        when {
            paused -> Unit
            !hasFix -> speedMps = 1000.0 / racePaceSecondsPerKm
            else -> speedMps *= SPEED_DECAY_PER_TICK
        }
        publish()
    }

    private fun publish() {
        if (!isTracking) return
        val measured = hasFix
        val distance = if (measured) {
            distanceMeters
        } else {
            RaceMath.projectedDistanceMeters(movingMillis, racePaceSecondsPerKm)
        }
        _raceInfo.value = RaceInfo(
            source = if (measured) RaceSource.GPS else RaceSource.ESTIMATED,
            distanceMeters = distance,
            movingMillis = movingMillis,
            speedMps = when {
                paused -> 0.0
                measured -> speedMps
                else -> 1000.0 / racePaceSecondsPerKm
            },
            targetDistanceMeters = targetDistanceMeters,
            racePaceSecondsPerKm = racePaceSecondsPerKm,
            hasFix = measured,
        )
    }
}
