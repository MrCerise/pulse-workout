package com.pulse.intervalcoach.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.engine.PhaseKind

/**
 * Data visualisation. Three primitives cover the whole application: a session timeline, a progress
 * ring, and a bar chart. All three draw from the phase palette, so a green segment in a timeline and
 * a green bar in a chart mean the same thing.
 */

/**
 * One segment per interval, with the current interval's progress filled in.
 *
 * Segments stop getting gaps once the workout is too dense for them, so a 300-interval plan still
 * reads as a bar instead of dissolving into dots.
 */
@Composable
fun TimelineBar(
    kinds: List<PhaseKind>,
    currentIndex: Int,
    progressInStep: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val colors = LocalPulseColors.current
    val description = if (kinds.isEmpty()) {
        "No intervals"
    } else {
        "Interval ${currentIndex + 1} of ${kinds.size}"
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        if (kinds.isEmpty()) return@Canvas
        val gap = if (kinds.size <= 40) size.width * 0.008f else 0f
        val slot = (size.width - gap * (kinds.size - 1)) / kinds.size
        kinds.forEachIndexed { index, kind ->
            val x = index * (slot + gap)
            val color = colors.phaseColor(kind)
            val alpha = when {
                index == currentIndex -> 0.35f
                index < currentIndex -> 0.85f
                else -> 0.22f
            }
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, 0f),
                size = Size(slot, size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            )
            if (index == currentIndex && progressInStep > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(slot * progressInStep.coerceIn(0f, 1f), size.height),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
            }
        }
    }
}

/**
 * A progress ring. The track is a token, not a faded accent, so the ring stays legible on every
 * surface; the stroke is solid — one colour, one meaning.
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val sweep = 360f * animated
            if (sweep > 0f) {
                drawArc(
                    brush = SolidColor(color),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** One day in an [ActivityChart]. */
data class ActivityBar(val label: String, val value: Long)

/**
 * Daily activity bars. Bars sit in equal-width slots so they line up with the labels underneath;
 * days with no recorded activity render as a two-pixel stub rather than disappearing, which is what
 * makes a zero day read as a zero instead of as missing data.
 */
@Composable
fun ActivityChart(
    bars: List<ActivityBar>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    showLabels: Boolean = true,
    labelEvery: Int = 1,
    emptyLabel: String = "No sessions recorded in this period.",
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    if (bars.isEmpty()) {
        Text(
            text = emptyLabel,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = modifier,
        )
        return
    }
    val max = bars.maxOf { it.value }.coerceAtLeast(1L)
    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = "Activity over ${bars.size} days" },
        ) {
            val slot = size.width / bars.size
            val barWidth = (slot - 4.dp.toPx()).coerceAtLeast(2.dp.toPx())
            bars.forEachIndexed { index, bar ->
                val fraction = bar.value.toFloat() / max.toFloat()
                val empty = bar.value <= 0L
                val barHeight = if (empty) {
                    2.dp.toPx()
                } else {
                    (size.height * fraction).coerceAtLeast(4.dp.toPx())
                }
                val x = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (empty) colors.track else colors.work,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f),
                )
            }
        }
        if (showLabels) {
            Spacer(Modifier.height(dimens.s))
            Row(Modifier.fillMaxWidth()) {
                bars.forEachIndexed { index, bar ->
                    val label = if (index % labelEvery == 0 || index == bars.lastIndex) bar.label else ""
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bar.value > 0) colors.textSecondary else colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A row of equally sized tiles, so the same three stats align across every screen. */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.s),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
