package com.pulse.intervalcoach.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.StarterWorkouts
import com.pulse.intervalcoach.data.WorkoutSummary
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.ErrorState
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.LoadingBox
import com.pulse.intervalcoach.ui.components.NeutralChip
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseChoiceChip
import com.pulse.intervalcoach.ui.components.PulseDropdown
import com.pulse.intervalcoach.ui.components.PulseDropdownItem
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.PulseListRow
import com.pulse.intervalcoach.ui.components.PulseTextField
import com.pulse.intervalcoach.ui.components.PulseTopBar
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.components.TimelineBar
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.PulseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.Scaffold

// ---------------------------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------------------------

enum class LibrarySort { RECENT, NAME, DURATION, CREATED }

data class LibraryUiState(
    val workouts: List<WorkoutSummary> = emptyList(),
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val favoritesOnly: Boolean = false,
    val grid: Boolean = false,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.RECENT)
    private val favoritesOnly = MutableStateFlow(false)
    private val grid = MutableStateFlow(false)

    val state = combine(
        container.workouts.summaries, query, sort, favoritesOnly, grid,
    ) { workouts, q, s, favOnly, isGrid ->
        val filtered = workouts
            .filter { !favOnly || it.isFavorite }
            .filter { workout ->
                q.isBlank() || listOf(workout.name, workout.description, workout.equipment.orEmpty()).any {
                    it.contains(q, ignoreCase = true)
                } || workout.tags.any { it.contains(q, ignoreCase = true) }
            }
            .let { list ->
                when (s) {
                    LibrarySort.RECENT -> list.sortedByDescending { it.lastUsedAt ?: it.updatedAt }
                    LibrarySort.NAME -> list.sortedBy { it.name.lowercase() }
                    LibrarySort.DURATION -> list.sortedBy { if (it.hasOpenEnded) Long.MAX_VALUE else it.knownDurationMillis }
                    LibrarySort.CREATED -> list.sortedByDescending { it.updatedAt }
                }
            }
        LibraryUiState(filtered, q, s, favOnly, isGrid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) { query.value = value }
    fun setSort(value: LibrarySort) { sort.value = value }
    fun toggleFavoritesOnly() { favoritesOnly.value = !favoritesOnly.value }
    fun toggleGrid() { grid.value = !grid.value }

    fun duplicate(id: String) = viewModelScope.launch {
        val plan = container.workouts.plan(id) ?: return@launch
        container.workouts.duplicate(plan)
    }

    fun delete(id: String) = viewModelScope.launch { container.workouts.delete(id) }
    fun setFavorite(id: String, favorite: Boolean) = viewModelScope.launch { container.workouts.setFavorite(id, favorite) }

    suspend fun plan(id: String): WorkoutPlan? = container.workouts.plan(id)
}

/**
 * The workout library.
 *
 * One screen, three states (list, grid, empty) and one "create" affordance in the header — the
 * floating action button that used to duplicate the header menu is gone, because two controls that
 * open the same menu is one control too many.
 */
@Composable
fun WorkoutLibraryScreen(
    container: AppContainer,
    onOpenWorkout: (String) -> Unit,
    onOpenTemplates: () -> Unit,
    onQuickBuilder: () -> Unit,
    onAdvancedBuilder: () -> Unit,
    onParser: () -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel(initializer = { LibraryViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingDelete by remember { mutableStateOf<WorkoutSummary?>(null) }
    var showNewMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = "Workouts",
                subtitle = "${state.workouts.size} saved on this device",
                actions = {
                    Box {
                        PulseIconButton(
                            icon = Icons.Filled.Sort,
                            contentDescription = stringResource(R.string.workouts_sort),
                            tooltip = "Sort",
                            onClick = { showSortMenu = true },
                            tint = if (state.sort != LibrarySort.RECENT) colors.accent else colors.textSecondary,
                        )
                        PulseDropdown(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            LibrarySort.entries.forEach { option ->
                                PulseDropdownItem(
                                    text = option.label(),
                                    selected = option == state.sort,
                                    onClick = { viewModel.setSort(option); showSortMenu = false },
                                )
                            }
                        }
                    }
                    PulseIconButton(
                        icon = if (state.grid) Icons.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = if (state.grid) "List view" else "Grid view",
                        tooltip = if (state.grid) "List view" else "Grid view",
                        onClick = viewModel::toggleGrid,
                    )
                    Box {
                        PulseIconButton(
                            icon = Icons.Filled.Add,
                            contentDescription = "New workout",
                            tooltip = "New workout",
                            onClick = { showNewMenu = true },
                            tint = colors.textPrimary,
                        )
                        PulseDropdown(expanded = showNewMenu, onDismissRequest = { showNewMenu = false }) {
                            PulseDropdownItem(
                                text = "Quick builder",
                                leadingIcon = Icons.Filled.FitnessCenter,
                                onClick = { showNewMenu = false; onQuickBuilder() },
                            )
                            PulseDropdownItem(
                                text = "Advanced builder",
                                leadingIcon = Icons.Filled.Tune,
                                onClick = { showNewMenu = false; onAdvancedBuilder() },
                            )
                            PulseDropdownItem(
                                text = "Describe in words",
                                leadingIcon = Icons.Filled.Spellcheck,
                                onClick = { showNewMenu = false; onParser() },
                            )
                            PulseDropdownItem(
                                text = "Template gallery",
                                leadingIcon = Icons.Filled.GridView,
                                onClick = { showNewMenu = false; onOpenTemplates() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PulseTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.s),
                placeholder = stringResource(R.string.workouts_search_hint),
                leadingIcon = Icons.Filled.Search,
                singleLine = true,
                trailing = {
                    if (state.query.isNotBlank()) {
                        PulseIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            onClick = { viewModel.setQuery("") },
                        )
                    }
                },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.pagePadding),
                horizontalArrangement = Arrangement.spacedBy(dimens.s),
                modifier = Modifier.padding(bottom = dimens.s),
            ) {
                item {
                    PulseChoiceChip(
                        text = "Favorites",
                        selected = state.favoritesOnly,
                        onClick = viewModel::toggleFavoritesOnly,
                    )
                }
                item {
                    PulseChoiceChip(text = "Templates", selected = false, onClick = onOpenTemplates)
                }
            }

            if (state.workouts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = if (state.query.isBlank()) "No workouts yet" else "Nothing matches \u201C${state.query}\u201D",
                        body = if (state.query.isBlank()) {
                            "Build one in under a minute, or start from the template gallery."
                        } else {
                            "Try a different word — search covers names, tags and equipment."
                        },
                        icon = Icons.Filled.FitnessCenter,
                        actionLabel = "Open templates",
                        onAction = onOpenTemplates,
                    )
                }
            } else if (state.grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = LocalPulseDimens.current.gridCellMin),
                    contentPadding = PaddingValues(dimens.pagePadding),
                    horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
                    verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
                ) {
                    items(state.workouts, key = { it.id }) { workout ->
                        PulseCard(onClick = { onOpenWorkout(workout.id) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
                                Text(
                                    text = workout.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis),
                                    style = PulseType.NumericSmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = dimens.pagePadding,
                        end = dimens.pagePadding,
                        bottom = dimens.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimens.s),
                ) {
                    items(state.workouts, key = { it.id }) { workout ->
                        WorkoutRow(
                            workout = workout,
                            onOpen = { onOpenWorkout(workout.id) },
                            onStart = {
                                scope.launch { viewModel.plan(workout.id)?.let { container.sessionController.startWorkout(it) } }
                            },
                            onFavorite = { viewModel.setFavorite(workout.id, !workout.isFavorite) },
                            onDuplicate = { viewModel.duplicate(workout.id) },
                            onDelete = { pendingDelete = workout },
                            onShare = { shareWorkout(context, container, workout.id) },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { workout ->
        ConfirmDialog(
            title = stringResource(R.string.delete_workout_title),
            body = "\u201C${workout.name}\u201D will be removed. Finished sessions keep their own copy of the workout, so your history stays readable.",
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = { viewModel.delete(workout.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun LibrarySort.label(): String = when (this) {
    LibrarySort.RECENT -> "Recently used"
    LibrarySort.NAME -> "Name"
    LibrarySort.DURATION -> "Duration"
    LibrarySort.CREATED -> "Recently created"
}

@Composable
private fun WorkoutRow(
    workout: WorkoutSummary,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    var menu by remember { mutableStateOf(false) }
    PulseCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            PulseListRow(
                title = workout.name,
                subtitle = buildString {
                    append(if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis))
                    append(" · ")
                    append(workout.intervalCount)
                    append(" intervals")
                    workout.equipment?.let { append(" · $it") }
                },
                onClick = onOpen,
                trailing = {
                    PulseIconButton(
                        icon = if (workout.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (workout.isFavorite) "Remove from favorites" else "Add to favorites",
                        tooltip = if (workout.isFavorite) "Remove from favorites" else "Add to favorites",
                        onClick = onFavorite,
                        tint = if (workout.isFavorite) colors.work else colors.textMuted,
                        modifier = Modifier.size(dimens.iconButtonSize),
                    )
                },
            )
            if (workout.tags.isNotEmpty() || workout.builtIn) {
                Row(
                    modifier = Modifier.padding(bottom = dimens.xs),
                    horizontalArrangement = Arrangement.spacedBy(dimens.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (workout.builtIn) NeutralChip("starter")
                    if (workout.tags.isNotEmpty()) {
                        Text(
                            text = workout.tags.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                PrimaryActionButton(
                    text = "Start",
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.PlayArrow,
                )
                Box {
                    SecondaryActionButton(
                        text = "More",
                        onClick = { menu = true },
                        icon = Icons.Filled.MoreVert,
                    )
                    PulseDropdown(expanded = menu, onDismissRequest = { menu = false }) {
                        PulseDropdownItem(
                            text = stringResource(R.string.action_duplicate),
                            leadingIcon = Icons.Filled.ContentCopy,
                            onClick = { menu = false; onDuplicate() },
                        )
                        PulseDropdownItem(
                            text = "Share summary",
                            leadingIcon = Icons.Filled.Share,
                            onClick = { menu = false; onShare() },
                        )
                        PulseDropdownItem(
                            text = stringResource(R.string.action_delete),
                            leadingIcon = Icons.Filled.Delete,
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Details
// ---------------------------------------------------------------------------------------------

@Composable
fun WorkoutDetailsScreen(
    container: AppContainer,
    workoutId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStart: (WorkoutPlan) -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val planFlow = remember(workoutId) { container.workouts.observeEntity(workoutId) }
    val entity by planFlow.collectAsStateWithLifecycle(initialValue = null)
    val preview by container.sessionController.preview.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val plan = remember(entity) { entity?.let { runCatching { com.pulse.intervalcoach.data.PlanCodec.decode(it.planJson) }.getOrNull() } }
    val expanded = remember(plan) {
        plan?.let { runCatching { com.pulse.engine.TimelineExpander.expand(it) }.getOrNull() }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = plan?.name ?: "Workout",
                subtitle = plan?.type?.displayName,
                onBack = onBack,
                backDescription = stringResource(R.string.back),
                actions = {
                    PulseIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Edit workout",
                        tooltip = "Edit workout",
                        onClick = { plan?.let { onEdit(it.id) } },
                    )
                },
            )
        },
    ) { padding ->
        if (entity != null && (plan == null || expanded == null)) {
            // The row exists but its stored structure cannot be read — a different situation from
            // "this workout is gone", and one the user can act on.
            Column(Modifier.padding(padding)) {
                ErrorState(
                    title = "This workout could not be opened",
                    body = "Its saved structure is unreadable, which usually means the file was truncated " +
                        "by a failed restore. Export a backup before deleting anything, then re-import it.",
                )
            }
            return@Scaffold
        }
        if (plan == null || expanded == null) {
            Column(Modifier.padding(padding)) {
                LoadingBox(label = "Loading workout…")
            }
            return@Scaffold
        }
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
            // --- Hero header ---
            item {
                PulseCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeutralChip(plan.type.displayName)
                            Spacer(Modifier.width(dimens.s))
                            entity?.let { e ->
                                PulseIconButton(
                                    icon = if (e.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (e.isFavorite) "Remove from favorites" else "Add to favorites",
                                    tooltip = if (e.isFavorite) "Remove from favorites" else "Add to favorites",
                                    onClick = { scope.launch { container.workouts.setFavorite(workoutId, !e.isFavorite) } },
                                    tint = if (e.isFavorite) colors.work else colors.textMuted,
                                    modifier = Modifier.size(dimens.iconButtonSize),
                                )
                            }
                        }
                        Spacer(Modifier.height(dimens.m))
                        Text(
                            text = if (expanded.hasOpenEnded) "≥ ${formatDuration(expanded.knownMillis)}" else formatDuration(expanded.totalMillis ?: 0L),
                            style = PulseType.NumericLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "total time",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                        if (plan.description.isNotBlank()) {
                            Spacer(Modifier.height(dimens.m))
                            Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                        Spacer(Modifier.height(dimens.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                            StatTile("Intervals", expanded.steps.size.toString(), modifier = Modifier.weight(1f))
                            expanded.steps.firstNotNullOfOrNull { it.roundsInGroup }?.let { rounds ->
                                StatTile("Rounds", rounds.toString(), modifier = Modifier.weight(1f))
                            }
                            plan.equipment?.let {
                                StatTile("Equipment", it.take(12), modifier = Modifier.weight(1f))
                            }
                        }
                        if (expanded.hasOpenEnded) {
                            Spacer(Modifier.height(dimens.m))
                            InfoBanner("Includes intervals that wait for you — total time is a minimum.")
                        }
                    }
                }
            }

            item {
                PrimaryActionButton(
                    text = "Start workout",
                    onClick = { onStart(plan) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.PlayArrow,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    SecondaryActionButton(
                        text = if (preview == null) "Preview cues" else "Stop preview",
                        onClick = {
                            if (preview == null) container.sessionController.startPreview(plan) else container.sessionController.stopPreview()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryActionButton(
                        text = stringResource(R.string.action_edit),
                        onClick = { onEdit(plan.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            preview?.let { state ->
                item {
                    InfoBanner(
                        text = "Previewing ${state.stepIndex + 1}/${state.totalSteps}: ${state.stepName}",
                        tone = com.pulse.intervalcoach.ui.components.PulseTone.INFO,
                    )
                }
            }

            item { SectionHeader(text = "Interval timeline", trailing = "${expanded.steps.size} intervals") }
            item {
                TimelineBar(
                    kinds = expanded.steps.map { it.kind },
                    currentIndex = -1,
                    progressInStep = 0f,
                    height = LocalPulseDimens.current.barHeightCompact,
                )
            }
            items(expanded.steps.size) { index ->
                val step = expanded.steps[index]
                PulseCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LocalPulseDimens.current.microGap)) {
                            Text(
                                text = step.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = buildString {
                                    append(if (step.isIndefinite) "Manual" else formatDuration(step.durationMillis))
                                    step.roundInGroup?.let { round -> append(" · round $round of ${step.roundsInGroup}") }
                                    step.sideLabel?.let { append(" · $it") }
                                },
                                style = PulseType.NumericSmall,
                                color = colors.textMuted,
                            )
                            step.notes?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                        }
                        Spacer(Modifier.width(dimens.m))
                        PhaseChip(kind = step.kind, name = null)
                    }
                }
            }

            plan.equipment?.let { equipment ->
                item { SectionHeader("Equipment") }
                item { PulseCard { Text(equipment, color = colors.textPrimary) } }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Template gallery
// ---------------------------------------------------------------------------------------------

@Composable
fun TemplateGalleryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    var filter by remember { mutableStateOf<WorkoutType?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val templates = remember { StarterWorkouts.templates }
    val filtered = templates.filter { filter == null || it.plan.type == filter }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            PulseTopBar(
                title = stringResource(R.string.workouts_templates),
                subtitle = "${templates.size} editable starting points",
                onBack = onBack,
                backDescription = stringResource(R.string.back),
            )
        },
    ) { padding ->
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
                Text(
                    text = "Editable starting points — tweak anything after you add them. Durations are calculated from each workout's real structure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    item {
                        PulseChoiceChip(text = "All", selected = filter == null, onClick = { filter = null })
                    }
                    items(WorkoutType.entries.size) { index ->
                        val type = WorkoutType.entries[index]
                        PulseChoiceChip(
                            text = type.displayName,
                            selected = filter == type,
                            onClick = { filter = if (filter == type) null else type },
                        )
                    }
                }
            }
            items(filtered.size) { index ->
                val template = filtered[index]
                val duration = remember(template) { StarterWorkouts.knownDuration(template.plan) }
                PulseCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = template.plan.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(dimens.s))
                            Text(
                                text = formatDuration(duration),
                                style = PulseType.NumericSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(dimens.s))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(dimens.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NeutralChip(template.plan.type.displayName)
                        }
                        Spacer(Modifier.height(dimens.s))
                        Text(template.plan.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        template.plan.equipment?.let {
                            Spacer(Modifier.height(dimens.xs))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                        Spacer(Modifier.height(dimens.m))
                        SecondaryActionButton(
                            text = stringResource(R.string.templates_use),
                            onClick = {
                                scope.launch {
                                    container.workouts.save(template.plan, tags = template.tags)
                                    onOpenWorkout(template.plan.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Shares a readable text summary of a workout (structure only — never fake session numbers). */
private fun shareWorkout(context: android.content.Context, container: AppContainer, workoutId: String) {
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default)
    scope.launch {
        val plan = container.workouts.plan(workoutId) ?: return@launch
        val expanded = runCatching { com.pulse.engine.TimelineExpander.expand(plan) }.getOrNull() ?: return@launch
        val text = buildString {
            appendLine(plan.name)
            appendLine(plan.description)
            appendLine()
            expanded.steps.forEach { step ->
                append(if (step.isIndefinite) "manual" else formatDuration(step.durationMillis))
                append("  ")
                appendLine(step.name)
            }
            appendLine()
            append("Total: ")
            append(expanded.totalMillis?.let { formatDuration(it) } ?: "≥ ${formatDuration(expanded.knownMillis)}")
        }
        withContext(Dispatchers.Main) {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, plan.name)
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share workout"))
        }
    }
}
