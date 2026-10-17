package com.mrcerise.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.AppLanguage
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes

/** Language chooser using the exact same drop-down (bottom-sheet) style as every other picker. */
@Composable
fun LanguagePicker(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    var expanded by remember { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.settings_language_system)
    val selectedLabel = if (selected == AppLanguage.SYSTEM) systemLabel else selected.nativeName
    Surface(
        onClick = { expanded = true },
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        border = BorderStroke(dimens.borderWidth, colors.borderStrong),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = dimens.buttonHeight).padding(horizontal = dimens.l, vertical = dimens.m),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Language", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(dimens.s))
            Text(selected.flag + " " + selectedLabel, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Spacer(Modifier.width(dimens.xs))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
    if (expanded) {
        PulseBottomSheet(title = "Language", onDismiss = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                PulseSheetRow(
                    text = language.flag + "  " + if (language == AppLanguage.SYSTEM) systemLabel else language.nativeName,
                    selected = language == selected,
                    onClick = { expanded = false; onSelect(language) },
                )
            }
        }
    }
}
