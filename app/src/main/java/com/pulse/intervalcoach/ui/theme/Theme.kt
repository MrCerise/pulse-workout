package com.pulse.intervalcoach.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Theme palettes, wallpaper accents and a short, reduced-motion-aware colour transition. */
@Composable
fun PulseTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    highContrast: Boolean,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = themeMode.isDark(systemDark)
    val trueBlack = themeMode == ThemeMode.TRUE_BLACK
    val context = LocalContext.current

    val base = pulseColors(dark = dark, trueBlack = trueBlack, highContrast = highContrast)
    val dynamicAccent = if (dynamicColor && !highContrast && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else null
    val target = if (dynamicAccent == null) base else base.withAccent(dynamicAccent)
    val colors = animatedColors(target, reducedMotion || highContrast)

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

fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.TRUE_BLACK -> true
}

/** Apply a wallpaper accent as a complete pair, not a coloured label on an unrelated orange tint. */
internal fun PulseColors.withAccent(candidate: Color): PulseColors {
    if (highContrast) return this
    val tint = lerp(surface, candidate, 0.10f)
    val backgrounds = listOf(background, nav, surface, surfaceRaised, surfaceOverlay, surfaceSunken, tint)
    if (backgrounds.any { contrastRatio(candidate, it) < 4.5 }) return this
    val ink = if (contrastRatio(Color.Black, candidate) >= contrastRatio(Color.White, candidate)) Color.Black else Color.White
    return copy(
        accent = candidate, accentAlt = candidate, accentTint = tint,
        brand = candidate, onBrand = ink, onAccent = ink,
        actionFill = candidate, actionText = ink,
    )
}


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

/** Never crossfade whole screens: doing so duplicates navigation and side effects. */
@Composable
private fun animatedColors(target: PulseColors, instant: Boolean): PulseColors {
    val duration = if (instant) 0 else 220
    @Composable
    fun colour(value: Color, name: String): Color =
        animateColorAsState(value, animationSpec = tween(duration), label = name).value
    return target.copy(
        background = colour(target.background, "theme.background"),
        nav = colour(target.nav, "theme.nav"),
        surface = colour(target.surface, "theme.surface"),
        surfaceRaised = colour(target.surfaceRaised, "theme.surfaceRaised"),
        surfaceSunken = colour(target.surfaceSunken, "theme.surfaceSunken"),
        surfaceOverlay = colour(target.surfaceOverlay, "theme.surfaceOverlay"),
        surfaceHover = colour(target.surfaceHover, "theme.surfaceHover"),
        textPrimary = colour(target.textPrimary, "theme.textPrimary"),
        textSecondary = colour(target.textSecondary, "theme.textSecondary"),
        textMuted = colour(target.textMuted, "theme.textMuted"),
        textDisabled = colour(target.textDisabled, "theme.textDisabled"),
        border = colour(target.border, "theme.border"),
        borderStrong = colour(target.borderStrong, "theme.borderStrong"),
        outline = colour(target.outline, "theme.outline"),
        accent = colour(target.accent, "theme.accent"),
        accentAlt = colour(target.accentAlt, "theme.accentAlt"),
        accentTint = colour(target.accentTint, "theme.accentTint"),
        brand = colour(target.brand, "theme.brand"),
        onBrand = colour(target.onBrand, "theme.onBrand"),
        heroSurface = colour(target.heroSurface, "theme.heroSurface"),
        heroText = colour(target.heroText, "theme.heroText"),
        heroMuted = colour(target.heroMuted, "theme.heroMuted"),
        work = colour(target.work, "theme.work"),
        rest = colour(target.rest, "theme.rest"),
        prepare = colour(target.prepare, "theme.prepare"),
        cooldown = colour(target.cooldown, "theme.cooldown"),
        destructive = colour(target.destructive, "theme.destructive"),
        info = colour(target.info, "theme.info"),
        infoTint = colour(target.infoTint, "theme.infoTint"),
        actionFill = colour(target.actionFill, "theme.actionFill"),
        actionText = colour(target.actionText, "theme.actionText"),
        onAccent = colour(target.onAccent, "theme.onAccent"),
    )
}
