package com.mrcerise.intervalcoach.ui.importer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.category
import com.mrcerise.intervalcoach.locale.categoryTitle
import com.mrcerise.intervalcoach.ui.components.GhostActionButton
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.TimelineBar
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseElevation
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseType
import com.pulse.engine.TimelineExpander
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration

/**
 * Confirms a workout received by QR code before it touches the library.
 *
 * A code can arrive from anywhere — the in-app scanner, an image, or a `pulse://import` link opened
 * by another app — so nothing is saved until the user has seen what it is and said yes.
 */
@Composable
fun SharedWorkoutDialog(
    plan: WorkoutPlan,
    busy: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val category = plan.category
    val hue = colors.categoryColor(category.hue)
    val timeline = remember(plan) { runCatching { TimelineExpander.expand(plan) }.getOrNull() }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            shape = LocalPulseShapes.current.sheet,
            color = colors.surfaceOverlay,
            contentColor = colors.textPrimary,
            border = BorderStroke(dimens.borderWidth, colors.border),
            shadowElevation = LocalPulseElevation.current.level3,
        ) {
            Column(
                modifier = Modifier.padding(dimens.xl),
                verticalArrangement = Arrangement.spacedBy(dimens.s),
            ) {
                Text(
                    text = stringResource(R.string.share_import_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(dimens.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.categoryTint(category.hue), LocalPulseShapes.current.control),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(category.icon, contentDescription = null, tint = hue, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(dimens.m))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = plan.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                append(strings.categoryTitle(category))
                                if (timeline != null) {
                                    append(" · ")
                                    append(
                                        timeline.totalMillis?.let { formatDuration(it) }
                                            ?: "≥ ${formatDuration(timeline.knownMillis)}",
                                    )
                                    append(" · ")
                                    append(strings.getString(R.string.interval_count, timeline.steps.size))
                                }
                            },
                            style = PulseType.NumericSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                if (timeline != null && timeline.steps.isNotEmpty()) {
                    TimelineBar(
                        kinds = timeline.steps.map { it.kind },
                        currentIndex = -1,
                        progressInStep = 0f,
                        height = dimens.barHeightCompact,
                    )
                }
                Text(
                    text = stringResource(R.string.share_import_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(dimens.s))
                PrimaryActionButton(
                    text = stringResource(R.string.share_import_add),
                    onClick = onAdd,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                GhostActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
