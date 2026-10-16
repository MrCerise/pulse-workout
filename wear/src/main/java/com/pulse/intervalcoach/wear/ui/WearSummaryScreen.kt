package com.pulse.intervalcoach.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.wear.R
import com.pulse.intervalcoach.wear.WearAppContainer
import com.pulse.intervalcoach.wear.ui.theme.LocalWearDimens
import com.pulse.intervalcoach.wear.ui.theme.LocalWearColors

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
    val dimens = LocalWearDimens.current
    val colors = LocalWearColors.current
    val summary by container.session.summary.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val pendingLogs by container.wearSync.pendingLogCount.collectAsStateWithLifecycle()

    Scaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val data = summary
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = dimens.summaryPaddingHorizontal),
            ) {
                if (data == null) {
                    Text(
                        text = stringResource(R.string.summary_title),
                        style = MaterialTheme.typography.title3,
                        color = colors.textPrimary,
                    )
                } else {
                    Text(
                        text = if (data.stoppedEarly) {
                            stringResource(R.string.summary_stopped_early)
                        } else {
                            stringResource(R.string.summary_title)
                        },
                        style = MaterialTheme.typography.caption1,
                        color = colors.textMuted,
                    )
                    Text(
                        text = data.planName,
                        style = MaterialTheme.typography.title3,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    Text(
                        text = formatDuration(data.activeMillis),
                        style = MaterialTheme.typography.display2,
                        color = colors.accent,
                    )
                    Text(
                        text = stringResource(
                            R.string.summary_intervals_value,
                            data.completedIntervals,
                            data.totalIntervals,
                        ),
                        style = MaterialTheme.typography.body2,
                        color = colors.textSecondary,
                    )
                    if (data.skippedIntervals > 0) {
                        Text(
                            text = stringResource(R.string.summary_skipped_value, data.skippedIntervals),
                            style = MaterialTheme.typography.caption1,
                            color = colors.textMuted,
                        )
                    }
                    // Three honest outcomes: it reached the phone, it is waiting for it, or the
                    // link is off and the record lives on this watch alone.
                    val savedText = when {
                        pendingLogs == 0 -> stringResource(R.string.summary_saved)
                        !prefs.syncEnabled -> stringResource(R.string.summary_on_watch)
                        else -> stringResource(R.string.summary_queued)
                    }
                    Text(
                        text = savedText,
                        style = MaterialTheme.typography.caption1,
                        color = when {
                            pendingLogs == 0 -> colors.work
                            !prefs.syncEnabled -> colors.textMuted
                            else -> colors.prepare
                        },
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.primaryButtonColors(
                        backgroundColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                    modifier = Modifier.padding(top = dimens.summaryPaddingTop),
                ) {
                    Text(stringResource(R.string.summary_done), style = MaterialTheme.typography.button)
                }
            }
        }
    }
}
