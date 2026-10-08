package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseFonts
import com.pulse.intervalcoach.ui.theme.TABULAR_FIGURES

/**
 * Buttons, fields and selectors — the interactive half of the design system.
 *
 * Three rules hold for every control here:
 *
 *  1. **Two emphases, not five.** A screen may have one primary action; everything else is
 *     secondary, ghost or destructive. There is deliberately no "medium-primary-gradient" tier.
 *  2. **40 dp visual height, 48 dp touch target.** Controls look compact and still satisfy the
 *     accessibility minimum, because the touch target is applied with
 *     `minimumInteractiveComponentSize` rather than by inflating the button.
 *  3. **States are tokenised.** Hover, pressed, focused, disabled and selected all resolve to a
 *     phase of the same palette, so a control cannot invent its own highlight.
 */

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/**
 * The primary action. A solid neutral block — light in dark themes, dark in light themes — rather
 * than a brand colour, which is what keeps the single accent meaningful elsewhere on the screen.
 * Use it once per view.
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = dimens.buttonHeight),
        shape = LocalPulseShapes.current.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.actionFill,
            contentColor = colors.actionText,
            disabledContainerColor = colors.surfaceHover,
            disabledContentColor = colors.textDisabled,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = dimens.l),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
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

/** Everything that is not the one primary action: same geometry, outlined instead of filled. */
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
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = dimens.buttonHeight),
        shape = LocalPulseShapes.current.control,
        border = BorderStroke(dimens.borderWidth, if (enabled) colors.outline else colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textDisabled,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = dimens.l),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
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

/** A borderless action for tertiary navigation ("Open the quick builder instead"). */
@Composable
fun GhostActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = dimens.buttonHeight),
        shape = LocalPulseShapes.current.control,
        color = Color.Transparent,
        contentColor = if (enabled) colors.textSecondary else colors.textDisabled,
    ) {
        Box(
            Modifier
                .padding(horizontal = dimens.m)
                .defaultMinSize(minHeight = dimens.buttonHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * A control that changes or destroys data. It is the only button allowed to carry hue, and it is
 * never the default action in a dialog.
 */
@Composable
fun DangerActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = dimens.buttonHeight),
        shape = LocalPulseShapes.current.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.destructive,
            contentColor = com.pulse.intervalcoach.ui.theme.legibleOn(colors.destructive),
            disabledContainerColor = colors.surfaceHover,
            disabledContentColor = colors.textDisabled,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = dimens.l),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * An icon-only button. It always carries a content description, and on pointer-driven devices it
 * gains a tooltip automatically, so an icon that is obvious to one person is still legible to the
 * next.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val button: @Composable () -> Unit = {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            // Material's IconButton already guarantees a 48 dp touch target around its content, so
            // the visual box can stay at the compact control size without losing the tap area.
            modifier = Modifier.size(dimens.iconButtonSize).then(modifier),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = tint ?: colors.textSecondary,
                disabledContentColor = colors.textDisabled,
            ),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
    }
    if (tooltip == null) {
        button()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    containerColor = colors.surfaceOverlay,
                    contentColor = colors.textPrimary,
                ) {
                    Text(tooltip, style = MaterialTheme.typography.bodySmall)
                }
            },
            state = rememberTooltipState(),
        ) {
            button()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Text input
// ---------------------------------------------------------------------------------------------

/**
 * The text field. One visual treatment everywhere: a raised fill, a one-pixel control boundary that
 * turns into the accent on focus, and a monospaced-free body face so typed text matches the rest of
 * the interface.
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        shape = LocalPulseShapes.current.control,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = label?.let { { Text(it, style = MaterialTheme.typography.labelLarge) } },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        },
        trailingIcon = trailing,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            disabledTextColor = colors.textDisabled,
            errorTextColor = colors.textPrimary,
            focusedContainerColor = colors.surfaceRaised,
            unfocusedContainerColor = colors.surfaceRaised,
            disabledContainerColor = colors.surface,
            errorContainerColor = colors.surfaceRaised,
            cursorColor = colors.accent,
            errorCursorColor = colors.destructive,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.outline,
            disabledBorderColor = colors.border,
            errorBorderColor = colors.destructive,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textMuted,
            errorLabelColor = colors.destructive,
            focusedPlaceholderColor = colors.textMuted,
            unfocusedPlaceholderColor = colors.textMuted,
            focusedLeadingIconColor = colors.textSecondary,
            unfocusedLeadingIconColor = colors.textMuted,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Selectors
// ---------------------------------------------------------------------------------------------

/**
 * A radio row: exactly one option, always visible, no dropdown. The segmented control is the
 * default because it shows the whole choice set at a glance; it degrades to a scrollable chip row
 * once there are more than four options, which is where equal columns stop being readable.
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
    val dimens = LocalPulseDimens.current
    Column(modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(dimens.s))
        }
        if (options.size <= 4) {
            PulseSegmented(options = options, selected = selected, onSelect = onSelect)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dimens.s),
            ) {
                options.forEach { (value, text) ->
                    PulseChoiceChip(
                        text = text,
                        selected = value == selected,
                        onClick = { onSelect(value) },
                    )
                }
            }
        }
    }
}

/**
 * The segmented control: a single track, one raised segment for the active choice. It is the same
 * shape language as the input it usually sits under, which is the point.
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
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Surface(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) colors.surface else Color.Transparent,
                    border = if (isSelected) BorderStroke(dimens.borderWidth, colors.border) else null,
                    contentColor = if (isSelected) colors.textPrimary else colors.textMuted,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** A single toggleable chip inside a scrollable option row. */
@Composable
fun PulseChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = LocalPulseShapes.current.control,
        color = if (selected) colors.accentTint else colors.surfaceRaised,
        contentColor = if (selected) colors.accent else colors.textSecondary,
        border = BorderStroke(
            dimens.borderWidth,
            if (selected) colors.accent else colors.border,
        ),
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = dimens.m),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A settings switch: title, optional explanation, and the control itself. The whole row is the tap
 * target, which is the behaviour people actually expect from a settings list.
 */
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .defaultMinSize(minHeight = dimens.minTouchTarget)
            .padding(vertical = dimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.textPrimary else colors.textDisabled,
            )
            if (hint != null) {
                Text(text = hint, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.width(dimens.m))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/**
 * A labelled slider (voice rate, pitch, sound volume): the value is always visible as a monospaced
 * number so the user can tell what they are setting.
 */
@Composable
fun PulseSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueLabel: String,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = PulseFonts.Mono,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                color = colors.textSecondary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = colors.actionFill,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.track,
            ),
        )
        Spacer(Modifier.height(dimens.xs))
    }
}

// ---------------------------------------------------------------------------------------------
// Steppers
// ---------------------------------------------------------------------------------------------

/** A duration field with explicit minus/plus controls — never a keyboard, never a date picker. */
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
    )
}

/** A counted field (rounds, repetitions). Same geometry as [DurationStepper] on purpose. */
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
        valueDescription = "$label $value",
        decreaseDescription = "Decrease $label",
        increaseDescription = "Increase $label",
        atMin = value <= min,
        atMax = value >= max,
        enabled = enabled,
        modifier = modifier,
        onDecrease = { onChange((value - 1).coerceIn(min, max)) },
        onIncrease = { onChange((value + 1).coerceIn(min, max)) },
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Column(Modifier.padding(start = dimens.m, top = dimens.xs, bottom = 2.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(
                    icon = Icons.Filled.Remove,
                    description = decreaseDescription,
                    enabled = enabled && !atMin,
                    onClick = onDecrease,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = valueDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = PulseFonts.Mono,
                            fontFeatureSettings = TABULAR_FIGURES,
                        ),
                        color = if (enabled) colors.textPrimary else colors.textDisabled,
                        maxLines = 1,
                    )
                }
                StepperButton(
                    icon = Icons.Filled.Add,
                    description = increaseDescription,
                    enabled = enabled && !atMax,
                    onClick = onIncrease,
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = 32.dp,
) {
    val colors = LocalPulseColors.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) colors.textSecondary else colors.textDisabled,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Menus
// ---------------------------------------------------------------------------------------------

/** A dropdown menu. Its surface colour comes from the scheme's overlay role, so menus match dialogs. */
@Composable
fun PulseDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}

/** One entry in a [PulseDropdown]. Text and icon colours are explicit so a menu cannot fall back. */
@Composable
fun PulseDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
) {
    val colors = LocalPulseColors.current
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (selected) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

/** Kept public so callers that build their own menu can reuse the field treatment. */
@Composable
fun rememberFieldState(initial: String = ""): Pair<String, (String) -> Unit> {
    var value by remember { mutableStateOf(initial) }
    return value to { value = it }
}
