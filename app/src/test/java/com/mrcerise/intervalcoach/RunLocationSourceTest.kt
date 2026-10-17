package com.mrcerise.intervalcoach

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.mrcerise.intervalcoach.session.AndroidRunLocationSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class RunLocationSourceTest {
    @Test fun `fused and network are subscribed even when GPS is enabled`() {
        mockkStatic(Looper::class, ContextCompat::class, LocationManagerCompat::class)
        try {
            val looper = mockk<Looper>()
            every { Looper.getMainLooper() } returns looper
            val manager = mockk<LocationManager>(relaxed = true)
            val context = mockk<Context>()
            every { context.applicationContext } returns context
            every { context.getSystemService(Context.LOCATION_SERVICE) } returns manager
            every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) } returns PackageManager.PERMISSION_GRANTED
            every { LocationManagerCompat.isLocationEnabled(manager) } returns true
            every { manager.isProviderEnabled(any()) } returns true
            every { manager.getLastKnownLocation(any()) } returns null
            val source = AndroidRunLocationSource(context)
            assertTrue(source.start({}))
            listOf("fused", "gps", "network").forEach { provider ->
                verify(exactly = 1) { manager.requestLocationUpdates(provider, 1_000L, 0f, any<LocationListener>(), looper) }
            }
            source.stop()
            verify(exactly = 3) { manager.removeUpdates(any<LocationListener>()) }
        } finally {
            unmockkStatic(Looper::class, ContextCompat::class, LocationManagerCompat::class)
        }
    }
}
