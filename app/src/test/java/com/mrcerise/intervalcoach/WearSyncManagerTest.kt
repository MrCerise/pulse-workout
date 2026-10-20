package com.mrcerise.intervalcoach

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.mrcerise.intervalcoach.wear.WearSyncManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSyncManagerTest {
    @Test fun `start registers one listener and a second start is a no-op`() {
        mockkStatic(Wearable::class)
        val job = Job()
        try {
            val messageClient = mockk<MessageClient>(relaxed = true)
            every { Wearable.getMessageClient(any<Context>()) } returns messageClient
            // The dispatcher is never advanced: only what start() registers and launches is counted.
            val scope = CoroutineScope(job + StandardTestDispatcher())
            val manager = WearSyncManager(
                context = mockk(relaxed = true),
                scope = scope,
                workouts = mockk(relaxed = true),
                sessions = mockk(relaxed = true),
                preferences = mockk(relaxed = true),
                reminders = mockk(relaxed = true),
                sessionController = mockk(relaxed = true),
                health = mockk(relaxed = true),
            )

            manager.start()
            val observers = job.children.count()
            manager.start()

            verify(exactly = 1) { messageClient.addListener(any()) }
            assertTrue(observers > 0)
            assertEquals(observers, job.children.count())
        } finally {
            job.cancel()
            unmockkStatic(Wearable::class)
        }
    }
}
