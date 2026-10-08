package com.pulse.intervalcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes

/**
 * Loading, empty, error and confirmation states.
 *
 * These are the screens people actually see when something is missing, so they get the same care as
 * a populated view: an explanation of the state, and — where one exists — the action that resolves
 * it. There are no illustrations and no large decorative glyphs; a single muted icon and a sentence
 * is enough.
 */

/**
 * The empty state. [actionLabel] plus [onAction] resolve the state; a state that cannot be resolved
 * offers no button at all rather than a dead end.
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
            .padding(vertical = dimens.xl, horizontal = dimens.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.surfaceRaised, LocalPulseShapes.current.control),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(dimens.m))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(dimens.m))
            SecondaryActionButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** A loading state that says what is loading. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String? = null) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = colors.accent,
            trackColor = colors.track,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
        if (label != null) {
            Spacer(Modifier.height(dimens.m))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

/**
 * The error state. It always names what failed and, when retrying is possible, offers the retry —
 * an error message without a way forward is just an apology.
 */
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
            .padding(vertical = dimens.xl, horizontal = dimens.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.dangerTint, LocalPulseShapes.current.control),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).background(colors.error, CircleShape))
        }
        Spacer(Modifier.height(dimens.m))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(dimens.m))
            SecondaryActionButton(text = retryLabel, onClick = onRetry)
        }
    }
}

/**
 * A confirmation dialog. Destructive confirmations read from the same tokens as everything else, and
 * the confirm button is only red when the action actually destroys something — a dialog that shouts
 * on every action stops being a signal.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = LocalPulseShapes.current.card,
        containerColor = colors.surfaceOverlay,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        },
        text = {
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) colors.error else colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            }
        },
    )
}

/**
 * The app's toast. Rendered through `SnackbarHost { PulseSnackbar(it) }` so every transient message
 * in the application has the same surface, radius and typography.
 */
@Composable
fun PulseSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    Surface(
        modifier = modifier.padding(horizontal = LocalPulseDimens.current.l),
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceOverlay,
        border = BorderStroke(LocalPulseDimens.current.borderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = LocalPulseDimens.current.m, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(Modifier.width(LocalPulseDimens.current.s))
                TextButton(onClick = { data.performAction() }) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                    )
                }
            }
        }
    }
}
