package com.pulse.intervalcoach.ui.parser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.engine.TimelineExpander
import com.pulse.engine.WorkoutPlan
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.parser.WorkoutTextParser
import com.pulse.intervalcoach.ui.components.GradientActionButton
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.TimelineBar
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.pulse.intervalcoach.R

/**
 * Text-to-workout preview.
 *
 * The parser is deterministic, so this screen shows exactly what was understood, what was not, and
 * the resulting intervals and total duration before anything is saved or started.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onStart: (WorkoutPlan) -> Unit,
) {
    val colors = LocalPulseColors.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Parsed workout") }
    var result by remember { mutableStateOf<WorkoutTextParser.ParseResult?>(null) }

    val parsed = result?.plan
    val expanded = remember(parsed) { parsed?.let { runCatching { TimelineExpander.expand(it) }.getOrNull() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.parser_title), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        Text("Describe it in words, preview exactly what was matched", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Describe the workout") },
                    placeholder = { Text(stringResource(R.string.parser_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientActionButton(
                        text = stringResource(R.string.voice_preview),
                        onClick = { result = WorkoutTextParser.parse(text, name) },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryActionButton(
                        text = "Fill in an example",
                        onClick = { text = "8 rounds, 30 seconds work, 15 seconds rest, 2 minute warm-up, 2 minute cool down" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            result?.let { parsedResult ->
                item {
                    InfoBanner(parsedResult.message)
                }
                item {
                    Text(
                        "This is a deterministic pattern parser, not an AI assistant: it only reports what it actually matched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            if (parsed != null && expanded != null) {
                item { SectionHeader("Parsed preview") }
                item {
                    PulseCard {
                        Column {
                            Text(
                                expanded.totalMillis?.let { formatDuration(it) } ?: "≥ ${formatDuration(expanded.knownMillis)}",
                                style = MaterialTheme.typography.displaySmall,
                                color = colors.work,
                                fontFeatureSettings = "tnum",
                            )
                            Text(
                                "${expanded.steps.size} intervals · total time" +
                                    (if (expanded.hasOpenEnded) " (minimum)" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(12.dp))
                            TimelineBar(
                                kinds = expanded.steps.map { it.kind },
                                currentIndex = -1,
                                progressInStep = 0f,
                                height = 12.dp,
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Workout name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    // A plain Column keeps this preview inside the scrolling list (no nested scroll).
                    Column {
                        expanded.steps.take(12).forEach { step ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(step.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (step.isIndefinite) "manual" else formatDuration(step.durationMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        fontFeatureSettings = "tnum",
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                PhaseChip(kind = step.kind, name = null)
                            }
                        }
                        if (expanded.steps.size > 12) {
                            Text(
                                "+ ${expanded.steps.size - 12} more intervals",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryActionButton(
                            text = stringResource(R.string.builder_save),
                            onClick = {
                                scope.launch {
                                    val plan = parsed.copy(name = name.ifBlank { "Parsed workout" })
                                    container.workouts.save(plan)
                                    onSaved(plan.id)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        GradientActionButton(
                            text = stringResource(R.string.builder_save_and_start),
                            onClick = {
                                scope.launch {
                                    val plan = parsed.copy(name = name.ifBlank { "Parsed workout" })
                                    container.workouts.save(plan)
                                    onStart(plan)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (result == null) {
                item {
                    Text(
                        "Supported patterns: \u201C6 rounds\u201D, \u201C40 seconds work\u201D, \u201C20 seconds rest\u201D, \u201C1 minute warm-up\u201D, " +
                            "\u201Ccool down 3 minutes\u201D, \u201Cget ready 10 seconds\u201D, \u201Ctabata\u201D, \u201Cemom 10 minutes\u201D, \u201Camrap 12 minutes\u201D, " +
                            "plus durations like \u201C90 seconds\u201D, \u201C1:30\u201D or \u201C2m\u201D.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}
