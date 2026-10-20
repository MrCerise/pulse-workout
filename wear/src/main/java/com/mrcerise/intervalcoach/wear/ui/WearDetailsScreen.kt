package com.mrcerise.intervalcoach.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.pulse.engine.IntervalNode
import com.pulse.engine.Node
import com.pulse.engine.PhaseKind
import com.pulse.engine.RepeatGroup
import com.pulse.engine.TimelineExpander
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.wear.R
import com.mrcerise.intervalcoach.wear.WearAppContainer
import com.mrcerise.intervalcoach.wear.ui.theme.LocalWearColors
import com.mrcerise.intervalcoach.wear.ui.theme.wearCategory
import com.pulse.sync.WatchCommand
import com.pulse.sync.WatchCommandKind

/** One line of a workout's structure, flattened for a lazy list: an interval or a block header. */
private sealed interface StructureLine {
    val depth: Int

    data class Interval(override val depth: Int, val name: String, val kind: PhaseKind, val millis: Long?) : StructureLine
    data class Block(override val depth: Int, val name: String, val repeat: Int) : StructureLine
}

private fun flatten(nodes: List<Node>, depth: Int = 0): List<StructureLine> = nodes.flatMap { node ->
    when (node) {
        is IntervalNode -> {
            val interval = node.interval
            if (interval.omitted) {
                emptyList()
            } else {
                listOf(
                    StructureLine.Interval(
                        depth = depth,
                        name = interval.displayName() + (interval.sideLabel?.let { " · $it" } ?: ""),
                        kind = interval.kind,
                        millis = if (interval.isIndefinite) null else interval.durationMillis,
                    ),
                )
            }
        }
        is RepeatGroup -> listOf(StructureLine.Block(depth, node.name, node.repeat)) + flatten(node.children, depth + 1)
    }
}

/**
 * A workout before it starts — the watch's version of the phone's details screen: what it is,
 * how long and how hard, and exactly what happens, drawn the way it was built (a block of eight
 * rounds is one block marked ×8). Start sits on the bottom edge, where the thumb already is.
 *
 * Starting while another session runs — here or on the phone — is confirmed first, in words that
 * say which session will stop.
 */
@Composable
fun WearDetailsScreen(
    container: WearAppContainer,
    workoutId: String,
    onStart: (String) -> Unit,
) {
    val colors = LocalWearColors.current
    val workouts by container.workouts.workouts.collectAsStateWithLifecycle()
    val active by container.session.active.collectAsStateWithLifecycle()
    val remote by container.wearSync.remoteSession.collectAsStateWithLifecycle()
    val workout = workouts.firstOrNull { it.id == workoutId }
    var confirming by remember { mutableStateOf(false) }

    fun start() {
        if (remote != null) {
            // Taking over means the phone stops: its engine, its spoken cues and its history entry
            // all end where the user can see them.
            container.wearSync.sendCommand(WatchCommand(WatchCommandKind.STOP))
        }
        onStart(workoutId)
    }

    val plan = workout?.plan
    val timeline = remember(plan) { plan?.let { runCatching { TimelineExpander.expand(it) }.getOrNull() } }
    val lines = remember(plan) { plan?.let { flatten(it.nodes) }.orEmpty() }

    PulseListScreen(
        edgeButton = {
            EdgeButton(
                onClick = {
                    // Resuming this same workout needs no question; replacing another one does.
                    val running = active?.plan?.id
                    if ((running != null && running != workoutId) || remote != null) confirming = true else start()
                },
                enabled = timeline != null,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.details_start))
            }
        },
    ) { spec ->
        if (plan == null || timeline == null) {
            item(key = "missing") { Caption(stringResource(R.string.player_load_failed), spec) }
            return@PulseListScreen
        }
        val category = wearCategory(plan.type)
        item(key = "mark") {
            Box(Modifier.transformed(this, spec).padding(top = 4.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(40.dp).background(category.tint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(22.dp))
                }
            }
        }
        item(key = "name") {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.transformed(this, spec).padding(horizontal = 16.dp),
            )
        }
        item(key = "total") {
            Text(
                text = timeline.totalMillis?.let { formatDuration(it) } ?: ("≥ " + formatDuration(timeline.knownMillis)),
                style = MaterialTheme.typography.numeralSmall,
                color = colors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.transformed(this, spec),
            )
        }
        item(key = "split") {
            val work = timeline.steps.filter { it.kind == PhaseKind.WORK }.sumOf { it.durationMillis }
            val rest = timeline.steps.filter { it.kind == PhaseKind.REST }.sumOf { it.durationMillis }
            Caption(
                text = stringResource(R.string.details_split, formatDuration(work), formatDuration(rest), timeline.steps.size),
                spec = spec,
                color = colors.textSecondary,
            )
        }
        plan.equipment?.takeIf { it.isNotBlank() }?.let { equipment ->
            item(key = "equipment") { Caption(equipment, spec) }
        }
        item(key = "favorite") {
            SwitchButton(
                checked = workout.favorite,
                onCheckedChange = { container.wearSync.setFavorite(workoutId, it) },
                modifier = Modifier.transformed(this, spec),
                transformation = SurfaceTransformation(spec),
                icon = {
                    Icon(
                        if (workout.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(R.string.details_favorite)) },
            )
        }
        item(key = "structure") { ScreenHeader(stringResource(R.string.details_structure), spec) }
        lines.forEachIndexed { index, line ->
            item(key = "line-$index") {
                StructureRow(line, Modifier.transformed(this, spec))
            }
        }
    }

    val takeover = remote != null
    AlertDialog(
        visible = confirming,
        onDismissRequest = { confirming = false },
        icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
        title = {
            Text(
                stringResource(if (takeover) R.string.library_takeover_title else R.string.library_replace_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                stringResource(if (takeover) R.string.library_takeover_text else R.string.library_replace_text),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                confirming = false
                start()
            })
        },
    )
}

@Composable
private fun StructureRow(line: StructureLine, modifier: Modifier) {
    val colors = LocalWearColors.current
    Row(
        modifier = modifier.padding(start = (16 + 12 * line.depth).dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (line) {
            is StructureLine.Interval -> {
                Box(Modifier.size(8.dp).background(colors.phaseColor(line.kind), CircleShape))
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = line.millis?.let { formatDuration(it) } ?: stringResource(R.string.details_manual),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
            is StructureLine.Block -> {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "×${line.repeat}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                    modifier = Modifier
                        .background(colors.accentTint, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
