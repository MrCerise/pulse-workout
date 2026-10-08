package com.pulse.intervalcoach.ui.builders

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.HapticCue
import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.Ladder
import com.pulse.engine.Node
import com.pulse.engine.PhaseKind
import com.pulse.engine.RepeatGroup
import com.pulse.engine.SoundCue
import com.pulse.engine.TimelineExpander
import com.pulse.engine.Validation
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.PlanCodec
import com.pulse.intervalcoach.data.QuickWorkoutFactory
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.DurationStepper
import com.pulse.intervalcoach.ui.components.PulseTone
import com.pulse.intervalcoach.ui.components.PulseTextField
import com.pulse.intervalcoach.ui.components.PulseTopBar
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.NumberStepper
import com.pulse.intervalcoach.ui.components.OptionRow
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.TimelineBar
import com.pulse.intervalcoach.ui.components.ToggleRow
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.PulseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.R

/**
 * Unfinished editor drafts.
 *
 * Drafts are stored as plain JSON in app-internal storage, keyed by workout id, so a phone call,
 * an accidental back gesture or a process kill never costs the user their edits.
 */
class DraftStore(context: Context) {
    private val dir = File(context.filesDir, "drafts").apply { mkdirs() }

    fun save(draftId: String, plan: WorkoutPlan) {
        runCatching { File(dir, "$draftId.json").writeText(PlanCodec.json.encodeToString(WorkoutPlan.serializer(), plan)) }
    }

    fun load(draftId: String): WorkoutPlan? {
        val file = File(dir, "$draftId.json")
        if (!file.exists()) return null
        return runCatching { PlanCodec.decode(file.readText()) }.getOrNull()
    }

    fun clear(draftId: String) {
        runCatching { File(dir, "$draftId.json").delete() }
    }

    fun ids(): List<String> = dir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
}

// ---------------------------------------------------------------------------------------------
// Quick builder
// ---------------------------------------------------------------------------------------------

class QuickBuilderViewModel(
    private val container: AppContainer,
    private val draftId: String,
    context: Context,
) : ViewModel() {

    private val drafts = DraftStore(context)

    private val _state = MutableStateFlow(QuickBuilderState())
    val state: StateFlow<QuickBuilderState> = _state.asStateFlow()

    data class QuickBuilderState(
        val name: String = "Quick workout",
        val work: Long = 40_000,
        val rest: Long = 20_000,
        val rounds: Int = 8,
        val preparation: Long = 10_000,
        val cooldown: Long = 0L,
        val includeFinalRest: Boolean = false,
        val dirty: Boolean = false,
    ) {
        fun toPlan(id: String, createdAt: Long): WorkoutPlan = QuickWorkoutFactory.build(
            workMillis = work,
            restMillis = rest,
            rounds = rounds,
            preparationMillis = preparation,
            cooldownMillis = cooldown,
            includeFinalRest = includeFinalRest,
            name = name.ifBlank { "Quick workout" },
            id = id,
            createdAt = createdAt,
        )
    }

    init {
        viewModelScope.launch {
            val prefs = container.preferences.current()
            val restored = withContext(Dispatchers.IO) { drafts.load(draftId) }
            _state.value = if (restored != null) {
                QuickBuilderState(
                    name = restored.name,
                    work = restored.allIntervals().firstOrNull { it.kind == PhaseKind.WORK }?.durationMillis ?: prefs.defaultWorkMillis,
                    rest = restored.allIntervals().firstOrNull { it.kind == PhaseKind.REST }?.durationMillis ?: prefs.defaultRestMillis,
                    rounds = restored.nodes.filterIsInstance<RepeatGroup>().firstOrNull()?.repeat ?: prefs.defaultRounds,
                    preparation = restored.allIntervals().firstOrNull { it.kind == PhaseKind.PREPARE }?.durationMillis ?: 0L,
                    cooldown = restored.allIntervals().firstOrNull { it.kind == PhaseKind.COOLDOWN }?.durationMillis ?: 0L,
                    includeFinalRest = restored.includeFinalRest,
                    dirty = true,
                )
            } else {
                QuickBuilderState(
                    work = prefs.defaultWorkMillis,
                    rest = prefs.defaultRestMillis,
                    rounds = prefs.defaultRounds,
                    preparation = prefs.defaultPreparationMillis,
                    includeFinalRest = prefs.defaultIncludeFinalRest,
                )
            }
        }
    }

    fun update(transform: (QuickBuilderState) -> QuickBuilderState) {
        _state.value = transform(_state.value).copy(dirty = true)
        viewModelScope.launch(Dispatchers.IO) {
            drafts.save(draftId, _state.value.toPlan(planId, createdAt))
        }
    }

    val planId = "draft-quick"
    private val createdAt = System.currentTimeMillis()

    fun buildPlan(id: String = UUID.randomUUID().toString()): WorkoutPlan = _state.value.toPlan(id, System.currentTimeMillis())

    suspend fun save(plan: WorkoutPlan) {
        container.workouts.save(plan)
        withContext(Dispatchers.IO) { drafts.clear(draftId) }
    }

    fun discardDraft() {
        viewModelScope.launch(Dispatchers.IO) { drafts.clear(draftId) }
    }
}

@Composable
fun QuickBuilderScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSavedAndStart: (WorkoutPlan) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: QuickBuilderViewModel = viewModel(
        initializer = { QuickBuilderViewModel(container, "quick", context.applicationContext) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showDiscard by remember { mutableStateOf(false) }

    val plan = remember(state) { viewModel.buildPlan("preview") }
    val duration = remember(plan) { runCatching { TimelineExpander.expand(plan).knownMillis }.getOrDefault(0L) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = stringResource(R.string.builder_quick_title),
                subtitle = "Work · rest · rounds — the workout builds itself",
                onBack = { if (state.dirty) showDiscard = true else onBack() },
                backDescription = stringResource(R.string.back),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = LocalPulseDimens.current.pagePadding,
                end = LocalPulseDimens.current.pagePadding,
                top = LocalPulseDimens.current.cardGap,
                bottom = LocalPulseDimens.current.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.cardGap),
        ) {
            item {
                PulseTextField(
                    value = state.name,
                    onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                    label = stringResource(R.string.sort_name),
                    singleLine = true,
                )
            }
            item {
                PulseCard {
                    Column {
                        DurationStepper("Work", state.work, onChange = { value -> viewModel.update { it.copy(work = value) } })
                        DurationStepper("Rest", state.rest, onChange = { value -> viewModel.update { it.copy(rest = value) } })
                        NumberStepper("Rounds", state.rounds, max = 100, onChange = { value -> viewModel.update { it.copy(rounds = value) } })
                        DurationStepper("Preparation", state.preparation, onChange = { value -> viewModel.update { it.copy(preparation = value) } })
                        DurationStepper("Cool-down", state.cooldown, onChange = { value -> viewModel.update { it.copy(cooldown = value) } })
                        ToggleRow(
                            label = stringResource(R.string.builder_include_final_rest),
                            hint = stringResource(R.string.builder_include_final_rest_hint),
                            checked = state.includeFinalRest,
                            onCheckedChange = { value -> viewModel.update { it.copy(includeFinalRest = value) } },
                        )
                    }
                }
            }
            item {
                PulseCard {
                    Column {
                        Text(formatDuration(duration), style = PulseType.NumericLarge, color = colors.textPrimary)
                        Text("total time", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        Spacer(Modifier.height(LocalPulseDimens.current.m))
                        val expanded = remember(plan) { runCatching { TimelineExpander.expand(plan) }.getOrNull() }
                        TimelineBar(
                            kinds = expanded?.steps?.map { it.kind }.orEmpty(),
                            currentIndex = -1,
                            progressInStep = 0f,
                            height = LocalPulseDimens.current.barHeightCompact,
                        )
                    }
                }
            }
            item {
                SecondaryActionButton(
                    text = stringResource(R.string.builder_save),
                    onClick = { scope.launch { val id = UUID.randomUUID().toString(); viewModel.save(viewModel.buildPlan(id)); onSaved(id) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                PrimaryActionButton(
                    text = stringResource(R.string.builder_save_and_start),
                    onClick = {
                        scope.launch {
                            val planToStart = viewModel.buildPlan(UUID.randomUUID().toString())
                            viewModel.save(planToStart)
                            onSavedAndStart(planToStart)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Save your progress?",
            body = "You have an unfinished quick workout.",
            confirmLabel = stringResource(R.string.save_draft),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                scope.launch { val id = UUID.randomUUID().toString(); viewModel.save(viewModel.buildPlan(id)); showDiscard = false; onSaved(id) }
            },
            onDismiss = { showDiscard = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Advanced builder
// ---------------------------------------------------------------------------------------------

/** Immutable snapshot used for undo/redo. */
data class BuilderSnapshot(val nodes: List<Node>, val name: String, val includeFinalRest: Boolean, val shuffle: Boolean)

class AdvancedBuilderViewModel(
    private val container: AppContainer,
    private val draftId: String,
    private val context: Context,
) : ViewModel() {

    private val drafts = DraftStore(context)
    private val undoStack = ArrayDeque<BuilderSnapshot>()
    private val redoStack = ArrayDeque<BuilderSnapshot>()

    private val _state = MutableStateFlow(AdvancedState())
    val state: StateFlow<AdvancedState> = _state.asStateFlow()

    data class AdvancedState(
        val plan: WorkoutPlan = WorkoutPlan(id = UUID.randomUUID().toString(), name = "New workout"),
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val problems: List<String> = emptyList(),
        val dirty: Boolean = false,
        val loaded: Boolean = false,
        val savedId: String? = null,
    )

    init {
        viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) { drafts.load(draftId) }
            val source = existing
                ?: (draftId.takeIf { !it.startsWith("new") }?.let { container.workouts.plan(it) })
                ?: WorkoutPlan(
                    id = UUID.randomUUID().toString(),
                    name = "New workout",
                    nodes = listOf(defaultInterval()),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            _state.value = AdvancedState(plan = source, dirty = existing != null, loaded = true)
        }
    }

    private fun defaultInterval() = IntervalNode(
        UUID.randomUUID().toString(),
        IntervalSpec(id = UUID.randomUUID().toString(), name = "Work", kind = PhaseKind.WORK, durationMillis = 30_000),
    )

    private fun mutate(block: (WorkoutPlan) -> WorkoutPlan) {
        val current = _state.value.plan
        undoStack.addLast(current.toSnapshot())
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        val updated = block(current).copy(updatedAt = System.currentTimeMillis())
        _state.value = _state.value.copy(
            plan = updated,
            dirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false,
            problems = Validation.validate(updated),
        )
        viewModelScope.launch(Dispatchers.IO) { drafts.save(draftId, updated) }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_state.value.plan.toSnapshot())
        val plan = previous.toPlan(_state.value.plan.id)
        _state.value = _state.value.copy(
            plan = plan,
            canUndo = undoStack.isNotEmpty(),
            canRedo = true,
            problems = Validation.validate(plan),
            dirty = true,
        )
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_state.value.plan.toSnapshot())
        val plan = next.toPlan(_state.value.plan.id)
        _state.value = _state.value.copy(
            plan = plan,
            canUndo = true,
            canRedo = redoStack.isNotEmpty(),
            problems = Validation.validate(plan),
            dirty = true,
        )
    }

    fun rename(name: String) = mutate { it.copy(name = name) }
    fun setDescription(text: String) = mutate { it.copy(description = text) }
    fun setType(type: WorkoutType) = mutate { it.copy(type = type) }
    fun setEquipment(text: String) = mutate { it.copy(equipment = text.ifBlank { null }) }
    fun setShuffle(value: Boolean) = mutate { it.copy(shuffleEager = value) }
    fun setIncludeFinalRest(value: Boolean) = mutate { it.copy(includeFinalRest = value) }

    fun addInterval(kind: PhaseKind = PhaseKind.WORK, millis: Long = 30_000) = mutate { plan ->
        val node = IntervalNode(
            UUID.randomUUID().toString(),
            IntervalSpec(id = UUID.randomUUID().toString(), name = PhaseKind.defaultLabel(kind), kind = kind, durationMillis = millis),
        )
        plan.copy(nodes = plan.nodes + node)
    }

    fun addGroup(repeat: Int = 3, restAfterMillis: Long = 30_000) = mutate { plan ->
        val group = RepeatGroup(
            nodeId = UUID.randomUUID().toString(),
            name = "Block",
            repeat = repeat,
            children = listOf(
                IntervalNode(UUID.randomUUID().toString(), IntervalSpec(UUID.randomUUID().toString(), "Work", PhaseKind.WORK, 30_000)),
                IntervalNode(UUID.randomUUID().toString(), IntervalSpec(UUID.randomUUID().toString(), "Rest", PhaseKind.REST, 15_000)),
            ),
            restAfterGroupMillis = restAfterMillis,
        )
        plan.copy(nodes = plan.nodes + group)
    }

    fun updateNode(nodeId: String, transform: (Node) -> Node) = mutate { plan ->
        plan.copy(nodes = plan.nodes.map { if (it.nodeId == nodeId) transform(it) else it })
    }

    fun updateInterval(groupId: String?, intervalId: String, transform: (IntervalSpec) -> IntervalSpec) = mutate { plan ->
        plan.copy(nodes = plan.nodes.map { node -> mapInterval(node, groupId, intervalId, transform) })
    }

    private fun mapInterval(node: Node, groupId: String?, intervalId: String, transform: (IntervalSpec) -> IntervalSpec): Node = when (node) {
        is IntervalNode -> if (node.nodeId == intervalId && groupId == null) node.copy(interval = transform(node.interval)) else node
        is RepeatGroup -> node.copy(children = node.children.map { child ->
            if (node.nodeId == groupId && child is IntervalNode && child.nodeId == intervalId) {
                child.copy(interval = transform(child.interval))
            } else {
                mapInterval(child, groupId, intervalId, transform)
            }
        })
    }

    fun move(nodeId: String, delta: Int) = mutate { plan ->
        plan.copy(nodes = moveInList(plan.nodes, nodeId, delta))
    }

    private fun moveInList(nodes: List<Node>, nodeId: String, delta: Int): List<Node> {
        val index = nodes.indexOfFirst { it.nodeId == nodeId }
        if (index >= 0) {
            val target = (index + delta).coerceIn(0, nodes.lastIndex)
            if (target == index) return nodes
            val mutable = nodes.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(target, item)
            return mutable
        }
        return nodes.map { node ->
            if (node is RepeatGroup) node.copy(children = moveInList(node.children, nodeId, delta)) else node
        }
    }

    fun duplicate(nodeId: String) = mutate { plan ->
        plan.copy(nodes = duplicateInList(plan.nodes, nodeId))
    }

    private fun duplicateInList(nodes: List<Node>, nodeId: String): List<Node> {
        val index = nodes.indexOfFirst { it.nodeId == nodeId }
        if (index >= 0) {
            val copy = copyNode(nodes[index])
            val mutable = nodes.toMutableList()
            mutable.add(index + 1, copy)
            return mutable
        }
        return nodes.map { node -> if (node is RepeatGroup) node.copy(children = duplicateInList(node.children, nodeId)) else node }
    }

    private fun copyNode(node: Node): Node = when (node) {
        is IntervalNode -> node.copy(
            nodeId = UUID.randomUUID().toString(),
            interval = node.interval.copy(id = UUID.randomUUID().toString()),
        )
        is RepeatGroup -> node.copy(
            nodeId = UUID.randomUUID().toString(),
            children = node.children.map(::copyNode),
        )
    }

    fun delete(nodeId: String) = mutate { plan ->
        val removed = removeFromList(plan.nodes, nodeId)
        // A workout can never become empty: keep at least one interval so saving stays possible.
        plan.copy(nodes = removed.ifEmpty { listOf(defaultInterval()) })
    }

    private fun removeFromList(nodes: List<Node>, nodeId: String): List<Node> {
        if (nodes.any { it.nodeId == nodeId }) return nodes.filterNot { it.nodeId == nodeId }
        return nodes.map { node -> if (node is RepeatGroup) node.copy(children = removeFromList(node.children, nodeId)) else node }
    }

    /** Adds a left/right pair for the selected interval: "Left" then a duplicate labelled "Right". */
    fun addLeftRight(intervalId: String, groupId: String?) = mutate { plan ->
        plan.copy(nodes = plan.nodes.map { node -> injectLeftRight(node, intervalId, groupId) })
    }

    private fun injectLeftRight(node: Node, intervalId: String, groupId: String?): Node = when (node) {
        is IntervalNode -> if (node.nodeId == intervalId) {
            node.copy(interval = node.interval.copy(name = node.interval.name.ifBlank { "Hold" }, sideLabel = "Left"))
        } else node
        is RepeatGroup -> {
            if (node.nodeId == groupId) {
                val expanded = node.children.flatMap { child ->
                    if (child is IntervalNode && child.nodeId == intervalId) {
                        val left = child.copy(interval = child.interval.copy(id = UUID.randomUUID().toString(), sideLabel = "Left"))
                        val right = child.copy(
                            nodeId = UUID.randomUUID().toString(),
                            interval = child.interval.copy(id = UUID.randomUUID().toString(), sideLabel = "Right"),
                        )
                        listOf(left, right)
                    } else {
                        listOf(child)
                    }
                }
                node.copy(children = expanded)
            } else {
                node.copy(children = node.children.map { injectLeftRight(it, intervalId, groupId) })
            }
        }
    }

    fun setLadder(groupId: String, ladder: Ladder?) = mutate { plan ->
        plan.copy(nodes = plan.nodes.map { node ->
            if (node is RepeatGroup && node.nodeId == groupId) node.copy(ladder = ladder) else node
        })
    }

    fun bulkDuration(deltaMillis: Long, kind: PhaseKind?) = mutate { plan ->
        plan.copy(nodes = plan.nodes.map { node -> adjust(node, deltaMillis, kind) })
    }

    private fun adjust(node: Node, delta: Long, kind: PhaseKind?): Node = when (node) {
        is IntervalNode -> if (kind == null || node.interval.kind == kind) {
            node.copy(interval = node.interval.copy(durationMillis = (node.interval.durationMillis + delta).coerceAtLeast(0L)))
        } else node
        is RepeatGroup -> node.copy(children = node.children.map { adjust(it, delta, kind) })
    }

    suspend fun save(): Result<WorkoutPlan> {
        val plan = _state.value.plan.copy(createdAt = System.currentTimeMillis())
        val problems = Validation.validate(plan)
        if (problems.isNotEmpty()) {
            _state.value = _state.value.copy(problems = problems)
            return Result.failure(IllegalArgumentException(problems.joinToString(" ")))
        }
        return runCatching {
            container.workouts.save(plan)
            withContext(Dispatchers.IO) { drafts.clear(draftId) }
            _state.value = _state.value.copy(dirty = false, savedId = plan.id)
            plan
        }
    }

    fun discardDraft() {
        viewModelScope.launch(Dispatchers.IO) { drafts.clear(draftId) }
    }

    private fun WorkoutPlan.toSnapshot() = BuilderSnapshot(nodes, name, includeFinalRest, shuffleEager)
    private fun BuilderSnapshot.toPlan(id: String) = WorkoutPlan(
        id = id,
        name = name,
        nodes = nodes,
        includeFinalRest = includeFinalRest,
        shuffleEager = shuffle,
    )
}

@Composable
fun AdvancedBuilderScreen(
    container: AppContainer,
    workoutId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val draftId = workoutId ?: "new-${remember { UUID.randomUUID().toString() }}"
    val viewModel: AdvancedBuilderViewModel = viewModel(
        key = draftId,
        initializer = { AdvancedBuilderViewModel(container, draftId, context.applicationContext) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var expandedNode by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var invalidMessage by remember { mutableStateOf<String?>(null) }

    val expanded = remember(state.plan) { runCatching { TimelineExpander.expand(state.plan) }.getOrNull() }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = if (workoutId == null) "New workout" else "Edit workout",
                subtitle = "Order, cues and structure — undo anything",
                onBack = { if (state.dirty) showDiscard = true else onBack() },
                backDescription = stringResource(R.string.back),
                actions = {
                    PulseIconButton(
                        icon = Icons.Filled.Undo,
                        contentDescription = stringResource(R.string.builder_undo),
                        tooltip = stringResource(R.string.builder_undo),
                        onClick = viewModel::undo,
                        enabled = state.canUndo,
                    )
                    PulseIconButton(
                        icon = Icons.Filled.Redo,
                        contentDescription = stringResource(R.string.builder_redo),
                        tooltip = stringResource(R.string.builder_redo),
                        onClick = viewModel::redo,
                        enabled = state.canRedo,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = LocalPulseDimens.current.pagePadding,
                end = LocalPulseDimens.current.pagePadding,
                top = LocalPulseDimens.current.cardGap,
                bottom = LocalPulseDimens.current.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.cardGap),
        ) {
            item {
                PulseTextField(
                    value = state.plan.name,
                    onValueChange = viewModel::rename,
                    label = stringResource(R.string.sort_name),
                    singleLine = true,
                )
            }
            item {
                PulseTextField(
                    value = state.plan.description,
                    onValueChange = viewModel::setDescription,
                    label = stringResource(R.string.builder_description),
                    minLines = 2,
                )
            }
            item {
                PulseCard {
                    Column {
                        OptionRow(
                            label = stringResource(R.string.builder_type),
                            options = listOf(
                                WorkoutType.HIIT, WorkoutType.TABATA, WorkoutType.CIRCUIT, WorkoutType.BOXING,
                                WorkoutType.EMOM, WorkoutType.AMRAP, WorkoutType.STRENGTH, WorkoutType.RUN_WALK,
                                WorkoutType.STRETCHING, WorkoutType.BREATHING, WorkoutType.FOCUS,
                                WorkoutType.CUSTOM_SEQUENCE, WorkoutType.COMPOUND, WorkoutType.STOPWATCH, WorkoutType.CUE_TIMER,
                            ).map { it to it.displayName },
                            selected = state.plan.type,
                            onSelect = viewModel::setType,
                        )
                        Spacer(Modifier.height(LocalPulseDimens.current.cardGap))
                        PulseTextField(
                            value = state.plan.equipment.orEmpty(),
                            onValueChange = viewModel::setEquipment,
                            label = stringResource(R.string.details_equipment),
                            singleLine = true,
                        )
                        ToggleRow(
                            label = stringResource(R.string.builder_include_final_rest),
                            hint = stringResource(R.string.builder_include_final_rest_hint),
                            checked = state.plan.includeFinalRest,
                            onCheckedChange = viewModel::setIncludeFinalRest,
                        )
                        ToggleRow(
                            label = stringResource(R.string.builder_shuffle),
                            hint = "Order is fixed the moment the session starts.",
                            checked = state.plan.shuffleEager,
                            onCheckedChange = viewModel::setShuffle,
                        )
                    }
                }
            }

            item {
                PulseCard {
                    Column {
                        Text(
                            expanded?.totalMillis?.let { formatDuration(it) } ?: "≥ ${formatDuration(expanded?.knownMillis ?: 0L)}",
                            style = MaterialTheme.typography.displaySmall,
                            color = colors.work,
                        )
                        Text(
                            "${expanded?.steps?.size ?: 0} intervals" +
                                (if (expanded?.hasOpenEnded == true) " · total is a minimum (some intervals wait for you)" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(LocalPulseDimens.current.m))
                        TimelineBar(
                            expanded?.steps?.map { it.kind }.orEmpty(),
                            currentIndex = -1,
                            progressInStep = 0f,
                            height = LocalPulseDimens.current.barHeightCompact,
                        )
                    }
                }
            }

            if (state.problems.isNotEmpty()) {
                item {
                    InfoBanner(state.problems.joinToString(" "), tone = PulseTone.WARNING)
                }
            }

            item { SectionHeader("Intervals") }

            items(state.plan.nodes.size) { index ->
                val node = state.plan.nodes[index]
                BuilderNodeCard(
                    node = node,
                    position = index,
                    total = state.plan.nodes.size,
                    expanded = expandedNode == node.nodeId,
                    onToggle = { expandedNode = if (expandedNode == node.nodeId) null else node.nodeId },
                    viewModel = viewModel,
                    onMoveUp = { viewModel.move(node.nodeId, -1) },
                    onMoveDown = { viewModel.move(node.nodeId, 1) },
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.s)) {
                    SecondaryActionButton("Add interval", { viewModel.addInterval() }, Modifier.weight(1f))
                    SecondaryActionButton("Add block", { viewModel.addGroup() }, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.s)) {
                    SecondaryActionButton("−5 s all", { viewModel.bulkDuration(-5_000, null) }, Modifier.weight(1f))
                    SecondaryActionButton("+5 s all", { viewModel.bulkDuration(5_000, null) }, Modifier.weight(1f))
                }
            }
            item {
                PrimaryActionButton(
                    text = stringResource(R.string.builder_save),
                    onClick = {
                        scope.launch {
                            viewModel.save()
                                .onSuccess { plan -> onSaved(plan.id) }
                                .onFailure { error -> invalidMessage = error.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.problems.isEmpty(),
                )
            }
        }
    }

    invalidMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { invalidMessage = null },
            title = { Text("Cannot save yet") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { invalidMessage = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.discard_draft_title)) },
            text = { Text("You have unsaved edits to “${state.plan.name}”. Drafts are kept automatically, so you can come back to them.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.discardDraft()
                    showDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.discard_draft)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.save().onSuccess { onSaved(it.id) }
                        showDiscard = false
                    }
                }) { Text(stringResource(R.string.save_draft)) }
            },
        )
    }
}

@Composable
private fun BuilderNodeCard(
    node: Node,
    position: Int,
    total: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    viewModel: AdvancedBuilderViewModel,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val colors = LocalPulseColors.current
    PulseCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
                    when (node) {
                        is IntervalNode -> {
                            Text(node.interval.displayName(), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (node.interval.isIndefinite) "Manual" else formatDuration(node.interval.durationMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        is RepeatGroup -> {
                            Text("${node.name} × ${node.repeat}", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            Text(
                                "${node.children.size} intervals" + if (node.restAfterGroupMillis > 0) " · rest ${formatDuration(node.restAfterGroupMillis)}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
                if (node is IntervalNode) PhaseChip(kind = node.interval.kind, name = null)
                PulseIconButton(
                    icon = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.builder_move_up),
                    tooltip = stringResource(R.string.builder_move_up),
                    onClick = onMoveUp,
                    enabled = position > 0,
                )
                PulseIconButton(
                    icon = Icons.Filled.ArrowDownward,
                    contentDescription = stringResource(R.string.builder_move_down),
                    tooltip = stringResource(R.string.builder_move_down),
                    onClick = onMoveDown,
                    enabled = position < total - 1,
                )
                PulseIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.action_duplicate),
                    tooltip = stringResource(R.string.action_duplicate),
                    onClick = { viewModel.duplicate(node.nodeId) },
                )
                PulseIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tooltip = stringResource(R.string.action_delete),
                    onClick = { viewModel.delete(node.nodeId) },
                    tint = colors.destructive,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(LocalPulseDimens.current.cardGap))
                when (node) {
                    is IntervalNode -> IntervalEditor(node, null, viewModel)
                    is RepeatGroup -> RepeatGroupEditor(node, viewModel)
                }
            }
        }
    }
}

@Composable
private fun IntervalEditor(node: IntervalNode, groupId: String?, viewModel: AdvancedBuilderViewModel) {
    val colors = LocalPulseColors.current
    val spec = node.interval
    Column {
        PulseTextField(
            value = spec.name,
            onValueChange = { value -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(name = value) } },
            label = "Interval name",
            singleLine = true,
        )
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        OptionRow(
            label = "Phase",
            options = listOf(
                PhaseKind.PREPARE, PhaseKind.WARM_UP, PhaseKind.WORK, PhaseKind.REST,
                PhaseKind.TRANSITION, PhaseKind.COOLDOWN, PhaseKind.CUSTOM,
            ).map { it to PhaseKind.defaultLabel(it) },
            selected = spec.kind,
            onSelect = { kind -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(kind = kind) } },
        )
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        ToggleRow(
            label = stringResource(R.string.builder_manual),
            hint = stringResource(R.string.builder_manual_hint),
            checked = spec.manualCompletion,
            onCheckedChange = { value -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(manualCompletion = value) } },
        )
        if (!spec.manualCompletion) {
            DurationStepper(
                label = stringResource(R.string.sort_duration),
                millis = spec.durationMillis,
                onChange = { value -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(durationMillis = value) } },
                allowZero = false,
            )
        }
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        OptionRow(
            label = stringResource(R.string.builder_sound),
            options = listOf(SoundCue.TICK, SoundCue.BEEP, SoundCue.DOUBLE_BEEP, SoundCue.BELL, SoundCue.WHISTLE, SoundCue.CHIME, SoundCue.BUZZ, SoundCue.NONE)
                .map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selected = spec.sound,
            onSelect = { cue -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(sound = cue) } },
        )
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        OptionRow(
            label = stringResource(R.string.builder_haptic),
            options = listOf(HapticCue.LIGHT, HapticCue.DOUBLE, HapticCue.STRONG, HapticCue.PATTERN_321, HapticCue.NONE)
                .map { it to it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } },
            selected = spec.haptic,
            onSelect = { cue -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(haptic = cue) } },
        )
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        PulseTextField(
            value = spec.speechText.orEmpty(),
            onValueChange = { value -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(speechText = value.ifBlank { null }) } },
            label = "Spoken cue (optional)",
        )
        PulseTextField(
            value = spec.notes.orEmpty(),
            onValueChange = { value -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(notes = value.ifBlank { null }) } },
            label = "Note (optional)",
        )
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        Row(horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.s)) {
            SecondaryActionButton(
                text = stringResource(R.string.builder_left_right),
                onClick = { viewModel.addLeftRight(node.nodeId, groupId) },
                modifier = Modifier.weight(1f),
            )
            if (groupId == null) {
                SecondaryActionButton(
                    text = stringResource(R.string.planner_delete),
                    onClick = { viewModel.delete(node.nodeId) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(LocalPulseDimens.current.xs))
        Text(
            "Tip: colour, image and repetition targets live on the interval and are preserved when you duplicate the block.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun RepeatGroupEditor(node: RepeatGroup, viewModel: AdvancedBuilderViewModel) {
    val colors = LocalPulseColors.current
    Column {
        PulseTextField(
            value = node.name,
            onValueChange = { value -> viewModel.updateNode(node.nodeId) { group -> (group as RepeatGroup).copy(name = value) } },
            label = "Block name",
            singleLine = true,
        )
        NumberStepper(
            label = stringResource(R.string.builder_repeats),
            value = node.repeat,
            onChange = { value -> viewModel.updateNode(node.nodeId) { group -> (group as RepeatGroup).copy(repeat = value) } },
            max = 100,
        )
        DurationStepper(
            label = stringResource(R.string.builder_rest_after_group),
            millis = node.restAfterGroupMillis,
            onChange = { value -> viewModel.updateNode(node.nodeId) { group -> (group as RepeatGroup).copy(restAfterGroupMillis = value) } },
        )
        ToggleRow(
            label = stringResource(R.string.builder_ladder),
            hint = "Lengthens work intervals by 10 s each round (clamped between 10 s and 90 s).",
            checked = node.ladder != null,
            onCheckedChange = { enabled ->
                viewModel.setLadder(
                    node.nodeId,
                    if (enabled) Ladder(target = Ladder.TargetKind.WORK, deltaMillis = 10_000, minMillis = 10_000, maxMillis = 90_000) else null,
                )
            },
        )
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        Text("Intervals inside this block", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        node.children.forEachIndexed { index, child ->
            Spacer(Modifier.height(LocalPulseDimens.current.xs))
            var expanded by remember { mutableStateOf(false) }
            PulseCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                            Text(
                                when (child) {
                                    is IntervalNode -> child.interval.displayName()
                                    is RepeatGroup -> child.name
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                            )
                            Text(
                                when (child) {
                                    is IntervalNode -> if (child.interval.isIndefinite) "Manual" else formatDuration(child.interval.durationMillis)
                                    is RepeatGroup -> "× ${child.repeat}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        PulseIconButton(
                            icon = Icons.Filled.ArrowUpward,
                            contentDescription = "Move up inside block",
                            onClick = { viewModel.move(child.nodeId, -1) },
                            enabled = index > 0,
                        )
                        PulseIconButton(
                            icon = Icons.Filled.ArrowDownward,
                            contentDescription = "Move down inside block",
                            onClick = { viewModel.move(child.nodeId, 1) },
                            enabled = index < node.children.lastIndex,
                        )
                        PulseIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Delete interval",
                            onClick = { viewModel.delete(child.nodeId) },
                            tint = colors.destructive,
                        )
                    }
                    if (expanded && child is IntervalNode) {
                        Spacer(Modifier.height(LocalPulseDimens.current.m))
                        IntervalEditor(child, node.nodeId, viewModel)
                    }
                }
            }
        }
        Spacer(Modifier.height(LocalPulseDimens.current.s))
        SecondaryActionButton(
            text = "Add interval to block",
            onClick = {
                viewModel.updateNode(node.nodeId) { group ->
                    (group as RepeatGroup).copy(
                        children = group.children + IntervalNode(
                            UUID.randomUUID().toString(),
                            IntervalSpec(UUID.randomUUID().toString(), "Work", PhaseKind.WORK, 30_000),
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
