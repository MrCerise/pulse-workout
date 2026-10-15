package com.pulse.intervalcoach.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.wear.R
import com.pulse.intervalcoach.wear.WearAppContainer
import com.pulse.intervalcoach.wear.history.WatchSessionRecord
import com.pulse.intervalcoach.wear.ui.theme.LocalWearColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Top-level so the formatter is built once; it follows the device locale, like the phone's. */
private val WATCH_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")

private fun formatWatchDate(millis: Long): String =
    WATCH_DATE_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * What this watch has run, newest first.
 *
 * The screen exists because the watch owns its sessions: a workout finished on a watch with no
 * phone — or with the link switched off — is recorded here rather than only queued for a device
 * that may never appear. Every row says **in words** where the record lives ("On your phone" /
 * "On this watch"); the colour only reinforces it, so the state survives a monochrome screen and
 * TalkBack.
 */
@Composable
fun WearHistoryScreen(
    container: WearAppContainer,
    onBack: () -> Unit,
) {
    val colors = LocalWearColors.current
    val records by container.history.records.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmingClear by remember { mutableStateOf(false) }

    // A session is "on your phone" once its log left the queue; until then it lives only here.
    val pendingIds = remember(prefs.pendingLogs) { prefs.pendingLogs.map { it.sessionId }.toSet() }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.title3,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            if (pendingIds.isNotEmpty()) {
                item(key = "pending") {
                    Text(
                        text = stringResource(R.string.history_pending_note, pendingIds.size),
                        style = MaterialTheme.typography.caption1,
                        color = colors.prepare,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
            if (records.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.caption1,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
            items(records, key = { it.sessionId }) { record ->
                HistoryRow(
                    record = record,
                    waitingForPhone = record.sessionId in pendingIds,
                )
            }
            if (records.isNotEmpty()) {
                item(key = "clear") {
                    Chip(
                        onClick = {
                            if (confirmingClear) {
                                confirmingClear = false
                                scope.launch { container.history.clear() }
                            } else {
                                confirmingClear = true
                            }
                        },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = colors.surface,
                            contentColor = if (confirmingClear) colors.destructive else colors.textPrimary,
                        ),
                        label = {
                            Text(
                                if (confirmingClear) {
                                    stringResource(R.string.history_clear_confirm)
                                } else {
                                    stringResource(R.string.history_clear)
                                },
                            )
                        },
                    )
                }
            }
            item(key = "back") {
                Chip(
                    onClick = onBack,
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = colors.surface,
                        contentColor = colors.textPrimary,
                    ),
                    label = { Text(stringResource(R.string.settings_back)) },
                )
            }
        }
    }
}

/** Two or three short lines; nothing to truncate, nothing signalled by colour alone. */
@Composable
private fun HistoryRow(
    record: WatchSessionRecord,
    waitingForPhone: Boolean,
) {
    val colors = LocalWearColors.current
    val state = stringResource(
        if (waitingForPhone) R.string.history_state_on_watch else R.string.history_state_on_phone,
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = record.planName,
            style = MaterialTheme.typography.body1,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.history_row_detail,
                state,
                formatDuration(record.activeMillis),
                formatWatchDate(record.startedAt),
            ),
            style = MaterialTheme.typography.caption1,
            color = if (waitingForPhone) colors.prepare else colors.work,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (record.stoppedEarly) {
            Text(
                text = stringResource(R.string.history_stopped),
                style = MaterialTheme.typography.caption1,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
