package com.mrcerise.intervalcoach.session

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import com.pulse.engine.RacePoint

/** User-visible acquisition state, independent of whether a past position was recorded. */
enum class RunTrackingStatus {
    INACTIVE, SEARCHING, TRACKING, SIGNAL_LOST,
    PERMISSION_REQUIRED, PRECISE_LOCATION_REQUIRED, LOCATION_DISABLED, UNAVAILABLE,
}

internal enum class RunLocationAccess { AVAILABLE, DENIED, APPROXIMATE, DISABLED, UNAVAILABLE }

internal data class RunLocationFix(
    val point: RacePoint,
    val elapsedRealtimeMillis: Long,
    val speedMps: Double? = null,
)

/** The platform boundary is replaceable so acquisition/recovery can be tested without a GPS. */
internal interface RunLocationSource {
    fun access(): RunLocationAccess
    fun start(onFix: (RunLocationFix) -> Unit): Boolean
    fun providersChanged(): Boolean = false
    fun stop()
}

internal class AndroidRunLocationSource(context: Context) : RunLocationSource {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val listeners = mutableListOf<LocationListenerCompat>()
    private val subscribedProviders = mutableSetOf<String>()

    override fun access(): RunLocationAccess {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return if (RunTracker.hasLocationPermission(context)) RunLocationAccess.APPROXIMATE else RunLocationAccess.DENIED
        }
        val manager = manager ?: return RunLocationAccess.UNAVAILABLE
        return when {
            !LocationManagerCompat.isLocationEnabled(manager) -> RunLocationAccess.DISABLED
            enabledProviders().isEmpty() -> RunLocationAccess.UNAVAILABLE
            else -> RunLocationAccess.AVAILABLE
        }
    }

    @SuppressLint("MissingPermission") // access() checks precise access; revocation races are caught below.
    override fun start(onFix: (RunLocationFix) -> Unit): Boolean {
        stop()
        if (access() != RunLocationAccess.AVAILABLE) return false
        val manager = manager ?: return false
        // A cold satellite fix can take a long time indoors. Listen to fused/network fixes as
        // well as GNSS; the tracker accepts only fresh positions accurate enough for a run.
        enabledProviders().forEach { provider ->
            val listener = object : LocationListenerCompat {
                override fun onLocationChanged(location: Location) { onFix(location.toFix()) }
            }
            runCatching {
                manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
            }.onSuccess {
                listeners.add(listener)
                subscribedProviders.add(provider)
                // Warm starts are useful, but cached data is still checked for age by RunTracker.
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let { onFix(it.toFix()) }
            }.onFailure {
                Log.w("RunLocationSource", "Could not subscribe to location provider $provider", it)
            }
        }
        return listeners.isNotEmpty()
    }

    override fun providersChanged(): Boolean = enabledProviders().toSet() != subscribedProviders

    override fun stop() {
        val oldListeners = listeners.toList()
        listeners.clear()
        subscribedProviders.clear()
        oldListeners.forEach { listener ->
            runCatching { manager?.removeUpdates(listener) }
                .onFailure { Log.w("RunLocationSource", "Could not remove location listener", it) }
        }
    }

    private fun enabledProviders(): List<String> {
        val manager = manager ?: return emptyList()
        return listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
    }

    private fun Location.toFix() = RunLocationFix(
        point = RacePoint(latitude, longitude, if (hasAccuracy()) accuracy else Float.POSITIVE_INFINITY),
        elapsedRealtimeMillis = elapsedRealtimeNanos / 1_000_000L,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
    )
}
