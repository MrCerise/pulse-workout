package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.mrcerise.intervalcoach.wear.R

/** Same generated, offline policy as the phone and public HTML, sized for the watch. */
@Composable
fun WearPrivacyScreen(onBack: () -> Unit) {
    val titles = stringArrayResource(R.array.privacy_section_titles)
    val bodies = stringArrayResource(R.array.privacy_section_bodies)
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { Text(stringResource(R.string.privacy_policy_title), style = MaterialTheme.typography.title3) }
            item { Text(stringResource(R.string.privacy_updated), style = MaterialTheme.typography.caption1) }
            titles.indices.forEach { index ->
                item { Text(titles[index], style = MaterialTheme.typography.title3) }
                // Short items stay navigable with rotary input on a small round screen.
                bodies[index].split(" ").chunked(18).forEach { words ->
                    item { Text(words.joinToString(" "), style = MaterialTheme.typography.body2) }
                }
            }
            item { Chip(onClick = onBack, label = { Text(stringResource(R.string.privacy_back)) }) }
        }
    }
}
