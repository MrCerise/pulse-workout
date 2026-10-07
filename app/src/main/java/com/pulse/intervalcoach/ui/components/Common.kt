package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.engine.PhaseKind
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens

/**
 * The shared building blocks every screen is made of.
 *
 * Two rules hold throughout, because both were broken before:
 *  - **nothing may overflow its container.** Chips scroll, stepper values are weighted, labels are
 *    ellipsised. A row of 15 options used to run off the right edge of the screen, unreachable.
 *  - **touch targets stay at or above 44 dp** and every ripple is clipped to the shape it draws in.
 */

@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shape = RoundedCornerShape(dimens.cardRadius)
    // Card's own onClick overload clips the ripple to `shape`; applying Modifier.clickable outside
    // the card used to paint a square ripple over rounded corners.
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Box(Modifier.padding(dimens.l)) { content() }
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Box(Modifier.padding(dimens.l)) { content() }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val dimens = LocalPulseDimens.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalPulseColors.current.textSecondary,
        modifier = modifier.padding(top = dimens.l, bottom = dimens.s),
    )
}

@Composable
fun PhaseChip(kind: PhaseKind, name: String?, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val fill = colors.phaseColor(kind)
    val label = name ?: PhaseKind.defaultLabel(kind)
    Row(
        modifier = modifier
            .background(fill, RoundedCornerShape(LocalPulseDimens.current.chipRadius))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "${PhaseKind.defaultLabel(kind)}${name?.let { ": $it" } ?: ""}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(colors.onAccent.copy(alpha = 0.75f), CircleShape)
                .clearAndSetSemantics { }
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Label for things that are *not* a phase — workout type, "starter", counts.
 *
 * Using [PhaseChip] for these painted them in the work-green "effort" colour, which broke the rule
 * that a phase colour always means the same thing.
 */
@Composable
fun NeutralChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = LocalPulseColors.current
    Row(
        modifier = modifier
            .background(colors.neutralFill, RoundedCornerShape(LocalPulseDimens.current.chipRadius))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textSecondary)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onNeutralFill,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(
        modifier = modifier
            .background(colors.surfaceRaised, RoundedCornerShape(dimens.cardRadius - 4.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(dimens.xs))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = accent ?: colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Colour-coded session timeline: one segment per interval, with the current interval's progress
 * filled in.
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
    height: Dp = 12.dp,
) {
    val colors = LocalPulseColors.current
    val description = if (kinds.isEmpty()) {
        "No intervals"
    } else {
        "Session timeline, interval ${(currentIndex + 1).coerceIn(1, kinds.size)} of ${kinds.size}"
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
    ) {
        if (kinds.isEmpty()) return@Canvas
        val gap = if (kinds.size > 40) 0f else 2.dp.toPx()
        val segmentWidth = (size.width - gap * (kinds.size - 1)) / kinds.size
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        var x = 0f
        kinds.forEachIndexed { index, kind ->
            val fill = colors.phaseColor(kind)
            val width = segmentWidth.coerceAtLeast(1f)
            when {
                index < currentIndex -> drawRoundRect(
                    color = fill.copy(alpha = 0.5f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(width, size.height),
                    cornerRadius = radius,
                )
                index == currentIndex -> {
                    drawRoundRect(
                        color = fill.copy(alpha = 0.3f),
                        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(width, size.height),
                        cornerRadius = radius,
                    )
                    // The progress marker the parameter always promised.
                    val done = progressInStep.coerceIn(0f, 1f)
                    if (done > 0f) {
                        drawRoundRect(
                            color = fill,
                            topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                            size = androidx.compose.ui.geometry.Size(
                                (width * done).coerceAtLeast(size.height),
                                size.height,
                            ),
                            cornerRadius = radius,
                        )
                    }
                }
                else -> drawRoundRect(
                    color = fill.copy(alpha = 0.22f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(width, size.height),
                    cornerRadius = radius,
                )
            }
            x += width + gap
        }
    }
}

/** Circular progress used by the player around the big countdown. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(colors.surfaceRaised, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(dimens.l))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(dimens.s))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(dimens.l))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalPulseColors.current.work)
    }
}

/** Tonal banner. [warning] switches the icon and the accent used for the border. */
@Composable
fun InfoBanner(
    text: String,
    tone: Color? = null,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    val colors = LocalPulseColors.current
    val accent = tone ?: if (warning) colors.prepare else colors.textSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (warning) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val colors = LocalPulseColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) colors.destructive else colors.work)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/**
 * +/- stepper for durations.
 *
 * The value is weighted and single-line: two steppers side by side on a 360 dp screen leave roughly
 * 150 dp each, and an unweighted label used to push the digits out of the row entirely.
 */
@Composable
fun DurationStepper(
    label: String,
    millis: Long,
    modifier: Modifier = Modifier,
    stepMillis: Long = 5_000L,
    maxMillis: Long = 24L * 60L * 60L * 1000L,
    allowZero: Boolean = true,
    enabled: Boolean = true,
    onChange: (Long) -> Unit,
) {
    val colors = LocalPulseColors.current
    val min = if (allowZero) 0L else stepMillis
    StepperField(
        label = label,
        value = formatDuration(millis),
        valueDescription = "$label ${formatDuration(millis)}",
        decreaseDescription = "Decrease $label",
        increaseDescription = "Increase $label",
        atMin = millis <= min,
        atMax = millis >= maxMillis,
        enabled = enabled,
        modifier = modifier,
        onDecrease = { onChange((millis - stepMillis).coerceIn(min, maxMillis)) },
        onIncrease = { onChange((millis + stepMillis).coerceIn(min, maxMillis)) },
        valueColor = colors.textPrimary,
    )
}

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 200,
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
) {
    val colors = LocalPulseColors.current
    StepperField(
        label = label,
        value = value.toString(),
        valueDescription = "$label $value",
        decreaseDescription = "Decrease $label",
        increaseDescription = "Increase $label",
        atMin = value <= min,
        atMax = value >= max,
        enabled = enabled,
        modifier = modifier,
        onDecrease = { onChange((value - 1).coerceIn(min, max)) },
        onIncrease = { onChange((value + 1).coerceIn(min, max)) },
        valueColor = colors.textPrimary,
    )
}

@Composable
private fun StepperField(
    label: String,
    value: String,
    valueDescription: String,
    decreaseDescription: String,
    increaseDescription: String,
    atMin: Boolean,
    atMax: Boolean,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceRaised, RoundedCornerShape(14.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDecrease,
                enabled = enabled && !atMin,
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Remove, contentDescription = decreaseDescription) }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = valueDescription },
            )
            IconButton(
                onClick = onIncrease,
                enabled = enabled && !atMax,
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = increaseDescription) }
        }
    }
}

/**
 * Single-choice row used instead of a spinner so every option is one tap away.
 *
 * The row scrolls: with 15 workout types or 8 cue sounds an unscrollable row pushed the later
 * options past the right edge of the screen, where they could not be tapped at all.
 */
@Composable
fun <T> OptionRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    Column(modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) colors.work else colors.surfaceRaised,
                    border = if (isSelected) {
                        null
                    } else {
                        BorderStroke(1.dp, colors.outlineVariant)
                    },
                    modifier = Modifier.clickable { onSelect(value) },
                ) {
                    Box(Modifier.defaultMinSize(minHeight = 44.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) colors.onAccent else colors.textPrimary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    hint: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalPulseColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .defaultMinSize(minHeight = LocalPulseDimens.current.minTouchTarget)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** Shared "start this workout" affordance so the row of buttons looks the same everywhere. */
@Composable
fun PrimaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }
}

@Composable
fun SecondaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun VerticalSpacer(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun rememberToggle(initial: Boolean = false): Pair<Boolean, () -> Unit> {
    var value by remember { mutableStateOf(initial) }
    return value to { value = !value }
}
