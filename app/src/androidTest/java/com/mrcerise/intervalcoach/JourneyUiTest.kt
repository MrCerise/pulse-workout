package com.mrcerise.intervalcoach

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrcerise.intervalcoach.data.AppLanguage
import com.mrcerise.intervalcoach.journey.*
import com.mrcerise.intervalcoach.locale.Localized
import com.mrcerise.intervalcoach.ui.theme.PulseTheme
import com.mrcerise.intervalcoach.ui.theme.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun setupOffersAllLevelsAndSavesAnOptionalRhythm() {
        var saved: JourneyLevel? = null
        var target = 0
        var days = 0
        var reminder: Int? = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Localized(AppLanguage.ENGLISH) {
                PulseTheme(ThemeMode.LIGHT, false, false, true) {
                    JourneySetup(JourneyState(null, emptyList(), emptyList(), emptyList()), {},
                        { level, count, mask, minute ->
                            saved = level; target = count; days = mask; reminder = minute
                        }, false)
                }
            }
        }
        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        compose.onNodeWithText("2").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Advanced").performScrollTo().assertIsSelected()
        compose.onNodeWithText("2").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Start my journey").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(JourneyLevel.ADVANCED, saved)
            assertEquals(2, target)
            assertEquals(2, Integer.bitCount(days))
            assertNull(reminder)
        }
    }

    @Test fun frenchSetupRemainsUsableWithLargeTextAndDarkHighContrast() {
        var saved = false
        compose.setContent {
            Localized(AppLanguage.FRENCH) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                    PulseTheme(ThemeMode.DARK, false, true, true) {
                        JourneySetup(JourneyState(null, emptyList(), emptyList(), emptyList()), {},
                            { _, _, _, _ -> saved = true }, false)
                    }
                }
            }
        }
        compose.onNodeWithText("Débutant").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Commencer mon parcours").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(saved) }
    }

    @Test fun previewExplainsVariationsAndRequiresExplicitStart() {
        var started = false
        compose.setContent {
            Localized(AppLanguage.ENGLISH) {
                PulseTheme(ThemeMode.LIGHT, false, false, true) {
                    JourneyPreview(JourneyCatalogue.nodes.first(), false, {}, { started = true })
                }
            }
        }
        compose.onNodeWithText("Easier: reduce the depth.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertFalse(started) }
        compose.onNodeWithText("Start session").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(started) }
    }
}
