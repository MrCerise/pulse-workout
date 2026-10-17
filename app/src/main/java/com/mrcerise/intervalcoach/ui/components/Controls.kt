package com.mrcerise.intervalcoach.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldMore
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseElevation
import com.mrcerise.intervalcoach.ui.theme.LocalPulseMotion
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseType
import kotlinx.coroutines.launch

/**
 * Buttons, fields, chips and pickers — the interactive half of the Momentum design system.
 *
 * Three rules are baked into the components themselves, so a screen cannot get them wrong:
 *
 *  - **Exactly one primary action per screen, and it is the brand block.** A solid brand fill with
 *    ink on it is the single loudest thing a page can show, which is what makes "where do I tap"
 *    answerable before anything is read.
 *  - **Buttons are imposing, fields are dense.** An action button is 56 dp tall with the display
 *    face on its label; fields, steppers and chips stay at 40 dp so the form keeps its rhythm.
 *  - **Every control has a real touch target.** A 36 dp icon button still exposes 48 dp, and a
 *    disabled control is flat, not dimmed.
 *
 * Enumerated choices and action clusters open a **bottom sheet** (`PulseSelect` / `PulseActionMenu`
 * over `PulseBottomSheet`) — a row of side-by-side buttons is never drawn where a menu is the
 * honest control, and no picker ever looks like a web form. Buttons are for actions a screen makes
 * obvious; sheets are for the rest.
 */

/** Shared horizontal padding inside a button, so every action has the same optical weight. */
private val buttonPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

// -------------------------------------------------------------------------------------------------
// Buttons
// -------------------------------------------------------------------------------------------------

/**
 * The primary action: a solid brand block with ink on it, one per screen.
 *
 * Full width wherever it is used, 56 dp tall, the display face on the label. It is the only
 * element in the interface allowed a saturated fill, so the screen always has exactly one obvious
 * next step — and it looks like it.
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
                style = PulseType.ButtonLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The secondary action: a raised surface with a visible border — the outlined button of the
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
        color = colors.surfaceRaised,
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
                style = PulseType.ButtonLabel,
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
                style = PulseType.ButtonLabel,
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
                style = PulseType.ButtonLabel,
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
        animationSpec = tween(LocalPulseMotion.current.fast()),
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
 * A labelled drop-down: the control for every enumerated choice.
 *
 * A choice among many options is a menu, not a row of buttons: the trigger is one full-width
 * control that shows the current value, and the options live in the drop-down where their labels
 * are never squeezed. Used for every enumerated preference (audio interruption, headphone
 * behaviour, density, units) and the builder's Phase / Sound / Vibration pickers, so they all
 * share one rhythm.
 */
@Composable
fun <T> OptionRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PulseSelect(
        label = label,
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * The picker field: a modern row showing the label and the current value, opening a
 * [PulseBottomSheet] with the options.
 *
 * The trigger is a settings-style row — label on the left, the current value and a chevron on the
 * right — so a form reads like a modern settings list rather than a stack of web form fields. The
 * chosen option wears the accent and a check inside the sheet.
 */
@Composable
fun <T> PulseSelect(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Surface(
        onClick = { if (enabled) expanded = true },
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.control,
        color = if (enabled) colors.surfaceRaised else colors.surfaceHover,
        contentColor = if (enabled) colors.textPrimary else colors.textDisabled,
        border = BorderStroke(dimens.borderWidth, colors.borderStrong),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.buttonHeight)
                .padding(horizontal = dimens.l, vertical = dimens.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.textPrimary else colors.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(dimens.s))
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.textSecondary else colors.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(dimens.xs))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (enabled) colors.textSecondary else colors.textDisabled,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (expanded) {
        PulseBottomSheet(title = label, onDismiss = { expanded = false }) {
            options.forEach { (value, text) ->
                PulseSheetRow(
                    text = text,
                    selected = value == selected,
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/**
 * One entry in a [PulseActionMenu]. A cluster of secondary actions becomes one menu trigger
 * instead of a row of half-width buttons.
 */
class PulseMenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A full-width button that opens a sheet of actions — the replacement for rows of side-by-side
 * buttons ("Add interval" / "Add block", "Preview cues" / "Edit" / "Schedule").
 *
 * The trigger looks like a secondary action button and carries a chevron, so a screen still shows
 * where the actions live without spending three buttons on them. The actions open in the same
 * bottom sheet as every other picker; destructive entries wear the error colour.
 */
@Composable
fun PulseActionMenu(
    text: String,
    items: List<PulseMenuAction>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { if (enabled) expanded = true },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = dimens.buttonHeight),
        shape = LocalPulseShapes.current.control,
        color = if (enabled) colors.surfaceRaised else colors.surfaceHover,
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
                style = PulseType.ButtonLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(dimens.s))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (expanded) {
        PulseBottomSheet(title = text, onDismiss = { expanded = false }) {
            items.forEach { action ->
                PulseSheetRow(
                    text = action.label,
                    leadingIcon = action.icon,
                    destructive = action.destructive,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/**
 * A wrapping row of standalone option chips — the unlabelled core of [OptionRow], also used where
 * a screen already has its own label. Options flow onto as many lines as they need; a label is
 * never ellipsized into a lie, and no chip touches its neighbour.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> PulseChipGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalPulseDimens.current
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
        verticalArrangement = Arrangement.spacedBy(dimens.chipGap),
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
        animationSpec = tween(LocalPulseMotion.current.fast()),
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

/** A duration field: tap the value pill to open a number-scroll (wheel) picker — hours, minutes, seconds. */
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
    val dimens = LocalPulseDimens.current
    val min = if (allowZero) 0L else stepMillis
    var editing by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PulseType.SectionLabel, color = colors.textMuted, modifier = Modifier.weight(1f))
        Surface(
            onClick = { if (enabled) editing = true },
            enabled = enabled,
            shape = LocalPulseShapes.current.control,
            color = colors.surfaceRaised,
            contentColor = if (enabled) colors.textPrimary else colors.textDisabled,
            border = BorderStroke(dimens.borderWidth, colors.borderStrong),
        ) {
            Row(Modifier.padding(horizontal = dimens.m, vertical = dimens.s), verticalAlignment = Alignment.CenterVertically) {
                Text(formatStepperDuration(millis), style = PulseType.StepperValue,
                    color = if (enabled) colors.textPrimary else colors.textDisabled,
                    modifier = Modifier.semantics { contentDescription = "$label duration, scroll to change" })
                Spacer(Modifier.width(dimens.xs))
                Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (editing) {
        var hours by remember(millis) { mutableStateOf((millis / 3_600_000L).toInt()) }
        var minutes by remember(millis) { mutableStateOf(((millis % 3_600_000L) / 60_000L).toInt()) }
        val stepSec = (stepMillis / 1000L).toInt().coerceAtLeast(1)
        val secondOptions = remember(stepSec, millis) {
            ((0..59 step stepSec).toList() + ((millis % 60_000L) / 1000L).toInt()).distinct().sorted()
        }
        var seconds by remember(millis) { mutableStateOf(((millis % 60_000L) / 1000L).toInt()) }
        fun emit() {
            val total = (hours * 3600L + minutes * 60L + seconds) * 1000L
            onChange(total.coerceIn(min, maxMillis))
        }
        PulseBottomSheet(title = label, onDismiss = { editing = false }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                NumberWheel((0..23).toList(), hours, "h", Modifier.weight(1f)) { hours = it; emit() }
                NumberWheel((0..59).toList(), minutes, "min", Modifier.weight(1f)) { minutes = it; emit() }
                NumberWheel(secondOptions, seconds, "s", Modifier.weight(1f)) { seconds = it; emit() }
            }
            Spacer(Modifier.height(dimens.m))
            PrimaryActionButton("Done", onClick = { editing = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * One scrolling number column: scroll (or tap) to centre a value; the centred value is selected.
 * A highlight band marks the centre row, values shrink/fade with distance from it.
 */
@Composable
private fun NumberWheel(
    values: List<Int>,
    selected: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val itemHeight = 44.dp
    val initialIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(listState, selected) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val info = listState.layoutInfo
                    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    val centred = info.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - centre) }
                    val idx = centred?.index
                    if (idx != null && idx in values.indices && values[idx] != selected) onSelect(values[idx])
                }
            }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(itemHeight * 3)) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(itemHeight)
                .background(colors.accentTint, LocalPulseShapes.current.control))
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = itemHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(values.size) { i ->
                    val value = values[i]
                    Box(Modifier.fillMaxWidth().height(itemHeight).clickable {
                        scope.launch { listState.animateScrollToItem(i) }; onSelect(value)
                    }, contentAlignment = Alignment.Center) {
                        Text(
                            value.toString(),
                            style = if (value == selected) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                            color = if (value == selected) colors.textPrimary else colors.textMuted,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(dimens.xs))
        Text(unit, style = PulseType.SectionLabel, color = colors.textMuted)
    }
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
// Sheets — the one modal surface: options and action menus both open here.
// -------------------------------------------------------------------------------------------------

/**
 * A modern bottom sheet: a scrim, and a rounded sheet that slides up from the bottom edge.
 *
 * This is the app's one modal picker surface — option choices and action menus both open a sheet —
 * drawn in the app's own dialog shell (one radius, one elevation, one button system) rather than
 * the platform's menu popup, which reads as a web form on a phone.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PulseBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = dimens.sheetRadius, topEnd = dimens.sheetRadius),
        containerColor = colors.surfaceOverlay,
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = dimens.s), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).background(colors.border, LocalPulseShapes.current.chip))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = dimens.xs)) {
            Spacer(Modifier.height(dimens.s))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = dimens.l),
            )
            Spacer(Modifier.height(dimens.s))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.s)
                    .navigationBarsPadding()
                    .padding(bottom = dimens.xxl),
                content = content,
            )
            // Guaranteed breathing room under the last row, above the gesture bar.
            Spacer(Modifier.height(dimens.xl))
        }
    }
}

/**
 * One large row inside a [PulseBottomSheet]: an option you pick or an action you take.
 *
 * The row is the full width of the sheet and 48 dp tall at minimum — a choice in a sheet is never
 * a small tap target. The selected option wears the accent tint and a check; a destructive action
 * wears the error colour.
 */
@Composable
fun PulseSheetRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val tint = when {
        destructive -> colors.error
        selected -> colors.accent
        else -> colors.textPrimary
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.control,
        color = if (selected) colors.accentTint else Color.Transparent,
        contentColor = tint,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.buttonHeight)
                .padding(horizontal = dimens.l, vertical = dimens.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (destructive) colors.error else if (selected) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(dimens.m))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Spacer(Modifier.width(dimens.s))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
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
