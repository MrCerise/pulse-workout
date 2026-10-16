package com.pulse.intervalcoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pulse.intervalcoach.R

/**
 * CADENCE type system — "Momentum".
 *
 * Two families, two jobs:
 *
 *  - [SpaceGrotesk] is the **display** face. Its slightly squared, engineered letterforms give the
 *    page titles, the hero figures and the countdown a voice of their own — the thing that makes a
 *    screen read as Intervals rather than as a settings page. It carries every number the user is
 *    meant to *feel* (the timer, the session totals) and every heading the user is meant to *see*.
 *  - [Inter] is the **text** face. A grotesque with a tall x-height, it keeps 12–14 sp labels
 *    legible at a glance mid-workout, and its tabular digits stop any inline count from reflowing.
 *
 * Both ship as static instances subset to the Latin ranges the app renders, so the pair costs
 * ~500 KB rather than the ~2 MB the full variable faces would. Each family carries its SIL Open
 * Font License in `res/raw/`, as the licence requires.
 *
 * ## The scale
 *
 * Confident, not shouty: a 15 sp body, a 26 sp page title, an 11 sp section label — and the
 * player's countdown as the single 64 sp moment on the whole interface. Display sizes step in
 * larger jumps than the text sizes, because contrast in *scale* is what builds hierarchy when the
 * palette stays quiet.
 */
object PulseFonts {
    /** Inter, the text family: prose, labels, captions. */
    val Inter: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    /** Prose and labels. Named `Sans` because that is what call sites mean by it. */
    val Sans: FontFamily = Inter

    /**
     * Space Grotesk, the display family: page titles, hero figures, the countdown. Three static
     * weights — the display voice never needs the extremes.
     */
    val Display: FontFamily = FontFamily(
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
        Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
        Font(R.font.space_grotesk_bold, FontWeight.Bold),
    )

    /**
     * Numeric text: the display face with tabular figures switched on. Space Grotesk ships a
     * `tnum` feature, so a countdown ticking from `1:00` to `0:59` never shifts a pixel — the
     * display face can carry the timer without reflowing it.
     */
    val Numeric: FontFamily = Display

    /**
     * The platform monospace, kept for the one place a stopwatch readout genuinely wants fixed
     * advance widths independent of the tabular feature — the cue script preview.
     */
    val Mono: FontFamily = FontFamily.Monospace
}

/** Tabular figures, so numbers keep a constant width inside running text. */
const val TABULAR_FIGURES = "tnum"

/**
 * Numeric styles. They live outside [Typography] because Material's role names describe prose, and
 * a timer is not prose — it is data. Every one of these is tabular.
 */
object PulseType {
    /** The player countdown and the instructor view. */
    val NumericDisplay = TextStyle(
        fontFamily = PulseFonts.Numeric,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Hero figures: session duration, parsed total, active time on the summary. */
    val NumericLarge = TextStyle(
        fontFamily = PulseFonts.Numeric,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.75).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Stat tile values and interval durations. */
    val NumericMedium = TextStyle(
        fontFamily = PulseFonts.Numeric,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Inline numbers: counts, BPM, timestamps in a list row. Small enough to stay in Inter. */
    val NumericSmall = TextStyle(
        fontFamily = PulseFonts.Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Field label above a control, and the section label above a group of controls. */
    val SectionLabel = TextStyle(
        fontFamily = PulseFonts.Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    )

    /** Value shown by a stepper. */
    val StepperValue = TextStyle(
        fontFamily = PulseFonts.Numeric,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
}

/** Kept for call sites that need "tabular digits" without reaching for a full style. */
val TimerTextStyle = TextStyle(
    fontFamily = PulseFonts.Numeric,
    fontFeatureSettings = TABULAR_FIGURES,
)

/**
 * Material3's role scale, bound to the Momentum type scale: the display and headline roles speak
 * Space Grotesk, every supporting role stays in Inter. Components that read
 * `MaterialTheme.typography.<role>` therefore inherit the same rhythm as hand-styled text.
 */
fun pulseTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = PulseFonts.Display,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            letterSpacing = (-1).sp,
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = PulseFonts.Display,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.75).sp,
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = PulseFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = PulseFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.4).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = PulseFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            lineHeight = 27.sp,
            letterSpacing = (-0.25).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = PulseFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.15).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = PulseFonts.Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.1).sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.sp,
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.1.sp,
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp,
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.6.sp,
        ),
    )
}
