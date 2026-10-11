package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * Surfaces, headers and rows — the structural half of the Prism design system.
 *
 * The rules these components encode:
 *
 *  - A surface is separated from the canvas by its **fill and a hairline**. Only chrome that
 *    genuinely floats (the navigation bar, sheets, dialogs) casts a shadow, which is why the one
 *    shadow on screen still reads.
 *  - There are four radii: 24 dp cards, 18 dp nested panels, 14 dp controls and pills for chips.
 *    A screen reads as a handful of large soft panels carrying small round pills.
 *  - A list row is 64 dp tall and its tap target is the whole row, which is what makes the app
 *    usable with one hand.
 */

/**
 * A card. Use it for anything that groups related content: a workout, a stat cluster, a form
 * section. Pass [onClick] to make the whole card a single tap target (the ripple is clipped to the
 * card's radius).
 *
 * [tint] washes the card in a colour — a category hue, a phase tint — without turning it into a
 * saturated block, which is how a screen with eight category cards stays calm.
 */
@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = LocalPulseColors.current.surface,
    tint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(LocalPulseDimens.current.cardPadding),
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shape = LocalPulseShapes.current.card
    val border = BorderStroke(dimens.borderWidth, colors.border)
    val background = tint?.let { wash(containerColor, it) } ?: containerColor
    val body: @Composable () -> Unit = {
        Box(Modifier.padding(contentPadding)) { content() }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = background,
            contentColor = colors.textPrimary,
            border = border,
        ) { body() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = background,
            contentColor = colors.textPrimary,
            border = border,
        ) { body() }
    }
}

/**
 * A hero surface: the two-stop accent gradient, used exactly twice in the app (the Today hero and
 * the session summary banner). Because it is one component with one brush, the gradient can never
 * drift between the two screens that use it.
 */
@Composable
fun PulseGradientCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    startColor: Color = LocalPulseColors.current.accent,
    endColor: Color = LocalPulseColors.current.accentAlt,
    contentPadding: PaddingValues = PaddingValues(LocalPulseDimens.current.cardPadding),
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val shape = LocalPulseShapes.current.card
    val body: @Composable () -> Unit = {
        Box(Modifier.padding(contentPadding)) { content() }
    }
    val brush = Brush.linearGradient(listOf(startColor, endColor))
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.actionText,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(brush, shape),
            ) { body() }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(brush),
        ) { body() }
    }
}

/**
 * Same silhouette as [PulseCard] but one step up the ramp and one step tighter in radius. Use it for
 * a group nested inside a card, or where the content should read as an inset control rather than as
 * a peer panel.
 */
@Composable
fun PulsePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LocalPulseDimens.current.l),
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.panel,
        color = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * Section label. Small, bold, tracked and muted — it labels a group of content without competing
 * with the content's own titles. [action] puts a single quiet control on the trailing edge ("See
 * all"), so a section header is never followed by a lonely button on its own line.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimens.l, bottom = dimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.width(dimens.s))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f, fill = true))
        if (actionLabel != null && onAction != null) {
            Surface(
                onClick = onAction,
                shape = LocalPulseShapes.current.chip,
                color = Color.Transparent,
                contentColor = colors.accent,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = dimens.s, vertical = dimens.xs),
                )
            }
        }
    }
}

/** A one-pixel separator between rows inside a surface. */
@Composable
fun PulseDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = LocalPulseDimens.current.borderWidth,
        color = LocalPulseColors.current.border,
    )
}

/**
 * A generic information row: optional leading glyph in a tinted tile, a title, a supporting line and
 * trailing content. Used for settings entries, workout rows and history rows so they all share one
 * rhythm.
 */
@Composable
fun PulseListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null,
    leadingContainer: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.listRowHeight)
                .padding(vertical = dimens.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(dimens.controlRadius))
                        .background(leadingContainer ?: colors.surfaceHover),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = leadingTint ?: colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(dimens.m))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.microGap)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(dimens.s))
                trailing()
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            color = Color.Transparent,
            contentColor = colors.textPrimary,
        ) { row() }
    } else {
        Box(modifier.fillMaxWidth()) { row() }
    }
}

/**
 * The screen header.
 *
 * Every pushed destination uses this so the back affordance, the title size, the subtitle position
 * and the hairline under the bar are identical everywhere. It wraps Material's [TopAppBar] rather
 * than replacing it, so window insets and accessibility behaviour stay on the framework's path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalPulseColors.current
    Column(modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDescription,
                        )
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = colors.textPrimary,
                navigationIconContentColor = colors.textSecondary,
                actionIconContentColor = colors.textSecondary,
            ),
            modifier = Modifier.height(LocalPulseDimens.current.topBarHeight),
        )
        PulseDivider()
    }
}

/**
 * The shared screen frame.
 *
 * Every destination in PULSE is a [PulseScaffold]: one background, one optional header, one
 * optional bottom bar, and content that is handed its padding rather than guessing at it. That is
 * what makes eleven screens share a page gutter without any of them owning one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalPulseColors.current
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        topBar = {
            if (title != null) {
                PulseTopBar(
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    backDescription = backDescription,
                    actions = actions,
                )
            }
        },
        content = content,
    )
}

/** Vertical breathing room, named so a screen never has to spell out a raw dp value. */
@Composable
fun VerticalSpacer(height: Dp) = Spacer(Modifier.height(height))

/**
 * Composites a colour wash: `overlay` at a small, fixed alpha over `base`. Used instead of
 * `copy(alpha = …)` on a surface so the result is a real pre-composited colour — the same trick the
 * palette uses for phase tints, and the reason a washed card's text contrast is not an estimate.
 */
private fun wash(base: Color, overlay: Color, strength: Float = 0.14f): Color =
    Color(
        red = base.red * (1 - strength) + overlay.red * strength,
        green = base.green * (1 - strength) + overlay.green * strength,
        blue = base.blue * (1 - strength) + overlay.blue * strength,
        alpha = base.alpha,
    )
