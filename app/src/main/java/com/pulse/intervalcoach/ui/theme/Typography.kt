package com.pulse.intervalcoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pulse.intervalcoach.R

/**
 * PULSE type system — "Signal".
 *
 * One family, [Inter], in four static weights. Inter is the face the Cloudflare dashboard is built
 * on (via their Inter-derived display face), and it earns its place here for three reasons that
 * matter in this app specifically: it is a *grotesque with tall x-height*, so 12–14 sp dashboard
 * text stays legible at a glance mid-workout; its digits exist in a **tabular** variant, so a
 * countdown ticking from `1:00` to `0:59` never shifts a pixel; and it is metrically close to the
 * platform faces, so a fallback — if the font resource ever fails to load — does not reflow a
 * screen.
 *
 * The four weights are static instances of the upstream variable face, subset to the Latin ranges
 * the app can render, so the whole family costs ~240 KB rather than 900 KB. The font ships with its
 * SIL Open Font License in `res/raw/inter_ofl.txt`, as the licence requires.
 *
 * ## The scale
 *
 * Dashboard, not marketing: a 15 sp body, 14 sp labels, an 11 sp uppercase section label, and a
 * heading range that stops at 22 sp for page titles. The two hero figures (the Today greeting and
 * the session summary headline) are the only places that go bigger, and the player's countdown is
 * the only place that goes to 60 sp.
 */
object PulseFonts {
    /** Inter, the one text family. */
    val Inter: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    /** Prose and labels. Named `Sans` because that is what call sites mean by it. */
    val Sans: FontFamily = Inter

    /**
     * Numeric text: Inter with tabular figures switched on. Not a monospace face — a *tabular* one,
     * which is what actually stops a changing number from reflowing its neighbours.
     */
    val Numeric: FontFamily = Inter

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
        fontSize = 60.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Hero figures: session duration, parsed total, active time on the summary. */
    val NumericLarge = TextStyle(
        fontFamily = PulseFonts.Numeric,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Stat tile values and interval durations. */
    val NumericMedium = TextStyle(
        fontFamily = PulseFonts.Numeric,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Inline numbers: counts, BPM, timestamps in a list row. */
    val NumericSmall = TextStyle(
        fontFamily = PulseFonts.Numeric,
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
 * Material3's role scale, bound to the Signal type scale. Components that read
 * `MaterialTheme.typography.<role>` therefore inherit the same rhythm as hand-styled text.
 */
fun pulseTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            letterSpacing = (-0.8).sp,
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.5).sp,
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.3).sp,
        ),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.1).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
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
            letterSpacing = 0.sp,
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = PulseFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
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
            letterSpacing = 0.sp,
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
