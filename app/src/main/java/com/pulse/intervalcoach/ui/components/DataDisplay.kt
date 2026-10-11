package com.pulse.intervalcoach.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
 * The one flourish Prism allows here is that the ring's sweep may be drawn with the accent gradient:
 * it is the moving part of a running session, and a gradient sweep reads as motion rather than as
 * decoration.
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
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        kinds.forEachIndexed { index, kind ->
            val x = index * (slot + gap)
            val color = colors.phaseColor(kind)
            val alpha = when {
                index == currentIndex -> 0.40f
                index < currentIndex -> 0.92f
                else -> 0.24f
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
 * surface. Pass [gradient] to draw the sweep with the accent gradient — used by the player's live
 * ring and nowhere else.
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    gradient: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "ring")
    val brush: Brush = if (gradient) {
        Brush.sweepGradient(listOf(color, colors.accentAlt, color), Offset(0.5f, 0.5f))
    } else {
        SolidColor(color)
    }
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
 * Bars carry a soft vertical gradient so a tall bar reads as *more* rather than merely as *longer*.
 */
@Composable
fun ActivityChart(
    bars: List<ActivityBar>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
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
            val barWidth = (slot - 6.dp.toPx()).coerceAtLeast(3.dp.toPx())
            bars.forEachIndexed { index, bar ->
                val fraction = bar.value.toFloat() / max.toFloat()
                val empty = bar.value <= 0L
                val barHeight = if (empty) {
                    2.dp.toPx()
                } else {
                    (size.height * fraction).coerceAtLeast(6.dp.toPx())
                }
                val x = index * slot + (slot - barWidth) / 2
                val top = size.height - barHeight
                if (empty) {
                    drawRoundRect(
                        color = colors.track,
                        topLeft = Offset(x, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                } else {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(color, color.copy(alpha = 0.62f)),
                            startY = top,
                            endY = size.height,
                        ),
                        topLeft = Offset(x, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2.4f, barWidth / 2.4f),
                    )
                }
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

/**
 * A horizontal progress bar with the accent gradient. Used for the weekly goal and for the player's
 * session progress, where a ring would compete with the countdown.
 */
@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    trackColor: Color = LocalPulseColors.current.track,
) {
    val colors = LocalPulseColors.current
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "progress")
    val radius = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
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
                .background(Brush.horizontalGradient(listOf(colors.accent, colors.accentAlt))),
        )
    }
}

/** Vertical breathing room between two stacked charts or a chart and its caption. */
@Composable
fun ChartSpacer() = Spacer(Modifier.height(LocalPulseDimens.current.m))

/** Shared motion duration for the two data animations, so they never disagree. */
@Composable
fun chartAnimationDuration(): Int = LocalPulseMotion.current.standard()
