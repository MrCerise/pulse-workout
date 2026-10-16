package com.mrcerise.intervalcoach.ui.components

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.engine.PhaseKind
import com.mrcerise.intervalcoach.data.WorkoutCategories
import com.mrcerise.intervalcoach.data.WorkoutCategory
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseColors
import com.mrcerise.intervalcoach.ui.theme.PulseFonts
import com.mrcerise.intervalcoach.ui.theme.PulseType
import com.mrcerise.intervalcoach.ui.theme.TABULAR_FIGURES

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
            modifier = Modifier.padding(horizontal = dimens.s, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
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
            modifier = Modifier.padding(horizontal = dimens.s, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
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
            modifier = Modifier.padding(horizontal = dimens.s, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = hue,
                modifier = Modifier.size(13.dp),
            )
            if (showTitle) {
                Spacer(Modifier.width(5.dp))
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
            modifier = Modifier.padding(horizontal = dimens.s, vertical = 3.dp),
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
 * A measured value — the dashboard's smallest unit of data.
 *
 * The label is a quiet 12 sp line, the value is a tabular figure one step larger than the body copy,
 * and the caption explains it if asked. A row of tiles therefore reads like a table: same height,
 * same baseline, no jitter when a number changes.
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
        shape = LocalPulseShapes.current.card,
        color = colors.surface,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.m),
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
                        .padding(top = 6.dp)
                        .size(7.dp)
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

/** Category picker: one drop-down row (same style as PulseSelect) opening the shared bottom sheet. */
@Composable
fun CategoryPicker(
    selected: WorkoutCategory,
    onSelect: (WorkoutCategory) -> Unit,
    modifier: Modifier = Modifier,
    enabledFor: (WorkoutCategory) -> Boolean = { true },
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    var expanded by remember { mutableStateOf(false) }
    val effective = WorkoutCategories.byKey(selected.key)
    Column(modifier.fillMaxWidth()) {
        Text("Category", style = PulseType.SectionLabel, color = colors.textMuted,
            modifier = Modifier.padding(start = dimens.xs, bottom = dimens.s))
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = LocalPulseShapes.current.control,
            color = colors.surfaceRaised,
            contentColor = colors.textPrimary,
            border = BorderStroke(dimens.borderWidth, colors.borderStrong),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = dimens.buttonHeight).padding(horizontal = dimens.l, vertical = dimens.m),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(effective.icon, contentDescription = null, tint = colors.categoryColor(effective.hue), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(dimens.s))
                Text(effective.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(KeyboardArrowDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
    if (expanded) {
        PulseBottomSheet(title = "Category", onDismiss = { expanded = false }) {
            WorkoutCategories.picker.forEach { category ->
                PulseSheetRow(
                    text = category.title,
                    leadingIcon = category.icon,
                    selected = category.key == effective.key,
                    onClick = { if (enabledFor(category)) { expanded = false; onSelect(category) } },
                )
            }
        }
    }
}
