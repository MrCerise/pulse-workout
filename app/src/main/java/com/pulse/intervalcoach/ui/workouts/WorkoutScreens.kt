package com.pulse.intervalcoach.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.data.WorkoutCategories
import com.pulse.intervalcoach.data.WorkoutCategory
import com.pulse.intervalcoach.data.WorkoutSummary
import com.pulse.intervalcoach.data.category
import com.pulse.intervalcoach.data.categoryOrCustom
import com.pulse.intervalcoach.ui.components.ConfirmDialog
import com.pulse.intervalcoach.ui.components.EmptyState
import com.pulse.intervalcoach.ui.components.ErrorState
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.LoadingBox
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseActionMenu
import com.pulse.intervalcoach.ui.components.PulseBottomSheet
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseChoiceChip
import com.pulse.intervalcoach.ui.components.PulseDivider
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.PulseListRow
import com.pulse.intervalcoach.ui.components.PulseMenuAction
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.PulseSheetRow
import com.pulse.intervalcoach.ui.components.PulseTextField
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.StatTile
import com.pulse.intervalcoach.ui.components.TimelineBar
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.intervalcoach.ui.theme.PulseType
import com.pulse.library.StarterWorkouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------------------------

enum class LibrarySort { RECENT, NAME, DURATION, CREATED }

private fun LibrarySort.label(): String = when (this) {
    LibrarySort.RECENT -> "Recently used"
    LibrarySort.NAME -> "Name"
    LibrarySort.DURATION -> "Duration"
    LibrarySort.CREATED -> "Recently created"
}

data class LibraryUiState(
    val workouts: List<WorkoutSummary> = emptyList(),
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val favoritesOnly: Boolean = false,
    val grid: Boolean = false,
    /** Persisted category id from the registry, or null for "everything". */
    val categoryKey: String? = null,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    /** The five filter controls, folded into one value so the category can join them. */
    private data class Criteria(
        val workouts: List<WorkoutSummary>,
        val query: String,
        val sort: LibrarySort,
        val favoritesOnly: Boolean,
        val grid: Boolean,
    )

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.RECENT)
    private val favoritesOnly = MutableStateFlow(false)
    private val grid = MutableStateFlow(false)
    private val categoryKey = MutableStateFlow<String?>(null)

    // `combine` tops out at five sources, so the five filter controls are folded into one stream and
    // the category joins it in a second pass. The alternative — a hand-rolled flow — would duplicate
    // the combinator's cancellation and back-pressure behaviour for no benefit.
    private val criteria = combine(
        container.workouts.summaries, query, sort, favoritesOnly, grid,
    ) { workouts, q, s, favOnly, isGrid ->
        Criteria(workouts, q, s, favOnly, isGrid)
    }

    val state = combine(criteria, categoryKey) { criteria, category ->
        val filtered = criteria.workouts
            .filter { !criteria.favoritesOnly || it.isFavorite }
            // Filtering by category is a lookup in the registry, not a `when` over an enum, so a new
            // category is filterable the moment it is registered.
            .filter { category == null || WorkoutCategories.byKey(it.type).key == category }
            .filter { workout ->
                criteria.query.isBlank() ||
                    WorkoutCategories.searchTerms(workout).any { it.contains(criteria.query, ignoreCase = true) }
            }
            .let { list ->
                when (criteria.sort) {
                    LibrarySort.RECENT -> list.sortedByDescending { it.lastUsedAt ?: it.updatedAt }
                    LibrarySort.NAME -> list.sortedBy { it.name.lowercase() }
                    LibrarySort.DURATION -> list.sortedBy { if (it.hasOpenEnded) Long.MAX_VALUE else it.knownDurationMillis }
                    LibrarySort.CREATED -> list.sortedByDescending { it.updatedAt }
                }
            }
        LibraryUiState(filtered, criteria.query, criteria.sort, criteria.favoritesOnly, criteria.grid, category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) { query.value = value }
    fun setSort(value: LibrarySort) { sort.value = value }
    fun toggleFavoritesOnly() { favoritesOnly.value = !favoritesOnly.value }
    fun toggleGrid() { grid.value = !grid.value }
    fun setCategory(key: String?) { categoryKey.value = key }

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
 * Browsing is **category-first**: the rail at the top is generated from the registry, so the filters
 * and the cards are guaranteed to describe the same set of categories, and a category that has no
 * workouts yet still shows up rather than silently disappearing from the vocabulary of the app.
 */
@Composable
fun WorkoutLibraryScreen(
    container: AppContainer,
    onOpenWorkout: (String) -> Unit,
    onQuickBuilder: () -> Unit,
    onAdvancedBuilder: () -> Unit,
    onStartWorkout: (WorkoutPlan) -> Unit,
    /** Opens the builder *for* an existing workout — the library's own route into editing. */
    onEdit: (String) -> Unit,
    onJsonImport: () -> Unit,
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

    PulseScaffold { padding ->
        Column(Modifier.padding(padding)) {
            PulsePageHeader(
                title = "Workouts",
                trailing = {
                    PulseIconButton(
                        icon = Icons.Filled.Sort,
                        contentDescription = stringResource(R.string.workouts_sort),
                        tooltip = "Sort",
                        onClick = { showSortMenu = true },
                        tint = if (state.sort != LibrarySort.RECENT) colors.accent else colors.textSecondary,
                    )
                    PulseIconButton(
                        icon = if (state.grid) Icons.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = if (state.grid) "List view" else "Grid view",
                        tooltip = if (state.grid) "List view" else "Grid view",
                        onClick = viewModel::toggleGrid,
                    )
                    PulseIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "New workout",
                        tooltip = "New workout",
                        onClick = { showNewMenu = true },
                        tint = colors.textPrimary,
                    )
                },
            )

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

            // --- Category rail -------------------------------------------------------------------
            CategoryFilterRail(
                categories = WorkoutCategories.all,
                selectedKey = state.categoryKey,
                onSelect = viewModel::setCategory,
            )

            Row(
                modifier = Modifier.padding(horizontal = dimens.pagePadding, vertical = dimens.s),
                horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
            ) {
                PulseChoiceChip(
                    text = "Favorites",
                    selected = state.favoritesOnly,
                    onClick = viewModel::toggleFavoritesOnly,
                )
            }

            if (state.workouts.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = if (state.query.isBlank() && state.categoryKey == null) {
                            "No workouts yet"
                        } else {
                            buildString {
                                append("Nothing matches ")
                                state.categoryKey?.let { append("${WorkoutCategories.byKey(it).title} ") }
                                if (state.query.isNotBlank()) append("“${state.query}”")
                            }.trim()
                        },
                        body = if (state.query.isBlank() && state.categoryKey == null) {
                            stringResource(R.string.workouts_empty_body)
                        } else {
                            "Try another category or search."
                        },
                        icon = Icons.Filled.FitnessCenter,
                        actionLabel = "Build a workout",
                        onAction = onQuickBuilder,
                    )
                }
            } else if (state.grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = dimens.gridCellMin),
                    contentPadding = PaddingValues(
                        start = dimens.pagePadding,
                        end = dimens.pagePadding,
                        bottom = dimens.xxl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
                    verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
                ) {
                    items(state.workouts, key = { it.id }) { workout ->
                        WorkoutGridCard(workout = workout, onOpen = { onOpenWorkout(workout.id) })
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = dimens.pagePadding,
                        end = dimens.pagePadding,
                        bottom = dimens.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
                ) {
                    // One surface for the whole list: rows are separated by hairlines rather than by
                    // card edges. A workout library is a table of names and numbers, so it is drawn
                    // as one — the list is a plain loop because a card cannot wrap lazy items, and a
                    // library of a few hundred rows is still cheap enough to lay out in one pass.
                    item {
                        PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.xs)) {
                            Column {
                                state.workouts.forEachIndexed { index, workout ->
                                    WorkoutRow(
                                        workout = workout,
                                        onOpen = { onOpenWorkout(workout.id) },
                                        onStart = {
                                            scope.launch {
                                                viewModel.plan(workout.id)?.let(onStartWorkout)
                                            }
                                        },
                                        onEdit = { onEdit(workout.id) },
                                        onFavorite = { viewModel.setFavorite(workout.id, !workout.isFavorite) },
                                        onDuplicate = { viewModel.duplicate(workout.id) },
                                        onDelete = { pendingDelete = workout },
                                        onShare = { shareWorkout(context, container, workout.id) },
                                    )
                                    if (index != state.workouts.lastIndex) PulseDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortMenu) {
        PulseBottomSheet(title = stringResource(R.string.workouts_sort), onDismiss = { showSortMenu = false }) {
            LibrarySort.entries.forEach { option ->
                PulseSheetRow(
                    text = option.label(),
                    selected = option == state.sort,
                    onClick = { viewModel.setSort(option); showSortMenu = false },
                )
            }
        }
    }
    if (showNewMenu) {
        PulseBottomSheet(title = "New workout", onDismiss = { showNewMenu = false }) {
            PulseSheetRow(
                text = "Quick builder",
                leadingIcon = Icons.Filled.FitnessCenter,
                onClick = { showNewMenu = false; onQuickBuilder() },
            )
            PulseSheetRow(
                text = "Advanced builder",
                leadingIcon = Icons.Filled.Tune,
                onClick = { showNewMenu = false; onAdvancedBuilder() },
            )
            PulseSheetRow(
                text = stringResource(R.string.json_title),
                leadingIcon = Icons.Filled.ContentCopy,
                onClick = { showNewMenu = false; onJsonImport() },
            )
        }
    }

    pendingDelete?.let { workout ->
        ConfirmDialog(
            title = stringResource(R.string.delete_workout_title),
            body = "Delete “${workout.name}”? Session history is kept.",
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = { viewModel.delete(workout.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * The category filter rail.
 *
 * It is generated from [WorkoutCategories.all] rather than from the workouts that happen to exist,
 * so the vocabulary of the app never depends on what is in the library today.
 */
@Composable
fun CategoryFilterRail(
    categories: List<WorkoutCategory>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalPulseDimens.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
    ) {
        item {
            PulseChoiceChip(
                text = "All",
                selected = selectedKey == null,
                onClick = { onSelect(null) },
            )
        }
        items(categories, key = { it.key }) { category ->
            PulseChoiceChip(
                text = category.title,
                selected = selectedKey == category.key,
                leadingIcon = category.icon,
                accent = LocalPulseColors.current.categoryColor(category.hue),
                onClick = { onSelect(if (selectedKey == category.key) null else category.key) },
            )
        }
    }
}

/** A workout as a category-coloured tile in the grid view. */
@Composable
private fun WorkoutGridCard(
    workout: WorkoutSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val category = workout.categoryOrCustom()
    val hue = colors.categoryColor(category.hue)
    PulseCard(
        modifier = modifier,
        onClick = onOpen,
        tint = hue,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    // The same category tile the details hero draws, one size step down: a square,
                    // because the grid card and the details page describe one workout the same way.
                    .background(colors.categoryTint(category.hue), LocalPulseShapes.current.control),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = hue,
                    modifier = Modifier.size(22.dp),
                )
            }
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
            Text(
                text = category.title,
                style = MaterialTheme.typography.labelSmall,
                color = hue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A workout as a row: identity, category, then the actions that matter — all on one 56 dp line.
 *
 * The row is deliberately flat: the whole row opens the workout, the trailing icons start it, pin it
 * or open the overflow menu. A library of twenty workouts therefore reads as a list, not as twenty
 * stacked cards, which is what keeps a dashboard dense.
 */
@Composable
private fun WorkoutRow(
    workout: WorkoutSummary,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val category = workout.categoryOrCustom()
    var menu by remember { mutableStateOf(false) }
    PulseListRow(
        title = workout.name,
        subtitle = buildString {
            append(if (workout.hasOpenEnded) "Open-ended" else formatDuration(workout.knownDurationMillis))
            append(" · ")
            append(workout.intervalCount)
            append(" intervals")
            workout.equipment?.let { append(" · $it") }
            if (workout.builtIn) append(" · starter")
            if (workout.tags.isNotEmpty()) append(" · ").append(workout.tags.joinToString(" · "))
        },
        leadingIcon = category.icon,
        leadingTint = colors.categoryColor(category.hue),
        leadingContainer = colors.categoryTint(category.hue),
        onClick = onOpen,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.xs),
            ) {
                PulseIconButton(
                    icon = if (workout.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (workout.isFavorite) "Remove from favorites" else "Add to favorites",
                    tooltip = if (workout.isFavorite) "Remove from favorites" else "Add to favorites",
                    onClick = onFavorite,
                    tint = if (workout.isFavorite) colors.accent else colors.textMuted,
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
                PulseIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "Start ${workout.name}",
                    tooltip = "Start now",
                    onClick = onStart,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
                PulseIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "More actions",
                    onClick = { menu = true },
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
                if (menu) {
                    PulseBottomSheet(title = "More actions", onDismiss = { menu = false }) {
                        PulseSheetRow(
                            text = stringResource(R.string.action_edit),
                            leadingIcon = Icons.Filled.Edit,
                            onClick = { menu = false; onEdit() },
                        )
                        PulseSheetRow(
                            text = stringResource(R.string.action_duplicate),
                            leadingIcon = Icons.Filled.ContentCopy,
                            onClick = { menu = false; onDuplicate() },
                        )
                        PulseSheetRow(
                            text = "Share summary",
                            leadingIcon = Icons.Filled.Share,
                            onClick = { menu = false; onShare() },
                        )
                        PulseSheetRow(
                            text = stringResource(R.string.action_delete),
                            leadingIcon = Icons.Filled.Delete,
                            destructive = true,
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Details
// ---------------------------------------------------------------------------------------------

/**
 * A workout in full.
 *
 * The header is a category statement rather than a label: the category's icon, hue and tagline say
 * what kind of session this is before the numbers do, and the numbers follow in the same card.
 */
@Composable
fun WorkoutDetailsScreen(
    container: AppContainer,
    workoutId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStart: (WorkoutPlan) -> Unit,
    onSchedule: (String) -> Unit,
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

    PulseScaffold { padding ->
        if (entity != null && (plan == null || expanded == null)) {
            Column(Modifier.padding(padding)) {
                ErrorState(
                    title = "This workout could not be opened",
                    body = "Workout data is unreadable. Export a backup before deleting it.",
                )
            }
            return@PulseScaffold
        }
        if (plan == null || expanded == null) {
            Column(Modifier.padding(padding)) {
                LoadingBox(label = "Loading workout…")
            }
            return@PulseScaffold
        }
        val category = plan.category
        val hue = colors.categoryColor(category.hue)
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
                    title = plan?.name ?: "Workout",
                    onBack = onBack,
                    trailing = {
                        PulseIconButton(
                            icon = Icons.Filled.Edit,
                            contentDescription = "Edit workout",
                            tooltip = "Edit workout",
                            onClick = { plan?.let { onEdit(it.id) } },
                        )
                    },
                )
            }

            // --- Category hero ------------------------------------------------------------------
            // Flat, not a card: the icon tile, the hue-tinted title and the stat tiles carry the
            // category themselves — wrapping them in a tinted card was a container inside which
            // every one of those is its own container.
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(colors.categoryTint(category.hue), LocalPulseShapes.current.control),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = hue,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Spacer(Modifier.width(dimens.m))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = hue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                        Spacer(Modifier.height(dimens.l))
                        Text(
                            text = if (expanded.hasOpenEnded) {
                                "≥ ${formatDuration(expanded.knownMillis)}"
                            } else {
                                formatDuration(expanded.totalMillis ?: 0L)
                            },
                            style = PulseType.NumericLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "total time",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                        Spacer(Modifier.height(dimens.l))
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
                            InfoBanner(stringResource(R.string.details_open_ended_note))
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
                PulseActionMenu(
                    text = "More actions",
                    icon = Icons.Filled.MoreVert,
                    items = listOf(
                        PulseMenuAction(if (preview == null) "Preview cues" else "Stop preview", icon = Icons.Filled.PlayArrow) {
                            if (preview == null) container.sessionController.startPreview(plan) else container.sessionController.stopPreview()
                        },
                        PulseMenuAction(stringResource(R.string.action_edit), icon = Icons.Filled.Edit) { onEdit(plan.id) },
                        PulseMenuAction(stringResource(R.string.planner_add), icon = Icons.Filled.Event) { onSchedule(plan.id) },
                    ),
                )
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
                    height = dimens.barHeightCompact,
                )
            }
            items(expanded.steps.size) { index ->
                val step = expanded.steps[index]
                PulseCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(dimens.microGap),
                        ) {
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

/** A quiet category pill for places that only need the name. */
@Composable
private fun CategoryLabel(category: WorkoutCategory) {
    Surface(
        shape = LocalPulseShapes.current.chip,
        color = LocalPulseColors.current.categoryTint(category.hue),
        contentColor = LocalPulseColors.current.categoryColor(category.hue),
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            maxLines = 1,
        )
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

// ---------------------------------------------------------------------------------------------
// Category
// ---------------------------------------------------------------------------------------------

/**
 * A category, on its own screen.
 *
 * This screen exists because the registry made it free: everything on it — the name, the icon, the
 * hue, the description, the default timings and the list of what you already have in this category —
 * is read from one [WorkoutCategory] record, so it came into being with the category rather than
 * needing to be written per category.
 */
@Composable
fun CategoryScreen(
    container: AppContainer,
    categoryKey: String,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onStart: (WorkoutPlan) -> Unit,
    onBuild: () -> Unit,
) {
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val category = remember(categoryKey) { WorkoutCategories.byKey(categoryKey) }
    val hue = colors.categoryColor(category.hue)
    val summaries by container.workouts.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val inCategory = remember(summaries, categoryKey) {
        summaries.filter { WorkoutCategories.byKey(it.type).key == category.key }
    }

    PulseScaffold { padding ->
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
                    title = category.title,
                    onBack = onBack,
                )
            }

            // Flat hero: the tinted icon tile and the stat tiles are containers themselves; a
            // card around them was one more box with nothing to add.
            item {
                Column {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(colors.categoryTint(category.hue), LocalPulseShapes.current.card),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = hue,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(Modifier.height(dimens.l))
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(dimens.l))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                            StatTile(
                                label = "Work",
                                value = formatDuration(category.defaults.workMillis),
                                accent = hue,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Rest",
                                value = formatDuration(category.defaults.restMillis),
                                accent = hue,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Rounds",
                                value = category.defaults.rounds.toString(),
                                accent = hue,
                                modifier = Modifier.weight(1f),
                            )
                        }
                }
            }

            item {
                PrimaryActionButton(
                    text = "Start ${category.title} now",
                    onClick = {
                        val d = category.defaults
                        onStart(
                            com.pulse.intervalcoach.data.QuickWorkoutFactory.build(
                                workMillis = d.workMillis,
                                restMillis = d.restMillis,
                                rounds = d.rounds,
                                preparationMillis = d.preparationMillis,
                                cooldownMillis = d.cooldownMillis,
                                includeFinalRest = d.includeFinalRest,
                                name = category.title,
                                type = category.type,
                                workName = d.workName,
                                restName = d.restName,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.PlayArrow,
                )
            }
            item {
                SecondaryActionButton(
                    text = "Adjust it in the quick builder",
                    onClick = onBuild,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Tune,
                )
            }

            if (inCategory.isNotEmpty()) {
                item { SectionHeader(text = "Your ${category.title} workouts", trailing = "${inCategory.size}") }
                // One table surface with 56 dp rows, hairline-separated — the same shape the
                // library and the Today page use for lists of workouts. Rows sit inside the
                // card's inset, so titles never hug the phone edge, and each row keeps the
                // category icon that identifies it everywhere else.
                item {
                    PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.xs)) {
                        Column {
                            inCategory.forEachIndexed { index, workout ->
                                PulseListRow(
                                    title = workout.name,
                                    subtitle = "${workout.intervalCount} intervals · " +
                                        (if (workout.hasOpenEnded) "open-ended" else formatDuration(workout.knownDurationMillis)),
                                    leadingIcon = workout.categoryOrCustom().icon,
                                    leadingTint = hue,
                                    leadingContainer = colors.categoryTint(category.hue),
                                    onClick = { onOpenWorkout(workout.id) },
                                    trailing = {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = hue)
                                    },
                                )
                                if (index != inCategory.lastIndex) PulseDivider()
                            }
                        }
                    }
                }
            } else {
                item {
                    EmptyState(
                        title = "No ${category.title} workouts yet",
                        body = "Create a workout or use the defaults above.",
                        icon = category.icon,
                        actionLabel = "Build one",
                        onAction = onBuild,
                    )
                }
            }
        }
    }
}
