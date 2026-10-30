package com.mrcerise.intervalcoach

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.IntentCompat
import androidx.health.connect.client.PermissionController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrcerise.intervalcoach.data.AppLanguage
import com.mrcerise.intervalcoach.locale.Localized
import com.mrcerise.intervalcoach.session.SessionSummaryData
import com.mrcerise.intervalcoach.ui.summary.SummaryImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** These regressions need real Android contexts; JVM framework stubs cannot expose either failure. */
@RunWith(AndroidJUnit4::class)
class LocalizedActivityFlowsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun healthPermissionLauncherSurvivesLanguageChanges() {
        val language = mutableStateOf(AppLanguage.SYSTEM)
        var owner: ActivityResultRegistryOwner? = null
        lateinit var context: Context
        lateinit var resources: Resources
        var configurationLocale: String? = null
        var label: String? = null
        compose.setContent {
            Localized(language.value) {
                val currentContext = LocalContext.current
                val currentResources = LocalResources.current
                val currentConfiguration = LocalConfiguration.current
                val currentLabel = stringResource(R.string.nav_today)
                val currentOwner = LocalActivityResultRegistryOwner.current
                // This is the registration that used to crash as soon as Health opened.
                rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract(),
                ) { }
                SideEffect {
                    context = currentContext
                    resources = currentResources
                    configurationLocale = currentConfiguration.locales[0].toLanguageTag()
                    label = currentLabel
                    owner = currentOwner
                }
            }
        }

        (AppLanguage.entries + AppLanguage.SYSTEM).forEach { selected ->
            compose.runOnIdle { language.value = selected }
            compose.runOnIdle {
                assertSame(compose.activity, owner)
                assertSame(context.resources, resources)
                assertEquals(context.resources.configuration.locales[0].toLanguageTag(), configurationLocale)
                assertEquals(context.getString(R.string.nav_today), label)
                val locale = selected.tag
                if (locale == null) {
                    assertSame(compose.activity, context)
                } else {
                    assertEquals(locale, context.resources.configuration.locales[0].toLanguageTag())
                }
            }
        }
    }

    @Test
    fun localizedContextCanShareAReadableWorkoutImage() {
        lateinit var context: Context
        compose.setContent {
            Localized(AppLanguage.FRENCH) {
                val currentContext = LocalContext.current
                SideEffect { context = currentContext }
            }
        }
        compose.waitForIdle()

        val bitmap = SummaryImage.render(context, SessionSummaryData(
            sessionId = "share-regression",
            workoutId = "workout",
            planName = "Entraînement",
            status = "COMPLETED",
            activeMillis = 60_000L,
            wallMillis = 65_000L,
            completedIntervals = 2,
            totalIntervals = 2,
            skippedIntervals = 0,
            roundsLogged = 1,
            stoppedEarly = false,
        ))
        val imageUri = try {
            SummaryImage.writeToCache(context, bitmap)
        } finally {
            bitmap.recycle()
        }

        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri).use { stream ->
                assertNotNull(stream)
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            assertEquals(SummaryImage.WIDTH, bounds.outWidth)
            assertEquals(SummaryImage.HEIGHT, bounds.outHeight)

            val chooser = SummaryImage.shareIntent(context, "Entraînement", imageUri)
            val send = requireNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("image/png", send.type)
            assertEquals(imageUri, IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, android.net.Uri::class.java))
            listOf(send, chooser).forEach { intent ->
                assertEquals(imageUri, intent.clipData?.getItemAt(0)?.uri)
                assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            }

            // Intercept the chooser so the test opens no external app or share UI.
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_CHOOSER), null, true)
            instrumentation.addMonitor(monitor)
            try {
                compose.runOnIdle { context.startActivity(chooser) }
                assertEquals(1, monitor.hits)
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        } finally {
            context.contentResolver.delete(imageUri, null, null)
        }
    }
}
