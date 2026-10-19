package com.mrcerise.intervalcoach.ui.workouts

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrcerise.intervalcoach.AppContainer
import com.mrcerise.intervalcoach.R
import com.mrcerise.intervalcoach.data.WorkoutCategories
import com.mrcerise.intervalcoach.data.WorkoutCategory
import com.mrcerise.intervalcoach.data.categoryOrCustom
import com.mrcerise.intervalcoach.locale.categoryTitle
import com.mrcerise.intervalcoach.ui.components.EmptyState
import com.mrcerise.intervalcoach.ui.components.PrimaryActionButton
import com.mrcerise.intervalcoach.ui.components.PulseCard
import com.mrcerise.intervalcoach.ui.components.PulseDivider
import com.mrcerise.intervalcoach.ui.components.PulseListRow
import com.mrcerise.intervalcoach.ui.components.PulsePageHeader
import com.mrcerise.intervalcoach.ui.components.PulseScaffold
import com.mrcerise.intervalcoach.ui.components.SecondaryActionButton
import com.mrcerise.intervalcoach.ui.components.SectionHeader
import com.mrcerise.intervalcoach.ui.components.StatTile
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors
import com.mrcerise.intervalcoach.ui.theme.LocalPulseDimens
import com.mrcerise.intervalcoach.ui.theme.LocalPulseShapes
import com.pulse.engine.TimelineExpander
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration

// The library lives in WorkoutLibraryScreen.kt and a single workout in WorkoutDetailsScreen.kt;
// this file holds the category page and what those screens share.

/** Shares a readable text summary of a workout (structure only — never fake session numbers). */
internal fun shareWorkoutText(context: Context, plan: WorkoutPlan) {
    val expanded = runCatching { TimelineExpander.expand(plan) }.getOrNull() ?: return
    val text = buildString {
        appendLine(plan.name)
        appendLine()
        expanded.steps.forEach { step ->
            append(if (step.isIndefinite) context.getString(R.string.manual) else formatDuration(step.durationMillis))
            append("  ")
            appendLine(step.name)
        }
        appendLine()
        append(context.getString(R.string.session_total)); append(": ")
        append(expanded.totalMillis?.let { formatDuration(it) } ?: "≥ ${formatDuration(expanded.knownMillis)}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, plan.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.summary_share_title)))
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
    val strings = LocalContext.current.resources
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
                    title = strings.categoryTitle(category),
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
                            text = strings.categoryTitle(category),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(dimens.l))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                            StatTile(
                                label = strings.getString(R.string.builder_work),
                                value = formatDuration(category.defaults.workMillis),
                                accent = hue,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = strings.getString(R.string.builder_rest),
                                value = formatDuration(category.defaults.restMillis),
                                accent = hue,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = strings.getString(R.string.builder_rounds),
                                value = category.defaults.rounds.toString(),
                                accent = hue,
                                modifier = Modifier.weight(1f),
                            )
                        }
                }
            }

            item {
                PrimaryActionButton(
                    text = strings.getString(R.string.action_start_category, strings.categoryTitle(category)),
                    onClick = {
                        val d = category.defaults
                        onStart(
                            com.mrcerise.intervalcoach.data.QuickWorkoutFactory.build(
                                workMillis = d.workMillis,
                                restMillis = d.restMillis,
                                rounds = d.rounds,
                                preparationMillis = d.preparationMillis,
                                cooldownMillis = d.cooldownMillis,
                                includeFinalRest = d.includeFinalRest,
                                name = strings.categoryTitle(category),
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
                    text = strings.getString(R.string.category_adjust),
                    onClick = onBuild,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Tune,
                )
            }

            if (inCategory.isNotEmpty()) {
                item { SectionHeader(text = strings.getString(R.string.category_your_workouts, strings.categoryTitle(category)), trailing = "${inCategory.size}") }
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
                                    subtitle = strings.getString(R.string.interval_count, workout.intervalCount) + " · " +
                                        (if (workout.hasOpenEnded) strings.getString(R.string.player_open_ended) else formatDuration(workout.knownDurationMillis)),
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
                        title = strings.getString(R.string.category_empty, strings.categoryTitle(category)),
                        body = strings.getString(R.string.category_empty_body),
                        icon = category.icon,
                        actionLabel = strings.getString(R.string.action_build_one),
                        onAction = onBuild,
                    )
                }
            }
        }
    }
}
