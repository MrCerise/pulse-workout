package com.mrcerise.intervalcoach.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseMotion
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.ThemeMode
import com.mrcerise.intervalcoach.ui.theme.pulseColors

/** Four visible choices: no menu, horizontal scrolling or hidden OLED option. */
@Composable
fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val dimens = LocalPulseDimens.current
    val options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
        ThemeMode.TRUE_BLACK to stringResource(R.string.theme_black),
    )
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(dimens.s)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                row.forEach { (mode, label) ->
                    ThemeTile(mode, label, mode == selected, { onSelect(mode) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(mode: ThemeMode, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val motion = LocalPulseMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(motion.fast()), label = "themePress")
    val outline by animateColorAsState(if (selected) colors.accent else colors.borderStrong, tween(motion.fast()), label = "themeOutline")
    val check by animateFloatAsState(if (selected) 1f else 0f, tween(motion.fast()), label = "themeCheck")
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(LocalPulseShapes.current.control)
            .selectable(selected = selected, role = Role.RadioButton, interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        shape = LocalPulseShapes.current.control,
        color = colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, outline),
    ) {
        Column(Modifier.padding(dimens.s), verticalArrangement = Arrangement.spacedBy(dimens.s)) {
            ThemePreview(mode, Modifier.fillMaxWidth().height(60.dp).clip(LocalPulseShapes.current.panel))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.xs)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = null, tint = colors.accent,
                    modifier = Modifier.size(18.dp).graphicsLayer { alpha = check; scaleX = 0.8f + check * 0.2f; scaleY = scaleX },
                )
            }
        }
    }
}

/** A miniature screen, with a split light/dark preview for Follow system. */
@Composable
private fun ThemePreview(mode: ThemeMode, modifier: Modifier = Modifier) {
    val light = remember { pulseColors(dark = false, trueBlack = false, highContrast = false) }
    val dark = remember(mode) { pulseColors(dark = true, trueBlack = mode == ThemeMode.TRUE_BLACK, highContrast = false) }
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(4.dp.toPx())
        val palettes = if (mode == ThemeMode.SYSTEM) listOf(light, dark) else listOf(if (mode == ThemeMode.LIGHT) light else dark)
        palettes.forEachIndexed { index, palette ->
            clipRect(left = if (index == 1) w / 2 else 0f, right = if (palettes.size == 2 && index == 0) w / 2 else w) {
                drawRect(palette.background)
                drawRoundRect(palette.surfaceRaised, Offset(w * 0.08f, h * 0.14f), Size(w * 0.84f, h * 0.72f), radius)
                drawRoundRect(palette.textPrimary, Offset(w * 0.16f, h * 0.28f), Size(w * 0.32f, h * 0.07f), radius)
                drawRoundRect(palette.textMuted, Offset(w * 0.16f, h * 0.44f), Size(w * 0.52f, h * 0.05f), radius)
                drawRoundRect(palette.actionFill, Offset(w * 0.16f, h * 0.62f), Size(w * 0.68f, h * 0.10f), radius)
            }
        }
    }
}
