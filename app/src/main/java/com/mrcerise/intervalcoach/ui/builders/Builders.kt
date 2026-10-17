package com.mrcerise.intervalcoach.ui.builders

import com.mrcerise.intervalcoach.locale.hapticLabel
import com.mrcerise.intervalcoach.locale.soundLabel
import com.mrcerise.intervalcoach.locale.phaseLabel
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import com.pulse.engine.RaceMath
import com.pulse.engine.RepeatGroup
import com.pulse.engine.SoundCue
import com.pulse.engine.TimelineExpander
import com.pulse.engine.Validation
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import com.pulse.engine.formatDuration
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.locale.AppLocale
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.PlanCodec
import com.mrcerise.intervalcoach.data.QuickWorkoutFactory
import com.mrcerise.intervalcoach.data.category
import com.mrcerise.intervalcoach.ui.components.CategoryPicker
import com.mrcerise.intervalcoach.ui.components.ConfirmDialog
import com.mrcerise.intervalcoach.ui.components.DurationStepper
import com.mrcerise.intervalcoach.ui.components.GhostActionButton
import com.mrcerise.intervalcoach.ui.components.InfoBanner
import com.mrcerise.intervalcoach.ui.components.NumberStepper
import com.mrcerise.intervalcoach.ui.components.OptionRow
import com.mrcerise.intervalcoach.ui.components.PhaseChip
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.PulseActionMenu
import com.mrcerise.intervalcoach.ui.components.PulseIconButton
import com.mrcerise.intervalcoach.ui.components.PulseMenuAction
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.PulseTextField
import com.mrcerise.intervalcoach.ui.components.PulseTone
import com.mrcerise.intervalcoach.ui.components.SecondaryActionButton
import com.mrcerise.intervalcoach.ui.components.PulseDivider
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.mrcerise.intervalcoach.ui.components.TimelineBar
import com.mrcerise.intervalcoach.ui.components.ToggleRow
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseElevation
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseType
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unfinished editor drafts.
 *
 * Drafts are stored as plain JSON in app-internal storage, keyed by workout id, so a phone call,
 * an accidental back gesture or a process kill never costs the user their edits.
 */
class DraftStore(context: Context) {
    private val dir = File(context.filesDir, "drafts").apply { mkdirs() }
    private val canonicalDir = dir.canonicalFile

    /** Imported workout IDs are data, not path components; map them to fixed-size filenames. */
    private fun fileFor(draftId: String): File? {
        if (draftId.length > 4096 || Files.isSymbolicLink(dir.toPath())) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(draftId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val file = File(dir, "$digest.json")
        if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile.parentFile != canonicalDir) return null
        return file
    }

    /** Read old safe-name drafts once for compatibility; never resolve a legacy path with separators. */
    private fun legacyFileFor(draftId: String): File? {
        if (!draftId.matches(Regex("[A-Za-z0-9_-]{1,128}")) || Files.isSymbolicLink(dir.toPath())) return null
        val file = File(dir, "$draftId.json")
        if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile.parentFile != canonicalDir) return null
        return file
    }

    fun save(draftId: String, plan: WorkoutPlan) {
        val file = fileFor(draftId) ?: return
        runCatching {
            file.writeText(PlanCodec.json.encodeToString(WorkoutPlan.serializer(), plan))
            legacyFileFor(draftId)?.delete()
        }
    }

    fun load(draftId: String): WorkoutPlan? {
        val file = fileFor(draftId) ?: return null
        val source = if (file.exists()) file else legacyFileFor(draftId) ?: return null
        return runCatching { PlanCodec.decode(source.readText()) }.getOrNull()
    }

    fun clear(draftId: String) {
        runCatching {
            fileFor(draftId)?.delete()
            legacyFileFor(draftId)?.delete()
        }
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
    private var labelResources = context.resources

    private val _state = MutableStateFlow(QuickBuilderState())
    val state: StateFlow<QuickBuilderState> = _state.asStateFlow()

    data class QuickBuilderState(
        val name: String = "",
        val work: Long = 40_000,
        val rest: Long = 20_000,
        val rounds: Int = 8,
        val preparation: Long = 10_000,
        val cooldown: Long = 0L,
        val includeFinalRest: Boolean = false,
        val dirty: Boolean = false,
    ) {
        fun toPlan(id: String, createdAt: Long, resources: android.content.res.Resources): WorkoutPlan = QuickWorkoutFactory.build(
            workMillis = work,
            restMillis = rest,
            rounds = rounds,
            preparationMillis = preparation,
            cooldownMillis = cooldown,
            includeFinalRest = includeFinalRest,
            name = name.ifBlank { resources.getString(R.string.quick_workout_name) },
            workName = resources.getString(R.string.builder_work),
            restName = resources.getString(R.string.builder_rest),
            preparationName = resources.getString(R.string.phase_prepare),
            cooldownName = resources.getString(R.string.builder_cooldown),
            roundName = resources.getString(R.string.builder_group),
            id = id,
            createdAt = createdAt,
        )
    }

    init {
        viewModelScope.launch {
            val prefs = container.preferences.current()
            labelResources = AppLocale.localizedContext(context, prefs.appLanguage).resources
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
                    name = labelResources.getString(R.string.quick_workout_name),
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
            drafts.save(draftId, _state.value.toPlan(planId, createdAt, labelResources))
        }
    }

    val planId = "draft-quick"
    private val createdAt = System.currentTimeMillis()

    fun buildPlan(id: String = UUID.randomUUID().toString()): WorkoutPlan = _state.value.toPlan(id, System.currentTimeMillis(), labelResources)

    suspend fun save(plan: WorkoutPlan) {
        container.workouts.save(plan)
        withContext(Dispatchers.IO) { drafts.clear(draftId) }
    }

    fun discardDraft() {
        viewModelScope.launch(Dispatchers.IO) { drafts.clear(draftId) }
    }
}

/** The workout name: a big inline-editable plate (same box as before, but typed in directly — no popup). */
@Composable
fun WorkoutNamePlate(
    name: String,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.builder_name_hint),
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LocalPulseShapes.current.control,
        color = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        border = BorderStroke(dimens.borderWidth, colors.borderStrong),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = dimens.l, vertical = dimens.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = onRename,
                singleLine = true,
                textStyle = PulseType.NamePlate.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (name.isBlank()) Text(placeholder, style = PulseType.NamePlate, color = colors.textMuted, textAlign = TextAlign.Center)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(dimens.m))
            Icon(Icons.Filled.Edit, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** A big inline name box for an interval/block — same plate as the workout name, typed in directly. */
@Composable
fun BigNameField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(modifier.fillMaxWidth()) {
        Text(label, style = PulseType.SectionLabel, color = colors.textMuted, modifier = Modifier.padding(start = dimens.xs, bottom = dimens.xs))
        Surface(modifier = Modifier.fillMaxWidth(), shape = LocalPulseShapes.current.control,
            color = colors.surfaceRaised, contentColor = colors.textPrimary,
            border = BorderStroke(dimens.borderWidth, colors.borderStrong)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = dimens.l, vertical = dimens.m),
                contentAlignment = Alignment.CenterStart) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value, onValueChange = onValueChange, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) Text(label, style = MaterialTheme.typography.titleLarge, color = colors.textMuted)
                            inner()
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun QuickBuilderScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSavedAndStart: (WorkoutPlan) -> Unit,
) {
    val strings = LocalContext.current.resources
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

    PulseScaffold { padding ->
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
                PulsePageHeader(
                    title = stringResource(R.string.builder_quick_title),
                    onBack = { if (state.dirty) showDiscard = true else onBack() },
                )
            }

            item {
                WorkoutNamePlate(
                    name = state.name,
                    onRename = { value -> viewModel.update { it.copy(name = value) } },
                )
            }
            item { SectionHeader(strings.getString(R.string.builder_editor_timing)) }
            item {
                Column {
                    DurationStepper(strings.getString(R.string.builder_work), state.work, onChange = { value -> viewModel.update { it.copy(work = value) } })
                    DurationStepper(strings.getString(R.string.builder_rest), state.rest, onChange = { value -> viewModel.update { it.copy(rest = value) } })
                    NumberStepper(strings.getString(R.string.builder_rounds), state.rounds, max = 100, onChange = { value -> viewModel.update { it.copy(rounds = value) } })
                    DurationStepper(strings.getString(R.string.builder_preparation), state.preparation, onChange = { value -> viewModel.update { it.copy(preparation = value) } })
                    DurationStepper(strings.getString(R.string.builder_cooldown), state.cooldown, onChange = { value -> viewModel.update { it.copy(cooldown = value) } })
                    ToggleRow(
                        label = stringResource(R.string.builder_include_final_rest),
                        checked = state.includeFinalRest,
                        onCheckedChange = { value -> viewModel.update { it.copy(includeFinalRest = value) } },
                    )

                }
            }
            item { SectionHeader(strings.getString(R.string.voice_preview), trailing = formatDuration(duration)) }
            item {
                Column {
                    Text(formatDuration(duration), style = PulseType.NumericLarge, color = colors.textPrimary)
                    Text(strings.getString(R.string.total_time_label), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
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
            title = strings.getString(R.string.builder_save_progress),
            body = strings.getString(R.string.builder_unfinished),
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

/**
 * Immutable snapshot used for undo/redo.
 *
 * A snapshot is the *whole* plan rather than the four fields the builder happened to edit first:
 * undoing an interval has to leave the name, category, description, equipment and creation date of
 * the workout alone, or an edit that undoes one step quietly rewrites the rest of the workout.
 */
data class BuilderSnapshot(val plan: WorkoutPlan)

class AdvancedBuilderViewModel(
    private val container: AppContainer,
    private val draftId: String,
    private val context: Context,
) : ViewModel() {

    private val drafts = DraftStore(context)
    private var labelResources = context.resources
    private val undoStack = ArrayDeque<BuilderSnapshot>()
    private val redoStack = ArrayDeque<BuilderSnapshot>()

    private val _state = MutableStateFlow(AdvancedState())
    val state: StateFlow<AdvancedState> = _state.asStateFlow()

    data class AdvancedState(
        val plan: WorkoutPlan = WorkoutPlan(id = UUID.randomUUID().toString(), name = ""),
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val problems: List<String> = emptyList(),
        val dirty: Boolean = false,
        val loaded: Boolean = false,
        val savedId: String? = null,
    )

    init {
        viewModelScope.launch {
            val prefs = container.preferences.current()
            labelResources = AppLocale.localizedContext(context, prefs.appLanguage).resources
            val existing = withContext(Dispatchers.IO) { drafts.load(draftId) }
            // A draft id that is not the "new-…" sentinel *is* the workout being edited, so the id is
            // re-stamped on whichever source wins: a draft written by an earlier session (or a row a
            // restore re-created) can never end up saved as a brand-new workout with the wrong id.
            val editingId = draftId.takeIf { !it.startsWith("new") }
            val source = existing
                ?: editingId?.let { container.workouts.plan(it) }
                ?: WorkoutPlan(
                    id = editingId ?: UUID.randomUUID().toString(),
                    name = labelResources.getString(if (editingId == null) R.string.new_workout_name else R.string.restored_workout_name),
                    nodes = listOf(defaultInterval()),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            val plan = if (editingId == null) source else source.copy(id = editingId)
            _state.value = AdvancedState(plan = plan, dirty = existing != null, loaded = true)
        }
    }

    private fun defaultInterval() = IntervalNode(
        UUID.randomUUID().toString(),
        IntervalSpec(id = UUID.randomUUID().toString(), name = labelResources.getString(R.string.builder_work), kind = PhaseKind.WORK, durationMillis = 30_000),
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
        persist()
    }

    /** Mirrors the current plan into the draft file, so a process kill never costs an edit. */
    private fun persist() {
        val plan = _state.value.plan
        viewModelScope.launch(Dispatchers.IO) { drafts.save(draftId, plan) }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_state.value.plan.toSnapshot())
        val plan = previous.plan
        _state.value = _state.value.copy(
            plan = plan,
            canUndo = undoStack.isNotEmpty(),
            canRedo = true,
            problems = Validation.validate(plan),
            dirty = true,
        )
        persist()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_state.value.plan.toSnapshot())
        val plan = next.plan
        _state.value = _state.value.copy(
            plan = plan,
            canUndo = true,
            canRedo = redoStack.isNotEmpty(),
            problems = Validation.validate(plan),
            dirty = true,
        )
        persist()
    }

    fun rename(name: String) = mutate { it.copy(name = name) }
    fun setType(type: WorkoutType) = mutate { it.copy(type = type) }
    fun setShuffle(value: Boolean) = mutate { it.copy(shuffleEager = value) }
    fun setIncludeFinalRest(value: Boolean) = mutate { it.copy(includeFinalRest = value) }

    /** Running system — planned pace in whole seconds per kilometre. */
    fun setRacePace(secondsPerKm: Int) = mutate { it.copy(racePaceSecondsPerKm = secondsPerKm.coerceAtLeast(30)) }

    /** Running system — target race distance in metres, or null for an open-ended run. */
    fun setRaceDistance(meters: Double?) = mutate { it.copy(raceDistanceMeters = meters?.takeIf { value -> value > 0.0 }) }

    fun addInterval(kind: PhaseKind = PhaseKind.WORK, millis: Long = 30_000) = mutate { plan ->
        val node = IntervalNode(
            UUID.randomUUID().toString(),
            IntervalSpec(id = UUID.randomUUID().toString(), name = labelResources.phaseLabel(kind), kind = kind, durationMillis = millis),
        )
        plan.copy(nodes = plan.nodes + node)
    }

    fun addGroup(repeat: Int = 3, restAfterMillis: Long = 30_000) = mutate { plan ->
        val group = RepeatGroup(
            nodeId = UUID.randomUUID().toString(),
            name = labelResources.getString(R.string.builder_group),
            repeat = repeat,
            children = listOf(
                IntervalNode(UUID.randomUUID().toString(), IntervalSpec(UUID.randomUUID().toString(), labelResources.getString(R.string.builder_work), PhaseKind.WORK, 30_000)),
                IntervalNode(UUID.randomUUID().toString(), IntervalSpec(UUID.randomUUID().toString(), labelResources.getString(R.string.builder_rest), PhaseKind.REST, 15_000)),
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
            node.copy(interval = node.interval.copy(name = node.interval.name.ifBlank { labelResources.getString(R.string.phase_hold) }, sideLabel = labelResources.getString(R.string.phase_left)))
        } else node
        is RepeatGroup -> {
            if (node.nodeId == groupId) {
                val expanded = node.children.flatMap { child ->
                    if (child is IntervalNode && child.nodeId == intervalId) {
                        val left = child.copy(interval = child.interval.copy(id = UUID.randomUUID().toString(), sideLabel = labelResources.getString(R.string.phase_left)))
                        val right = child.copy(
                            nodeId = UUID.randomUUID().toString(),
                            interval = child.interval.copy(id = UUID.randomUUID().toString(), sideLabel = labelResources.getString(R.string.phase_right)),
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
        // The plan keeps its own `createdAt`: it is either the timestamp the workout was first
        // stored under (an edit) or the one this session's new plan was built with. Overwriting it
        // with "now" on every save is what made an edited workout look like it had been re-created.
        val plan = _state.value.plan
        val problems = Validation.validate(plan)
        if (problems.isNotEmpty()) {
            _state.value = _state.value.copy(problems = problems)
            return Result.failure(IllegalArgumentException(problems.joinToString(" ")))
        }
        return runCatching {
            // No `tags` argument on purpose: the builder edits a workout's structure, and a save that
            // passed an explicit (empty) list would clear the tags the library filters and searches
            // by. `null` tells the repository to leave the stored tags and folder alone.
            container.workouts.save(plan)
            withContext(Dispatchers.IO) { drafts.clear(draftId) }
            _state.value = _state.value.copy(dirty = false, savedId = plan.id)
            plan
        }
    }

    fun discardDraft() {
        viewModelScope.launch(Dispatchers.IO) { drafts.clear(draftId) }
    }

    private fun WorkoutPlan.toSnapshot() = BuilderSnapshot(this)
}

@Composable
fun AdvancedBuilderScreen(
    container: AppContainer,
    workoutId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val strings = LocalContext.current.resources
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

    PulseScaffold { padding ->
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
                PulsePageHeader(
                    title = if (workoutId == null) strings.getString(R.string.workouts_new) else strings.getString(R.string.workout_edit),
                    onBack = { if (state.dirty) showDiscard = true else onBack() },
                    navigationActions = {
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
            }

            item {
                WorkoutNamePlate(
                    name = state.plan.name,
                    onRename = viewModel::rename,
                )
            }
            item {
                Column {
                    CategoryPicker(
                        selected = state.plan.category,
                        onSelect = { viewModel.setType(it.type) },
                    )
                    Spacer(Modifier.height(LocalPulseDimens.current.s))
                    ToggleRow(
                        label = stringResource(R.string.builder_include_final_rest),
                        checked = state.plan.includeFinalRest,
                        onCheckedChange = viewModel::setIncludeFinalRest,
                    )
                    ToggleRow(
                        label = stringResource(R.string.builder_shuffle),
                        checked = state.plan.shuffleEager,
                        onCheckedChange = viewModel::setShuffle,
                    )

                }
            }

            // The running system: categories that track race info expose their pacing inputs here.
            if (state.plan.category.capabilities.supportsRaceInfo) {
                item { SectionHeader(stringResource(R.string.builder_race_section)) }
                item {
                    Column {
                        DurationStepper(
                            label = stringResource(R.string.builder_race_pace),
                            millis = (state.plan.racePaceSecondsPerKm ?: RaceMath.DEFAULT_PACE_SECONDS_PER_KM) * 1000L,
                            stepMillis = 5_000L,
                            maxMillis = 3_600_000L,
                            allowZero = false,
                            onChange = { value ->
                                viewModel.setRacePace((value.coerceIn(60_000L, 3_600_000L) / 1000L).toInt())
                            },
                        )
                        NumberStepper(
                            label = stringResource(R.string.builder_race_distance),
                            value = ((state.plan.raceDistanceMeters ?: 0.0) / 1000.0).roundToInt(),
                            min = 0,
                            max = 200,
                            onChange = { km -> viewModel.setRaceDistance(if (km <= 0) null else km * 1000.0) },
                        )
                        Text(
                            text = stringResource(R.string.builder_race_distance_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = LocalPulseDimens.current.xs),
                        )
                    }
                }
            }

            item {
                Column {
                    Text(
                        expanded?.totalMillis?.let { formatDuration(it) } ?: "≥ ${formatDuration(expanded?.knownMillis ?: 0L)}",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.work,
                    )
                    Text(
                        strings.getString(R.string.interval_count, expanded?.steps?.size ?: 0) +
                            (if (expanded?.hasOpenEnded == true) strings.getString(R.string.builder_minimum) else ""),
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

            if (state.problems.isNotEmpty()) {
                item {
                    InfoBanner(strings.getString(R.string.builder_validation), tone = PulseTone.WARNING)
                }
            }

            item { SectionHeader(strings.getString(R.string.builder_editor_intervals)) }

            items(state.plan.nodes.size) { index ->
                val node = state.plan.nodes[index]
                // The interval list is a table: hairline-separated rows on the canvas, the same
                // idiom as the library and the Today page — not a stack of cards.
                Column {
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
                    if (index != state.plan.nodes.lastIndex) {
                        Spacer(Modifier.height(LocalPulseDimens.current.s))
                        PulseDivider()
                    }
                }
            }

            item {
                PulseActionMenu(
                    text = strings.getString(R.string.builder_add_to_workout),
                    icon = Icons.Filled.Add,
                    items = listOf(
                        PulseMenuAction(strings.getString(R.string.builder_add_interval)) { viewModel.addInterval() },
                        PulseMenuAction(strings.getString(R.string.builder_add_block)) { viewModel.addGroup() },
                    ),
                )
            }
            item {
                PulseActionMenu(
                    text = strings.getString(R.string.builder_adjust_durations),
                    items = listOf(
                        PulseMenuAction(strings.getString(R.string.builder_duration_change, "−10")) { viewModel.bulkDuration(-10_000, null) },
                        PulseMenuAction(strings.getString(R.string.builder_duration_change, "−5")) { viewModel.bulkDuration(-5_000, null) },
                        PulseMenuAction(strings.getString(R.string.builder_duration_change, "+5")) { viewModel.bulkDuration(5_000, null) },
                        PulseMenuAction(strings.getString(R.string.builder_duration_change, "+10")) { viewModel.bulkDuration(10_000, null) },
                    ),
                )
            }
            item {
                PrimaryActionButton(
                    text = stringResource(R.string.builder_save),
                    onClick = {
                        scope.launch {
                            viewModel.save()
                                .onSuccess { plan -> onSaved(plan.id) }
                                .onFailure { invalidMessage = strings.getString(R.string.workout_save_failed) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.problems.isEmpty(),
                )
            }
        }
    }

    // Both dialogs are the app's own ConfirmDialog, not platform AlertDialogs: one shape, one
    // radius, one button system everywhere a decision is asked for.
    invalidMessage?.let { message ->
        ConfirmDialog(
            title = stringResource(R.string.builder_invalid_title),
            body = message,
            confirmLabel = stringResource(R.string.ok),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = { invalidMessage = null },
            onDismiss = { invalidMessage = null },
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_draft_title),
            body = strings.getString(R.string.builder_discard_edits, state.plan.name),
            confirmLabel = stringResource(R.string.discard_draft),
            dismissLabel = stringResource(R.string.save_draft),
            destructive = true,
            onConfirm = {
                viewModel.discardDraft()
                showDiscard = false
                onBack()
            },
            onDismiss = {
                scope.launch {
                    viewModel.save().onSuccess { onSaved(it.id) }
                    showDiscard = false
                }
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
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current
    Column {
        // The whole row is the tap target: clicking anywhere on the bar — not just the name —
        // drops the interval editor open. The action buttons consume their own taps first.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.xs),
        ) {
            Column(Modifier.weight(1f)) {
                when (node) {
                    is IntervalNode -> {
                        Text(node.interval.displayName(), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (node.interval.isIndefinite) strings.getString(R.string.manual) else formatDuration(node.interval.durationMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    is RepeatGroup -> {
                        Text(
                            "${node.name} × ${node.repeat}",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            strings.getString(R.string.interval_count, node.children.size) + if (node.restAfterGroupMillis > 0) strings.getString(R.string.builder_block_rest, formatDuration(node.restAfterGroupMillis)) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
            if (node is IntervalNode) PhaseChip(kind = node.interval.kind, name = null)
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
            // A vertical reorder pair uses half the horizontal room of two adjacent arrows and
            // keeps the move controls together at the trailing edge of the row.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            }
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

@Composable
private fun BuilderEditorSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = PulseType.SectionLabel,
        color = LocalPulseColors.current.textMuted,
        modifier = Modifier.padding(start = LocalPulseDimens.current.xs),
    )
}

@Composable
private fun IntervalEditor(node: IntervalNode, groupId: String?, viewModel: AdvancedBuilderViewModel) {
    val strings = LocalContext.current.resources
    val spec = node.interval
    val dimens = LocalPulseDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.m)) {
        BuilderEditorSectionLabel(stringResource(R.string.builder_editor_basics))
        BigNameField(
            value = spec.name,
            onValueChange = { value -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(name = value) } },
            label = stringResource(R.string.builder_interval_name),
        )
        OptionRow(
            label = stringResource(R.string.builder_phase),
            options = listOf(
                PhaseKind.PREPARE, PhaseKind.WARM_UP, PhaseKind.WORK, PhaseKind.REST,
                PhaseKind.TRANSITION, PhaseKind.COOLDOWN, PhaseKind.CUSTOM,
            ).map { it to strings.phaseLabel(it) },
            selected = spec.kind,
            onSelect = { kind -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(kind = kind) } },
        )

        PulseDivider()
        BuilderEditorSectionLabel(stringResource(R.string.builder_editor_timing))
        ToggleRow(
            label = stringResource(R.string.builder_manual),
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

        PulseDivider()
        BuilderEditorSectionLabel(stringResource(R.string.builder_editor_cues))
        OptionRow(
            label = stringResource(R.string.builder_sound),
            options = listOf(SoundCue.TICK, SoundCue.BEEP, SoundCue.DOUBLE_BEEP, SoundCue.BELL, SoundCue.WHISTLE, SoundCue.CHIME, SoundCue.BUZZ, SoundCue.NONE)
                .map { it to strings.soundLabel(it) },
            selected = spec.sound,
            onSelect = { cue -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(sound = cue) } },
        )
        OptionRow(
            label = stringResource(R.string.builder_haptic),
            options = listOf(HapticCue.LIGHT, HapticCue.DOUBLE, HapticCue.STRONG, HapticCue.PATTERN_321, HapticCue.NONE)
                .map { it to strings.hapticLabel(it) },
            selected = spec.haptic,
            onSelect = { cue -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(haptic = cue) } },
        )

        PulseDivider()
        BuilderEditorSectionLabel(stringResource(R.string.builder_editor_voice_notes))
        PulseTextField(
            value = spec.speechText.orEmpty(),
            onValueChange = { value -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(speechText = value.ifBlank { null }) } },
            label = stringResource(R.string.builder_speech),
        )
        PulseTextField(
            value = spec.notes.orEmpty(),
            onValueChange = { value -> viewModel.updateInterval(groupId, node.nodeId) { it.copy(notes = value.ifBlank { null }) } },
            label = stringResource(R.string.builder_notes),
        )

        PulseDivider()
        val leftRightLabel = stringResource(R.string.builder_left_right)
        val deleteLabel = stringResource(R.string.planner_delete)
        PulseActionMenu(
            text = stringResource(R.string.builder_interval_actions),
            items = buildList {
                add(PulseMenuAction(leftRightLabel) { viewModel.addLeftRight(node.nodeId, groupId) })
                if (groupId == null) {
                    add(PulseMenuAction(deleteLabel, destructive = true) { viewModel.delete(node.nodeId) })
                }
            },
        )
    }
}

@Composable
private fun RepeatGroupEditor(node: RepeatGroup, viewModel: AdvancedBuilderViewModel) {
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
        BuilderEditorSectionLabel(stringResource(R.string.builder_editor_block_settings))
        BigNameField(
            value = node.name,
            onValueChange = { value -> viewModel.updateNode(node.nodeId) { group -> (group as RepeatGroup).copy(name = value) } },
            label = stringResource(R.string.builder_block_name),
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
            hint = strings.getString(R.string.builder_ladder_preset),
            checked = node.ladder != null,
            onCheckedChange = { enabled ->
                viewModel.setLadder(
                    node.nodeId,
                    if (enabled) Ladder(target = Ladder.TargetKind.WORK, deltaMillis = 10_000, minMillis = 10_000, maxMillis = 90_000) else null,
                )
            },
        )
        PulseDivider()
        BuilderEditorSectionLabel(stringResource(R.string.builder_editor_intervals))
        node.children.forEachIndexed { index, child ->
            var expanded by remember { mutableStateOf(false) }
            Column {
                // Whole-bar tap target, the same idiom as the interval table above: the row
                // drops its editor wherever the click lands outside the action buttons.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.xs),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (child) {
                                is IntervalNode -> child.interval.displayName()
                                is RepeatGroup -> child.name
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when (child) {
                                is IntervalNode -> if (child.interval.isIndefinite) strings.getString(R.string.manual) else formatDuration(child.interval.durationMillis)
                                is RepeatGroup -> "× ${child.repeat}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                    PulseIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tooltip = stringResource(R.string.action_delete),
                        onClick = { viewModel.delete(child.nodeId) },
                        tint = colors.destructive,
                    )
                    // Keep reordering visually separate from deletion, with the arrows stacked
                    // as one vertical control at the far edge of the child row.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PulseIconButton(
                            icon = Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(R.string.builder_move_up),
                            tooltip = stringResource(R.string.builder_move_up),
                            onClick = { viewModel.move(child.nodeId, -1) },
                            enabled = index > 0,
                        )
                        PulseIconButton(
                            icon = Icons.Filled.ArrowDownward,
                            contentDescription = stringResource(R.string.builder_move_down),
                            tooltip = stringResource(R.string.builder_move_down),
                            onClick = { viewModel.move(child.nodeId, 1) },
                            enabled = index < node.children.lastIndex,
                        )
                    }
                }
                if (expanded && child is IntervalNode) {
                    Spacer(Modifier.height(dimens.m))
                    IntervalEditor(child, node.nodeId, viewModel)
                }
            }
            if (index != node.children.lastIndex) PulseDivider()
        }
        SecondaryActionButton(
            text = stringResource(R.string.builder_add_interval_to_block),
            onClick = {
                viewModel.updateNode(node.nodeId) { group ->
                    (group as RepeatGroup).copy(
                        children = group.children + IntervalNode(
                            UUID.randomUUID().toString(),
                            IntervalSpec(UUID.randomUUID().toString(), strings.getString(R.string.builder_work), PhaseKind.WORK, 30_000),
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
