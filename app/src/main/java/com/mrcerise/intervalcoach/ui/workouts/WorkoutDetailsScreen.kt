package com.mrcerise.intervalcoach.ui.workouts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.PlanCodec
import com.mrcerise.intervalcoach.data.category
import com.mrcerise.intervalcoach.locale.categoryTitle
import com.mrcerise.intervalcoach.ui.components.ConfirmDialog
import com.mrcerise.intervalcoach.ui.components.ErrorState
import com.mrcerise.intervalcoach.ui.components.GhostActionButton
import com.mrcerise.intervalcoach.ui.components.InfoBanner
import com.mrcerise.intervalcoach.ui.components.LoadingBox
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.PulseBottomSheet
import com.mrcerise.intervalcoach.ui.components.PulseCard
import com.mrcerise.intervalcoach.ui.components.PulseDivider
import com.mrcerise.intervalcoach.ui.components.PulseIconButton
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.PulseSheetRow
import com.mrcerise.intervalcoach.ui.components.PulseTone
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.mrcerise.intervalcoach.ui.components.StatTile
import com.mrcerise.intervalcoach.ui.components.TimelineBar
import com.mrcerise.intervalcoach.ui.components.WorkoutQrCodes
import com.mrcerise.intervalcoach.ui.components.WorkoutQrCode
import com.mrcerise.intervalcoach.ui.summary.SummaryImage
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseElevation
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseType
import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.Node
import com.pulse.engine.RepeatGroup
import com.pulse.engine.TimelineExpander
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.sync.ShareCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A workout in full.
 *
 * Top to bottom it answers the questions in the order they are asked: what is this (category and
 * name), how long and how hard (total, work and rest time), what can I do with it (start, preview,
 * share, schedule), and what exactly happens (the structure, drawn the way it was built — a block
 * of eight rounds is one block marked ×8, not sixteen cards).
 */
@Composable
fun WorkoutDetailsScreen(
    container: AppContainer,
    workoutId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStart: (WorkoutPlan) -> Unit,
    onSchedule: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val context = LocalContext.current
    val strings = context.resources
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val planFlow = remember(workoutId) { container.workouts.observeEntity(workoutId) }
    val entity by planFlow.collectAsStateWithLifecycle(initialValue = null)
    val preview by container.sessionController.preview.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showShare by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val plan = remember(entity) { entity?.let { runCatching { PlanCodec.decode(it.planJson) }.getOrNull() } }
    val expanded = remember(plan) { plan?.let { runCatching { TimelineExpander.expand(it) }.getOrNull() } }

    PulseScaffold { padding ->
        if (entity != null && (plan == null || expanded == null)) {
            Column(Modifier.padding(padding)) {
                ErrorState(
                    title = strings.getString(R.string.workout_unreadable_title),
                    body = strings.getString(R.string.workout_unreadable_body),
                )
            }
            return@PulseScaffold
        }
        if (plan == null || expanded == null) {
            Column(Modifier.padding(padding)) {
                LoadingBox(label = strings.getString(R.string.workout_loading))
            }
            return@PulseScaffold
        }
        val category = plan.category
        val hue = colors.categoryColor(category.hue)
        val workMillis = expanded.steps.filter { it.kind.isEffort && !it.isIndefinite }.sumOf { it.durationMillis }
        val restMillis = expanded.steps.filter { it.kind.isRecovery && !it.isIndefinite }.sumOf { it.durationMillis }
        val isFavorite = entity?.isFavorite == true

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = dimens.pagePadding,
                end = dimens.pagePadding,
                top = dimens.cardGap,
                bottom = dimens.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            item {
                PulsePageHeader(
                    title = plan.name,
                    onBack = onBack,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.xs)) {
                            PulseIconButton(
                                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = strings.getString(
                                    if (isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add,
                                ),
                                tooltip = strings.getString(
                                    if (isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add,
                                ),
                                onClick = { scope.launch { container.workouts.setFavorite(workoutId, !isFavorite) } },
                                tint = if (isFavorite) colors.accent else colors.textMuted,
                            )
                            PulseIconButton(
                                icon = Icons.Filled.Edit,
                                contentDescription = strings.getString(R.string.workout_edit),
                                tooltip = strings.getString(R.string.workout_edit),
                                onClick = { onEdit(plan.id) },
                            )
                            PulseIconButton(
                                icon = Icons.Filled.MoreVert,
                                contentDescription = strings.getString(R.string.action_more),
                                tooltip = strings.getString(R.string.action_more),
                                onClick = { showMore = true },
                            )
                        }
                    },
                )
            }

            // --- Hero: category, total, work and rest ---------------------------------------
            item {
                PulseCard(tint = hue) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(colors.categoryTint(category.hue), LocalPulseShapes.current.control),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(category.icon, contentDescription = null, tint = hue, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.width(dimens.m))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = strings.categoryTitle(category),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = hue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                plan.equipment?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(dimens.l))
                        Text(
                            text = expanded.totalMillis?.let { formatDuration(it) } ?: "≥ ${formatDuration(expanded.knownMillis)}",
                            style = PulseType.NumericLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = strings.getString(R.string.total_time_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(dimens.l))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                            StatTile(
                                label = strings.getString(R.string.builder_work),
                                value = formatDuration(workMillis),
                                accent = colors.work,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = strings.getString(R.string.builder_rest),
                                value = formatDuration(restMillis),
                                accent = colors.rest,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = strings.getString(R.string.builder_editor_intervals),
                                value = expanded.steps.size.toString(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (expanded.hasOpenEnded) {
                            Spacer(Modifier.height(dimens.m))
                            InfoBanner(stringResource(R.string.details_open_ended_note))
                        }
                    }
                }
            }

            // --- Actions ---------------------------------------------------------------------
            item {
                PrimaryActionButton(
                    text = strings.getString(R.string.details_start),
                    onClick = { onStart(plan) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.PlayArrow,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(dimens.s),
                ) {
                    DetailAction(
                        icon = if (preview == null) Icons.Filled.RecordVoiceOver else Icons.Filled.Stop,
                        label = strings.getString(if (preview == null) R.string.details_preview else R.string.workout_stop_preview),
                        onClick = {
                            if (preview == null) container.sessionController.startPreview(plan) else container.sessionController.stopPreview()
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    DetailAction(
                        icon = Icons.Filled.Share,
                        label = strings.getString(R.string.action_share),
                        onClick = { showShare = true },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    DetailAction(
                        icon = Icons.Filled.Event,
                        label = strings.getString(R.string.planner_add),
                        onClick = { onSchedule(plan.id) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            preview?.let { state ->
                item {
                    InfoBanner(
                        text = strings.getString(R.string.workout_previewing, state.stepIndex + 1, state.totalSteps, state.stepName),
                        tone = PulseTone.INFO,
                    )
                }
            }

            // --- Structure -------------------------------------------------------------------
            item {
                SectionHeader(
                    text = strings.getString(R.string.details_timeline),
                    trailing = strings.getString(R.string.interval_count, expanded.steps.size),
                )
            }
            item {
                TimelineBar(
                    kinds = expanded.steps.map { it.kind },
                    currentIndex = -1,
                    progressInStep = 0f,
                    height = dimens.barHeightCompact,
                )
            }
            // No keys: imported plans are not guaranteed unique node ids, and a duplicate key crashes.
            items(plan.nodes) { node ->
                PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.s)) {
                    StructureNode(node)
                }
            }
            if (plan.tags.isNotEmpty()) {
                item {
                    Text(
                        text = plan.tags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }

    val current = plan
    if (showShare && current != null) {
        PulseBottomSheet(title = stringResource(R.string.summary_share_title), onDismiss = { showShare = false }) {
            PulseSheetRow(
                text = stringResource(R.string.workout_share_qr),
                leadingIcon = Icons.Filled.QrCode,
                onClick = { showShare = false; showQr = true },
            )
            PulseSheetRow(
                text = stringResource(R.string.workout_share_summary),
                leadingIcon = Icons.Filled.Description,
                onClick = { showShare = false; shareWorkoutText(context, current) },
            )
        }
    }
    if (showQr && current != null) {
        WorkoutQrDialog(plan = current, onDismiss = { showQr = false })
    }
    if (showMore && current != null) {
        PulseBottomSheet(title = stringResource(R.string.action_more), onDismiss = { showMore = false }) {
            PulseSheetRow(
                text = stringResource(R.string.action_duplicate),
                leadingIcon = Icons.Filled.ContentCopy,
                onClick = {
                    showMore = false
                    scope.launch {
                        val copy = runCatching { container.workouts.duplicate(current) }.getOrNull()
                        if (copy != null) onOpenWorkout(copy.id)
                    }
                },
            )
            PulseSheetRow(
                text = stringResource(R.string.action_delete),
                leadingIcon = Icons.Filled.Delete,
                destructive = true,
                onClick = { showMore = false; confirmDelete = true },
            )
        }
    }
    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_workout_title),
            body = strings.getString(R.string.delete_workout_body, current.name),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.workouts.delete(current.id)
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** One of the secondary actions under Start: an icon over a short label, equal widths. */
@Composable
private fun DetailAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        border = BorderStroke(dimens.borderWidth, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.s, vertical = dimens.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A node of the workout as it was built. Intervals are one line each; a repeat block is a header
 * (name, ×rounds, time per round) over its children, indented behind a rule in the block's colour.
 */
@Composable
private fun StructureNode(node: Node) {
    when (node) {
        is IntervalNode -> IntervalLine(node.interval)
        is RepeatGroup -> GroupBlock(node)
    }
}

@Composable
private fun IntervalLine(interval: IntervalSpec) {
    if (interval.omitted) return
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(colors.phaseColor(interval.kind), LocalPulseShapes.current.chip),
        )
        Spacer(Modifier.width(dimens.m))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.microGap)) {
            Text(
                text = buildString {
                    append(interval.displayName())
                    interval.sideLabel?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            interval.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.width(dimens.m))
        Text(
            text = if (interval.isIndefinite) strings.getString(R.string.manual) else formatDuration(interval.durationMillis),
            style = PulseType.NumericSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun GroupBlock(group: RepeatGroup) {
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val perRound = remember(group) { if (group.ladder == null) knownMillis(group.children) else null }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                val detail = buildString {
                    perRound?.let { append(strings.getString(R.string.details_block_per_round, formatDuration(it))) }
                    if (group.restAfterGroupMillis > 0L) {
                        append(strings.getString(R.string.builder_block_rest, formatDuration(group.restAfterGroupMillis)))
                    }
                }.trim().removePrefix("·").trim()
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
            Surface(
                shape = LocalPulseShapes.current.chip,
                color = colors.accent.copy(alpha = 0.14f),
                contentColor = colors.accent,
            ) {
                Text(
                    text = strings.getString(R.string.details_times, group.repeat),
                    style = PulseType.NumericSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = dimens.s, vertical = dimens.microGap),
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(colors.accent.copy(alpha = 0.4f)),
            )
            Spacer(Modifier.width(dimens.m))
            Column(Modifier.weight(1f)) {
                group.children.forEachIndexed { index, child ->
                    StructureNode(child)
                    if (index != group.children.lastIndex && child is RepeatGroup) PulseDivider()
                }
            }
        }
    }
}

/** Time of one pass through [nodes], or null when a manual interval makes it open-ended. */
private fun knownMillis(nodes: List<Node>): Long? {
    var total = 0L
    for (node in nodes) {
        total += when (node) {
            is IntervalNode -> when {
                node.interval.omitted -> 0L
                node.interval.isIndefinite -> return null
                else -> node.interval.durationMillis
            }
            is RepeatGroup -> {
                if (node.ladder != null) return null
                val inner = knownMillis(node.children) ?: return null
                (inner + node.restAfterGroupMillis) * node.repeat
            }
        }
    }
    return total
}

/**
 * The workout as a QR code another phone can scan into its library. The code carries the whole
 * workout ([ShareCode]); the dialog also offers it as an image, for chats and email.
 */
@Composable
internal fun WorkoutQrDialog(plan: WorkoutPlan, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val scope = rememberCoroutineScope()
    val qr = remember(plan) { runCatching { WorkoutQrCodes.render(ShareCode.encode(plan)) }.getOrNull() }
    var sharing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = LocalPulseShapes.current.sheet,
            color = colors.surfaceOverlay,
            contentColor = colors.textPrimary,
            border = BorderStroke(dimens.borderWidth, colors.border),
            shadowElevation = LocalPulseElevation.current.level3,
        ) {
            Column(
                modifier = Modifier.padding(dimens.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.m),
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                if (qr != null) {
                    WorkoutQrCode(qr = qr)
                    Text(
                        text = stringResource(R.string.workout_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryActionButton(
                        text = stringResource(R.string.workout_share_qr_image),
                        enabled = !sharing,
                        icon = Icons.Filled.Share,
                        onClick = {
                            sharing = true
                            scope.launch {
                                try {
                                    val uri = withContext(Dispatchers.IO) { WorkoutQrCodes.writeToCache(context, qr) }
                                    context.startActivity(SummaryImage.shareIntent(context, plan.name, uri))
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, R.string.summary_share_error, android.widget.Toast.LENGTH_LONG).show()
                                } finally {
                                    sharing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    InfoBanner(text = stringResource(R.string.workout_qr_too_large), tone = PulseTone.WARNING)
                }
                GhostActionButton(text = stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
