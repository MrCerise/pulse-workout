package com.pulse.intervalcoach.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pulse.engine.PhaseKind
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseMotion

/**
 * Data visualisation. Three primitives cover the whole application: a session timeline, a progress
 * ring, and a bar chart. All three draw from the phase palette, so a green segment in a timeline and
 * a green bar in a chart mean the same thing.
 *
 * Nothing here is decorative. The ring is a single flat stroke rather than a gradient sweep, the
 * bars are honest rectangles with one corner radius, and the timeline is a row of segments: a
 * dashboard earns its polish from alignment and restraint, not from ornament.
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
    height: Dp = 8.dp,
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
        val gap = if (kinds.size <= 60) size.width * 0.004f else 0f
        val slot = (size.width - gap * (kinds.size - 1)) / kinds.size
        val radius = CornerRadius(size.height * 0.34f, size.height * 0.34f)
        kinds.forEachIndexed { index, kind ->
            val x = index * (slot + gap)
            val color = colors.phaseColor(kind)
            val alpha = when {
                index == currentIndex -> 0.34f
                index < currentIndex -> 0.95f
                else -> 0.20f
            }
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, 0f),
                size = Size(slot, size.height),
                cornerRadius = radius,
            )
            if (index == currentIndex && progressInStep > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(slot * progressInStep.coerceIn(0f, 1f), size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/**
 * A progress ring. The track is a token, not a faded accent, so the ring stays legible on every
 * surface, and the sweep is a flat stroke of one colour so the ring can sit next to a coloured chart
 * without the two competing.
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    showRemainder: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = progressSpec(),
        label = "ring",
    )
    val brush = SolidColor(color)
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
                    brush = brush,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            // A second, dimmer arc behind the live sweep: it marks the interval's own end without
            // adding a gradient, a glow or a second colour to the panel.
            if (showRemainder && sweep in 0.01f..359.99f) {
                drawArc(
                    color = colors.accent.copy(alpha = 0.18f),
                    startAngle = -90f + sweep,
                    sweepAngle = 360f - sweep,
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
 *
 * Bars are flat fills with a 3 dp top radius, and a hairline baseline closes the chart, so the plot
 * reads as a table of days rather than as decoration.
 */
@Composable
fun ActivityChart(
    bars: List<ActivityBar>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    showLabels: Boolean = true,
    labelEvery: Int = 1,
    emptyLabel: String = "No sessions recorded in this period.",
    color: Color = LocalPulseColors.current.work,
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
            val barWidth = (slot - 4.dp.toPx()).coerceAtLeast(3.dp.toPx())
            val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            bars.forEachIndexed { index, bar ->
                val fraction = bar.value.toFloat() / max.toFloat()
                val empty = bar.value <= 0L
                val barHeight = if (empty) {
                    2.dp.toPx()
                } else {
                    (size.height * fraction).coerceAtLeast(4.dp.toPx())
                }
                val x = index * slot + (slot - barWidth) / 2
                val top = size.height - barHeight
                drawRoundRect(
                    color = if (empty) colors.track else color,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
            drawLine(
                color = colors.border,
                start = Offset(0f, size.height - 0.5f),
                end = Offset(size.width, size.height - 0.5f),
                strokeWidth = 1f,
            )
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

/**
 * A horizontal progress bar: a flat fill in a rounded track.
 *
 * Used for the weekly goal and for progress that would compete with a countdown if it were a
 * ring. The fill is the accent by default and takes a colour when the bar belongs to a category —
 * one bar component with a colour, rather than a second bar component drawn by hand, which is how
 * two bars on two pages end up with two different radii.
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = LocalPulseColors.current.track,
    fillColor: Color = LocalPulseColors.current.accent,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = progressSpec(),
        label = "progress",
    )
    val radius = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Progress ${(animated * 100).toInt()} percent" }
            .clip(radius)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.coerceIn(0.001f, 1f))
                .height(height)
                .background(fillColor),
        )
    }
}

/**
 * The animation spec every data visual shares. It reads the reduced-motion switch through
 * [LocalPulseMotion], so a user who asked the system for less motion gets an instant value instead
 * of a sweep — an accessibility setting that still animates is not an accessibility setting.
 */
@Composable
private fun progressSpec(): AnimationSpec<Float> =
    tween(durationMillis = LocalPulseMotion.current.standard(), easing = LinearOutSlowInEasing)
