package com.mrcerise.intervalcoach.ui.components

import com.mrcerise.intervalcoach.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The app's month calendar.
 *
 * Drawn from the same tokens as every other control — day cells in the shared 6 dp radius, the
 * selected day as a solid brand block with ink on it (the same "one loud thing" rule as the primary
 * button), today outlined in the accent — so a date picked here is picked in the same visual
 * language as everything else, not in a platform widget that wandered in from another app.
 *
 * Weeks start on Monday, matching the first column of the week charts.
 */
@Composable
fun PulseCalendar(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate = LocalDate.now(),
    maxDate: LocalDate = LocalDate.now().plusYears(2),
) {
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shapes = LocalPulseShapes.current
    var visibleMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }

    fun shiftMonth(delta: Long) {
        val next = visibleMonth.plusMonths(delta)
        // The header buttons never page into a month that holds no selectable day.
        if (!next.isBefore(YearMonth.from(minDate)) && !next.isAfter(YearMonth.from(maxDate))) {
            visibleMonth = next
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // --- Month header -----------------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = visibleMonth
                    .atDay(1)
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy", strings.configuration.locales[0]))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(strings.configuration.locales[0]) else it.toString() },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            PulseIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = strings.getString(R.string.calendar_previous),
                tooltip = strings.getString(R.string.calendar_previous),
                onClick = { shiftMonth(-1) },
                enabled = !visibleMonth.isBefore(YearMonth.from(minDate)),
            )
            PulseIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = strings.getString(R.string.calendar_next),
                tooltip = strings.getString(R.string.calendar_next),
                onClick = { shiftMonth(1) },
                enabled = !visibleMonth.isAfter(YearMonth.from(maxDate)),
            )
        }

        Spacer(Modifier.height(dimens.s))

        // --- Weekday initials -------------------------------------------------------------------
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, strings.configuration.locales[0]).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(dimens.xs))

        // --- Day grid ---------------------------------------------------------------------------
        val firstOfMonth = visibleMonth.atDay(1)
        val leadingBlanks = (firstOfMonth.dayOfWeek.value + 6) % 7 // Monday-first offset
        val firstCell = firstOfMonth.minusDays(leadingBlanks.toLong())
        val weeks = (leadingBlanks + visibleMonth.lengthOfMonth() + 6) / 7
        val today = LocalDate.now()
        repeat(weeks) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { dayOfWeek ->
                    val date = firstCell.plusDays((week * 7 + dayOfWeek).toLong())
                    val inMonth = date.month == visibleMonth.month
                    val selectable = !date.isBefore(minDate) && !date.isAfter(maxDate)
                    val isSelected = date == selectedDate
                    val isToday = date == today
                    val container = when {
                        isSelected -> colors.actionFill
                        selectable && inMonth -> colors.surface
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        isSelected -> colors.actionText
                        !selectable || !inMonth -> colors.textDisabled
                        isToday -> colors.accent
                        else -> colors.textPrimary
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(dimens.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            onClick = { if (selectable) onSelectDate(date) },
                            enabled = selectable,
                            modifier = Modifier
                                .size(40.dp)
                                .semantics { contentDescription = date.toString() },
                            shape = shapes.control,
                            color = container,
                            contentColor = textColor,
                            border = when {
                                isToday && !isSelected -> BorderStroke(dimens.borderWidth, colors.accent)
                                else -> null
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
