package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens

/** One destination in the [PulseBottomBar]. */
data class PulseNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * The bottom navigation bar.
 *
 * Built by hand rather than configured through Material's `NavigationBar` for one reason: the
 * selected state here is a tinted accent container behind the icon, which is a quieter and far more
 * legible "you are here" than a colour-filled pill spanning the whole item. The bar sits on the
 * navigation surface with a hairline on top, so it separates from the canvas without a shadow.
 */
@Composable
fun PulseBottomBar(
    items: List<PulseNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.nav,
    ) {
        Column(Modifier.fillMaxWidth()) {
            PulseDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = dimens.s),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = item.route == currentRoute
                    PulseNavButton(
                        item = item,
                        selected = selected,
                        onClick = { onSelect(item.route) },
                        // `weight` only exists inside a Row, so it is supplied here rather than
                        // inside the button itself.
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PulseNavButton(
    item: PulseNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(LocalPulseDimens.current.controlRadius),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) colors.accent else colors.textMuted,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 26.dp)
                    .clip(RoundedCornerShape(LocalPulseDimens.current.chipRadius))
                    .background(if (selected) colors.accentTint else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
