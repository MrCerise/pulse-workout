package com.pulse.intervalcoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * PULSE type system — v1.4 "Graphite".
 *
 * Two families, both of which the platform already ships, so the app adds no font assets and no
 * download step:
 *
 *  - **Sans** ([FontFamily.Default]) for every piece of prose and every label;
 *  - **Mono** ([FontFamily.Monospace]) for anything numeric — countdowns, durations, interval
 *    counts, heart-rate numbers. Monospaced digits are the reason a countdown that ticks from
 *    `1:00` to `0:59` does not shift a single pixel, and the reason the interface reads like a
 *    tool an engineer would use rather than a marketing page.
 *
 * The scale is deliberately tight: a 1.33 step between headings, 13–14 sp for body copy. Sizes are
 * paired with line heights and, for headings, slightly negative tracking, which is what separates a
 * designed scale from a list of font sizes.
 */
object PulseFonts {
    val Sans: FontFamily = FontFamily.Default
    val Mono: FontFamily = FontFamily.Monospace
}

/** Tabular figures, so numbers keep a constant width inside running text. */
const val TABULAR_FIGURES = "tnum"

/**
 * Numeric styles. They live outside [Typography] because Material's role names describe prose, and
 * a timer is not prose — it is data. Every one of these is monospaced and tabular.
 */
object PulseType {
    /** The player countdown and the instructor view. */
    val NumericDisplay = TextStyle(
        fontFamily = PulseFonts.Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.0).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Hero figures: session duration, parsed total, active time on the summary. */
    val NumericLarge = TextStyle(
        fontFamily = PulseFonts.Mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Stat tile values and interval durations. */
    val NumericMedium = TextStyle(
        fontFamily = PulseFonts.Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Inline numbers: counts, BPM, timestamps in a list row. */
    val NumericSmall = TextStyle(
        fontFamily = PulseFonts.Mono,
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
        letterSpacing = 0.9.sp,
    )

    /** Value shown by a stepper. */
    val StepperValue = TextStyle(
        fontFamily = PulseFonts.Mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
}

/** Kept for call sites that need "monospaced, tabular" without reaching for a full style. */
val TimerTextStyle = TextStyle(
    fontFamily = PulseFonts.Mono,
    fontFeatureSettings = TABULAR_FIGURES,
)

/**
 * Material3's role scale, bound to the PULSE type scale. Components that read
 * `MaterialTheme.typography.<role>` therefore inherit the same rhythm as hand-styled text.
 */
fun pulseTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            lineHeight = 60.sp,
            letterSpacing = (-1.2).sp,
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            letterSpacing = (-0.8).sp,
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.4).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.3).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.sp,
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.sp,
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            letterSpacing = 0.1.sp,
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.6.sp,
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.9.sp,
        ),
    )
}
