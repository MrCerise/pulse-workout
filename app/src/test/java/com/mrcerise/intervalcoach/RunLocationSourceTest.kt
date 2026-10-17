package com.mrcerise.intervalcoach

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
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
        mockkStatic(Looper::class)
        try {
            val looper = mockk<Looper>()
            every { Looper.getMainLooper() } returns looper
            val manager = mockk<LocationManager>(relaxed = true)
            val context = mockk<Context>()
            every { context.applicationContext } returns context
            every { context.getSystemService(Context.LOCATION_SERVICE) } returns manager
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
            unmockkStatic(Looper::class)
        }
    }
}
