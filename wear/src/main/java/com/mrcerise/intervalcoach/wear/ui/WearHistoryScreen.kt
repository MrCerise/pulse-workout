package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.history.WatchSessionRecord
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors
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
 * "On this watch"); the icon and colour only reinforce it, so the state survives a monochrome
 * screen and TalkBack.
 */
@Composable
fun WearHistoryScreen(container: WearAppContainer) {
    val colors = LocalWearColors.current
    val records by container.history.records.collectAsStateWithLifecycle()
    val prefs by container.preferences.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmingClear by remember { mutableStateOf(false) }

    // A session is "on your phone" once its log left the queue; until then it lives only here.
    val pendingIds = remember(prefs.pendingLogs) { prefs.pendingLogs.map { it.sessionId }.toSet() }

    PulseListScreen { spec ->
        item(key = "title") { ScreenHeader(stringResource(R.string.library_history), spec) }
        if (pendingIds.isNotEmpty()) {
            item(key = "pending") {
                Caption(stringResource(R.string.history_pending_note, pendingIds.size), spec, color = colors.prepare)
            }
        }
        if (records.isEmpty()) {
            item(key = "empty") { Caption(stringResource(R.string.history_empty), spec) }
        }
        items(records, key = { it.sessionId }) { record ->
            HistoryCard(
                record = record,
                waitingForPhone = record.sessionId in pendingIds,
                modifier = Modifier.transformed(this, spec),
            )
        }
        if (records.isNotEmpty()) {
            item(key = "clear") {
                Button(
                    onClick = { confirmingClear = true },
                    modifier = Modifier.transformed(this, spec).padding(top = 4.dp),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.destructiveTint,
                        contentColor = colors.destructive,
                        iconColor = colors.destructive,
                    ),
                    icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    label = { Text(stringResource(R.string.history_clear)) },
                )
            }
        }
    }

    AlertDialog(
        visible = confirmingClear,
        onDismissRequest = { confirmingClear = false },
        icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = colors.destructive) },
        title = { Text(stringResource(R.string.history_clear_title), textAlign = TextAlign.Center) },
        text = { Text(stringResource(R.string.history_clear_text), textAlign = TextAlign.Center) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    confirmingClear = false
                    scope.launch { container.history.clear() }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.destructive,
                    contentColor = colors.canvas,
                ),
            )
        },
    )
}

/** A read-only card: name, then time and date, then where the record lives. */
@Composable
private fun HistoryCard(
    record: WatchSessionRecord,
    waitingForPhone: Boolean,
    modifier: Modifier,
) {
    val colors = LocalWearColors.current
    val stateColor = if (waitingForPhone) colors.prepare else colors.work
    Column(
        modifier = modifier
            .background(colors.surface, MaterialTheme.shapes.large)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = record.planName,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDuration(record.activeMillis) + " · " + formatWatchDate(record.startedAt),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (waitingForPhone) Icons.Filled.Watch else Icons.Filled.CloudDone,
                contentDescription = null,
                tint = stateColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(
                    if (waitingForPhone) R.string.history_state_on_watch else R.string.history_state_on_phone,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = stateColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (record.stoppedEarly) {
            Text(
                text = stringResource(R.string.history_stopped),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
    }
}
