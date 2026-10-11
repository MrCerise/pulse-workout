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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * PULSE — "Signal" theme entry point.
 *
 * ## Design direction
 *
 * A neutral dashboard: a rich charcoal ramp for the dark theme, a clean paper ramp for the light
 * one, hairline borders instead of stacked shadows, and one brand accent — the Cloudflare-inspired
 * orange — spent deliberately on the brand mark, the single primary action per screen, the active
 * navigation item and the focus ring. Everything else that carries colour carries a *meaning*: an
 * interval phase, a workout category, a state.
 *
 * ## What that means in practice
 *
 *  - **Chrome is neutral, content is coloured.** Navigation, headers, tables and text use the
 *    neutral ramp only, so a hue on screen is always information.
 *  - **Density is a feature.** 16 dp gutters, 40 dp controls, a 56 dp bar and 13–15 sp copy: the
 *    amount of workout a user can read without scrolling is the point of the redesign.
 *  - **Depth is a hairline.** A card is separated by its fill and one border. Only menus, dialogs,
 *    snackbars and the phone navigation bar float.
 *  - **Radii are restrained.** 6 dp controls, 8 dp cards, 12 dp sheets, pills for chips.
 *
 * Every value lives in [PulsePalette] / [PulseDimens] / [PulseElevation] / [PulseType], and every
 * pairing this theme can produce is measured in `docs/CONTRAST.md` by `scripts/contrast_check.py`.
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
        .let { base ->
            if (dynamicAccent == null) base else base.copy(
                accent = dynamicAccent,
                accentAlt = dynamicAccent.copy(alpha = 0.72f),
            )
        }

    val dimens = PulseDimens()
    val shapes = PulseShapes()

    // The activity draws edge-to-edge, so the status and navigation bar *icons* are ours to choose.
    // They follow the theme the user picked, not the system one: choosing Light on a dark device must
    // not leave white glyphs on white chrome.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalPulseColors provides colors,
        LocalPulseDimens provides dimens,
        LocalPulseShapes provides shapes,
        LocalPulseElevation provides PulseElevation(),
        LocalPulseMotion provides PulseMotion(reducedMotion),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = pulseTypography(),
            shapes = Shapes(
                // `extraSmall` is the smallest *structural* radius, not a pill: Material components
                // that build their own shape (a text field's body, a menu item's highlight) read it,
                // and pointing it at `shapes.chip` rendered every one of them as a capsule.
                extraSmall = shapes.panel,
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
            surfaceSunken = PulsePalette.LightSurfaceSunken,
            surfaceOverlay = PulsePalette.LightSurfaceOverlay,
            surfaceHover = PulsePalette.LightSurfaceHover,
            textPrimary = if (highContrast) Color(0xFF000000) else PulsePalette.LightTextPrimary,
            textSecondary = if (highContrast) Color(0xFF31383F) else PulsePalette.LightTextSecondary,
            textMuted = PulsePalette.LightTextMuted,
            textDisabled = PulsePalette.LightTextDisabled,
            border = PulsePalette.LightBorder,
            borderStrong = PulsePalette.LightBorderStrong,
            outline = PulsePalette.LightOutline,
            accent = accentOverride ?: PulsePalette.LightAccent,
            accentAlt = PulsePalette.LightAccentAlt,
            accentTint = PulsePalette.LightAccentTint,
            brand = PulsePalette.LightBrand,
            onBrand = PulsePalette.LightOnBrand,
            heroSurface = PulsePalette.LightHeroSurface,
            heroText = PulsePalette.LightHeroText,
            heroMuted = PulsePalette.LightHeroMuted,
            work = PulsePalette.LightWork,
            rest = PulsePalette.LightRest,
            prepare = PulsePalette.LightPrepare,
            cooldown = PulsePalette.LightCooldown,
            destructive = PulsePalette.LightDestructive,
            info = PulsePalette.LightInfo,
            infoTint = PulsePalette.LightInfoTint,
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
        surfaceSunken = if (trueBlack) PulsePalette.BlackSurfaceSunken else PulsePalette.SurfaceSunken,
        surfaceOverlay = if (trueBlack) PulsePalette.BlackSurfaceOverlay else PulsePalette.SurfaceOverlay,
        surfaceHover = if (trueBlack) PulsePalette.BlackSurfaceHover else PulsePalette.SurfaceHover,
        textPrimary = if (highContrast) PulsePalette.HighContrastText else PulsePalette.TextPrimary,
        textSecondary = if (highContrast) PulsePalette.HighContrastTextSecondary else PulsePalette.TextSecondary,
        textMuted = PulsePalette.TextMuted,
        textDisabled = PulsePalette.TextDisabled,
        border = if (trueBlack) PulsePalette.BlackBorder else PulsePalette.Border,
        borderStrong = PulsePalette.BorderStrong,
        outline = if (highContrast) PulsePalette.HighContrastOutline else PulsePalette.Outline,
        accent = accentOverride ?: if (highContrast) PulsePalette.HighContrastAccent else PulsePalette.Accent,
        accentAlt = PulsePalette.AccentAlt,
        accentTint = PulsePalette.AccentTint,
        brand = PulsePalette.Brand,
        onBrand = PulsePalette.OnBrand,
        heroSurface = PulsePalette.HeroSurface,
        heroText = PulsePalette.HeroText,
        heroMuted = PulsePalette.HeroMuted,
        work = if (highContrast) PulsePalette.HighContrastWork else PulsePalette.Work,
        rest = if (highContrast) PulsePalette.HighContrastRest else PulsePalette.Rest,
        prepare = if (highContrast) PulsePalette.HighContrastPrepare else PulsePalette.Prepare,
        cooldown = if (highContrast) PulsePalette.HighContrastCooldown else PulsePalette.Cooldown,
        destructive = PulsePalette.Destructive,
        info = PulsePalette.Info,
        infoTint = PulsePalette.InfoTint,
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
        surfaceContainerLow = surfaceSunken,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceOverlay,
        surfaceDim = surfaceSunken,
        surfaceBright = surfaceOverlay,
        outline = outline,
        outlineVariant = border,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        scrim = scrim,
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
    if (relativeLuminance(background) > 0.35) PulsePalette.OnBrand else PulsePalette.HeroText

/** Walks up a `ContextWrapper` chain to the activity that owns the window. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var context: android.content.Context? = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
