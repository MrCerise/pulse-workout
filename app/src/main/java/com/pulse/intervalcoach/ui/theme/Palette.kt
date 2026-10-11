package com.pulse.intervalcoach.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw material for the "Prism" design system — the Compose mirror of `res/values/colors.xml`.
 *
 * Nothing outside this package should read [PulsePalette] directly: screens and components consume
 * the semantic tokens on [PulseColors] instead, so a hex value can only ever change in one place.
 * `scripts/contrast_check.py` asserts that every constant below is byte-identical to its resource
 * counterpart; drift is a build failure, not a code-review mistake.
 */
object PulsePalette {

    // ---------------------------------------------------------------- Ink (dark, default)

    val Canvas = Color(0xFF0A0912)
    val Nav = Color(0xFF120F1C)
    val Surface = Color(0xFF171423)
    val SurfaceRaised = Color(0xFF1F1B2E)
    val SurfaceOverlay = Color(0xFF272137)
    val SurfaceHover = Color(0xFF2E2842)
    val Border = Color(0xFF2B2540)
    val Outline = Color(0xFF8B7FB8)

    val TextPrimary = Color(0xFFF6F4FF)
    val TextSecondary = Color(0xFFB9B2D4)
    val TextMuted = Color(0xFF948DB4)
    val TextDisabled = Color(0xFF5E5780)

    /**
     * The two-stop accent. [Accent] (violet) carries text, selection and focus; [AccentAlt] (cyan)
     * is its gradient partner and is only drawn *behind* or *beside* it, never as a label on its
     * own — which is why it is declared here but never reaches a text token.
     */
    val Accent = Color(0xFFA78BFA)
    val AccentAlt = Color(0xFF22D3EE)
    val AccentTint = Color(0xFF241E3C)

    val Work = Color(0xFF4ADE80)
    val WorkTint = Color(0xFF122B1E)
    val Rest = Color(0xFF38BDF8)
    val RestTint = Color(0xFF0E2431)
    val Prepare = Color(0xFFFBBF24)
    val PrepareTint = Color(0xFF2E2411)
    val Cooldown = Color(0xFFC084FC)
    val CooldownTint = Color(0xFF251A38)
    val Destructive = Color(0xFFFB7185)
    val DestructiveTint = Color(0xFF331419)
    val NeutralFill = Color(0xFF2E2842)

    val ActionFill = Color(0xFFF6F4FF)
    val ActionText = Color(0xFF0A0912)
    val OnAccent = Color(0xFF0A0912)

    // ---------------------------------------------------------------- Paper (light)

    val LightCanvas = Color(0xFFF5F4FA)
    val LightNav = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceRaised = Color(0xFFF1EEF9)
    val LightSurfaceOverlay = Color(0xFFFFFFFF)
    val LightSurfaceHover = Color(0xFFE8E4F4)
    val LightBorder = Color(0xFFE3DEF0)
    val LightOutline = Color(0xFF6D6690)

    val LightTextPrimary = Color(0xFF1A1626)
    val LightTextSecondary = Color(0xFF574F74)
    val LightTextMuted = Color(0xFF6A6288)
    val LightTextDisabled = Color(0xFFA29BC0)

    val LightAccent = Color(0xFF6D28D9)
    val LightAccentAlt = Color(0xFF0E7490)
    val LightAccentTint = Color(0xFFEDE9FE)

    val LightWork = Color(0xFF147536)
    val LightWorkTint = Color(0xFFE7F5EB)
    val LightRest = Color(0xFF0369A1)
    val LightRestTint = Color(0xFFE4F1F8)
    val LightPrepare = Color(0xFF965B06)
    val LightPrepareTint = Color(0xFFFAF1DE)
    val LightCooldown = Color(0xFF7E22CE)
    val LightCooldownTint = Color(0xFFF3EAFD)
    val LightDestructive = Color(0xFFBE123C)
    val LightDestructiveTint = Color(0xFFFCE9EE)
    val LightNeutralFill = Color(0xFFEAE6F6)

    val LightActionFill = Color(0xFF1A1626)
    val LightActionText = Color(0xFFFFFFFF)
    val LightOnAccent = Color(0xFFFFFFFF)

    // ---------------------------------------------------------------- Void (OLED)

    val BlackCanvas = Color(0xFF000000)
    val BlackNav = Color(0xFF000000)
    val BlackSurface = Color(0xFF0B0A14)
    val BlackSurfaceRaised = Color(0xFF14121F)
    val BlackSurfaceOverlay = Color(0xFF1B1826)
    val BlackSurfaceHover = Color(0xFF221F30)
    val BlackBorder = Color(0xFF1D1A2A)

    // ---------------------------------------------------------------- Derived (Compose only)
    // These are not resource tokens: they are computed or conditional values that no XML surface
    // needs (the widget renders its own colours from the tokens above). They still live here rather
    // than in a screen, so "which grey is the groove behind a progress ring" has one answer.

    /** Track behind progress rings, sliders and chart baselines. Visible, never competing. */
    val Track = Color(0xFF332D4A)
    val LightTrack = Color(0xFFDAD5EB)

    /** The "increased contrast" switch: same hues, lifted, and text pushed to the extremes. */
    val HighContrastText = Color(0xFFFFFFFF)
    val HighContrastTextSecondary = Color(0xFFDCD7EF)
    val HighContrastOutline = Color(0xFFADA4D2)
    val HighContrastWork = Color(0xFF7DF0A6)
    val HighContrastRest = Color(0xFF7DD8FC)
    val HighContrastPrepare = Color(0xFFFDD97A)
    val HighContrastCooldown = Color(0xFFDCBEFF)
    val HighContrastAccent = Color(0xFFC9B6FF)

    // ---------------------------------------------------------------- Category hues
    // One hue per workout family, used by the category registry so a HIIT card and a mobility card
    // are told apart at a glance without the chrome turning into a box of crayons. These are the
    // *display* hues the registry maps to; like every other colour here they are declared once and
    // consumed through `PulseColors`.

    val CatCardio = Color(0xFFFB7185)
    val CatCardioTint = Color(0xFF331419)
    val CatConditioning = Color(0xFFFBBF24)
    val CatConditioningTint = Color(0xFF2E2411)
    val CatStrength = Color(0xFFA78BFA)
    val CatStrengthTint = Color(0xFF241E3C)
    val CatMobility = Color(0xFF38BDF8)
    val CatMobilityTint = Color(0xFF0E2431)
    val CatMind = Color(0xFF4ADE80)
    val CatMindTint = Color(0xFF122B1E)
    val CatTools = Color(0xFF94A3B8)
    val CatToolsTint = Color(0xFF1F2637)

    val LightCatCardio = Color(0xFFBE123C)
    val LightCatCardioTint = Color(0xFFFCE9EE)
    val LightCatConditioning = Color(0xFF965B06)
    val LightCatConditioningTint = Color(0xFFFAF1DE)
    val LightCatStrength = Color(0xFF6D28D9)
    val LightCatStrengthTint = Color(0xFFEDE9FE)
    val LightCatMobility = Color(0xFF0369A1)
    val LightCatMobilityTint = Color(0xFFE4F1F8)
    val LightCatMind = Color(0xFF147536)
    val LightCatMindTint = Color(0xFFE7F5EB)
    val LightCatTools = Color(0xFF475569)
    val LightCatToolsTint = Color(0xFFE9EDF3)
}
