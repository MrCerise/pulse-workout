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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.engine.PhaseKind
import com.pulse.intervalcoach.data.WorkoutCategories
import com.pulse.intervalcoach.data.WorkoutCategory
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseColors
import com.pulse.intervalcoach.ui.theme.PulseFonts
import com.pulse.intervalcoach.ui.theme.PulseType
import com.pulse.intervalcoach.ui.theme.TABULAR_FIGURES

/**
 * Chips, badges, stat tiles and banners — the small pieces that carry state.
 *
 * Colour appears here and in the category tiles, and nowhere else in the chrome: a phase chip and a
 * category pill are the two places a hue is the message. Every chip is a *tinted* pill — hue for the
 * label, its pre-composited tint for the fill — rather than a saturated block, which keeps a screen
 * with twenty chips calm and keeps the measured contrast in `docs/CONTRAST.md` exactly true.
 */

/** Tone shared by [StatusBadge] and [InfoBanner]. */
enum class PulseTone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }

/**
 * The interval-phase label. A tinted pill with a phase dot, so the meaning survives greyscale and
 * colour-blindness.
 */
@Composable
fun PhaseChip(kind: PhaseKind, name: String?, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val accent = colors.phaseColor(kind)
    val label = name ?: PhaseKind.defaultLabel(kind)
    Surface(
        modifier = modifier,
        shape = LocalPulseShapes.current.chip,
        color = colors.phaseTint(kind),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Neutral metadata pill: interval count, "starter", equipment. Sentence case by design. */
@Composable
fun NeutralChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier,
        shape = LocalPulseShapes.current.chip,
        color = colors.neutralFill,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    Surface(
        modifier = modifier,
        shape = LocalPulseShapes.current.chip,
        color = colors.categoryTint(category.hue),
        contentColor = hue,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = hue,
                modifier = Modifier.size(14.dp),
            )
            if (showTitle) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    Surface(
        modifier = modifier,
        shape = LocalPulseShapes.current.chip,
        color = toneTint(colors, tone),
        contentColor = colors.textSecondary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(toneAccent(colors, tone), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/**
 * A measured value. The label is a tracked uppercase caption; the value is monospaced and tabular so
 * a column of tiles scans like a table instead of a jumble of widths.
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
    val valueColor = accent ?: colors.textPrimary
    Surface(
        modifier = modifier,
        shape = LocalPulseShapes.current.panel,
        color = colors.surfaceRaised,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.l, vertical = dimens.m),
            verticalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent ?: colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(dimens.xs))
                }
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
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
 * An inline message: an explanation, a warning, a failed import. The tone lives in the glyph and the
 * container tint while the sentence itself stays in primary text, so a long explanation is never
 * rendered in a low-contrast accent colour.
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
        shape = LocalPulseShapes.current.panel,
        color = toneTint(colors, tone),
        border = BorderStroke(
            dimens.borderWidth,
            if (tone == PulseTone.NEUTRAL) colors.border else accent.copy(alpha = 0.32f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(dimens.l),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                )
                Spacer(Modifier.width(dimens.m))
            } else {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(7.dp)
                        .background(accent, CircleShape),
                )
                Spacer(Modifier.width(dimens.l))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    PulseTone.NEUTRAL -> colors.surfaceRaised
    PulseTone.INFO -> colors.accentTint
    PulseTone.SUCCESS -> colors.successTint
    PulseTone.WARNING -> colors.warningTint
    PulseTone.DANGER -> colors.dangerTint
}

/** A row of equally sized tiles, so the same three stats align across every screen. */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.m),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Shared padding for a pill's interior. Kept here so both pill components agree. */
val pillPadding: PaddingValues
    @Composable get() = PaddingValues(horizontal = LocalPulseDimens.current.m, vertical = 5.dp)

/** Kept for call sites that build a numeric label by hand. */
val chipNumberStyle = PulseType.NumericSmall.copy(fontFamily = PulseFonts.Mono, fontFeatureSettings = TABULAR_FIGURES)

/**
 * The category picker: a grid of every registered category, in the registry's own order.
 *
 * It replaces what used to be a hard-coded list of enum constants in the builder. That list had to
 * be kept in step with the engine by hand, and it could not show an icon or a colour because neither
 * lived anywhere central. Now the picker is generated from [WorkoutCategories.all], shows each
 * category's glyph in its own hue, and states the chosen category's tagline underneath — so picking
 * a category in the builder and reading one in the library are visibly the same act.
 *
 * [enabledFor] lets a caller grey out categories the current builder cannot express yet.
 */
@Composable
fun CategoryPicker(
    selected: WorkoutCategory,
    onSelect: (WorkoutCategory) -> Unit,
    modifier: Modifier = Modifier,
    enabledFor: (WorkoutCategory) -> Boolean = { true },
    showTagline: Boolean = true,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shapes = LocalPulseShapes.current
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "Category",
            style = PulseType.SectionLabel,
            color = colors.textMuted,
            modifier = Modifier.padding(start = dimens.s, bottom = dimens.s),
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
                                .heightIn(min = 60.dp),
                            shape = shapes.panel,
                            color = when {
                                isSelected -> colors.categoryTint(category.hue)
                                else -> colors.surfaceRaised
                            },
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
                                    modifier = Modifier.size(20.dp),
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
                                        modifier = Modifier.size(18.dp),
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
        if (showTagline) {
            Spacer(Modifier.height(dimens.s))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.categoryTint(selected.hue), shapes.chip)
                    .padding(horizontal = dimens.m, vertical = dimens.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = selected.icon,
                    contentDescription = null,
                    tint = colors.categoryColor(selected.hue),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(dimens.s))
                Text(
                    text = selected.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.categoryColor(selected.hue),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
