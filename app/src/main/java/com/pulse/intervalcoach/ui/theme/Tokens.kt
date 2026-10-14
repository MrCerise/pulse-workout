package com.pulse.intervalcoach.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.engine.PhaseKind

/**
 * The six colour families a workout category can belong to.
 *
 * A family is a *colour promise*, not a taxonomy: it exists so the same hue always means the same
 * kind of training, whether it appears on a category chip, a library card or the player header. The
 * mapping from [com.pulse.engine.WorkoutType] to a family lives in the category registry.
 */
enum class CategoryHue {
    /** Running, cycling, rowing, run/walk — anything measured in heartbeats per minute. */
    CARDIO,

    /** Interval protocols: HIIT, Tabata, EMOM, AMRAP, circuits, boxing. */
    CONDITIONING,

    /** Loaded work: strength, compound blocks. */
    STRENGTH,

    /** Range, recovery and control: stretching, mobility flows. */
    MOBILITY,

    /** Breathing, focus and other low-intensity work. */
    MIND,

    /** Stopwatch, repeating cue and custom sequences — utilities rather than training styles. */
    TOOLS,
}

/**
 * The resolved colour set for one theme. Every field is a token, not an ad-hoc colour: screens read
 * `LocalPulseColors.current`, never a raw hex value, which is what keeps eighteen destinations
 * looking like one product.
 *
 * Fields are grouped the way the design system is documented (surfaces → text → lines → accent →
 * brand → hero → phase → category → state) so a new screen can find what it needs without inventing
 * a colour.
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
    /** Navigation chrome: the sidebar, the rail, the bottom bar. */
    val nav: Color,
    /** Cards, panels and table surfaces. */
    val surface: Color,
    /** Inputs, chips, stat tiles, secondary buttons. */
    val surfaceRaised: Color,
    /** One step *below* the canvas: table headers, wells, inset groups. */
    val surfaceSunken: Color,
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
    /** One step louder than [border]: the edge of a filled field, a nested divider. Decorative. */
    val borderStrong: Color,
    /** Meaningful control boundary: inputs, focus rings, toggles. Reaches 3:1 (WCAG 1.4.11). */
    val outline: Color,

    // --- Accent ----------------------------------------------------------------------------------
    /** The interactive accent: links, selection, focus rings, live data. */
    val accent: Color,
    /**
     * A lighter stop of the accent. Drawn as a *graphic* — a bar, a sweep, a rank badge — never as a
     * label, so the contrast report measures it at 3:1 rather than 4.5:1.
     */
    val accentAlt: Color,
    /** Pre-composited [accent] tint used as a container fill (selected rows, active nav items). */
    val accentTint: Color,

    // --- Brand -----------------------------------------------------------------------------------
    /** The brand fill: the primary action and the wordmark. Ink sits on it, never white. */
    val brand: Color,
    /** Ink measured against [brand]. */
    val onBrand: Color,

    // --- Hero ------------------------------------------------------------------------------------
    /**
     * The ink panel. Deliberately dark in *both* themes, so a hero card reads as a feature panel
     * rather than as one more card in the stack.
     */
    val heroSurface: Color,
    val heroText: Color,
    val heroMuted: Color,

    // --- Interval phases -------------------------------------------------------------------------
    val work: Color,
    val rest: Color,
    val prepare: Color,
    val cooldown: Color,
    val destructive: Color,

    // --- Informational state ---------------------------------------------------------------------
    /** "Here is something to read" — a different hue from "here is the thing to do". */
    val info: Color,
    val infoTint: Color,

    // --- Solid action surface --------------------------------------------------------------------
    /** Fill of the primary action (the brand block). */
    val actionFill: Color,
    /** Label colour on [actionFill]. */
    val actionText: Color,

    /** Text/icon colour drawn on top of a saturated fill (dots, badges, brand blocks). */
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
        PhaseKind.CUSTOM -> neutralFill
    }

    // --- Categories ------------------------------------------------------------------------------

    /** The hue a workout category is drawn in. */
    fun categoryColor(hue: CategoryHue): Color = when (hue) {
        CategoryHue.CARDIO -> if (isDark) PulsePalette.CatCardio else PulsePalette.LightCatCardio
        CategoryHue.CONDITIONING -> if (isDark) PulsePalette.CatConditioning else PulsePalette.LightCatConditioning
        CategoryHue.STRENGTH -> if (isDark) PulsePalette.CatStrength else PulsePalette.LightCatStrength
        CategoryHue.MOBILITY -> if (isDark) PulsePalette.CatMobility else PulsePalette.LightCatMobility
        CategoryHue.MIND -> if (isDark) PulsePalette.CatMind else PulsePalette.LightCatMind
        CategoryHue.TOOLS -> if (isDark) PulsePalette.CatTools else PulsePalette.LightCatTools
    }

    /** Pre-composited container fill for [categoryColor], measured in `docs/CONTRAST.md`. */
    fun categoryTint(hue: CategoryHue): Color = when (hue) {
        CategoryHue.CARDIO -> if (isDark) PulsePalette.CatCardioTint else PulsePalette.LightCatCardioTint
        CategoryHue.CONDITIONING -> if (isDark) PulsePalette.CatConditioningTint else PulsePalette.LightCatConditioningTint
        CategoryHue.STRENGTH -> if (isDark) PulsePalette.CatStrengthTint else PulsePalette.LightCatStrengthTint
        CategoryHue.MOBILITY -> if (isDark) PulsePalette.CatMobilityTint else PulsePalette.LightCatMobilityTint
        CategoryHue.MIND -> if (isDark) PulsePalette.CatMindTint else PulsePalette.LightCatMindTint
        CategoryHue.TOOLS -> if (isDark) PulsePalette.CatToolsTint else PulsePalette.LightCatToolsTint
    }

    // --- Semantic aliases ------------------------------------------------------------------------
    // States reuse the phase hues on purpose: one palette, one meaning per hue, no second family of
    // "status colours" that could drift away from the measured pairings.

    val success: Color get() = work
    val warning: Color get() = prepare
    val error: Color get() = destructive

    /**
     * Pre-composited container fills that pair with the semantic aliases above: success (work
     * green), warning (prepare amber) and danger (red). These are the exact values the contrast
     * report measures, so a banner's label contrast is a verified number, not an alpha estimate.
     */
    val successTint: Color get() = phaseTint(PhaseKind.WORK)
    val warningTint: Color get() = phaseTint(PhaseKind.PREPARE)
    val dangerTint: Color
        get() = if (isDark) PulsePalette.DestructiveTint else PulsePalette.LightDestructiveTint

    /** Neutral pill fill for non-phase metadata (counts, "starter", equipment). */
    val neutralFill: Color get() = if (isDark) PulsePalette.NeutralFill else PulsePalette.LightNeutralFill

    /** Text colour that sits on [neutralFill]. */
    val onNeutralFill: Color get() = textPrimary

    /** Track behind rings, sliders and chart baselines. Visible, never competing with the fill. */
    val track: Color get() = if (isDark) PulsePalette.Track else PulsePalette.LightTrack

    /** Subtle accent wash for a selected navigation item or an active row. */
    val selection: Color get() = accentTint

    /** The accent at a low alpha, for the halo behind a live ring or a focused control. */
    val glow: Color get() = accent.copy(alpha = if (isDark) 0.32f else 0.20f)

    /** Dimmer used behind sheets and dialogs. */
    val scrim: Color get() = Color.Black.copy(alpha = 0.56f)

    /**
     * Ink that stays legible on a chromatic or brand fill. Kept for the call sites that draw their
     * own block of accent colour; the components use [onBrand] for the brand pair explicitly.
     */
    val onAccentGradient: Color get() = onBrand
}

val LocalPulseColors = staticCompositionLocalOf {
    pulseColors(dark = true, trueBlack = false, highContrast = false)
}

/**
 * Spacing, sizing and radius scale. Every screen composes from these steps only — a stray `13.dp`
 * in a screen is what makes an interface look hand-assembled, so there is no reason to write one.
 *
 * The base unit is 4 dp and the ramp is deliberately tighter than a mobile-marketing layout: Signal
 * is a dashboard, and a dashboard is *dense*. A 16 dp gutter, 16 dp of card padding and 12 dp
 * between cards put a workout row, its metadata and its action on one comfortable screen without
 * scroll.
 */
@Immutable
data class PulseDimens(
    /**
     * 2 dp. Reserved for one job: stacking a caption or a hint directly under the line it belongs
     * to. It is never a section gap — if two elements are not parts of one sentence, they are
     * separated by [s] or more.
     */
    val microGap: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val huge: Dp = 32.dp,
    val massive: Dp = 48.dp,

    /** Horizontal page gutter, shared by every scrolling screen. */
    val pagePadding: Dp = 16.dp,
    /** Interior padding of a card or panel. */
    val cardPadding: Dp = 16.dp,
    /** Vertical gap between sibling cards. */
    val cardGap: Dp = 12.dp,
    /** Gap inside a cluster of chips or small controls. */
    val chipGap: Dp = 6.dp,

    // --- Reusable content metrics ---------------------------------------------------------------
    // These are not spacing: they size a specific kind of content, and a second screen that shows
    // the same kind of content must use the same number. Anything that sizes one screen's artwork
    // stays with that screen.

    /** Timeline bar used as a thumbnail inside a card or a builder preview. */
    val barHeightCompact: Dp = 8.dp,
    /** Timeline bar used as a screen's main visual. */
    val barHeightRegular: Dp = 14.dp,
    /** Activity bar chart in Progress. */
    val chartHeight: Dp = 120.dp,
    /** Minimum cell width for `GridCells.Adaptive`; the grid decides the columns. */
    val gridCellMin: Dp = 168.dp,
    /** Width of a card in a horizontal carousel — narrow enough for a sliver of the next one. */
    val carouselCardWidth: Dp = 228.dp,
    /** Height of the player's ring panel for each density preference. */
    val playerPanelCompact: Dp = 196.dp,
    val playerPanelStandard: Dp = 248.dp,
    val playerPanelLarge: Dp = 300.dp,
    /** Height of the Today hero card on a phone. */
    val heroHeight: Dp = 168.dp,

    /** Control heights. */
    val controlHeight: Dp = 40.dp,
    val buttonHeight: Dp = 48.dp,
    val iconButtonSize: Dp = 36.dp,
    val topBarHeight: Dp = 56.dp,
    val listRowHeight: Dp = 56.dp,
    /** One label/value line in a metric table. */
    val metricRowHeight: Dp = 44.dp,
    /** The bottom navigation bar on a phone. */
    val navBarHeight: Dp = 64.dp,
    /** The icon rail on a tablet held upright. */
    val railWidth: Dp = 72.dp,
    /** The full sidebar, at the largest breakpoint. */
    val sidebarWidth: Dp = 244.dp,
    /** Horizontal inset of the bottom navigation bar when it does not span the screen. */
    val navBarInset: Dp = 12.dp,

    /** Minimum touch target. Never draw an interactive element smaller than this. */
    val minTouchTarget: Dp = 48.dp,

    /** Border widths. */
    val borderWidth: Dp = 1.dp,
    val focusRingWidth: Dp = 2.dp,

    // --- Radii -----------------------------------------------------------------------------------
    // Soft rectangles for controls and cards; pills remain reserved for labels and transport.
    /** Cards and panels — the standard dashboard rectangle. */
    val cardRadius: Dp = 16.dp,
    /** A surface nested inside a card: one step tighter so nesting reads as nesting. */
    val panelRadius: Dp = 12.dp,
    /** Buttons, inputs, navigation items, icon buttons — and every selectable chip. */
    val controlRadius: Dp = 12.dp,
    /** Label chips, badges and pill-shaped bars — fully round, because they carry no action. */
    val chipRadius: Dp = 999.dp,
    /** Bottom sheets and the largest surfaces. */
    val sheetRadius: Dp = 24.dp,
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
    /** Buttons, fields, steppers, navigation items — and every chip you can tap. */
    val control: RoundedCornerShape = RoundedCornerShape(12.dp),
    /** A surface nested inside a card, and the smallest radius Material's own components get. */
    val panel: RoundedCornerShape = RoundedCornerShape(12.dp),
    /** Cards. */
    val card: RoundedCornerShape = RoundedCornerShape(16.dp),
    /** Sheets, dialogs, the largest surfaces. */
    val sheet: RoundedCornerShape = RoundedCornerShape(24.dp),
    /** Labels only: a phase tag, a category pill, a status badge, a progress track. Never a control. */
    val chip: RoundedCornerShape = RoundedCornerShape(999.dp),
    /** Fully round: avatars and the single round transport control in the player. */
    val pill: RoundedCornerShape = RoundedCornerShape(999.dp),
)

val LocalPulseShapes = staticCompositionLocalOf { PulseShapes() }

/**
 * Elevation. Signal draws depth three ways and each has one job: a tonal fill step separates a
 * surface from the canvas, a hairline keeps that edge crisp on any density, and a real shadow is
 * reserved for things that genuinely float above the page — menus, dialogs, snackbars and the phone
 * navigation bar. Nothing else casts one, which is why the few shadows on screen still mean
 * something.
 */
@Immutable
data class PulseElevation(
    /** Raised surface: a card on the canvas. Fill plus hairline, no shadow. */
    val level0: Dp = 0.dp,
    /** Interactive surface: a pressed card, a selected row, a text field. */
    val level1: Dp = 1.dp,
    /** Overlay: a menu, a tooltip, a snackbar. */
    val level2: Dp = 4.dp,
    /** Floating chrome: the phone navigation bar, a bottom sheet, a dialog. */
    val level3: Dp = 12.dp,
)

val LocalPulseElevation = staticCompositionLocalOf { PulseElevation() }

/**
 * Motion. Signal moves quickly and quietly — 150 ms for feedback, 200 ms for selection, 250 ms for a
 * sheet — because a dashboard should feel immediate rather than choreographed. The reduced-motion
 * switch collapses every duration to zero, because an accessibility setting that still animates is
 * not an accessibility setting.
 */
@Immutable
data class PulseMotion(val reduced: Boolean) {
    /** 150 ms — hover, press, colour and elevation feedback. */
    fun fast(): Int = if (reduced) 0 else 150

    /** 200 ms — selection, expansion, navigation transitions. */
    fun standard(): Int = if (reduced) 0 else 200

    /** 250 ms — sheets, dialogs and the player's ring sweep. */
    fun slow(): Int = if (reduced) 0 else 250

    /** Duration in ms, collapsed to 0 when the user prefers reduced motion. */
    fun duration(normal: Int): Int = if (reduced) 0 else normal

    /** Slightly longer beat for a value that travels a long way, flattened when motion is reduced. */
    fun emphasis(): Int = if (reduced) 0 else 240
}

val LocalPulseMotion = staticCompositionLocalOf { PulseMotion(reduced = false) }
