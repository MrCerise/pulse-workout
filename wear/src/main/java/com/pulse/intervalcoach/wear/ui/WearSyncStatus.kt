package com.pulse.intervalcoach.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.wear.R

/**
 * One line describing where this watch's data comes from and where it goes.
 *
 * Home and Settings both show it, from the same three facts, so the two screens can never
 * disagree about whether the watch is paired, reachable, or on its own. "Solo" is not an error
 * state: it is what a watch that has never met a phone — or one whose owner turned the link off —
 * is meant to look like.
 */
@Composable
fun watchSyncStatus(
    phoneLibrarySeen: Boolean,
    syncEnabled: Boolean,
    phoneConnected: Boolean,
): String = when {
    !syncEnabled -> stringResource(R.string.sync_status_off)
    !phoneLibrarySeen -> stringResource(R.string.sync_status_solo)
    phoneConnected -> stringResource(R.string.sync_status_connected)
    else -> stringResource(R.string.sync_status_offline)
}
