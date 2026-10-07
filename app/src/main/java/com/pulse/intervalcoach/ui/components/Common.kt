package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.engine.PhaseKind
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens

@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val shape = RoundedCornerShape(LocalPulseDimens.current.cardRadius)
    val base = modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Card(
        modifier = base,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.padding(LocalPulseDimens.current.l)) { content() }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalPulseColors.current.textSecondary,
        modifier = modifier.padding(top = LocalPulseDimens.current.l, bottom = LocalPulseDimens.current.s),
    )
}

@Composable
fun PhaseChip(kind: PhaseKind, name: String?, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val fill = colors.phaseColor(kind)
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
            text = name ?: PhaseKind.defaultLabel(kind),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatTile(label: String, value: String, accent: Color? = null, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    Column(
        modifier = modifier
            .background(colors.surfaceRaised, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent ?: colors.textPrimary)
    }
}

/** Colour-coded session timeline: one segment per interval, with a progress marker. */
@Composable
fun TimelineBar(
    kinds: List<PhaseKind>,
    currentIndex: Int,
    progressInStep: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val colors = LocalPulseColors.current
    val description = if (kinds.isEmpty()) "No intervals" else "Session timeline, interval ${currentIndex + 1} of ${kinds.size}"
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
    ) {
        if (kinds.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val segmentWidth = (size.width - gap * (kinds.size - 1)) / kinds.size
        var x = 0f
        kinds.forEachIndexed { index, kind ->
            val color = when {
                index < currentIndex -> colors.phaseColor(kind).copy(alpha = 0.45f)
                index == currentIndex -> colors.phaseColor(kind)
                else -> colors.phaseColor(kind).copy(alpha = 0.22f)
            }
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(segmentWidth.coerceAtLeast(1f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            )
            x += segmentWidth + gap
        }
    }
}

/** Circular progress used by the player around the big countdown. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
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
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LocalPulseDimens.current.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = LocalPulseColors.current.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalPulseColors.current.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(LocalPulseDimens.current.l))
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

@Composable
fun InfoBanner(text: String, tone: Color? = null, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background((tone ?: colors.surfaceRaised), RoundedCornerShape(14.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
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

/** +/- stepper for durations. Large touch targets, and it announces its value to TalkBack. */
@Composable
fun DurationStepper(
    label: String,
    millis: Long,
    modifier: Modifier = Modifier,
    stepMillis: Long = 5_000L,
    maxMillis: Long = 24L * 60L * 60L * 1000L,
    allowZero: Boolean = true,
    onChange: (Long) -> Unit,
) {
    val colors = LocalPulseColors.current
    val min = if (allowZero) 0L else stepMillis
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onChange((millis - stepMillis).coerceIn(min, maxMillis)) },
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease $label") }
            Text(
                text = formatDuration(millis),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "$label ${formatDuration(millis)}" },
            )
            IconButton(
                onClick = { onChange((millis + stepMillis).coerceIn(min, maxMillis)) },
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Increase $label") }
        }
    }
}

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 200,
    onChange: (Int) -> Unit,
) {
    val colors = LocalPulseColors.current
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1).coerceIn(min, max)) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "$label $value" },
            )
            IconButton(onClick = { onChange((value + 1).coerceIn(min, max)) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}

/** Simple single-choice row used instead of a spinner so every option is one tap away. */
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
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) colors.work else colors.surfaceRaised,
                    modifier = Modifier.clickable { onSelect(value) },
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) colors.onAccent else colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
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
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalPulseColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        androidx.compose.material3.Switch(
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
fun SecondaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VerticalSpacer(height: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(height))

@Composable
fun rememberToggle(initial: Boolean = false): Pair<Boolean, () -> Unit> {
    var value by remember { mutableStateOf(initial) }
    return value to { value = !value }
}
