package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.AppLanguage
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * A grid of language choices, each showing its flag and its own name.
 *
 * Mirrors [ThemePicker]: two columns, a hairline border that lifts to the accent when selected, and
 * a check mark that fades in. Used in both the settings page and the welcome tour so the choice
 * looks the same wherever it is offered.
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
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(dimens.s),
    ) {
        AppLanguage.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.s),
            ) {
                row.forEach { language ->
                    val label = if (language == AppLanguage.SYSTEM) {
                        stringResource(R.string.settings_language_system)
                    } else {
                        language.nativeName
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = language == selected,
                                onClick = { onSelect(language) },
                                role = Role.RadioButton,
                            ),
                        shape = shapes.control,
                        color = if (language == selected) colors.accentTint else colors.surface,
                        border = BorderStroke(
                            dimens.borderWidth,
                            if (language == selected) colors.accent else colors.borderStrong,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(dimens.s),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(dimens.xs),
                        ) {
                            Text(
                                text = language.flag,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (language == selected) colors.accent else colors.textSecondary,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
