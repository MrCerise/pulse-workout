package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseElevation
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * Empty, loading, error and confirmation states.
 *
 * These are the screens a user sees when something is missing or something broke, so they follow one
 * rule: an empty or error state that can be resolved always offers the action that resolves it, and
 * the action is a real [PrimaryActionButton] rather than a piece of text that happens to be tappable.
 *
 * Visually they are the calmest screens in the app — a quiet glyph in a sunken well, a 17 sp title, a
 * 14 sp explanation — because a dead end should look deliberate, not broken.
 */

/**
 * The "nothing here yet" state. The glyph sits in a sunken well so the screen still reads as a
 * designed surface rather than as a blank one.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.xl, vertical = dimens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.m),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(LocalPulseShapes.current.card)
                .background(colors.surfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon ?: Icons.Filled.Flag,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(dimens.xs))
            PrimaryActionButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** A centred spinner with an optional line of context. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String? = null) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.m),
    ) {
        CircularProgressIndicator(
            color = colors.accent,
            trackColor = colors.track,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The "this went wrong" state, with a retry when there is something to retry. */
@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again",
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.xl, vertical = dimens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.m),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.dangerTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(dimens.xs))
            SecondaryActionButton(text = retryLabel, onClick = onRetry, icon = Icons.Filled.Refresh)
        }
    }
}

/**
 * The app's dialog shell.
 *
 * One radius, one elevation, one fill, one type ramp and one button system: a dialog in the
 * builder and a dialog in Settings are the same object, and neither of them is the platform's
 * default. The platform's own dialog would be the fastest thing to ship, and it would also be the
 * one place in the app that does not look like PULSE.
 */
@Composable
fun PulseDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val elevation = LocalPulseElevation.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = LocalPulseShapes.current.sheet,
            color = colors.surfaceOverlay,
            contentColor = colors.textPrimary,
            border = BorderStroke(dimens.borderWidth, colors.border),
            shadowElevation = elevation.level3,
        ) {
            Column(
                modifier = Modifier.padding(dimens.xl),
                verticalArrangement = Arrangement.spacedBy(dimens.s),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
                content()
            }
        }
    }
}

/**
 * A confirmation dialog. It is only red when it destroys something — [destructive] is required for
 * the destructive styling, so a routine confirmation cannot accidentally look like a warning.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val dimens = LocalPulseDimens.current
    PulseDialog(title = title, onDismiss = onDismiss) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalPulseColors.current.textSecondary,
        )
        Spacer(Modifier.height(dimens.s))
        if (destructive) {
            DangerActionButton(text = confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryActionButton(text = confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
        }
        GhostActionButton(
            text = dismissLabel,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The snackbar: overlay fill, hairline, the app's floating elevation, an accent action. */
@Composable
fun PulseSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val elevation = LocalPulseElevation.current
    Surface(
        modifier = modifier.padding(horizontal = dimens.l, vertical = dimens.s),
        shape = LocalPulseShapes.current.card,
        color = colors.surfaceOverlay,
        contentColor = colors.textPrimary,
        border = androidx.compose.foundation.BorderStroke(dimens.borderWidth, colors.border),
        shadowElevation = elevation.level2,
    ) {
        Row(
            modifier = Modifier.padding(start = dimens.l, end = dimens.s, top = dimens.s, bottom = dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            val actionLabel = data.visuals.actionLabel
            if (actionLabel != null) {
                Spacer(Modifier.width(dimens.s))
                Surface(
                    onClick = { data.performAction() },
                    shape = LocalPulseShapes.current.control,
                    color = Color.Transparent,
                    contentColor = colors.accent,
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = dimens.m, vertical = dimens.xs),
                    )
                }
            }
        }
    }
}
