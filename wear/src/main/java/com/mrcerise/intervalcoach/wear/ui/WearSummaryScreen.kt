package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors

/**
 * Post-workout summary, mirroring the phone's: what ran, for how long, how much of it completed —
 * plus the one thing unique to the watch: where the record went. It is always written to this
 * watch's own history; it additionally reaches the phone's history as soon as the link can deliver
 * it, and waits in the queue when it cannot — including when the link is switched off, so turning
 * it back on later still delivers the workout instead of losing it.
 */
@Composable
fun WearSummaryScreen(
    container: WearAppContainer,
    onDone: () -> Unit,
) {
    val colors = LocalWearColors.current
    val summary by container.session.summary.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val pendingLogs by container.wearSync.pendingLogCount.collectAsStateWithLifecycle()

    PulseListScreen(
        edgeButton = {
            EdgeButton(onClick = onDone) {
                Text(stringResource(R.string.summary_done))
            }
        },
    ) { spec ->
        val data = summary
        item(key = "badge") {
            val stopped = data?.stoppedEarly == true
            Box(Modifier.transformed(this, spec).padding(top = 4.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(if (stopped) colors.prepareTint else colors.accentTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (stopped) Icons.Filled.Flag else Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (stopped) colors.prepare else colors.accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        item(key = "title") {
            Caption(
                text = stringResource(if (data?.stoppedEarly == true) R.string.summary_stopped_early else R.string.summary_title),
                spec = spec,
            )
        }
        if (data != null) {
            item(key = "name") {
                Text(
                    text = data.planName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.transformed(this, spec).padding(horizontal = 12.dp),
                )
            }
            item(key = "time") {
                Text(
                    text = formatDuration(data.activeMillis),
                    style = MaterialTheme.typography.numeralMedium,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.transformed(this, spec),
                )
            }
            item(key = "intervals") {
                val intervals = stringResource(R.string.summary_intervals_value, data.completedIntervals, data.totalIntervals)
                val skipped = if (data.skippedIntervals > 0) {
                    " · " + stringResource(R.string.summary_skipped_value, data.skippedIntervals)
                } else {
                    ""
                }
                Caption(intervals + skipped, spec, color = colors.textSecondary)
            }
            item(key = "saved") {
                // Three honest outcomes: it reached the phone, it is waiting for it, or the link is
                // off and the record lives on this watch alone. Icon and words, never colour alone.
                val (icon, text, tint) = when {
                    pendingLogs == 0 -> Triple(Icons.Filled.CloudDone, stringResource(R.string.summary_saved), colors.work)
                    !prefs.syncEnabled -> Triple(Icons.Filled.Watch, stringResource(R.string.summary_on_watch), colors.textMuted)
                    else -> Triple(Icons.Filled.CloudQueue, stringResource(R.string.summary_queued), colors.prepare)
                }
                SavedLine(icon, text, tint, Modifier.transformed(this, spec))
            }
        }
    }
}

@Composable
private fun SavedLine(icon: ImageVector, text: String, tint: Color, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
}
