package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseColors
import com.pulse.intervalcoach.ui.theme.PulseFonts
import com.pulse.intervalcoach.ui.theme.TABULAR_FIGURES

/**
 * Chips, badges, stat tiles and banners — the small pieces that carry state.
 *
 * Colour appears here and nowhere else in the chrome: a phase chip is the one place an interval's
 * meaning is expressed as hue. Every chip is a *tinted* pill (phase colour for the label, its
 * pre-composited tint for the fill) rather than a saturated block, which keeps a screen with twenty
 * chips calm and keeps the measured contrast in `docs/CONTRAST.md` exactly true.
 */

/** Tone shared by [StatusBadge] and [InfoBanner]. */
enum class PulseTone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }

/**
 * The interval-phase label. Uppercase, tracked, and paired with a phase dot so the meaning survives
 * greyscale and colour-blindness.
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
        border = BorderStroke(dimens.borderWidth, accent.copy(alpha = if (colors.isDark) 0.35f else 0.30f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.s, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(accent, CircleShape))
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

/** Neutral metadata pill: workout type, interval count, "starter". Sentence case by design. */
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
            modifier = Modifier.padding(horizontal = dimens.s, vertical = 4.dp),
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
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.s, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(toneAccent(colors, tone), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/**
 * A measured value. Labels are uppercase and muted; values are monospaced and tabular so a column of
 * tiles scans like a table instead of a jumble of widths.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier,
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.s),
            verticalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = PulseFonts.Mono,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                color = accent ?: colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        shape = LocalPulseShapes.current.control,
        color = toneTint(colors, tone),
        border = BorderStroke(
            dimens.borderWidth,
            if (tone == PulseTone.NEUTRAL) colors.border else accent.copy(alpha = 0.30f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(dimens.m),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.width(dimens.s))
            } else {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(6.dp)
                        .background(accent, CircleShape),
                )
                Spacer(Modifier.width(dimens.m))
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
