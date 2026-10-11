package com.pulse.intervalcoach.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseType

/**
 * Buttons, fields, chips and pickers — the interactive half of the Signal design system.
 *
 * Three rules are baked into the components themselves, so a screen cannot get them wrong:
 *
 *  - **Exactly one primary action per screen, and it is the brand block.** A solid orange fill with
 *    ink on it is the single loudest thing a page can show, which is what makes "where do I tap"
 *    answerable before anything is read.
 *  - **Controls are 40 dp, radii are 6 dp.** Density is a feature: more of the workout fits on the
 *    screen, and the silhouette stays a tool rather than a toy.
 *  - **Every control has a real touch target.** A 36 dp icon button still exposes 48 dp, and a
 *    disabled control is flat, not dimmed.
 */

/** Shared horizontal padding inside a button, so every action has the same optical weight. */
private val buttonPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

// -------------------------------------------------------------------------------------------------
// Buttons
// -------------------------------------------------------------------------------------------------

/**
 * The primary action: a solid brand block with ink on it, one per screen.
 *
 * Full width wherever it is used, 40 dp tall, 14 sp semibold label. It is the only element in the
 * interface allowed a saturated fill, so the screen always has exactly one obvious next step.
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
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = dimens.buttonHeight),
        enabled = enabled,
        shape = LocalPulseShapes.current.control,
        color = if (enabled) colors.actionFill else colors.surfaceHover,
        contentColor = if (enabled) colors.actionText else colors.textDisabled,
    ) {
        Row(
            modifier = Modifier.padding(buttonPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
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

/**
 * The secondary action: a white/raised surface with a visible border — the outlined button of the
 * system. It never competes with the brand block, and it is the default for "Cancel", "Save for
 * later" and everything else that is not the point of the screen.
 */
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
        color = colors.surface,
        contentColor = if (enabled) colors.textPrimary else colors.textDisabled,
        border = BorderStroke(dimens.borderWidth, colors.borderStrong),
    ) {
        Row(
            modifier = Modifier.padding(buttonPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
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
            modifier = Modifier.padding(buttonPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
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
            modifier = Modifier.padding(buttonPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
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

/** A 36 dp icon button with a guaranteed 48 dp touch target and a hover/press fill. */
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
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        targetValue = if (hovered && enabled) colors.surfaceHover else Color.Transparent,
        label = "iconButtonFill",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .size(dimens.minTouchTarget)
            .then(if (tooltip != null) Modifier.semantics { this.contentDescription = tooltip } else Modifier),
    ) {
        Box(
            modifier = Modifier
                .size(dimens.iconButtonSize)
                .clip(LocalPulseShapes.current.control)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> colors.textDisabled
                    else -> tint ?: colors.textSecondary
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Fields
// -------------------------------------------------------------------------------------------------

/**
 * A text field. The label sits above the field rather than floating inside it — the value is what
 * you came to read, so nothing is ever drawn on top of it — and the field itself is a sunken well
 * with a visible border, which is what makes it read as an input rather than as a panel.
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
                text = label,
                style = PulseType.SectionLabel,
                color = colors.textMuted,
                modifier = Modifier.padding(start = dimens.xs, bottom = dimens.xs),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = LocalPulseShapes.current.control,
            color = if (enabled) colors.surfaceSunken else colors.surfaceHover,
            border = BorderStroke(
                dimens.borderWidth,
                when {
                    isError -> colors.error
                    enabled -> colors.borderStrong
                    else -> colors.border
                },
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.m, vertical = dimens.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(dimens.s))
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
        if (isError) {
            Spacer(Modifier.height(dimens.xs))
            Text(
                text = "Check this field",
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = dimens.xs),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Pickers
// -------------------------------------------------------------------------------------------------

/**
 * A label with its options beneath it, two per row. Used in forms and in the settings screens.
 *
 * Two columns rather than one stretched row because that is what the width allows: the builder's
 * phase list has seven entries, and seven equal columns on a phone ellipsize every one of them. An
 * option you cannot read is not an option. The grid also matches [CategoryPicker], which offers the
 * same kind of choice two cells at a time.
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
    val optionsPerRow = 2
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.chipGap),
    ) {
        Text(
            text = label,
            style = PulseType.SectionLabel,
            color = colors.textMuted,
            modifier = Modifier.padding(start = dimens.xs),
        )
        options.chunked(optionsPerRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
            ) {
                row.forEach { (value, text) ->
                    PulseChoiceChip(
                        text = text,
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // A short final row pads itself out to the same two columns, so the last option is
                // never stretched across a width the others did not get.
                repeat(optionsPerRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A segmented control: a sunken track with a raised segment for the current choice.
 *
 * The selected segment is lifted rather than merely tinted, so "you are here" is legible at a glance
 * even when the accent is being used somewhere else on the same screen.
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
        color = colors.surfaceSunken,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(dimens.xs),
            horizontalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                val container by animateColorAsState(
                    targetValue = if (isSelected) colors.surface else Color.Transparent,
                    label = "segment",
                )
                Surface(
                    onClick = { onSelect(value) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = dimens.controlHeight - dimens.s),
                    shape = LocalPulseShapes.current.control,
                    color = container,
                    contentColor = if (isSelected) colors.textPrimary else colors.textSecondary,
                    border = if (isSelected) {
                        BorderStroke(dimens.borderWidth, colors.borderStrong)
                    } else {
                        null
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = text,
                            style = if (isSelected) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A selectable filter / option chip.
 *
 * It is a **rectangle**, not a pill: a chip you tap is a control, and controls in this system all
 * share one 6 dp radius, one 40 dp-or-under height and a hairline border. Only the labels that carry
 * no action (a phase tag, a category name, a status badge) are allowed to be fully round — see
 * `docs/DESIGN.md` §1.3. This is what the builder's option rows and the library's filter rail are
 * built from, so both read as a row of small buttons rather than a row of bubbles.
 */
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
        targetValue = if (selected) colors.accentTint else colors.surface,
        label = "chip",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 32.dp),
        shape = LocalPulseShapes.current.control,
        color = container,
        contentColor = if (selected) tint else colors.textSecondary,
        border = BorderStroke(
            dimens.borderWidth,
            if (selected) tint.copy(alpha = 0.42f) else colors.borderStrong,
        ),
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
                    modifier = Modifier.size(14.dp),
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
                .padding(vertical = dimens.s),
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
                    checkedThumbColor = colors.onAccent,
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
                activeTickColor = colors.onAccent,
                inactiveTickColor = colors.track,
            ),
        )
    }
}

/** A duration field with − / + buttons. The value is tabular so the layout never shifts. */
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.microGap)) {
            Text(
                text = label,
                style = PulseType.SectionLabel,
                color = colors.textMuted,
            )
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
    size: Dp = 36.dp,
) {
    val colors = LocalPulseColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        shape = LocalPulseShapes.current.control,
        color = if (enabled) colors.surface else colors.surfaceHover,
        contentColor = if (enabled) colors.textPrimary else colors.textDisabled,
        border = BorderStroke(LocalPulseDimens.current.borderWidth, colors.borderStrong),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Menus
// -------------------------------------------------------------------------------------------------

/** A dropdown menu surface: overlay fill, hairline border, the app's floating elevation. */
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
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        contentPadding = PaddingValues(horizontal = dimens.m, vertical = dimens.xs),
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
