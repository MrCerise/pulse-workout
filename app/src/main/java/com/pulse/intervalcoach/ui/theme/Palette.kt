package com.pulse.intervalcoach.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw material for the "Graphite" design system — the Compose mirror of `res/values/colors.xml`.
 *
 * Nothing outside this package should read [PulsePalette] directly: screens and components consume
 * the semantic tokens on [PulseColors] instead, so a hex value can only ever change in one place.
 * `scripts/contrast_check.py` asserts that every constant below is byte-identical to its resource
 * counterpart; drift is a build failure, not a code-review mistake.
 */
object PulsePalette {

    // ---------------------------------------------------------------- Cockpit (dark, default)

    val Canvas = Color(0xFF08090A)
    val Nav = Color(0xFF0B0C0E)
    val Surface = Color(0xFF101114)
    val SurfaceRaised = Color(0xFF16181B)
    val SurfaceOverlay = Color(0xFF1D1F23)
    val SurfaceHover = Color(0xFF222429)
    val Border = Color(0xFF24272C)
    val Outline = Color(0xFF5F6470)

    val TextPrimary = Color(0xFFF7F8F8)
    val TextSecondary = Color(0xFF9BA1AE)
    val TextMuted = Color(0xFF7A818F)
    val TextDisabled = Color(0xFF4B5058)

    val Accent = Color(0xFF7C8CFF)
    val AccentTint = Color(0xFF1F2235)

    val Work = Color(0xFF4CC38A)
    val WorkTint = Color(0xFF182A25)
    val Rest = Color(0xFF5BC8E8)
    val RestTint = Color(0xFF1A2B32)
    val Prepare = Color(0xFFE3B341)
    val PrepareTint = Color(0xFF2E281A)
    val Cooldown = Color(0xFFA78BFA)
    val CooldownTint = Color(0xFF252234)
    val Destructive = Color(0xFFF2555A)
    val DestructiveTint = Color(0xFF301B1E)
    val NeutralFill = Color(0xFF222429)

    val ActionFill = Color(0xFFF7F8F8)
    val ActionText = Color(0xFF08090A)
    val OnAccent = Color(0xFF0A0B0D)

    // ---------------------------------------------------------------- Daylight (light)

    val LightCanvas = Color(0xFFF7F8FA)
    val LightNav = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceRaised = Color(0xFFF3F4F7)
    val LightSurfaceOverlay = Color(0xFFFFFFFF)
    val LightSurfaceHover = Color(0xFFECEEF2)
    val LightBorder = Color(0xFFE6E8EC)
    val LightOutline = Color(0xFF767C88)

    val LightTextPrimary = Color(0xFF14161A)
    val LightTextSecondary = Color(0xFF545B68)
    val LightTextMuted = Color(0xFF656B77)
    val LightTextDisabled = Color(0xFFA3A8B2)

    val LightAccent = Color(0xFF3D4FD6)
    val LightAccentTint = Color(0xFFECEDFB)

    val LightWork = Color(0xFF177245)
    val LightWorkTint = Color(0xFFE8F1EC)
    val LightRest = Color(0xFF0B6E85)
    val LightRestTint = Color(0xFFE7F0F3)
    val LightPrepare = Color(0xFF8A5A00)
    val LightPrepareTint = Color(0xFFF3EEE6)
    val LightCooldown = Color(0xFF5B3FD1)
    val LightCooldownTint = Color(0xFFEFECFA)
    val LightDestructive = Color(0xFFBE2F3C)
    val LightDestructiveTint = Color(0xFFF8EAEC)
    val LightNeutralFill = Color(0xFFEDEFF3)

    val LightActionFill = Color(0xFF14161A)
    val LightActionText = Color(0xFFFFFFFF)
    val LightOnAccent = Color(0xFFFFFFFF)

    // ---------------------------------------------------------------- True black (OLED)

    val BlackCanvas = Color(0xFF000000)
    val BlackNav = Color(0xFF000000)
    val BlackSurface = Color(0xFF0A0A0C)
    val BlackSurfaceRaised = Color(0xFF131316)
    val BlackSurfaceOverlay = Color(0xFF191A1D)
    val BlackSurfaceHover = Color(0xFF1E1F23)
    val BlackBorder = Color(0xFF1C1D21)

    // ---------------------------------------------------------------- Derived (Compose only)
    // These are not resource tokens: they are computed or conditional values that no XML surface
    // needs (the widget renders its own colours from the tokens above). They still live here rather
    // than in a screen, so "which grey is the groove behind a progress ring" has one answer.

    /** Track behind progress rings, sliders and chart baselines. Visible, never competing. */
    val Track = Color(0xFF30343A)
    val LightTrack = Color(0xFFD7DAE0)

    /** The "increased contrast" switch: same hues, lifted, and text pushed to the extremes. */
    val HighContrastText = Color(0xFFFFFFFF)
    val HighContrastTextSecondary = Color(0xFFC9CEDA)
    val HighContrastOutline = Color(0xFF8A90A0)
    val HighContrastWork = Color(0xFF6FE0A6)
    val HighContrastRest = Color(0xFF83DCF7)
    val HighContrastPrepare = Color(0xFFF3CD74)
    val HighContrastCooldown = Color(0xFFC6B1FF)
}
