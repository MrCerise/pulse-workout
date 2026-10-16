package com.pulse.intervalcoach.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseMotion
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseElevation
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * Navigation — the shell of the application.
 *
 * CADENCE has one navigation model in three shapes, and the shape follows the window:
 *
 *  - **a sidebar** (>= 1000 dp) — the full dashboard chrome: a wordmark, sections, 36 dp rows with
 *    a glyph, a label, a tinted active row and an accent beat dot. This is where the app stops
 *    looking like a phone app and starts looking like a tool;
 *  - **an icon rail** (>= 600 dp) — the same destinations, glyph-only, with a tinted active pill;
 *  - **a floating bottom bar** (phone) — a detached pill that hovers above the system navigation
 *    area on a soft shadow, with a tinted indicator behind the active destination's glyph. It is
 *    the one piece of chrome allowed to cast a shadow, because it genuinely floats.
 *
 * All three read the same [PulseNavItem] list and the same selection treatment, so switching between
 * them changes the layout and nothing else.
 */

/** One destination. [selectedIcon] lets a destination swap to a filled glyph when it is active. */
data class PulseNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
)

/**
 * A labelled group of destinations inside the sidebar. A group with a null label is rendered without
 * a heading, which is how the primary four sit directly under the wordmark.
 */
data class PulseNavGroup(
    val label: String? = null,
    val items: List<PulseNavItem>,
)

/**
 * The floating bottom bar.
 *
 * A `nav`-filled pill, detached from the screen edges and lifted above the system navigation area:
 * a hairline keeps its edge crisp and one shadow sells the float. Nothing else in the app casts a
 * shadow, which is why this one still means something. The active destination gets a tinted pill
 * behind its glyph — the one place the accent is spent in the bar.
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = dimens.navBarInset, vertical = dimens.navBarInset)
            .shadow(LocalPulseElevation.current.level3, LocalPulseShapes.current.pill),
        shape = LocalPulseShapes.current.pill,
        color = colors.nav,
        contentColor = colors.textPrimary,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.navBarHeight)
                .padding(horizontal = dimens.s),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                PulseNavBarButton(
                    item = item,
                    selected = item.route == currentRoute,
                    onClick = { onSelect(item.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PulseNavBarButton(
    item: PulseNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val indicator by animateColorAsState(
        animationSpec = tween(LocalPulseMotion.current.fast()),
        targetValue = when {
            selected -> colors.accentTint
            hovered -> colors.surfaceHover
            else -> Color.Transparent
        },
        label = "navIndicator",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.semantics { contentDescription = item.label },
        shape = LocalPulseShapes.current.control,
        color = Color.Transparent,
        contentColor = if (selected) colors.accent else colors.textMuted,
    ) {
        Column(
            modifier = Modifier.padding(vertical = dimens.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 30.dp)
                    .clip(LocalPulseShapes.current.control)
                    .background(indicator),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon ?: item.icon else item.icon,
                    contentDescription = null,
                    tint = if (selected) colors.accent else colors.textMuted,
                    modifier = Modifier.size(22.dp),
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
 * The icon rail: a tablet held upright, or a phone in landscape.
 *
 * The destinations are the same objects in the same order, so the rail is not a second navigation
 * model to keep in step — it is the same one rotated, with room for the wordmark at the top.
 */
@Composable
fun PulseNavRail(
    items: List<PulseNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
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
        Row(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = dimens.m, horizontal = dimens.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (header != null) {
                    header()
                    Spacer(Modifier.height(dimens.l))
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items.forEach { item ->
                        PulseRailButton(
                            item = item,
                            selected = item.route == currentRoute,
                            onClick = { onSelect(item.route) },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (footer != null) {
                    footer()
                }
            }
            PulseRailDivider()
        }
    }
}

@Composable
private fun PulseRailButton(
    item: PulseNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        animationSpec = tween(LocalPulseMotion.current.fast()),
        targetValue = when {
            selected -> colors.accentTint
            hovered -> colors.surfaceHover
            else -> Color.Transparent
        },
        label = "railFill",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(width = 52.dp, height = 44.dp)
            .semantics { contentDescription = item.label },
        shape = LocalPulseShapes.current.control,
        color = fill,
        contentColor = if (selected) colors.accent else colors.textMuted,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (selected) item.selectedIcon ?: item.icon else item.icon,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The sidebar: the full dashboard chrome at the largest breakpoint.
 *
 * A wordmark at the head, then sections of destinations — 36 dp rows, 18 dp glyphs, a 13 sp label,
 * a tinted fill and an accent beat dot when active. Below the destinations, a footer slot carries
 * the account and appearance controls, which is where a dashboard keeps them.
 */
@Composable
fun PulseAppSidebar(
    groups: List<PulseNavGroup>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier
            .width(dimens.sidebarWidth)
            .fillMaxHeight(),
        color = colors.nav,
        contentColor = colors.textPrimary,
    ) {
        Row(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = dimens.m),
            ) {
                if (header != null) {
                    Box(Modifier.padding(horizontal = dimens.m)) { header() }
                    Spacer(Modifier.height(dimens.l))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimens.s),
                ) {
                    groups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.microGap)) {
                            if (group.label != null) {
                                Text(
                                    text = group.label,
                                    style = com.pulse.intervalcoach.ui.theme.PulseType.SectionLabel,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(
                                        start = dimens.m,
                                        top = dimens.s,
                                        bottom = dimens.xs,
                                    ),
                                )
                            }
                            group.items.forEach { item ->
                                PulseSidebarButton(
                                    item = item,
                                    selected = item.route == currentRoute,
                                    onClick = { onSelect(item.route) },
                                )
                            }
                        }
                    }
                }
                if (footer != null) {
                    Spacer(Modifier.height(dimens.s))
                    Box(Modifier.padding(horizontal = dimens.m)) { footer() }
                }
            }
            PulseRailDivider()
        }
    }
}

@Composable
private fun PulseSidebarButton(
    item: PulseNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        animationSpec = tween(LocalPulseMotion.current.fast()),
        targetValue = when {
            selected -> colors.accentTint
            hovered -> colors.surfaceHover
            else -> Color.Transparent
        },
        label = "sidebarFill",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.s)
            .height(36.dp),
        shape = LocalPulseShapes.current.control,
        color = fill,
        contentColor = if (selected) colors.accent else colors.textSecondary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = dimens.s, end = dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (selected) colors.accent else Color.Transparent,
                        RoundedCornerShape(3.dp),
                    ),
            )
            Spacer(Modifier.width(dimens.m))
            Icon(
                imageVector = if (selected) item.selectedIcon ?: item.icon else item.icon,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(dimens.m))
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.accent else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The hairline drawn between a rail and the content beside it — the one edge that separates the
 * shell from the page, and the reason neither needs a shadow.
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
