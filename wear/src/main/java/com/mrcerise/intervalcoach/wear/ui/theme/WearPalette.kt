package com.mrcerise.intervalcoach.wear.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The "Momentum" palette on the watch — a mirror of the phone app's `PulsePalette`.
 *
 * A Wear APK cannot share Gradle modules with an application module, so the values are mirrored
 * here instead of referenced. `scripts/contrast_check.py` asserts that every constant below is
 * byte-identical to the same-named constant in the phone's `ui/theme/Palette.kt`, so the two can
 * never drift apart: the watch always ships the exact colours the phone's contrast report
 * (`docs/CONTRAST.md`) was measured against.
 *
 * Watches are AMOLED and dark by default, so only the dark ("Ink"), OLED ("Void") and
 * high-contrast ramps are mirrored; the phone's light "Paper" theme has no watch equivalent and a
 * `LIGHT` phone setting is treated as system-following on the watch.
 */
object PulseWearPalette {

    // ---------------------------------------------------------------- Ink (dark, default)

    val Canvas = Color(0xFF101216)
    val Nav = Color(0xFF14161B)
    val Surface = Color(0xFF181B20)
    val SurfaceRaised = Color(0xFF1E2126)
    val SurfaceOverlay = Color(0xFF23262C)
    val SurfaceHover = Color(0xFF2A2E34)
    val SurfaceSunken = Color(0xFF121418)

    val Border = Color(0xFF2E323A)
    val BorderStrong = Color(0xFF3B4048)
    val Outline = Color(0xFF8A9099)

    val TextPrimary = Color(0xFFF4F6F8)
    val TextSecondary = Color(0xFFC3C7CC)
    val TextMuted = Color(0xFF9CA2A9)
    val TextDisabled = Color(0xFF6E747C)

    /** Progress ring grooves. */
    val Track = Color(0xFF3A3E45)

    /** The accent (lime) and its lighter partner. */
    val Accent = Color(0xFFA3E635)
    val AccentAlt = Color(0xFFD9F99D)
    val AccentTint = Color(0xFF232B10)

    /** The brand fill and the ink measured against it. */
    val Brand = Color(0xFFA3E635)
    val OnBrand = Color(0xFF101216)

    val Work = Color(0xFF4CC38A)
    val WorkTint = Color(0xFF1D2D28)
    val Rest = Color(0xFF6CABF8)
    val RestTint = Color(0xFF212A36)
    val Prepare = Color(0xFFF5B544)
    val PrepareTint = Color(0xFF322B20)
    val Cooldown = Color(0xFFB18CFA)
    val CooldownTint = Color(0xFF292636)
    val Destructive = Color(0xFFF87171)
    val DestructiveTint = Color(0xFF322325)
    val NeutralFill = Color(0xFF2E3138)

    val ActionFill = Color(0xFFA3E635)
    val ActionText = Color(0xFF101216)
    val OnAccent = Color(0xFF101216)

    // ---------------------------------------------------------------- Category hues (dark)

    val CatCardio = Color(0xFFF0607A)
    val CatCardioTint = Color(0xFF312126)
    val CatConditioning = Color(0xFFF5B544)
    val CatConditioningTint = Color(0xFF322B20)
    val CatStrength = Color(0xFFB18CFA)
    val CatStrengthTint = Color(0xFF292636)
    val CatMobility = Color(0xFF5BA8F5)
    val CatMobilityTint = Color(0xFF1F2935)
    val CatMind = Color(0xFF4CC38A)
    val CatMindTint = Color(0xFF1D2D28)
    val CatTools = Color(0xFF98A2B3)
    val CatToolsTint = Color(0xFF26292D)

    // ---------------------------------------------------------------- Void (OLED)

    val BlackCanvas = Color(0xFF000000)
    val BlackNav = Color(0xFF000000)
    val BlackSurface = Color(0xFF0B0C0E)
    val BlackSurfaceRaised = Color(0xFF131518)
    val BlackSurfaceOverlay = Color(0xFF17191D)
    val BlackSurfaceHover = Color(0xFF1E2024)
    val BlackSurfaceSunken = Color(0xFF0B0C0E)
    val BlackBorder = Color(0xFF23262A)

    // ---------------------------------------------------------------- High contrast (dark)

    val HighContrastText = Color(0xFFFFFFFF)
    val HighContrastTextSecondary = Color(0xFFE4E7EA)
    val HighContrastOutline = Color(0xFFB7BDC4)
    val HighContrastWork = Color(0xFF7FDCA9)
    val HighContrastRest = Color(0xFF9CC8FF)
    val HighContrastPrepare = Color(0xFFFBD37E)
    val HighContrastCooldown = Color(0xFFCDB4FF)
    val HighContrastAccent = Color(0xFFD9F99D)
}
