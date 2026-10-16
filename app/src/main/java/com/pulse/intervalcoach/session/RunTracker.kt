package com.pulse.intervalcoach.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.pulse.engine.RaceInfo
import com.pulse.engine.RaceMath
import com.pulse.engine.RacePoint
import com.pulse.engine.RaceSource
import com.pulse.engine.WorkoutPlan
import kotlinx.coroutines.CoroutineScope
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
class RunTracker(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _raceInfo = MutableStateFlow<RaceInfo?>(null)
    val raceInfo: StateFlow<RaceInfo?> = _raceInfo.asStateFlow()

    /** The last read-out, frozen when the session ends — what the summary screen shows. */
    var finalInfo: RaceInfo? = null
        private set

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
    private var lastLocationNanos = 0L

    private var tickerJob: Job? = null
    private var locationListener: LocationListener? = null

    /** Starts tracking a run described by [plan]. Safe to call without location permission. */
    fun start(plan: WorkoutPlan) = onMain {
        stopInternal()
        isTracking = true
        finalInfo = null
        hasFix = false
        paused = true
        movingMillis = 0L
        lastTickAt = SystemClock.elapsedRealtime()
        distanceMeters = 0.0
        lastPoint = null
        speedMps = 0.0
        lastLocationNanos = 0L
        targetDistanceMeters = plan.raceDistanceMeters?.takeIf { it > 0.0 }
        racePaceSecondsPerKm = plan.racePaceSecondsPerKm?.takeIf { it > 0 } ?: RaceMath.DEFAULT_PACE_SECONDS_PER_KM
        publish()
        requestLocationUpdates()
        tickerJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive && isTracking) {
                delay(1_000L)
                onMain { tick() }
            }
        }
    }

    /** Stops tracking and freezes [finalInfo] for the summary. */
    fun stop() = onMain { stopInternal() }

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
            lastTickAt = SystemClock.elapsedRealtime()
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
    }

    // -------------------------------------------------------------------------------------------
    // Internals (main thread only)
    // -------------------------------------------------------------------------------------------

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun stopInternal() {
        if (!isTracking) return
        tickerJob?.cancel()
        tickerJob = null
        locationListener?.let { runCatching { locationManager.removeUpdates(it) } }
        locationListener = null
        advanceMovingClock()
        publish()
        finalInfo = _raceInfo.value
        isTracking = false
    }

    private fun requestLocationUpdates() {
        if (!hasLocationPermission(appContext)) return
        val provider = pickProvider() ?: return
        val listener = LocationListener { location -> onMain { onLocation(location) } }
        val ok = runCatching {
            locationManager.requestLocationUpdates(
                provider,
                /* minTimeMillis = */ 1_000L,
                /* minDistanceMeters = */ 0.5f,
                listener,
                Looper.getMainLooper(),
            )
        }.isSuccess
        if (ok) {
            locationListener = listener
            runCatching { locationManager.getLastKnownLocation(provider) }
                .getOrNull()
                ?.let { onLocation(it) }
        }
    }

    private fun pickProvider(): String? {
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return candidates.firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private fun onLocation(location: Location) {
        if (!isTracking || paused) return
        val point = RacePoint(location.latitude, location.longitude, location.accuracy)
        val previous = lastPoint
        val step = RaceMath.stepMeters(previous, point)
        if (previous != null) {
            distanceMeters += step
            if (step > 0.0) {
                val sampled = if (location.hasSpeed() && location.speed >= 0f) {
                    location.speed.toDouble()
                } else {
                    val dt = (location.elapsedRealtimeNanos - lastLocationNanos) / 1_000_000_000.0
                    if (dt > 0.5) step / dt else null
                }
                if (sampled != null) {
                    speedMps = SPEED_SMOOTHING * speedMps + (1 - SPEED_SMOOTHING) * sampled
                }
            }
        }
        // Seed or reseed only with fixes good enough to trust as a position.
        if (point.accuracyMeters <= RaceMath.MAX_ACCEPTABLE_ACCURACY_METERS) {
            lastPoint = point
            if (!hasFix) {
                hasFix = true
                speedMps = 0.0
            }
        }
        lastLocationNanos = location.elapsedRealtimeNanos
        publish()
    }

    private fun advanceMovingClock() {
        val now = SystemClock.elapsedRealtime()
        if (!paused && isTracking) {
            movingMillis += (now - lastTickAt).coerceAtLeast(0L)
        }
        lastTickAt = now
    }

    private fun tick() {
        if (!isTracking) return
        advanceMovingClock()
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
