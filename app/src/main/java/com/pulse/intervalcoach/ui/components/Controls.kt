package com.pulse.intervalcoach.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseType

/**
 * Buttons, fields, chips and pickers — the interactive half of the Prism design system.
 *
 * Three rules are baked into the components themselves, so a screen cannot get them wrong:
 *
 *  - **The primary action carries the gradient.** It is the only element on screen allowed to, so
 *    "where do I tap" is answered before anything is read.
 *  - **Chips are pills, controls are 14 dp, buttons are 52 dp.** The sizes are the hierarchy: a
 *    screen reads as big soft panels, one loud action and a scatter of small round pills.
 *  - **Every control has a real touch target.** A 40 dp icon button still exposes 48 dp, and a
 *    disabled control is flat, not dimmed.
 */

// -------------------------------------------------------------------------------------------------
// Buttons
// -------------------------------------------------------------------------------------------------

/**
 * The primary action: the accent gradient, full width wherever it is used, 52 dp tall.
 *
 * The gradient is the one decorative thing Prism allows itself, and it is reserved for the action a
 * screen exists for — start a workout, save a plan, grant a permission.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shape = LocalPulseShapes.current.control
    val brush = Brush.linearGradient(listOf(colors.accent, colors.accentAlt))
    val background: Modifier = if (enabled) {
        Modifier.background(brush, shape)
    } else {
        Modifier.background(colors.surfaceHover, shape)
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = dimens.buttonHeight)
            .clip(shape)
            .then(background),
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        contentColor = if (enabled) colors.onAccentGradient else colors.textDisabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.l),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(dimens.s))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The secondary action: a raised surface with a hairline. The peer of [PrimaryActionButton]. */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = dimens.buttonHeight),
        enabled = enabled,
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        contentColor = if (enabled) colors.textPrimary else colors.textDisabled,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.l),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(dimens.s))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The quietest action: no container, accent label. For "skip", "not now", "learn more". */
@Composable
fun GhostActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = dimens.buttonHeight),
        enabled = enabled,
        shape = LocalPulseShapes.current.control,
        color = Color.Transparent,
        contentColor = if (enabled) colors.accent else colors.textDisabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.l),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(dimens.s))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Destructive confirmation: tinted red, never a solid block, and only when it destroys something. */
@Composable
fun DangerActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = dimens.buttonHeight),
        enabled = enabled,
        shape = LocalPulseShapes.current.control,
        color = colors.dangerTint,
        contentColor = if (enabled) colors.error else colors.textDisabled,
        border = BorderStroke(dimens.borderWidth, colors.error.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.l),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(dimens.s))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A 40 dp icon button with a guaranteed 48 dp touch target. */
@Composable
fun PulseIconButton(
    icon: ImageVector,
    /**
     * Pass `null` only when the button repeats a label that is already on screen (the chevron at
     * the end of a labelled row); anything else needs a real description for TalkBack.
     */
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    tooltip: String? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(dimens.minTouchTarget)
            .then(if (tooltip != null) Modifier.semantics { this.contentDescription = tooltip } else Modifier),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Fields
// -------------------------------------------------------------------------------------------------

/**
 * A text field. Rounded, raised, with the label above rather than floating inside — the value is
 * what you came to read, so nothing is ever drawn on top of it.
 */
@Composable
fun PulseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
    isError: Boolean = false,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = PulseType.SectionLabel,
                color = colors.textMuted,
                modifier = Modifier.padding(start = dimens.s, bottom = dimens.xs),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = LocalPulseShapes.current.control,
            color = if (enabled) colors.surfaceRaised else colors.surfaceHover,
            border = BorderStroke(
                dimens.borderWidth,
                if (isError) colors.error else colors.border,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.l, vertical = dimens.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(dimens.m))
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else minLines,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (enabled) colors.textPrimary else colors.textDisabled,
                    ),
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = dimens.controlHeight - dimens.m),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textDisabled,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (trailing != null) {
                    Spacer(Modifier.width(dimens.s))
                    trailing()
                }
            }
        }
    }
}

/** Remembers a text field's value without a ViewModel. For dialogs and one-shot inputs. */
@Composable
fun rememberFieldState(initial: String = ""): Pair<String, (String) -> Unit> {
    var value by remember(initial) { mutableStateOf(initial) }
    return value to { value = it }
}

// -------------------------------------------------------------------------------------------------
// Pickers
// -------------------------------------------------------------------------------------------------

/** A label with a row of pill options beside it. Used in forms and in the settings screens. */
@Composable
fun <T> OptionRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = PulseType.SectionLabel,
            color = colors.textMuted,
            modifier = Modifier.padding(start = dimens.s, bottom = dimens.s),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
        ) {
            options.forEach { (value, text) ->
                PulseChoiceChip(
                    text = text,
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A segmented control: a pill track with a filled pill for the selected segment.
 *
 * The selected segment is filled rather than outlined because at this size an outline reads as a
 * border around nothing — the fill is what makes "you are here" legible at a glance.
 */
@Composable
fun <T> PulseSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.chip,
        color = colors.surfaceRaised,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(dimens.xs),
            horizontalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                val container by animateColorAsState(
                    targetValue = if (isSelected) colors.accentTint else Color.Transparent,
                    label = "segment",
                )
                Surface(
                    onClick = { onSelect(value) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = dimens.controlHeight),
                    shape = LocalPulseShapes.current.chip,
                    color = container,
                    contentColor = if (isSelected) colors.accent else colors.textMuted,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** A filter pill. Round, because a small element is the one place a full radius is honest. */
@Composable
fun PulseChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    accent: Color? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val tint = accent ?: colors.accent
    val container by animateColorAsState(
        targetValue = if (selected) tint else colors.surfaceRaised,
        label = "chip",
    )
    val content = if (selected) colors.onAccentGradient else colors.textSecondary
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = LocalPulseShapes.current.chip,
        color = container,
        contentColor = content,
        border = if (selected) {
            null
        } else {
            BorderStroke(dimens.borderWidth, colors.border)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(dimens.xs))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Toggles, sliders, steppers
// -------------------------------------------------------------------------------------------------

/** A labelled switch row with an optional hint underneath. */
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
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        color = Color.Transparent,
        contentColor = colors.textPrimary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.listRowHeight)
                .padding(vertical = dimens.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.microGap)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) colors.textPrimary else colors.textDisabled,
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.width(dimens.m))
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccentGradient,
                    checkedTrackColor = colors.accent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = colors.textMuted,
                    uncheckedTrackColor = colors.surfaceHover,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

/** A labelled slider with a live value readout. */
@Composable
fun PulseSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueLabel: String,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(dimens.s))
            Text(
                text = valueLabel,
                style = PulseType.NumericSmall,
                color = colors.accent,
            )
        }
        Spacer(Modifier.height(dimens.xs))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.track,
                activeTickColor = colors.onAccentGradient,
                inactiveTickColor = colors.track,
            ),
        )
    }
}

/** A duration field with − / + buttons. The value is monospaced so the layout never shifts. */
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
    val min = if (allowZero) 0L else stepMillis
    StepperField(
        label = label,
        value = formatStepperDuration(millis),
        valueDescription = label + " duration",
        decreaseDescription = "Decrease $label",
        increaseDescription = "Increase $label",
        atMin = millis <= min,
        atMax = millis >= maxMillis,
        enabled = enabled,
        onDecrease = { onChange((millis - stepMillis).coerceAtLeast(min)) },
        onIncrease = { onChange((millis + stepMillis).coerceAtMost(maxMillis)) },
        modifier = modifier,
    )
}

/** A count field with − / + buttons. */
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
    StepperField(
        label = label,
        value = value.toString(),
        valueDescription = label + " count",
        decreaseDescription = "Decrease $label",
        increaseDescription = "Increase $label",
        atMin = value <= min,
        atMax = value >= max,
        enabled = enabled,
        onDecrease = { onChange((value - 1).coerceAtLeast(min)) },
        onIncrease = { onChange((value + 1).coerceAtMost(max)) },
        modifier = modifier,
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                style = PulseType.SectionLabel,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(dimens.xs))
            Text(
                text = value,
                style = PulseType.StepperValue,
                color = if (enabled) colors.textPrimary else colors.textDisabled,
                modifier = Modifier.semantics { contentDescription = valueDescription },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(
                icon = Icons.Filled.Remove,
                description = decreaseDescription,
                enabled = enabled && !atMin,
                onClick = onDecrease,
            )
            StepperButton(
                icon = Icons.Filled.Add,
                description = increaseDescription,
                enabled = enabled && !atMax,
                onClick = onIncrease,
            )
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = 40.dp,
) {
    val colors = LocalPulseColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        shape = LocalPulseShapes.current.control,
        color = if (enabled) colors.surfaceRaised else colors.surfaceHover,
        contentColor = if (enabled) colors.textPrimary else colors.textDisabled,
        border = BorderStroke(LocalPulseDimens.current.borderWidth, colors.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Menus
// -------------------------------------------------------------------------------------------------

/** A dropdown menu surface: overlay fill, 18 dp radius, hairline border. */
@Composable
fun PulseDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .background(colors.surfaceOverlay, LocalPulseShapes.current.panel)
            .border(dimens.borderWidth, colors.border, LocalPulseShapes.current.panel),
        content = content,
    )
}

/** One entry in a [PulseDropdown]. */
@Composable
fun PulseDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) colors.accent else colors.textPrimary,
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.xs),
    )
}

// -------------------------------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------------------------------

/** `1:05`, `12:30`, `1:02:03` — the stepper readout. */
private fun formatStepperDuration(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = (millis + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Shared text style for a control's inner label, so no control picks its own size. */
@Composable
fun ControlLabelText(text: String, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
        Text(text = text, color = colors.textPrimary, modifier = modifier, textAlign = TextAlign.Center)
    }
}

/** A quiet clickable tile used by the builders' interval rows. */
@Composable
fun SelectableTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shape = RoundedCornerShape(dimens.panelRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .background(if (selected) colors.accentTint else colors.surfaceRaised, shape)
            .border(
                width = dimens.borderWidth,
                color = if (selected) colors.accent else colors.border,
                shape = shape,
            )
            .padding(dimens.m),
    ) { content() }
}
