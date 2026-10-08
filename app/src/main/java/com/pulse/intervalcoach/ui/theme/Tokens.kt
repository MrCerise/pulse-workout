package com.pulse.intervalcoach.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.engine.PhaseKind

/**
 * The resolved colour set for one theme. Every field is a token, not an ad-hoc colour: screens read
 * `LocalPulseColors.current`, never a raw hex value, which is what keeps eleven destinations looking
 * like one product.
 *
 * Fields are grouped the way the design system is documented (surfaces → text → borders → phase →
 * state) so a new screen can find what it needs without inventing a colour.
 */
@Immutable
data class PulseColors(
    /** True when the resolved theme is a dark one (dark or OLED). */
    val isDark: Boolean,
    /** User-facing "true black" (OLED) switch. */
    val trueBlack: Boolean = false,
    /** User-facing "increased contrast" switch. */
    val highContrast: Boolean = false,

    // --- Surfaces --------------------------------------------------------------------------------
    /** App canvas: the surface behind everything. */
    val background: Color,
    /** Navigation bars and side chrome. */
    val nav: Color,
    /** Cards, panels, list surfaces. */
    val surface: Color,
    /** Inputs, chips, stat tiles, secondary buttons. */
    val surfaceRaised: Color,
    /** Dialogs, menus, tooltips, snackbars. */
    val surfaceOverlay: Color,
    /** Hover / pressed fill drawn on top of [surfaceRaised]. */
    val surfaceHover: Color,

    // --- Text ------------------------------------------------------------------------------------
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,

    // --- Lines -----------------------------------------------------------------------------------
    /** Decorative hairline separator. Quiet by design. */
    val border: Color,
    /** Meaningful control boundary: inputs, focus rings, toggles. Reaches 3:1 (WCAG 1.4.11). */
    val outline: Color,

    // --- Brand / interactive ---------------------------------------------------------------------
    /** The single interactive accent: links, focus rings, selection, informational state. */
    val accent: Color,
    /** Pre-composited [accent] tint used as a container fill. */
    val accentTint: Color,

    // --- Interval phases -------------------------------------------------------------------------
    val work: Color,
    val rest: Color,
    val prepare: Color,
    val cooldown: Color,
    val destructive: Color,

    // --- Solid action surface --------------------------------------------------------------------
    /** Fill of the primary action (light block in dark themes, dark block in light ones). */
    val actionFill: Color,
    /** Label colour on [actionFill]. */
    val actionText: Color,

    /** Text/icon colour drawn on top of a saturated phase fill (dots, live badges). */
    val onAccent: Color,
) {
    /** The colour that identifies an interval phase. */
    fun phaseColor(kind: PhaseKind): Color = when (kind) {
        PhaseKind.WORK, PhaseKind.WARM_UP -> work
        PhaseKind.REST -> rest
        PhaseKind.TRANSITION -> rest
        PhaseKind.PREPARE -> prepare
        PhaseKind.COOLDOWN -> cooldown
        PhaseKind.CUSTOM -> textSecondary
    }

    /**
     * Container fill for a phase chip. These are the pre-composited tint values that
     * `docs/CONTRAST.md` measures, so a chip's label contrast is a verified number rather than an
     * estimate of an alpha blend.
     */
    fun phaseTint(kind: PhaseKind): Color = when (kind) {
        PhaseKind.WORK, PhaseKind.WARM_UP -> if (isDark) PulsePalette.WorkTint else PulsePalette.LightWorkTint
        PhaseKind.REST, PhaseKind.TRANSITION -> if (isDark) PulsePalette.RestTint else PulsePalette.LightRestTint
        PhaseKind.PREPARE -> if (isDark) PulsePalette.PrepareTint else PulsePalette.LightPrepareTint
        PhaseKind.COOLDOWN -> if (isDark) PulsePalette.CooldownTint else PulsePalette.LightCooldownTint
        PhaseKind.CUSTOM -> if (isDark) PulsePalette.NeutralFill else PulsePalette.LightNeutralFill
    }

    // --- Semantic aliases --------------------------------------------------------------------------
    // States reuse the phase hues on purpose: one palette, one meaning per hue, no second family of
    // "status colours" that could drift away from the measured pairings.

    val success: Color get() = work
    val warning: Color get() = prepare
    val error: Color get() = destructive
    val info: Color get() = accent

    /**
     * Pre-composited container fills that pair with the semantic aliases above: success (work
     * green), warning (prepare amber) and danger (red). These are the exact values the contrast
     * report measures, so a banner's label contrast is a verified number, not an alpha estimate.
     */
    val successTint: Color get() = phaseTint(PhaseKind.WORK)
    val warningTint: Color get() = phaseTint(PhaseKind.PREPARE)
    val dangerTint: Color
        get() = if (isDark) PulsePalette.DestructiveTint else PulsePalette.LightDestructiveTint

    /** Neutral pill fill for non-phase metadata (workout type, counts, "starter"). */
    val neutralFill: Color get() = surfaceHover

    /** Text colour that sits on [neutralFill]. */
    val onNeutralFill: Color get() = textPrimary

    /** Track behind rings, sliders and chart baselines. Visible, never competing with the fill. */
    val track: Color get() = if (isDark) PulsePalette.Track else PulsePalette.LightTrack

    /** Subtle accent wash for a selected navigation item or an active row. */
    val selection: Color get() = accentTint
}

val LocalPulseColors = staticCompositionLocalOf {
    pulseColors(dark = true, trueBlack = false, highContrast = false)
}

/**
 * Spacing, sizing and radius scale. Every screen composes from these steps only — a stray `13.dp`
 * in a screen is what makes an interface look hand-assembled, so there is no reason to write one.
 *
 * The base unit is 4 dp; the scale is 4 / 8 / 12 / 16 / 24 / 32 / 48. Sizes are picked to sit
 * between Material's 48 dp touch minimum and the compact 32–40 dp controls this design system uses
 * visually: a control can be 40 dp tall and still expose a 48 dp touch target through
 * `minimumInteractiveComponentSize`.
 */
@Immutable
data class PulseDimens(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val huge: Dp = 48.dp,

    /** Horizontal page gutter, shared by every scrolling screen. */
    val pagePadding: Dp = 16.dp,
    /** Interior padding of a card or panel. */
    val cardPadding: Dp = 16.dp,
    /** Vertical gap between sibling cards. */
    val cardGap: Dp = 12.dp,

    /** Control heights. */
    val controlHeight: Dp = 40.dp,
    val buttonHeight: Dp = 40.dp,
    val iconButtonSize: Dp = 36.dp,
    val topBarHeight: Dp = 56.dp,
    val listRowHeight: Dp = 56.dp,

    /** Minimum touch target. Never draw an interactive element smaller than this. */
    val minTouchTarget: Dp = 48.dp,

    /** Border widths. */
    val borderWidth: Dp = 1.dp,
    val focusRingWidth: Dp = 2.dp,

    // --- Radii -----------------------------------------------------------------------------------
    /** Cards, panels, dialogs and sheets. */
    val cardRadius: Dp = 12.dp,
    /** Buttons, inputs, navigation items, icon buttons. */
    val controlRadius: Dp = 8.dp,
    /** Chips, badges, small metadata pills. */
    val chipRadius: Dp = 6.dp,
    /** Fully round: avatars and the single round transport control in the player. */
    val pillRadius: Dp = 999.dp,
)

val LocalPulseDimens = staticCompositionLocalOf { PulseDimens() }

/**
 * Shape scale. Compose components read shapes from `MaterialTheme.shapes`, so the tokens are exposed
 * there too and a component can never pick a radius outside the scale.
 */
@Immutable
data class PulseShapes(
    val control: RoundedCornerShape = RoundedCornerShape(8.dp),
    val card: RoundedCornerShape = RoundedCornerShape(12.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(16.dp),
    val chip: RoundedCornerShape = RoundedCornerShape(6.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(999.dp),
)

val LocalPulseShapes = staticCompositionLocalOf { PulseShapes() }

/**
 * Motion. Durations sit in the 100–200 ms band: long enough to read as intentional, short enough
 * that a rapid sequence of interval changes never queues up behind an animation. The reduced-motion
 * switch collapses every duration to zero, because an accessibility setting that still animates is
 * not an accessibility setting.
 */
@Immutable
data class PulseMotion(val reduced: Boolean) {
    /** 120 ms — hover, press, colour and elevation feedback. */
    fun fast(): Int = if (reduced) 0 else 120

    /** 180 ms — selection, expansion, navigation transitions. */
    fun standard(): Int = if (reduced) 0 else 180

    /** Duration in ms, collapsed to 0 when the user prefers reduced motion. */
    fun duration(normal: Int): Int = if (reduced) 0 else normal
}

val LocalPulseMotion = staticCompositionLocalOf { PulseMotion(reduced = false) }
