package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseLayout
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseType

/**
 * The responsive layout kit.
 *
 * Three components live here, and they exist for one reason: **the same idea must look the same
 * wherever it appears.** A category badge on the Today page, a row in the library and the header of
 * a workout's own page are one idea, so they are one component at three sizes — not three
 * components that happen to be similar. The same is true of a row of statistics: the tiles wrap
 * when the window is narrow and sit side by side when it is wide, but they are the same tiles
 * either way, with the same gap and the same height.
 *
 * Everything here reads its colours from `LocalPulseColors`, its sizes from `LocalPulseDimens`
 * and its responsive numbers from `LocalPulseLayout`, so a component can never drift away from
 * the rest of the interface.
 */

/**
 * The sizes an icon tile comes in.
 *
 * One component, four sizes, one radius per size taken from the shape scale. A tile at the head of
 * a list row, a tile on a grid card, a tile on a workout's page and a tile on a category page are
 * the same object at four scales — which is why they now share a shape, a radius and a tint
 * treatment instead of each drawing its own.
 */
enum class PulseIconTileSize(
    /** The side of the square. */
    val box: Dp,
    /** The side of the glyph inside it. */
    val glyph: Dp,
) {
    /** At the head of a list row. */
    INLINE(32.dp, 17.dp),

    /** On a card in a grid. */
    SMALL(40.dp, 22.dp),

    /** At the head of a page. */
    MEDIUM(48.dp, 26.dp),

    /** The hero tile of a category page. */
    LARGE(64.dp, 34.dp),
}

/**
 * A tinted rounded square that identifies a row, a card or a page.
 *
 * The tint carries the meaning — the accent for something live, a category hue for something that
 * belongs to a training family, a warning tint for something scheduled — and the glyph sits in it.
 * The tile is never a decoration: it is the one place a hue *is* the message.
 */
@Composable
fun PulseIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    container: Color? = null,
    size: PulseIconTileSize = PulseIconTileSize.INLINE,
) {
    val colors = LocalPulseColors.current
    val shapes = LocalPulseShapes.current
    // The radius comes from the shape scale: a square that small is a control, a square that big
    // is a card, and neither invents a radius of its own.
    val shape = if (size == PulseIconTileSize.LARGE) shapes.card else shapes.control
    Box(
        modifier = modifier
            .size(size.box)
            .clip(shape)
            .background(container ?: colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: colors.textSecondary,
            modifier = Modifier.size(size.glyph),
        )
    }
}

/**
 * A one-tap start tile: the category's glyph in its own hue on its own tint.
 *
 * The **same** tile appears in the Today page's category rail and in its quick-start grid: the
 * rail's is narrower because it scrolls sideways, and nothing else about them differs — same
 * shape, same padding, same type, same icon. Two tiles that start the same kind of session have
 * to look like the same tile, or the page reads as two designers' work.
 */
@Composable
fun PulseStartTile(
    title: String,
    icon: ImageVector,
    hue: Color,
    tint: Color,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = LocalPulseShapes.current.card,
        color = tint,
        contentColor = hue,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.m),
            verticalArrangement = Arrangement.spacedBy(dimens.s),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = hue,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = duration,
                style = PulseType.NumericSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * One tile's content: a label, a value and, optionally, an accent and a caption.
 *
 * The value is the point, so the label is quiet and the caption is quieter still. A tile with an
 * accent is a headline; a tile without one is a number.
 */
data class PulseStat(
    val label: String,
    val value: String,
    val accent: Color? = null,
    val icon: ImageVector? = null,
    val caption: String? = null,
)

/**
 * A row of statistics that lays itself out for the window it is given.
 *
 * Three tiles in a row is right on a tablet and wrong on a phone: at 360 dp the phone gives each
 * tile about 100 dp, which is not enough to hold "Longest streak" and a value without truncating
 * one of them. So the grid asks the width it actually has, fits as many tiles as will hold their
 * minimum, and wraps the rest onto the next line — two tiles and one on a phone, three across on a
 * tablet — with the same gap, the same height and the same tiles in both cases.
 */
@Composable
fun PulseStatGrid(
    stats: List<PulseStat>,
    modifier: Modifier = Modifier,
    minTileWidth: Dp? = null,
) {
    if (stats.isEmpty()) return
    val dimens = LocalPulseDimens.current
    val gap = dimens.cardGap
    val tileWidth = minTileWidth ?: LocalPulseLayout.current.statTileMinWidth
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // How many tiles fit: the width available, divided by the narrowest a tile may be, with
        // one gap fewer than there are tiles.
        val columns = ((maxWidth + gap) / (tileWidth + gap)).toInt().coerceIn(1, stats.size)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            stats.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The row takes the height of its tallest tile, so the tiles in it share
                        // one baseline instead of stepping down.
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEach { stat ->
                        StatTile(
                            label = stat.label,
                            value = stat.value,
                            accent = stat.accent,
                            icon = stat.icon,
                            caption = stat.caption,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    // A short last row keeps the grid's right edge aligned with the rows above it.
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * A grid of equally-sized cells that decides its own column count.
 *
 * The Today page's presets are two across on a phone and four across on a tablet; the library's
 * cards fill the width. Both are the same idea — a row of things that should sit side by side when
 * there is room and stack when there is not — so both are this component with a different
 * [minCellWidth]. A screen that lays out two columns by hand is a screen that will still be two
 * columns on a tablet, with two very wide tiles and a lot of empty page beside them.
 */
@Composable
fun PulseCellGrid(
    count: Int,
    modifier: Modifier = Modifier,
    minCellWidth: Dp? = null,
    gap: Dp? = null,
    content: @Composable RowScope.(index: Int) -> Unit,
) {
    if (count <= 0) return
    val dimens = LocalPulseDimens.current
    val cellWidth = minCellWidth ?: LocalPulseLayout.current.tileMinWidth
    val cellGap = gap ?: dimens.cardGap
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // One gap fewer than there are cells: the gap between the last cell of a row and the first
        // cell of the next is not a gap inside a row.
        val columns = ((maxWidth + cellGap) / (cellWidth + cellGap)).toInt().coerceIn(1, count)
        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
            var start = 0
            while (start < count) {
                val end = minOf(start + columns, count)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(cellGap),
                ) {
                    for (index in start until end) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            content(index)
                        }
                    }
                    // A short last row keeps the grid's right edge aligned with the rows above it.
                    repeat(columns - (end - start)) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                start = end
            }
        }
    }
}

/**
 * The padding every scrolling page's content sits inside.
 *
 * One function, one gutter: the left edge of a card lands in the same place on every destination,
 * and — because the top padding is the same too — every page's title sits at the same height
 * below the breadcrumb bar. A page that invents its own padding is a page whose heading is a few
 * pixels higher or lower than every other heading, which is the kind of difference a person
 * notices without being able to say what it is.
 */
@Composable
fun pageContentPadding(
    top: Dp? = null,
    bottom: Dp? = null,
): PaddingValues {
    val dimens = LocalPulseDimens.current
    return PaddingValues(
        start = dimens.pagePadding,
        end = dimens.pagePadding,
        top = top ?: dimens.cardGap,
        bottom = bottom ?: dimens.huge,
    )
}
