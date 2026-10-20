package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors
import com.mrcerise.intervalcoach.wear.R

/** Same generated, offline policy as the phone and public HTML, sized for the watch. */
@Composable
fun WearPrivacyScreen() {
    val colors = LocalWearColors.current
    val titles = stringArrayResource(R.array.privacy_section_titles)
    val bodies = stringArrayResource(R.array.privacy_section_bodies)
    PulseListScreen { spec ->
        item { ScreenHeader(stringResource(R.string.privacy_policy_title), spec) }
        item { Caption(stringResource(R.string.privacy_updated), spec) }
        titles.indices.forEach { index ->
            item {
                Text(
                    text = titles[index],
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.accent,
                    modifier = Modifier.transformed(this, spec).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            // Short items stay navigable with rotary input on a small round screen.
            bodies[index].split(" ").chunked(18).forEach { words ->
                item {
                    Text(
                        text = words.joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.transformed(this, spec).padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}
