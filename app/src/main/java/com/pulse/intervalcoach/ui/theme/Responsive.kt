package com.pulse.intervalcoach.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The three window widths the interface is designed for.
 *
 * The app is one design at three sizes, not three designs. A **compact** window is a phone held
 * upright: one column, one action, a floating navigation pill. A **medium** window is a tablet in
 * portrait or a phone in landscape: an icon rail and two columns where a list can afford them. An
 * **expanded** window is a tablet in landscape or a desktop: a full sidebar and a measured column
 * of content that never stretches past what the eye can hold.
 *
 * The ladder is the same one the shell draws its navigation from — a rail at 600 dp and a sidebar
 * at 1000 dp — so the chrome and the page always agree about how much room there is.
 */
enum class PulseWindowSize {
    /** Below 600 dp: a phone. One column, the floating navigation pill. */
    COMPACT,

    /** 600–999 dp: a tablet in portrait, or a phone in landscape. The icon rail. */
    MEDIUM,

    /** 1000 dp and wider: a tablet in landscape, or a desktop. The full sidebar. */
    EXPANDED,
    ;

    /** True on a phone-sized window. */
    val isCompact: Boolean get() = this == COMPACT

    /** True when the window is wide enough for the full sidebar. */
    val isExpanded: Boolean get() = this == EXPANDED
}

/** The width at which the shell swaps the floating navigation pill for the icon rail. */
const val PULSE_MEDIUM_BREAKPOINT_DP: Int = 600

/** The width at which the shell swaps the icon rail for the full sidebar. */
const val PULSE_EXPANDED_BREAKPOINT_DP: Int = 1000

/**
 * Resolves a window width in dp to its size class.
 *
 * Kept as a pure function so the breakpoints can be tested, and so the shell and the page
 * components agree on one ladder instead of each inventing its own.
 */
fun pulseWindowSize(widthDp: Int): PulseWindowSize = when {
    widthDp < PULSE_MEDIUM_BREAKPOINT_DP -> PulseWindowSize.COMPACT
    widthDp < PULSE_EXPANDED_BREAKPOINT_DP -> PulseWindowSize.MEDIUM
    else -> PulseWindowSize.EXPANDED
}

/**
 * The current window size class.
 *
 * Provided once by the theme, from the configuration the window actually has — so it follows a
 * rotation, a fold, a split-screen resize and a desktop window drag, without any screen having to
 * ask for it.
 */
val LocalPulseWindowSize = compositionLocalOf { PulseWindowSize.COMPACT }

/**
 * The responsive metrics: the numbers that change with the window rather than with the theme.
 *
 * These live beside the fixed tokens because a *size* is not a *style*: the colour of a card does
 * not depend on the window, but the measure of a page, the narrowest a stat tile may be and the
 * height of the hero all do. Reading them from one place is what keeps a tablet page and a phone
 * page recognisably the same page.
 */
@Immutable
data class PulseLayout(
    /**
     * The widest a page's content column may get.
     *
     * A page that stretches to the width of a 27-inch monitor is a page nobody can read: the
     * title drifts away from the cards and the eye has to travel. Every destination therefore
     * draws inside this measure and is centred inside it, which is also what makes the shell's
     * sidebar page and the phone page look like the same product.
     */
    val contentMaxWidth: Dp = 760.dp,

    /**
     * The narrowest a stat tile may be before the row wraps.
     *
     * A tile narrower than this cannot hold a label like "Longest streak" and a tabular value
     * without truncating one of them, so a row of tiles wraps instead of squeezing. The number is
     * chosen so that **two** tiles still fit across a 360 dp phone — the common case — and three
     * fit across a tablet.
     */
    val statTileMinWidth: Dp = 124.dp,

    /**
     * The narrowest a card may be in a responsive grid.
     *
     * The same rule for cards as for tiles: below this width the card's content starts to
     * truncate, so the grid adds a column instead.
     */
    val cardMinWidth: Dp = 196.dp,

    /**
     * The narrowest a small square tile may be in a responsive grid.
     *
     * The one-tap tiles on the Today page are narrower than a card — a category, a preset — so
     * they get their own floor, and a grid of them packs two across on a phone and four on a
     * tablet instead of stretching one tile across the page.
     */
    val tileMinWidth: Dp = 140.dp,

    /**
     * The height of the Today hero.
     *
     * The hero grows with the window, because the week's progress and the primary action both
     * have to fit above the fold on a phone and there is no reason to leave the space empty on a
     * tablet.
     */
    val heroHeight: Dp = 168.dp,
)

/**
 * The responsive metrics for one window size.
 *
 * A pure function of the size class, so the same window always resolves to the same numbers and
 * a preview can ask for a tablet's metrics without a device.
 */
fun pulseLayout(size: PulseWindowSize): PulseLayout = when (size) {
    PulseWindowSize.COMPACT -> PulseLayout(
        contentMaxWidth = 760.dp,
        statTileMinWidth = 124.dp,
        cardMinWidth = 196.dp,
        tileMinWidth = 140.dp,
        heroHeight = 168.dp,
    )

    PulseWindowSize.MEDIUM -> PulseLayout(
        contentMaxWidth = 760.dp,
        statTileMinWidth = 124.dp,
        cardMinWidth = 196.dp,
        tileMinWidth = 140.dp,
        heroHeight = 200.dp,
    )

    PulseWindowSize.EXPANDED -> PulseLayout(
        contentMaxWidth = 760.dp,
        statTileMinWidth = 124.dp,
        cardMinWidth = 196.dp,
        tileMinWidth = 140.dp,
        heroHeight = 216.dp,
    )
}

/** The responsive metrics of the current window. */
val LocalPulseLayout = compositionLocalOf { pulseLayout(PulseWindowSize.COMPACT) }

/**
 * The window size class of the window the app is currently in.
 *
 * Reads the configuration rather than the screen, so it is correct in split-screen and on a
 * foldable, where the app's window is smaller than the panel it is drawn on.
 */
@Composable
fun currentWindowSize(): PulseWindowSize = pulseWindowSize(LocalConfiguration.current.screenWidthDp)
