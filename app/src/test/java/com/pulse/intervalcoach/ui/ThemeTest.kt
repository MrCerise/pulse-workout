package com.pulse.intervalcoach.ui

import androidx.compose.ui.graphics.Color
import com.pulse.intervalcoach.ui.theme.PulseMotion
import com.pulse.intervalcoach.ui.theme.ThemeMode
import com.pulse.intervalcoach.ui.theme.contrastRatio
import com.pulse.intervalcoach.ui.theme.isDark
import com.pulse.intervalcoach.ui.theme.pulseColors
import com.pulse.intervalcoach.ui.theme.withAccent
import org.junit.Assert.*
import org.junit.Test

class ThemeTest {
    @Test fun `system theme resolves to the device appearance for the shell toggle`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertTrue(ThemeMode.TRUE_BLACK.isDark(systemDark = false))
    }

    @Test fun `wallpaper accents supply a legible action pair and matching tint`() {
        listOf(false, true).forEach { dark ->
            val base = pulseColors(dark = dark, trueBlack = false, highContrast = false)
            val candidate = if (dark) Color(0xFFB8C8FF) else Color(0xFF174EA6)
            val result = base.withAccent(candidate)
            assertEquals(candidate, result.accent)
            assertEquals(candidate, result.actionFill)
            assertNotEquals(base.accentTint, result.accentTint)
            listOf(result.background, result.surface, result.surfaceRaised, result.accentTint).forEach {
                assertTrue(contrastRatio(result.accent, it) >= 4.5)
            }
            assertTrue(contrastRatio(result.actionText, result.actionFill) >= 4.5)
            assertEquals(base.work, result.work)
            assertEquals(base.rest, result.rest)
        }
    }

    @Test fun `low contrast wallpaper colors fall back to the tested palette`() {
        val base = pulseColors(dark = true, trueBlack = false, highContrast = false)
        assertEquals(base, base.withAccent(base.surface))
    }

    @Test fun `high contrast ignores dynamic accent and OLED canvas stays black`() {
        val contrast = pulseColors(dark = true, trueBlack = true, highContrast = true)
        assertEquals(contrast, contrast.withAccent(Color.Cyan))
        assertEquals(Color.Black, contrast.background)
        assertEquals(Color.Black, contrast.nav)
    }

    @Test fun `reduced motion collapses every interaction duration`() {
        val motion = PulseMotion(reduced = true)
        assertEquals(listOf(0, 0, 0, 0, 0), listOf(motion.fast(), motion.standard(), motion.slow(), motion.emphasis(), motion.duration(220)))
        // The Momentum beat: feedback stays sub-350 ms so the app still answers instantly.
        assertTrue(PulseMotion(reduced = false).standard() in 150..350)
    }
}
