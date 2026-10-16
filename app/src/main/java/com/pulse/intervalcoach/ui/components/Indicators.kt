package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.engine.PhaseKind
import com.pulse.intervalcoach.data.WorkoutCategories
import com.pulse.intervalcoach.data.WorkoutCategory
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseElevation
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseColors
import com.pulse.intervalcoach.ui.theme.PulseFonts
import com.pulse.intervalcoach.ui.theme.PulseType
import com.pulse.intervalcoach.ui.theme.TABULAR_FIGURES

/**
 * Chips, badges, stat tiles and banners — the small pieces that carry state.
 *
 * Colour appears here and in the category tiles, and nowhere else in the chrome: a phase chip, a
 * category pill and a status badge are the places a hue *is* the message. Every chip is a *tinted*
 * pill — hue for the glyph, its pre-composited tint for the fill, a hairline to keep the edge crisp
 * on a white card — rather than a saturated block, which keeps a screen with twenty chips calm and
 * keeps the measured contrast in `docs/CONTRAST.md` exactly true.
 */

/** Tone shared by [StatusBadge] and [InfoBanner]. */
enum class PulseTone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }

/**
 * The one label chip.
 *
 * Every pill in the app — a phase, a category, a piece of metadata, a status — is this component
 * with a different colour and a different glyph. They share one padding, one radius, one type
 * size and one dot, so a chip in a list row and a chip on a card are unmistakably the same object.
 * A chip that carries an action is *not* this one: that is a [PulseChoiceChip], a control, and it
 * is drawn as a rectangle.
 */
@Composable
fun LabelChip(
    text: String?,
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    glyph: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier,
        shape = LocalPulseShapes.current.chip,
        color = container,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.chipPaddingHorizontal,
                vertical = dimens.chipPaddingVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                glyph()
                if (text != null) Spacer(Modifier.width(dimens.chipLabelGap))
            }
            if (text != null) {
                Text(
                    text = text,
                    style = textStyle,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                if (text != null || glyph != null) Spacer(Modifier.width(dimens.chipLabelGap))
                trailing()
            }
        }
    }
}

/**
 * The interval-phase label. A tinted pill with a phase dot, so the meaning survives greyscale and
 * colour-blindness.
 */
@Composable
fun PhaseChip(kind: PhaseKind, name: String?, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val accent = colors.phaseColor(kind)
    LabelChip(
        text = name ?: PhaseKind.defaultLabel(kind),
        container = colors.phaseTint(kind),
        contentColor = accent,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(dimens.statusDotSize)
                .background(accent, CircleShape),
        )
    }
}

/** Neutral metadata pill: interval count, "starter", equipment. Sentence case by design. */
@Composable
fun NeutralChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    LabelChip(
        text = text,
        container = colors.neutralFill,
        contentColor = colors.textSecondary,
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(dimens.chipGlyphSize),
            )
        }
    }
}

/**
 * A workout category pill: the category's glyph in its own hue on its own tint.
 *
 * This is the whole point of the category registry — one record, one look, on a card, in a filter
 * rail and in the player header.
 */
@Composable
fun CategoryChip(
    category: WorkoutCategory,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val hue = colors.categoryColor(category.hue)
    LabelChip(
        text = if (showTitle) category.title else null,
        container = colors.categoryTint(category.hue),
        contentColor = hue,
        modifier = modifier,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = hue,
            modifier = Modifier.size(dimens.chipGlyphSize),
        )
    }
}

/**
 * A compact state badge — "connected", "ready", "unavailable". The dot carries the colour; the word
 * next to it is what communicates, so the badge still reads in greyscale.
 */
@Composable
fun StatusBadge(
    text: String,
    tone: PulseTone,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    LabelChip(
        text = text,
        container = toneTint(colors, tone),
        contentColor = colors.textSecondary,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(dimens.statusDotSize)
                .background(toneAccent(colors, tone), CircleShape),
        )
    }
}

/**
 * A measured value — the dashboard's smallest unit of data.
 *
 * The label is a quiet 12 sp line, the value is a tabular figure one step larger than the body copy,
 * and the caption explains it if asked. A row of tiles therefore reads like a table: same height,
 * same baseline, no jitter when a number changes.
 *
 * A tile is a **card** — the same surface, the same radius, the same hairline and the same shadow
 * as anything else on the page — with a number in it. It used to be a flat rectangle with a border
 * and no shadow, which is why a tile and a card sitting next to each other looked like two
 * different products.
 *
 * [accent] and [icon] are optional and mutually compatible: a tile with neither is a number, a tile
 * with both is a headline.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    caption: String? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val elevation = LocalPulseElevation.current
    val shape = LocalPulseShapes.current.card
    val valueColor = accent ?: colors.textPrimary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation.level1, shape),
        shape = shape,
        color = colors.surface,
        contentColor = colors.textPrimary,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.statTileHeight)
                .padding(horizontal = dimens.m, vertical = dimens.m),
            verticalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent ?: colors.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(dimens.xs))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(
                text = value,
                style = PulseType.NumericMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A micro-stat: a label over a value, with no surface of its own.
 *
 * The dense counterpart of [StatTile] — the readout you glance at from a metre away rather than
 * read. It has no card because a card in a dense readout is noise, but it is still one component:
 * the same label type, the same tabular value, the same muted label, wherever it appears.
 */
@Composable
fun PulseMicroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    valueColor: Color? = null,
) {
    val colors = LocalPulseColors.current
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = PulseType.NumericSmall,
            color = valueColor ?: colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * An inline message: an explanation, a warning, a failed import. The tone lives in the glyph, the
 * container tint and the left edge while the sentence itself stays in primary text, so a long
 * explanation is never rendered in a low-contrast accent colour.
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: PulseTone = PulseTone.NEUTRAL,
    title: String? = null,
    icon: ImageVector? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val accent = toneAccent(colors, tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.card,
        color = toneTint(colors, tone),
        border = BorderStroke(
            dimens.borderWidth,
            if (tone == PulseTone.NEUTRAL) colors.border else accent.copy(alpha = 0.32f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.m),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.width(dimens.s))
            } else {
                Box(
                    Modifier
                        .padding(top = dimens.statusDotSize)
                        .size(dimens.statusDotSize)
                        .background(accent, CircleShape),
                )
                Spacer(Modifier.width(dimens.m))
            }
            Column(verticalArrangement = Arrangement.spacedBy(dimens.microGap)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

private fun toneAccent(colors: PulseColors, tone: PulseTone): Color = when (tone) {
    PulseTone.NEUTRAL -> colors.textMuted
    PulseTone.INFO -> colors.info
    PulseTone.SUCCESS -> colors.success
    PulseTone.WARNING -> colors.warning
    PulseTone.DANGER -> colors.error
}

private fun toneTint(colors: PulseColors, tone: PulseTone): Color = when (tone) {
    PulseTone.NEUTRAL -> colors.surfaceSunken
    PulseTone.INFO -> colors.infoTint
    PulseTone.SUCCESS -> colors.successTint
    PulseTone.WARNING -> colors.warningTint
    PulseTone.DANGER -> colors.dangerTint
}

/**
 * A label/value pair on one line — the smallest row of a dashboard table.
 *
 * Screens stack these inside one [PulseCard] with [PulseDivider]s between them instead of stacking
 * five separate stat cards: the numbers still align in a column, and the page stops looking like a
 * pile of boxes. The value is set in tabular figures, so a column of them stays flush.
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    icon: ImageVector? = null,
    caption: String? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.metricRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(dimens.s))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.microGap)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(dimens.m))
        Text(
            text = value,
            style = PulseType.NumericSmall.copy(fontFamily = PulseFonts.Numeric, fontFeatureSettings = TABULAR_FIGURES),
            color = valueColor ?: colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The category picker: a grid of every registered category, in the registry's own order.
 *
 * It is generated from [WorkoutCategories.all], shows each category's glyph in its own hue, and
 * uses the same category identity as the library — so picking a category in the builder and reading
 * one in the library are visibly the same act.
 *
 * [enabledFor] lets a caller grey out categories the current builder cannot express yet.
 */
@Composable
fun CategoryPicker(
    selected: WorkoutCategory,
    onSelect: (WorkoutCategory) -> Unit,
    modifier: Modifier = Modifier,
    enabledFor: (WorkoutCategory) -> Boolean = { true },
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shapes = LocalPulseShapes.current
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "Category",
            style = PulseType.SectionLabel,
            color = colors.textMuted,
            modifier = Modifier.padding(start = dimens.xs, bottom = dimens.s),
        )
        Column(verticalArrangement = Arrangement.spacedBy(dimens.chipGap)) {
            WorkoutCategories.all.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { category ->
                        val isSelected = category.key == selected.key
                        val enabled = enabledFor(category)
                        val hue = colors.categoryColor(category.hue)
                        Surface(
                            onClick = { if (enabled) onSelect(category) },
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = shapes.card,
                            color = when {
                                isSelected -> colors.categoryTint(category.hue)
                                else -> colors.surface
                            },
                            border = BorderStroke(
                                dimens.borderWidth,
                                if (isSelected) hue.copy(alpha = 0.44f) else colors.border,
                            ),
                            contentColor = if (enabled) hue else colors.textDisabled,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = if (enabled) hue else colors.textDisabled,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(dimens.s))
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected && enabled) hue else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = hue,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    // A row of one would otherwise stretch its single tile to full width, which reads
                    // as a full-width button rather than as half of a pair.
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
