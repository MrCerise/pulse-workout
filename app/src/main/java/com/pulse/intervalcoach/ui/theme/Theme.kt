package com.pulse.intervalcoach.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * PULSE — v1.4 "Graphite" theme entry point.
 *
 * ## Design direction
 *
 * The application is styled after a modern developer tool: a near-black canvas, a neutral slate
 * ramp for every surface, one-pixel separators instead of shadows, a tight type scale with
 * monospaced numerals, and colour reserved for meaning. Dark is the primary theme; light is a
 * first-class sibling built from the same ramp rather than an inverted afterthought.
 *
 * ## What that means in practice
 *
 *  - **Chrome is monochrome.** Navigation, headers, buttons and text never carry hue.
 *  - **Colour means something.** Only interval phases (work / rest / prepare / cool-down) and state
 *    (destructive, accent) are chromatic.
 *  - **Radii are small and consistent.** 12 dp for surfaces, 8 dp for controls, 6 dp for chips.
 *    Nothing is a pill unless it is genuinely round.
 *  - **Elevation is a border, not a shadow.** A card is separated from the canvas by its fill and a
 *    hairline, which stays crisp on every density and in every theme.
 *
 * Every value lives in [PulsePalette] / [PulseDimens] / [PulseType], and every pairing this theme
 * can produce is measured in `docs/CONTRAST.md` by `scripts/contrast_check.py`.
 */
@Composable
fun PulseTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    highContrast: Boolean,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.TRUE_BLACK -> true
    }
    val trueBlack = themeMode == ThemeMode.TRUE_BLACK
    val context = LocalContext.current

    // Dynamic colour is a wallpaper-derived palette, so it is never allowed to repaint the neutral
    // ramp — a wallpaper cannot promise our measured ratios, and letting it drive surfaces is what
    // makes an app look like a different product per device. It donates the *accent* only, and only
    // when that accent still reaches 4.5:1 on the canvas; otherwise the design-system accent wins.
    val wantsDynamic = dynamicColor && !highContrast && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val canvas = if (dark) {
        if (trueBlack) PulsePalette.BlackCanvas else PulsePalette.Canvas
    } else {
        PulsePalette.LightCanvas
    }
    val dynamicAccent = if (wantsDynamic) {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scheme.primary.takeIf { contrastRatio(it, canvas) >= 4.5 }
    } else {
        null
    }

    val colors = pulseColors(dark = dark, trueBlack = trueBlack, highContrast = highContrast)
        .let { base -> if (dynamicAccent == null) base else base.copy(accent = dynamicAccent) }

    val dimens = PulseDimens()
    val shapes = PulseShapes()

    CompositionLocalProvider(
        LocalPulseColors provides colors,
        LocalPulseDimens provides dimens,
        LocalPulseShapes provides shapes,
        LocalPulseMotion provides PulseMotion(reducedMotion),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = pulseTypography(),
            shapes = Shapes(
                extraSmall = shapes.chip,
                small = shapes.control,
                medium = shapes.card,
                large = shapes.sheet,
                extraLarge = shapes.sheet,
            ),
            content = content,
        )
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK, TRUE_BLACK }

/**
 * Resolves the token set for one theme. Kept as a pure function (no composition) so previews, the
 * first frame and the contrast tooling can all build the same palette.
 */
fun pulseColors(
    dark: Boolean,
    trueBlack: Boolean,
    highContrast: Boolean,
    accentOverride: Color? = null,
): PulseColors {
    if (!dark) {
        return PulseColors(
            isDark = false,
            highContrast = highContrast,
            background = PulsePalette.LightCanvas,
            nav = PulsePalette.LightNav,
            surface = PulsePalette.LightSurface,
            surfaceRaised = PulsePalette.LightSurfaceRaised,
            surfaceOverlay = PulsePalette.LightSurfaceOverlay,
            surfaceHover = PulsePalette.LightSurfaceHover,
            textPrimary = if (highContrast) Color(0xFF000000) else PulsePalette.LightTextPrimary,
            textSecondary = if (highContrast) Color(0xFF2E3646) else PulsePalette.LightTextSecondary,
            textMuted = PulsePalette.LightTextMuted,
            textDisabled = PulsePalette.LightTextDisabled,
            border = PulsePalette.LightBorder,
            outline = PulsePalette.LightOutline,
            accent = accentOverride ?: PulsePalette.LightAccent,
            accentTint = PulsePalette.LightAccentTint,
            work = PulsePalette.LightWork,
            rest = PulsePalette.LightRest,
            prepare = PulsePalette.LightPrepare,
            cooldown = PulsePalette.LightCooldown,
            destructive = PulsePalette.LightDestructive,
            actionFill = PulsePalette.LightActionFill,
            actionText = PulsePalette.LightActionText,
            onAccent = PulsePalette.LightOnAccent,
        )
    }
    return PulseColors(
        isDark = true,
        trueBlack = trueBlack,
        highContrast = highContrast,
        background = if (trueBlack) PulsePalette.BlackCanvas else PulsePalette.Canvas,
        nav = if (trueBlack) PulsePalette.BlackNav else PulsePalette.Nav,
        surface = if (trueBlack) PulsePalette.BlackSurface else PulsePalette.Surface,
        surfaceRaised = if (trueBlack) PulsePalette.BlackSurfaceRaised else PulsePalette.SurfaceRaised,
        surfaceOverlay = if (trueBlack) PulsePalette.BlackSurfaceOverlay else PulsePalette.SurfaceOverlay,
        surfaceHover = if (trueBlack) PulsePalette.BlackSurfaceHover else PulsePalette.SurfaceHover,
        textPrimary = if (highContrast) PulsePalette.HighContrastText else PulsePalette.TextPrimary,
        textSecondary = if (highContrast) PulsePalette.HighContrastTextSecondary else PulsePalette.TextSecondary,
        textMuted = PulsePalette.TextMuted,
        textDisabled = PulsePalette.TextDisabled,
        border = if (trueBlack) PulsePalette.BlackBorder else PulsePalette.Border,
        outline = if (highContrast) PulsePalette.HighContrastOutline else PulsePalette.Outline,
        accent = accentOverride ?: PulsePalette.Accent,
        accentTint = PulsePalette.AccentTint,
        work = if (highContrast) PulsePalette.HighContrastWork else PulsePalette.Work,
        rest = if (highContrast) PulsePalette.HighContrastRest else PulsePalette.Rest,
        prepare = if (highContrast) PulsePalette.HighContrastPrepare else PulsePalette.Prepare,
        cooldown = if (highContrast) PulsePalette.HighContrastCooldown else PulsePalette.Cooldown,
        destructive = PulsePalette.Destructive,
        actionFill = PulsePalette.ActionFill,
        actionText = PulsePalette.ActionText,
        onAccent = PulsePalette.OnAccent,
    )
}

/**
 * Binds every Material3 role to a PULSE token.
 *
 * Material components (dialogs, menus, switches, chips, the text-field focus ring) read their
 * colours from here. Mapping them by hand is what stops a framework default — the stock purple-grey
 * baseline — from appearing in the middle of the palette.
 */
fun PulseColors.toMaterialScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = legibleOn(accent),
        primaryContainer = accentTint,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = work,
        onSecondary = legibleOn(work),
        secondaryContainer = phaseTint(com.pulse.engine.PhaseKind.WORK),
        onSecondaryContainer = work,
        tertiary = cooldown,
        onTertiary = legibleOn(cooldown),
        tertiaryContainer = phaseTint(com.pulse.engine.PhaseKind.COOLDOWN),
        onTertiaryContainer = cooldown,
        error = destructive,
        onError = legibleOn(destructive),
        errorContainer = dangerTint,
        onErrorContainer = destructive,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = background,
        surfaceContainerLow = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceOverlay,
        surfaceDim = background,
        surfaceBright = surfaceOverlay,
        outline = outline,
        outlineVariant = border,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        scrim = Color.Black,
    )
}

// --- WCAG helpers (also used by the contrast report generator) -----------------------------------

fun relativeLuminance(color: Color): Double {
    fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/**
 * Text colour that stays readable on an arbitrary tint: ink on light fills, near-white on dark ones.
 * Used wherever a caller supplies its own background (generated share cards, dynamic accents).
 */
fun legibleOn(background: Color): Color =
    if (relativeLuminance(background) > 0.35) PulsePalette.OnAccent else PulsePalette.TextPrimary
