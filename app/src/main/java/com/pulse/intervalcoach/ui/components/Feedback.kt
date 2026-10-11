package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * Empty, loading, error and confirmation states.
 *
 * These are the screens a user sees when something is missing or something broke, so they follow one
 * rule: an empty or error state that can be resolved always offers the action that resolves it, and
 * the action is a real [PrimaryActionButton] rather than a piece of text that happens to be tappable.
 */

/**
 * The "nothing here yet" state. The glyph sits in a tinted circle so an empty screen still looks
 * designed rather than broken.
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
                .size(76.dp)
                .background(
                    Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.30f), colors.accentAlt.copy(alpha = 0.30f))),
                    LocalPulseShapes.current.card,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon ?: Icons.Filled.Flag,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(34.dp),
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
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp),
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
                .size(76.dp)
                .background(colors.dangerTint, LocalPulseShapes.current.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(34.dp),
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
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = LocalPulseShapes.current.card,
            color = colors.surfaceOverlay,
            contentColor = colors.textPrimary,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(dimens.xl),
                verticalArrangement = Arrangement.spacedBy(dimens.m),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(dimens.xs))
                if (destructive) {
                    DangerActionButton(text = confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryActionButton(text = confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = LocalPulseShapes.current.control,
                ) {
                    Text(
                        text = dismissLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** The snackbar. Overlay fill, 18 dp radius, accent action. */
@Composable
fun PulseSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier.padding(horizontal = dimens.l, vertical = dimens.s),
        shape = LocalPulseShapes.current.panel,
        color = colors.surfaceOverlay,
        contentColor = colors.textPrimary,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.l, vertical = dimens.m),
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
                Spacer(Modifier.width(dimens.m))
                TextButton(
                    onClick = { data.performAction() },
                    shape = LocalPulseShapes.current.chip,
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                    )
                }
            }
        }
    }
}
