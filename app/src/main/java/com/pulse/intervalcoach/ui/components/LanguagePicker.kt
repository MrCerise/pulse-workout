package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.AppLanguage
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * A language chooser that scales to a long list of languages.
 *
 * A grid of tiles is fine for the six languages the app ships today, but it does not survive a
 * catalogue of twenty or thirty: it would push every other control off the screen. A single control
 * that opens a scrollable list keeps the footprint constant no matter how many entries
 * [AppLanguage] gains, while still showing each language's flag and its own name.
 */
@Composable
fun LanguagePicker(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shapes = LocalPulseShapes.current
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = if (selected == AppLanguage.SYSTEM) {
        stringResource(R.string.settings_language_system)
    } else {
        selected.nativeName
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.control,
            color = colors.surface,
            border = androidx.compose.foundation.BorderStroke(dimens.borderWidth, colors.borderStrong),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.s),
            ) {
                Text(
                    text = selected.flag,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppLanguage.entries.forEach { language ->
                val label = if (language == AppLanguage.SYSTEM) {
                    stringResource(R.string.settings_language_system)
                } else {
                    language.nativeName
                }
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimens.s),
                        ) {
                            Text(text = language.flag, style = MaterialTheme.typography.titleMedium)
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    },
                )
            }
        }
    }
}
