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
import androidx.compose.ui.unit.dp
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
import com.pulse.intervalcoach.wear.ui.theme.LocalWearColors

/**
 * Post-workout summary, mirroring the phone's: what ran, for how long, how much of it completed —
 * plus the one thing unique to the watch: where the record went. A finished watch session is sent
 * to the phone immediately when connected and queued until then.
 */
@Composable
fun WearSummaryScreen(
    container: WearAppContainer,
    onDone: () -> Unit,
) {
    val colors = LocalWearColors.current
    val summary by container.session.summary.collectAsStateWithLifecycle()
    val pendingLogs by container.wearSync.pendingLogCount.collectAsStateWithLifecycle()

    Scaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val data = summary
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
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
                    Text(
                        text = if (pendingLogs == 0) {
                            stringResource(R.string.summary_saved)
                        } else {
                            stringResource(R.string.summary_queued)
                        },
                        style = MaterialTheme.typography.caption1,
                        color = if (pendingLogs == 0) colors.work else colors.prepare,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.primaryButtonColors(
                        backgroundColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(stringResource(R.string.summary_done), style = MaterialTheme.typography.button)
                }
            }
        }
    }
}
