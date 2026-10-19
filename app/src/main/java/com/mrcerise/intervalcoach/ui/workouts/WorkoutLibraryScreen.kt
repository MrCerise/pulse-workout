package com.mrcerise.intervalcoach.ui.workouts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.WorkoutCategories
import com.mrcerise.intervalcoach.data.WorkoutCategory
import com.mrcerise.intervalcoach.data.WorkoutSummary
import com.mrcerise.intervalcoach.data.categoryOrCustom
import com.mrcerise.intervalcoach.locale.categoryTitle
import com.mrcerise.intervalcoach.ui.components.ConfirmDialog
import com.mrcerise.intervalcoach.ui.components.EmptyState
import com.mrcerise.intervalcoach.ui.components.PulseBottomSheet
import com.mrcerise.intervalcoach.ui.components.PulseCard
import com.mrcerise.intervalcoach.ui.components.PulseChoiceChip
import com.mrcerise.intervalcoach.ui.components.PulseDivider
import com.mrcerise.intervalcoach.ui.components.PulseIconButton
import com.mrcerise.intervalcoach.ui.components.PulseListRow
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.PulseSheetRow
import com.mrcerise.intervalcoach.ui.components.PulseTextField
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.mrcerise.intervalcoach.ui.importer.WorkoutCodeScanner
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseElevation
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.mrcerise.intervalcoach.ui.theme.PulseType
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------------------------

enum class LibrarySort { RECENT, NAME, DURATION, CREATED }

@Composable
private fun LibrarySort.label(): String = when (this) {
    LibrarySort.RECENT -> stringResource(R.string.sort_recent)
    LibrarySort.NAME -> stringResource(R.string.sort_name)
    LibrarySort.DURATION -> stringResource(R.string.sort_duration)
    LibrarySort.CREATED -> stringResource(R.string.sort_created)
}

data class LibraryUiState(
    val workouts: List<WorkoutSummary> = emptyList(),
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val favoritesOnly: Boolean = false,
    val grid: Boolean = false,
    /** Persisted category id from the registry, or null for "everything". */
    val categoryKey: String? = null,
    /** Everything in the library, before any filter. */
    val total: Int = 0,
) {
    /** No search and no filter: the library is shown in its sections. */
    val browsing: Boolean get() = query.isBlank() && !favoritesOnly && categoryKey == null
}

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
    // the category joins it in a second pass.
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
        LibraryUiState(filtered, criteria.query, criteria.sort, criteria.favoritesOnly, criteria.grid, category, criteria.workouts.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) { query.value = value }
    fun setSort(value: LibrarySort) { sort.value = value }
    fun toggleFavoritesOnly() { favoritesOnly.value = !favoritesOnly.value }
    fun toggleGrid() { grid.value = !grid.value }
    fun setCategory(key: String?) { categoryKey.value = key }
    fun clearFilters() {
        favoritesOnly.value = false
        categoryKey.value = null
        query.value = ""
    }

    fun duplicate(id: String) = viewModelScope.launch {
        val plan = container.workouts.plan(id) ?: return@launch
        container.workouts.duplicate(plan)
    }

    fun delete(id: String) = viewModelScope.launch { container.workouts.delete(id) }
    fun setFavorite(id: String, favorite: Boolean) = viewModelScope.launch { container.workouts.setFavorite(id, favorite) }

    suspend fun plan(id: String): WorkoutPlan? = container.workouts.plan(id)
}

/** A titled run of workouts on the browsing page. */
private data class LibrarySection(val key: String, val title: String, val workouts: List<WorkoutSummary>)

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

/**
 * The workout library.
 *
 * Browsing shows the library in three sections — favourites, the user's own workouts, then the
 * bundled starters — so twenty-odd templates never bury what the user made. Searching or filtering
 * collapses it to one result list. List and grid share one scrolling container, so the search field
 * and filters scroll away with the content instead of pinning half the screen.
 *
 * New workouts come from the + button, which also holds every import: a scanned QR code, an image
 * of one, or AI/JSON. A scanned code is handed to [onImportCode]; nothing is saved until the user
 * confirms it there.
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
    /** Raw text from a scanned or pictured QR code; the caller decodes it and asks before saving. */
    onImportCode: (String) -> Unit,
) {
    val context = LocalContext.current
    val strings = context.resources
    val viewModel: LibraryViewModel = viewModel(initializer = { LibraryViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<WorkoutSummary?>(null) }
    var qrPlan by remember { mutableStateOf<WorkoutPlan?>(null) }
    var showNewMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val hasActiveFilters = state.favoritesOnly || state.categoryKey != null

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.Default) {
                    runCatching { WorkoutCodeScanner.decodeImage(context, uri) }.getOrNull()
                }
                if (text == null) {
                    Toast.makeText(context, R.string.share_image_no_code, Toast.LENGTH_LONG).show()
                } else {
                    onImportCode(text)
                }
            }
        }
    }
    val scanCode: () -> Unit = {
        WorkoutCodeScanner.scan(
            context = context,
            onScanned = onImportCode,
            onUnavailable = { Toast.makeText(context, R.string.share_scanner_unavailable, Toast.LENGTH_LONG).show() },
        )
    }
    val pickImage: () -> Unit = {
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val startWorkout: (String) -> Unit = { id -> scope.launch { viewModel.plan(id)?.let(onStartWorkout) } }
    val shareQr: (String) -> Unit = { id -> scope.launch { qrPlan = viewModel.plan(id) } }
    val shareText: (String) -> Unit = { id -> scope.launch { viewModel.plan(id)?.let { shareWorkoutText(context, it) } } }

    val sections = remember(state.workouts, state.browsing, strings) {
        if (!state.browsing) {
            listOf(LibrarySection("results", "", state.workouts))
        } else {
            listOf(
                LibrarySection("favorites", strings.getString(R.string.workouts_section_favorites), state.workouts.filter { it.isFavorite }),
                LibrarySection("yours", strings.getString(R.string.workouts_section_yours), state.workouts.filter { !it.isFavorite && !it.builtIn }),
                LibrarySection("starters", strings.getString(R.string.workouts_section_starters), state.workouts.filter { !it.isFavorite && it.builtIn }),
            ).filter { it.workouts.isNotEmpty() }
        }
    }

    PulseScaffold(
        floatingActionButton = {
            Surface(
                onClick = { showNewMenu = true },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = colors.actionFill,
                contentColor = colors.actionText,
                shadowElevation = LocalPulseElevation.current.level3,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = strings.getString(R.string.workouts_new), modifier = Modifier.size(26.dp))
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = if (state.grid) GridCells.Adaptive(minSize = dimens.gridCellMin) else GridCells.Fixed(1),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = dimens.pagePadding, end = dimens.pagePadding, bottom = dimens.huge),
            horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            fullWidth("header") {
                PulsePageHeader(
                    title = strings.getString(R.string.nav_workouts),
                    description = strings.getQuantityString(R.plurals.workouts_count, state.total, state.total),
                    trailing = {
                        PulseIconButton(
                            icon = Icons.Filled.QrCodeScanner,
                            contentDescription = strings.getString(R.string.workouts_scan_qr),
                            tooltip = strings.getString(R.string.workouts_scan_qr),
                            onClick = scanCode,
                        )
                    },
                )
            }
            fullWidth("search") {
                PulseTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.workouts_search_hint),
                    leadingIcon = Icons.Filled.Search,
                    singleLine = true,
                    trailing = {
                        if (state.query.isNotBlank()) {
                            PulseIconButton(
                                icon = Icons.Filled.Close,
                                contentDescription = strings.getString(R.string.action_clear_search),
                                onClick = { viewModel.setQuery("") },
                            )
                        }
                    },
                )
            }
            fullWidth("filters") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { showSortMenu = true },
                            shape = LocalPulseShapes.current.control,
                            color = colors.surfaceRaised,
                            contentColor = colors.textPrimary,
                            border = BorderStroke(dimens.borderWidth, colors.borderStrong),
                        ) {
                            Row(Modifier.padding(horizontal = dimens.m, vertical = dimens.s), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Sort,
                                    contentDescription = stringResource(R.string.workouts_sort),
                                    tint = if (state.sort != LibrarySort.RECENT) colors.accent else colors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(dimens.xs))
                                Text(state.sort.label(), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (hasActiveFilters || state.query.isNotBlank()) {
                            Text(
                                text = strings.getQuantityString(R.plurals.workouts_count, state.workouts.size, state.workouts.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.width(dimens.s))
                        }
                        PulseIconButton(
                            icon = if (state.grid) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = strings.getString(if (state.grid) R.string.view_list_label else R.string.view_grid_label),
                            tooltip = strings.getString(if (state.grid) R.string.view_list_label else R.string.view_grid_label),
                            onClick = viewModel::toggleGrid,
                        )
                    }
                    Spacer(Modifier.size(dimens.s))
                    CategoryFilterRail(
                        categories = WorkoutCategories.picker,
                        selectedKey = state.categoryKey,
                        onSelect = viewModel::setCategory,
                        favoritesOnly = state.favoritesOnly,
                        onToggleFavorites = viewModel::toggleFavoritesOnly,
                        // The grid already insets the page; the rail adds no padding of its own.
                        edgePadding = 0.dp,
                    )
                }
            }

            if (state.workouts.isEmpty()) {
                fullWidth("empty") {
                    EmptyState(
                        title = when {
                            state.total == 0 -> stringResource(R.string.workouts_empty_title)
                            state.query.isBlank() && state.categoryKey == null && state.favoritesOnly ->
                                stringResource(R.string.workouts_no_favorites_title)
                            else -> buildString {
                                append(strings.getString(R.string.schedule_no_matches)); append(" ")
                                state.categoryKey?.let { append("${strings.categoryTitle(WorkoutCategories.byKey(it))} ") }
                                if (state.favoritesOnly) append(strings.getString(R.string.filter_favorites)).append(" ")
                                if (state.query.isNotBlank()) append("“${state.query}”")
                            }.trim()
                        },
                        body = when {
                            state.total == 0 -> stringResource(R.string.workouts_empty_body)
                            state.query.isBlank() && state.categoryKey == null && state.favoritesOnly ->
                                stringResource(R.string.home_no_favorites)
                            else -> stringResource(R.string.workouts_filter_empty_body)
                        },
                        icon = Icons.Filled.FitnessCenter,
                        actionLabel = if (state.total == 0) strings.getString(R.string.action_build) else strings.getString(R.string.filter_clear),
                        onAction = if (state.total == 0) onQuickBuilder else viewModel::clearFilters,
                    )
                }
            }

            sections.forEach { section ->
                if (section.title.isNotEmpty()) {
                    fullWidth("section-${section.key}") {
                        SectionHeader(text = section.title, trailing = section.workouts.size.toString())
                    }
                }
                if (state.grid) {
                    items(section.workouts, key = { "${section.key}-${it.id}" }) { workout ->
                        WorkoutGridCard(
                            workout = workout,
                            onOpen = { onOpenWorkout(workout.id) },
                            onStart = { startWorkout(workout.id) },
                        )
                    }
                } else {
                    // One surface per section, rows split by hairlines: a library is a table of names
                    // and numbers, so it is drawn as one rather than as a stack of cards.
                    fullWidth("list-${section.key}") {
                        PulseCard(contentPadding = PaddingValues(horizontal = dimens.l, vertical = dimens.xs)) {
                            Column {
                                section.workouts.forEachIndexed { index, workout ->
                                    WorkoutRow(
                                        workout = workout,
                                        showStarterLabel = !state.browsing,
                                        onOpen = { onOpenWorkout(workout.id) },
                                        onStart = { startWorkout(workout.id) },
                                        onEdit = { onEdit(workout.id) },
                                        onFavorite = { viewModel.setFavorite(workout.id, !workout.isFavorite) },
                                        onDuplicate = { viewModel.duplicate(workout.id) },
                                        onDelete = { pendingDelete = workout },
                                        onShareQr = { shareQr(workout.id) },
                                        onShareText = { shareText(workout.id) },
                                    )
                                    if (index != section.workouts.lastIndex) PulseDivider()
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
        PulseBottomSheet(title = strings.getString(R.string.workouts_new), onDismiss = { showNewMenu = false }) {
            SectionHeader(text = stringResource(R.string.workouts_section_create))
            PulseSheetRow(
                text = strings.getString(R.string.builder_quick_title),
                leadingIcon = Icons.Filled.FitnessCenter,
                onClick = { showNewMenu = false; onQuickBuilder() },
            )
            PulseSheetRow(
                text = strings.getString(R.string.builder_advanced_title),
                leadingIcon = Icons.Filled.Tune,
                onClick = { showNewMenu = false; onAdvancedBuilder() },
            )
            SectionHeader(text = stringResource(R.string.workouts_section_import))
            PulseSheetRow(
                text = strings.getString(R.string.workouts_scan_qr),
                supportingText = strings.getString(R.string.workouts_scan_qr_body),
                leadingIcon = Icons.Filled.QrCodeScanner,
                onClick = { showNewMenu = false; scanCode() },
            )
            PulseSheetRow(
                text = strings.getString(R.string.workouts_import_image),
                supportingText = strings.getString(R.string.workouts_import_image_body),
                leadingIcon = Icons.Filled.AddPhotoAlternate,
                onClick = { showNewMenu = false; pickImage() },
            )
            PulseSheetRow(
                text = strings.getString(R.string.json_title),
                leadingIcon = Icons.Filled.AutoAwesome,
                onClick = { showNewMenu = false; onJsonImport() },
            )
        }
    }

    qrPlan?.let { plan -> WorkoutQrDialog(plan = plan, onDismiss = { qrPlan = null }) }

    pendingDelete?.let { workout ->
        ConfirmDialog(
            title = stringResource(R.string.delete_workout_title),
            body = strings.getString(R.string.delete_workout_body, workout.name),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = { viewModel.delete(workout.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** An item that takes the whole row in both the list and the grid layout. */
private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * The library's single horizontal filter rail: All, Favorites, then every category.
 *
 * Categories are generated from [WorkoutCategories.all] rather than from the workouts that happen
 * to exist, so the available filters never depend on what is in the library today.
 */
@Composable
fun CategoryFilterRail(
    categories: List<WorkoutCategory>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    favoritesOnly: Boolean,
    onToggleFavorites: () -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: androidx.compose.ui.unit.Dp = LocalPulseDimens.current.pagePadding,
) {
    val strings = LocalContext.current.resources
    val dimens = LocalPulseDimens.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = edgePadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
    ) {
        item {
            PulseChoiceChip(
                text = stringResource(R.string.filter_all),
                selected = selectedKey == null && !favoritesOnly,
                onClick = { onSelect(null); if (favoritesOnly) onToggleFavorites() },
            )
        }
        item {
            PulseChoiceChip(
                text = stringResource(R.string.filter_favorites),
                selected = favoritesOnly,
                leadingIcon = if (favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                onClick = onToggleFavorites,
            )
        }
        items(categories, key = { it.key }) { category ->
            PulseChoiceChip(
                text = strings.categoryTitle(category),
                selected = selectedKey == category.key,
                leadingIcon = category.icon,
                accent = LocalPulseColors.current.categoryColor(category.hue),
                onClick = { onSelect(if (selectedKey == category.key) null else category.key) },
            )
        }
    }
}

/** A workout as a category-coloured tile in the grid view, startable without opening it. */
@Composable
private fun WorkoutGridCard(
    workout: WorkoutSummary,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalContext.current.resources
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.categoryTint(category.hue), LocalPulseShapes.current.control),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = category.icon, contentDescription = null, tint = hue, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                if (workout.isFavorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = strings.getString(R.string.filter_favorites),
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = workout.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (workout.hasOpenEnded) strings.getString(R.string.player_open_ended) else formatDuration(workout.knownDurationMillis),
                        style = PulseType.NumericSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = strings.categoryTitle(category),
                        style = MaterialTheme.typography.labelSmall,
                        color = hue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PulseIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = strings.getString(R.string.action_start_named, workout.name),
                    tooltip = strings.getString(R.string.action_start_now),
                    onClick = onStart,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
            }
        }
    }
}

/**
 * A workout as a row: identity, duration and size, then the actions that matter — favourite, start
 * and the overflow sheet with edit, duplicate, sharing and delete.
 */
@Composable
private fun WorkoutRow(
    workout: WorkoutSummary,
    showStarterLabel: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShareQr: () -> Unit,
    onShareText: () -> Unit,
) {
    val strings = LocalContext.current.resources
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val category = workout.categoryOrCustom()
    var menu by remember { mutableStateOf(false) }
    PulseListRow(
        title = workout.name,
        subtitle = buildString {
            append(if (workout.hasOpenEnded) strings.getString(R.string.player_open_ended) else formatDuration(workout.knownDurationMillis))
            append(" · ")
            append(strings.getString(R.string.interval_count, workout.intervalCount))
            workout.equipment?.let { append(" · $it") }
            if (showStarterLabel && workout.builtIn) append(strings.getString(R.string.starter_label))
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
                    contentDescription = strings.getString(if (workout.isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add),
                    tooltip = strings.getString(if (workout.isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add),
                    onClick = onFavorite,
                    tint = if (workout.isFavorite) colors.accent else colors.textMuted,
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
                PulseIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = strings.getString(R.string.action_start_named, workout.name),
                    tooltip = strings.getString(R.string.action_start_now),
                    onClick = onStart,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
                PulseIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = strings.getString(R.string.action_more),
                    onClick = { menu = true },
                    modifier = Modifier.size(dimens.iconButtonSize),
                )
                if (menu) {
                    PulseBottomSheet(title = workout.name, onDismiss = { menu = false }) {
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
                            text = stringResource(R.string.workout_share_qr),
                            leadingIcon = Icons.Filled.QrCode,
                            onClick = { menu = false; onShareQr() },
                        )
                        PulseSheetRow(
                            text = stringResource(R.string.workout_share_summary),
                            leadingIcon = Icons.Filled.Description,
                            onClick = { menu = false; onShareText() },
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
