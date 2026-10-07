package com.pulse.intervalcoach.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.PhaseKind
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.WorkoutType
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.data.StarterWorkouts
import com.pulse.intervalcoach.data.WorkoutSummary
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.NeutralChip
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.components.TimelineBar
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.R

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

    private val query = kotlinx.coroutines.flow.MutableStateFlow("")
    private val sort = kotlinx.coroutines.flow.MutableStateFlow(LibrarySort.RECENT)
    private val favoritesOnly = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val grid = kotlinx.coroutines.flow.MutableStateFlow(false)

    val state = kotlinx.coroutines.flow.combine(
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
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), LibraryUiState())

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingDelete by remember { mutableStateOf<WorkoutSummary?>(null) }
    var showNewMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_workouts)) },
                actions = {
                    IconButton(onClick = { showNewMenu = true }) {
                        Icon(Icons.Filled.Bolt, contentDescription = "Create workout")
                    }
                    DropdownMenu(expanded = showNewMenu, onDismissRequest = { showNewMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.builder_quick_title)) }, onClick = { showNewMenu = false; onQuickBuilder() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.builder_advanced_title)) }, onClick = { showNewMenu = false; onAdvancedBuilder() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.parser_title)) }, onClick = { showNewMenu = false; onParser() })
                        DropdownMenuItem(text = { Text("Template gallery") }, onClick = { showNewMenu = false; onOpenTemplates() })
                    }
                    IconButton(onClick = viewModel::toggleGrid) {
                        Icon(
                            if (state.grid) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (state.grid) "List view" else "Grid view",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.workouts_search_hint)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.workouts_sort))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            LibrarySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label()) },
                                    onClick = { viewModel.setSort(option); expanded = false },
                                )
                            }
                        }
                    }
                },
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = state.favoritesOnly, onClick = viewModel::toggleFavoritesOnly, label = { Text(stringResource(R.string.home_favorites)) })
                FilterChip(selected = false, onClick = onOpenTemplates, label = { Text(stringResource(R.string.workouts_templates)) })
            }

            if (state.workouts.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = if (state.query.isBlank()) "No workouts yet" else "Nothing matches “${state.query}”",
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
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.workouts, key = { it.id }) { workout ->
                        PulseCard(onClick = { onOpenWorkout(workout.id) }) {
                            Column {
                                Text(workout.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
            body = "“${workout.name}” will be removed. Finished sessions keep their own copy of the workout, so your history stays readable.",
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
    var menu by remember { mutableStateOf(false) }
    PulseCard(onClick = onOpen) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(workout.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (workout.builtIn) {
                            Spacer(Modifier.width(8.dp))
                            NeutralChip("starter")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append(if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis))
                            append(" · ")
                            append(workout.intervalCount)
                            append(" intervals")
                            workout.equipment?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (workout.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (workout.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (workout.isFavorite) colors.work else colors.textSecondary,
                    )
                }
                IconButton(onClick = onStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${workout.name}", tint = colors.work)
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Share summary") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { menu = false; onShare() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (workout.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(workout.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Details
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailsScreen(
    container: AppContainer,
    workoutId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStart: (WorkoutPlan) -> Unit,
) {
    val colors = LocalPulseColors.current
    val planFlow = remember(workoutId) { container.workouts.observeEntity(workoutId) }
    val entity by planFlow.collectAsStateWithLifecycle(initialValue = null)
    val preview by container.sessionController.preview.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val plan = remember(entity) { entity?.let { runCatching { com.pulse.intervalcoach.data.PlanCodec.decode(it.planJson) }.getOrNull() } }
    val expanded = remember(plan) {
        plan?.let { runCatching { com.pulse.engine.TimelineExpander.expand(it) }.getOrNull() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan?.name ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { plan?.let { onEdit(it.id) } }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit workout")
                    }
                },
            )
        },
    ) { padding ->
        if (plan == null || expanded == null) {
            Column(Modifier.padding(padding)) {
                EmptyState(title = "Workout not found", body = "It may have been deleted. Your saved sessions are unaffected.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PulseCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeutralChip(plan.type.displayName)
                            Spacer(Modifier.width(8.dp))
                            entity?.let { e ->
                                TextButton(onClick = { scope.launch { container.workouts.setFavorite(workoutId, !e.isFavorite) } }) {
                                    Icon(
                                        if (e.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (e.isFavorite) colors.work else colors.textSecondary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (e.isFavorite) "Favorited" else "Favorite")
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                "Total",
                                expanded.totalMillis?.let { formatDuration(it) } ?: "≥ ${formatDuration(expanded.knownMillis)}",
                                accent = colors.work,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile("Intervals", expanded.steps.size.toString(), modifier = Modifier.weight(1f))
                            expanded.steps.firstNotNullOfOrNull { it.roundsInGroup }?.let { rounds ->
                                StatTile("Rounds", rounds.toString(), modifier = Modifier.weight(1f))
                            }
                        }
                        if (expanded.hasOpenEnded) {
                            Spacer(Modifier.height(10.dp))
                            InfoBanner("Includes intervals that wait for you — total time is a minimum.")
                        }
                    }
                }
            }

            item {
                PrimaryActionButton(
                    text = stringResource(R.string.details_start),
                    onClick = { onStart(plan) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryActionButton(
                        text = if (preview == null) "Preview cues" else "Stop preview",
                        onClick = {
                            if (preview == null) container.sessionController.startPreview(plan) else container.sessionController.stopPreview()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryActionButton(text = stringResource(R.string.action_edit), onClick = { onEdit(plan.id) }, modifier = Modifier.weight(1f))
                }
            }
            preview?.let { state ->
                item {
                    InfoBanner("Previewing ${state.stepIndex + 1}/${state.totalSteps}: ${state.stepName}")
                }
            }

            item { SectionHeader("Interval timeline") }
            item {
                TimelineBar(
                    kinds = expanded.steps.map { it.kind },
                    currentIndex = -1,
                    progressInStep = 0f,
                    height = 10.dp,
                )
            }
            items(expanded.steps.size) { index ->
                val step = expanded.steps[index]
                PulseCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(step.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    append(if (step.isIndefinite) "Manual" else formatDuration(step.durationMillis))
                                    step.roundInGroup?.let { round -> append(" · round $round of ${step.roundsInGroup}") }
                                    step.sideLabel?.let { append(" · $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            step.notes?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateGalleryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val colors = LocalPulseColors.current
    var filter by remember { mutableStateOf<WorkoutType?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val templates = remember { StarterWorkouts.templates }
    val filtered = templates.filter { filter == null || it.plan.type == filter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workouts_templates)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Editable starting points — tweak anything after you add them. Durations are calculated from each workout's real structure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                    }
                    items(WorkoutType.entries.size) { index ->
                        val type = WorkoutType.entries[index]
                        FilterChip(
                            selected = filter == type,
                            onClick = { filter = if (filter == type) null else type },
                            label = { Text(type.displayName) },
                        )
                    }
                }
            }
            items(filtered.size) { index ->
                val template = filtered[index]
                val duration = remember(template) { StarterWorkouts.knownDuration(template.plan) }
                PulseCard {
                    Column {
                        Text(template.plan.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NeutralChip(template.plan.type.displayName)
                            Text(formatDuration(duration), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(template.plan.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        template.plan.equipment?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryActionButton(
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
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
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
        withContextMain {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, plan.name)
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share workout"))
        }
    }
}

private suspend fun withContextMain(block: () -> Unit) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { block() }
