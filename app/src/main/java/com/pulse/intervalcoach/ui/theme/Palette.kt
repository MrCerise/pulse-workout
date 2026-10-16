package com.pulse.intervalcoach.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw material for the "Momentum" design system — the Compose mirror of `res/values/colors.xml`.
 *
 * Nothing outside this package should read [PulsePalette] directly: screens and components consume
 * the semantic tokens on [PulseColors] instead, so a hex value can only ever change in one place.
 * `scripts/contrast_check.py` asserts that every constant below is byte-identical to its resource
 * counterpart; drift is a build failure, not a code-review mistake.
 */
object PulsePalette {

    // ---------------------------------------------------------------- Ink (dark, default)
    // A neutral charcoal ramp. Five steps, close together: hierarchy comes from a fill step plus a
    // hairline, never from a brightness jump. Not pure black, which is what keeps long sessions
    // comfortable and lets the one shadow on screen mean something.

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

    /** Slider, ring and chart grooves. Visible, never competing with the fill. */
    val Track = Color(0xFF3A3E45)

    /**
     * The accent. [Accent] (volt lime) carries text, selection and focus; [AccentAlt] is its
     * lighter partner and is only drawn as a graphic — a bar, a sweep, a rank badge — never as a
     * label on its own, which is why it is measured at 3:1 rather than 4.5:1.
     */
    val Accent = Color(0xFFA3E635)
    val AccentAlt = Color(0xFFD9F99D)
    val AccentTint = Color(0xFF232B10)

    /** The brand fill and the ink measured against it — one pair, one place. */
    val Brand = Color(0xFFA3E635)
    val OnBrand = Color(0xFF101216)

    /** The ink panel: dark in **both** themes, so a hero card is a feature panel, not a card. */
    val HeroSurface = Color(0xFF131519)
    val HeroText = Color(0xFFF4F6F8)
    val HeroMuted = Color(0xFFA8B0BA)

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

    /** Informational state — a different hue from the accent on purpose. */
    val Info = Color(0xFF7CB7FF)
    val InfoTint = Color(0xFF232B36)

    val ActionFill = Color(0xFFA3E635)
    val ActionText = Color(0xFF101216)
    val OnAccent = Color(0xFF101216)

    // ---------------------------------------------------------------- Paper (light)
    // The same roles with inverted polarity. Chrome is white; the canvas is one step off it, so a
    // card edge is legible without a shadow.

    val LightCanvas = Color(0xFFF4F6F8)
    val LightNav = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceRaised = Color(0xFFF7F8FA)
    val LightSurfaceOverlay = Color(0xFFFFFFFF)
    val LightSurfaceHover = Color(0xFFEBEEF1)
    val LightSurfaceSunken = Color(0xFFEFF2F5)
    val LightBorder = Color(0xFFE3E7EB)
    val LightBorderStrong = Color(0xFFCBD2D9)
    val LightOutline = Color(0xFF6B7280)

    val LightTextPrimary = Color(0xFF1B1E21)
    val LightTextSecondary = Color(0xFF4B5157)
    val LightTextMuted = Color(0xFF666C74)
    val LightTextDisabled = Color(0xFFA6ACB3)
    val LightTrack = Color(0xFFE3E5E8)

    /**
     * A deeper orange than the brand fill: this one carries *text* (links, selected labels, the
     * focus ring) and has to clear 4.5:1 on white. The brand fill carries ink and stays #F38020.
     */
    val LightAccent = Color(0xFF3F6212)
    val LightAccentAlt = Color(0xFF365314)
    val LightAccentTint = Color(0xFFF1F5E1)
    val LightBrand = Color(0xFFA3E635)
    val LightOnBrand = Color(0xFF101216)

    val LightHeroSurface = Color(0xFF131519)
    val LightHeroText = Color(0xFFF4F6F8)
    val LightHeroMuted = Color(0xFFA8B0BA)

    val LightWork = Color(0xFF12603C)
    val LightWorkTint = Color(0xFFE7EFEC)
    val LightRest = Color(0xFF0A56A8)
    val LightRestTint = Color(0xFFE6EEF6)
    val LightPrepare = Color(0xFF7A5100)
    val LightPrepareTint = Color(0xFFF2EEE6)
    val LightCooldown = Color(0xFF5B21B6)
    val LightCooldownTint = Color(0xFFEFE9F8)
    val LightDestructive = Color(0xFFA8291F)
    val LightDestructiveTint = Color(0xFFF6EAE9)
    val LightNeutralFill = Color(0xFFE9EBEE)
    val LightInfo = Color(0xFF0B57C2)
    val LightInfoTint = Color(0xFFE7EEF9)

    val LightActionFill = Color(0xFFA3E635)
    val LightActionText = Color(0xFF101216)
    val LightOnAccent = Color(0xFF101216)

    // ---------------------------------------------------------------- Void (OLED)

    val BlackCanvas = Color(0xFF000000)
    val BlackNav = Color(0xFF000000)
    val BlackSurface = Color(0xFF0B0C0E)
    val BlackSurfaceRaised = Color(0xFF131518)
    val BlackSurfaceOverlay = Color(0xFF17191D)
    val BlackSurfaceHover = Color(0xFF1E2024)
    val BlackSurfaceSunken = Color(0xFF0B0C0E)
    val BlackBorder = Color(0xFF23262A)

    // ---------------------------------------------------------------- Derived (Compose only)
    // Not resource tokens: computed or conditional values that no XML surface needs (the widget
    // renders its own colours from the tokens above). They still live here rather than in a screen,
    // so "which grey is the groove behind a progress ring" has exactly one answer.

    /** The "increased contrast" switch: same hues, lifted, and text pushed to the extremes. */
    val HighContrastText = Color(0xFFFFFFFF)
    val HighContrastTextSecondary = Color(0xFFE4E7EA)
    val HighContrastOutline = Color(0xFFB7BDC4)
    val HighContrastWork = Color(0xFF7FDCA9)
    val HighContrastRest = Color(0xFF9CC8FF)
    val HighContrastPrepare = Color(0xFFFBD37E)
    val HighContrastCooldown = Color(0xFFCDB4FF)
    val HighContrastAccent = Color(0xFFD9F99D)

    // ---------------------------------------------------------------- Category hues
    // One hue per workout family, used by the category registry so a HIIT card and a mobility card
    // are told apart at a glance without the chrome turning into a box of crayons. Each ships with
    // the pre-composited tint it is drawn on.

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

    val LightCatCardio = Color(0xFFA3123A)
    val LightCatCardioTint = Color(0xFFF6E7EB)
    val LightCatConditioning = Color(0xFF7A5100)
    val LightCatConditioningTint = Color(0xFFF2EEE6)
    val LightCatStrength = Color(0xFF5B21B6)
    val LightCatStrengthTint = Color(0xFFEFE9F8)
    val LightCatMobility = Color(0xFF0A5C8A)
    val LightCatMobilityTint = Color(0xFFE6EFF3)
    val LightCatMind = Color(0xFF106544)
    val LightCatMindTint = Color(0xFFE7F0EC)
    val LightCatTools = Color(0xFF3F4753)
    val LightCatToolsTint = Color(0xFFECEDEE)
}
