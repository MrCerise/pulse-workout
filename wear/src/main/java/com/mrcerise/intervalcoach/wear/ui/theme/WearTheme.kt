package com.mrcerise.intervalcoach.wear.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.pulse.engine.PhaseKind
import com.pulse.engine.WorkoutType

/** Theme modes offered on the watch. The phone's `LIGHT` setting maps to [SYSTEM] here. */
enum class WearThemeMode { SYSTEM, DARK, TRUE_BLACK }

/**
 * Semantic tokens for the watch UI — the same roles as the phone's `PulseColors`, restricted to
 * what a 200 dp round screen actually renders. Material components read the [ColorScheme] built
 * from these; Pulse-specific drawing (phase rings, category marks) reads them directly.
 */
data class WearColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val border: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val track: Color,
    val accent: Color,
    val accentAlt: Color,
    val accentTint: Color,
    val onAccent: Color,
    val destructive: Color,
    val destructiveTint: Color,
    val work: Color,
    val workTint: Color,
    val rest: Color,
    val restTint: Color,
    val prepare: Color,
    val prepareTint: Color,
    val cooldown: Color,
    val cooldownTint: Color,
) {
    /** Phase colour — the same mapping the phone's `phaseColor` uses, so a rest bar is blue on both. */
    fun phaseColor(kind: PhaseKind): Color = when (kind) {
        PhaseKind.WORK, PhaseKind.WARM_UP -> work
        PhaseKind.REST, PhaseKind.TRANSITION -> rest
        PhaseKind.PREPARE -> prepare
        PhaseKind.COOLDOWN -> cooldown
        PhaseKind.CUSTOM -> textSecondary
    }

    /** The dark, pre-composited partner of [phaseColor], for fills behind phase-coloured content. */
    fun phaseTint(kind: PhaseKind): Color = when (kind) {
        PhaseKind.WORK, PhaseKind.WARM_UP -> workTint
        PhaseKind.REST, PhaseKind.TRANSITION -> restTint
        PhaseKind.PREPARE -> prepareTint
        PhaseKind.COOLDOWN -> cooldownTint
        PhaseKind.CUSTOM -> surfaceRaised
    }
}

fun wearColors(trueBlack: Boolean, highContrast: Boolean): WearColors {
    val p = PulseWearPalette
    return WearColors(
        canvas = if (trueBlack) p.BlackCanvas else p.Canvas,
        surface = if (trueBlack) p.BlackSurface else p.Surface,
        surfaceRaised = if (trueBlack) p.BlackSurfaceRaised else p.SurfaceRaised,
        surfaceHigh = if (trueBlack) p.BlackSurfaceOverlay else p.SurfaceOverlay,
        border = if (trueBlack) p.BlackBorder else p.Border,
        outline = if (highContrast) p.HighContrastOutline else p.Outline,
        textPrimary = if (highContrast) p.HighContrastText else p.TextPrimary,
        textSecondary = if (highContrast) p.HighContrastTextSecondary else p.TextSecondary,
        textMuted = p.TextMuted,
        track = p.Track,
        accent = if (highContrast) p.HighContrastAccent else p.Accent,
        accentAlt = p.AccentAlt,
        accentTint = p.AccentTint,
        onAccent = if (trueBlack) p.BlackCanvas else p.OnAccent,
        destructive = p.Destructive,
        destructiveTint = p.DestructiveTint,
        work = if (highContrast) p.HighContrastWork else p.Work,
        workTint = p.WorkTint,
        rest = if (highContrast) p.HighContrastRest else p.Rest,
        restTint = p.RestTint,
        prepare = if (highContrast) p.HighContrastPrepare else p.Prepare,
        prepareTint = p.PrepareTint,
        cooldown = if (highContrast) p.HighContrastCooldown else p.Cooldown,
        cooldownTint = p.CooldownTint,
    )
}

val LocalWearColors = staticCompositionLocalOf { wearColors(trueBlack = false, highContrast = false) }

/** How a workout type is recognised at a glance: the phone's category icon and hue family. */
data class WearCategory(val icon: ImageVector, val color: Color, val tint: Color)

/** Mirrors the phone's category registry (`data/Categories.kt`): same icon, same hue family. */
fun wearCategory(type: WorkoutType): WearCategory {
    val p = PulseWearPalette
    val conditioning = p.CatConditioning to p.CatConditioningTint
    val cardio = p.CatCardio to p.CatCardioTint
    val strength = p.CatStrength to p.CatStrengthTint
    val mobility = p.CatMobility to p.CatMobilityTint
    val mind = p.CatMind to p.CatMindTint
    val tools = p.CatTools to p.CatToolsTint
    val (icon, hue) = when (type) {
        WorkoutType.HIIT -> Icons.Filled.LocalFireDepartment to conditioning
        WorkoutType.TABATA -> Icons.Filled.Bolt to conditioning
        WorkoutType.CIRCUIT -> Icons.Filled.Repeat to conditioning
        WorkoutType.BOXING -> Icons.Filled.SportsMma to conditioning
        WorkoutType.EMOM -> Icons.Filled.Timer to conditioning
        WorkoutType.E2MOM -> Icons.Filled.Redo to conditioning
        WorkoutType.AMRAP -> Icons.Filled.AllInclusive to conditioning
        WorkoutType.STRENGTH -> Icons.Filled.FitnessCenter to strength
        WorkoutType.RUN -> Icons.Filled.Speed to cardio
        WorkoutType.RUN_WALK -> Icons.Filled.DirectionsRun to cardio
        WorkoutType.STRETCHING -> Icons.Filled.AccessibilityNew to mobility
        WorkoutType.BREATHING -> Icons.Filled.Air to mind
        WorkoutType.FOCUS -> Icons.Filled.Psychology to mind
        WorkoutType.CUSTOM_SEQUENCE -> Icons.Filled.Tune to tools
        WorkoutType.COMPOUND -> Icons.Filled.Layers to tools
        WorkoutType.STOPWATCH -> Icons.Filled.Schedule to tools
        WorkoutType.CUE_TIMER -> Icons.Filled.NotificationsActive to tools
    }
    return WearCategory(icon = icon, color = hue.first, tint = hue.second)
}

/** The Pulse palette in Material 3 roles, so every framework component inherits it. */
private fun WearColors.toColorScheme(): ColorScheme = ColorScheme(
    primary = accent,
    primaryDim = accent,
    primaryContainer = accentTint,
    onPrimary = onAccent,
    onPrimaryContainer = accentAlt,
    secondary = rest,
    secondaryDim = rest,
    secondaryContainer = restTint,
    onSecondary = canvas,
    onSecondaryContainer = rest,
    tertiary = work,
    tertiaryDim = work,
    tertiaryContainer = workTint,
    onTertiary = canvas,
    onTertiaryContainer = work,
    surfaceContainerLow = surface,
    surfaceContainer = surfaceRaised,
    surfaceContainerHigh = surfaceHigh,
    onSurface = textPrimary,
    onSurfaceVariant = textSecondary,
    outline = outline,
    outlineVariant = border,
    background = canvas,
    onBackground = textPrimary,
    error = destructive,
    errorDim = destructive,
    errorContainer = destructiveTint,
    onError = canvas,
    onErrorContainer = destructive,
)

/**
 * The watch theme: Pulse tokens through [LocalWearColors], and the same tokens as the Wear
 * Material 3 colour scheme so framework components (buttons, cards, dialogs, the time text)
 * inherit the palette.
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
        MaterialTheme(colorScheme = colors.toColorScheme(), content = content)
    }
}
