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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * Surfaces, headers and rows — the structural half of the PULSE design system.
 *
 * The rules these components encode:
 *
 *  - A surface is separated from the canvas by its **fill and a one-pixel border**, never by a
 *    shadow. Elevation that is drawn rather than rendered stays identical in every theme and does
 *    not blur text on a low-density screen.
 *  - There are exactly three radii: 12 dp for surfaces, 8 dp for controls, 6 dp for chips.
 *  - A list row is 56 dp tall and its tap target is the whole row, which is what makes the app
 *    usable with one hand.
 */

/**
 * A card. Use it for anything that groups related content: a workout, a stat cluster, a form
 * section. Pass [onClick] to make the whole card a single tap target (the ripple is clipped to the
 * card's radius).
 */
@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(LocalPulseDimens.current.cardPadding),
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val shape = LocalPulseShapes.current.card
    val border = BorderStroke(dimens.borderWidth, colors.border)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = colors.surface,
            contentColor = colors.textPrimary,
            border = border,
        ) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = colors.surface,
            contentColor = colors.textPrimary,
            border = border,
        ) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

/**
 * Same silhouette as [PulseCard] but one step up the ramp. Use it for a group nested inside a card,
 * or where the content should read as an inset control rather than a peer panel.
 */
@Composable
fun PulsePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LocalPulseDimens.current.m),
    content: @Composable () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.controlRadius),
        color = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * Section label. Small, semibold, tracked and muted — it labels a group of content without
 * competing with the content's own titles.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimens.s, bottom = dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
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
 * A generic information row: optional leading glyph, a title, a supporting line and trailing
 * content. Used for settings entries, workout rows and history rows so they all share one rhythm.
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
                .padding(vertical = dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(dimens.chipRadius))
                        .background(leadingContainer ?: colors.surfaceHover),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = leadingTint ?: colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(dimens.m))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        ) {
            row()
        }
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
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
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
                containerColor = colors.nav,
                titleContentColor = colors.textPrimary,
                navigationIconContentColor = colors.textSecondary,
                actionIconContentColor = colors.textSecondary,
            ),
        )
        PulseDivider()
    }
}

/** Vertical breathing room, named so a screen never has to spell out a raw dp value. */
@Composable
fun VerticalSpacer(height: Dp) = Spacer(Modifier.height(height))
