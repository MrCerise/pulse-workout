package com.pulse.intervalcoach.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseElevation
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * Navigation.
 *
 * PULSE has one navigation model and two shapes for it:
 *
 *  - **a floating pill bar** on a phone — inset from the edges, casting the app's one real shadow,
 *    so the content behind it reads as continuous rather than as sitting in a tray;
 *  - **a rail** on a tablet or in landscape, where a bottom bar would be the furthest thing from
 *    the thumb.
 *
 * Both are built from the same [PulseNavItem] list and the same selection treatment — a gradient pill
 * behind the active destination's glyph — so switching between them changes the layout and nothing
 * else.
 */

/** One destination. [selectedIcon] lets a destination swap to a filled glyph when it is active. */
data class PulseNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
)

/**
 * The floating bottom bar.
 *
 * It floats on purpose: the canvas continues behind it, the hairline that separates every other
 * chrome element is replaced by a real shadow, and the active destination is a gradient pill. That
 * combination is what makes "you are here" legible without the bar shouting.
 */
@Composable
fun PulseFloatingNavBar(
    items: List<PulseNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val elevation = LocalPulseElevation.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.navBarInset, vertical = dimens.s),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = LocalPulseShapes.current.chip,
            color = colors.nav,
            contentColor = colors.textPrimary,
            shadowElevation = elevation.level3,
            tonalElevation = 0.dp,
            border = null,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = dimens.s, vertical = dimens.s)
                    .height(dimens.navBarHeight - dimens.l),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    PulseNavButton(
                        item = item,
                        selected = item.route == currentRoute,
                        onClick = { onSelect(item.route) },
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
    val dimens = LocalPulseDimens.current
    val gradient = Brush.linearGradient(listOf(colors.accent, colors.accentAlt))
    val container by animateColorAsState(
        targetValue = if (selected) colors.accentTint else androidx.compose.ui.graphics.Color.Transparent,
        label = "nav",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = item.label },
        shape = LocalPulseShapes.current.chip,
        color = container,
        contentColor = if (selected) colors.accent else colors.textMuted,
    ) {
        Column(
            modifier = Modifier.padding(vertical = dimens.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 28.dp)
                    .then(
                        if (selected) Modifier.background(gradient, LocalPulseShapes.current.chip) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon ?: item.icon else item.icon,
                    contentDescription = null,
                    tint = if (selected) colors.onAccentGradient else colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.accent else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The vertical rail used on tablets and in landscape.
 *
 * The destinations are the same objects in the same order, so the rail is not a second navigation
 * model to keep in step — it is the same one rotated, with room for a wordmark at the top.
 */
@Composable
fun PulseNavRail(
    items: List<PulseNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier
            .width(dimens.railWidth)
            .fillMaxHeight(),
        color = colors.nav,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = dimens.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (header != null) {
                header()
                Spacer(Modifier.height(dimens.m))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(dimens.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items.forEach { item ->
                    PulseNavButton(
                        item = item,
                        selected = item.route == currentRoute,
                        onClick = { onSelect(item.route) },
                        modifier = Modifier.width(dimens.railWidth - dimens.m),
                    )
                }
            }
        }
    }
}

/**
 * A divider in the shape of a hairline, used between a rail and the content beside it. Exposed here
 * so a screen never has to build one by hand.
 */
@Composable
fun PulseRailDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(LocalPulseColors.current.border),
    )
}

/** Rounded corner helper kept next to the navigation that uses it, so the pill shape has one owner. */
val navPillShape: RoundedCornerShape
    @Composable get() = LocalPulseShapes.current.chip
