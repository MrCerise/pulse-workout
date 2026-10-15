package com.pulse.intervalcoach.wear.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.pulse.engine.PhaseKind

/** Theme modes offered on the watch. The phone's `LIGHT` setting maps to [SYSTEM] here. */
enum class WearThemeMode { SYSTEM, DARK, TRUE_BLACK }

/**
 * Semantic tokens for the watch UI — the same roles as the phone's `PulseColors`, restricted to
 * what a 200 dp round screen actually renders.
 */
data class WearColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val work: Color,
    val rest: Color,
    val prepare: Color,
    val cooldown: Color,
) {
    /** Phase colour — the same mapping the phone's `phaseColor` uses, so a rest bar is blue on both. */
    fun phaseColor(kind: PhaseKind): Color = when (kind) {
        PhaseKind.WORK, PhaseKind.WARM_UP -> work
        PhaseKind.REST, PhaseKind.TRANSITION -> rest
        PhaseKind.PREPARE -> prepare
        PhaseKind.COOLDOWN -> cooldown
        PhaseKind.CUSTOM -> textSecondary
    }
}

fun wearColors(trueBlack: Boolean, highContrast: Boolean): WearColors {
    val p = PulseWearPalette
    return if (trueBlack) {
        WearColors(
            canvas = p.BlackCanvas,
            surface = p.BlackSurface,
            surfaceRaised = p.BlackSurfaceRaised,
            border = p.BlackBorder,
            textPrimary = if (highContrast) p.HighContrastText else p.TextPrimary,
            textSecondary = if (highContrast) p.HighContrastTextSecondary else p.TextSecondary,
            textMuted = p.TextMuted,
            track = p.Track,
            accent = if (highContrast) p.HighContrastAccent else p.Accent,
            onAccent = p.BlackCanvas,
            destructive = p.Destructive,
            work = if (highContrast) p.HighContrastWork else p.Work,
            rest = if (highContrast) p.HighContrastRest else p.Rest,
            prepare = if (highContrast) p.HighContrastPrepare else p.Prepare,
            cooldown = if (highContrast) p.HighContrastCooldown else p.Cooldown,
        )
    } else {
        WearColors(
            canvas = p.Canvas,
            surface = p.Surface,
            surfaceRaised = p.SurfaceRaised,
            border = p.Border,
            textPrimary = if (highContrast) p.HighContrastText else p.TextPrimary,
            textSecondary = if (highContrast) p.HighContrastTextSecondary else p.TextSecondary,
            textMuted = p.TextMuted,
            track = p.Track,
            accent = if (highContrast) p.HighContrastAccent else p.Accent,
            onAccent = p.OnAccent,
            destructive = p.Destructive,
            work = if (highContrast) p.HighContrastWork else p.Work,
            rest = if (highContrast) p.HighContrastRest else p.Rest,
            prepare = if (highContrast) p.HighContrastPrepare else p.Prepare,
            cooldown = if (highContrast) p.HighContrastCooldown else p.Cooldown,
        )
    }
}

val LocalWearColors = staticCompositionLocalOf { wearColors(trueBlack = false, highContrast = false) }

/**
 * The watch theme: Pulse tokens through [LocalWearColors], and the subset mapped onto the Wear
 * Material defaults so framework components (chips, buttons, the time text) inherit the palette.
 */
@Composable
fun PulseWearTheme(
    themeMode: WearThemeMode,
    highContrast: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        WearThemeMode.SYSTEM -> isSystemInDarkTheme()
        WearThemeMode.DARK -> true
        WearThemeMode.TRUE_BLACK -> true
    }
    val colors = wearColors(trueBlack = dark && themeMode == WearThemeMode.TRUE_BLACK, highContrast = highContrast)
    CompositionLocalProvider(LocalWearColors provides colors) {
        androidx.wear.compose.material.MaterialTheme(
            colors = androidx.wear.compose.material.Colors(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                secondary = colors.surfaceRaised,
                onSecondary = colors.textPrimary,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                background = colors.canvas,
                onBackground = colors.textPrimary,
                error = colors.destructive,
                onError = colors.canvas,
            ),
            content = content,
        )
    }
}
