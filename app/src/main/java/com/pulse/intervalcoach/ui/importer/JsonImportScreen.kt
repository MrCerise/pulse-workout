package com.pulse.intervalcoach.ui.importer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulse.engine.WorkoutImport
import com.pulse.engine.formatDuration
import com.pulse.intervalcoach.AppContainer
import com.pulse.intervalcoach.R
import com.pulse.intervalcoach.ui.components.*
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import java.io.IOException

@Composable
fun JsonImportScreen(container: AppContainer, onBack: () -> Unit, onOpenWorkout: (String) -> Unit, onOpenLibrary: () -> Unit) {
    val model: JsonImportViewModel = viewModel(initializer = { JsonImportViewModel(container.workouts::importJson) })
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val dimens = LocalPulseDimens.current
    var copied by remember { mutableStateOf(false) }
    var clipboardFailed by remember { mutableStateOf(false) }
    var showAll by remember(state.preview) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.load {
            context.contentResolver.openInputStream(uri)?.use(WorkoutImport::readText)
                ?: throw IOException(context.getString(R.string.json_file_error))
        }
    }

    PulseScaffold { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.cardGap),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            item { PulsePageHeader(title = stringResource(R.string.json_title), description = stringResource(R.string.json_intro), onBack = onBack) }
            item {
                PulseCard {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
                        Text(stringResource(R.string.json_ai_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.json_ai_help), style = MaterialTheme.typography.bodyMedium)
                        PulseTextField(value = state.request, onValueChange = { model.request(it); copied = false },
                            label = stringResource(R.string.json_request), placeholder = stringResource(R.string.json_request_hint), minLines = 2)
                        SecondaryActionButton(
                            text = stringResource(if (copied) R.string.json_copied else R.string.json_copy_prompt),
                            onClick = {
                                clipboardFailed = runCatching {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("PULSE workout prompt", WorkoutImport.aiPrompt(state.request)))
                                }.isFailure
                                copied = !clipboardFailed
                            }, modifier = Modifier.fillMaxWidth(),
                        )
                        if (clipboardFailed) InfoBanner(stringResource(R.string.json_clipboard_error), tone = PulseTone.WARNING)
                        Text(stringResource(R.string.json_privacy), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.json_input_title)) }
            item {
                SecondaryActionButton(stringResource(R.string.json_choose_file),
                    { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    Modifier.fillMaxWidth(), enabled = !state.busy)
            }
            item {
                PulseTextField(value = state.text, onValueChange = model::edit, label = stringResource(R.string.json_input),
                    placeholder = "{\"format\":\"pulse.workouts\",\"version\":1,\"workouts\":[…]}",
                    enabled = !state.busy, minLines = 5, isError = state.error != null)
            }
            item {
                SecondaryActionButton(stringResource(R.string.json_example), { model.edit(WorkoutImport.example) },
                    Modifier.fillMaxWidth(), enabled = !state.busy)
            }
            state.error?.let { error -> item { InfoBanner(error, tone = PulseTone.WARNING) } }
            if (state.busy) item { InfoBanner(stringResource(R.string.json_working), tone = PulseTone.INFO) }
            if (state.savedIds.isNotEmpty()) {
                item { InfoBanner(stringResource(R.string.json_saved, state.savedIds.size), tone = PulseTone.INFO) }
                item {
                    PrimaryActionButton(stringResource(R.string.json_view_saved), {
                        if (state.savedIds.size == 1) onOpenWorkout(state.savedIds.single()) else onOpenLibrary()
                    }, Modifier.fillMaxWidth())
                }
            } else {
                item {
                    PrimaryActionButton(stringResource(R.string.json_validate), model::preview,
                        Modifier.fillMaxWidth(), enabled = !state.busy && state.text.isNotBlank())
                }
            }
            state.preview?.let { preview ->
                item { SectionHeader(stringResource(R.string.json_preview_title, preview.plans.size)) }
                item { InfoBanner(stringResource(R.string.json_review), tone = PulseTone.INFO) }
                preview.plans.forEachIndexed { index, plan ->
                    val timeline = preview.timelines[index]
                    item(key = "workout-$index") {
                        PulseCard {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
                                Text(plan.name, style = MaterialTheme.typography.titleLarge)
                                Text(plan.type.name.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
                                if (plan.description.isNotBlank()) Text(plan.description)
                                plan.equipment?.let { Text(stringResource(R.string.json_equipment, it)) }
                                if (plan.tags.isNotEmpty()) Text(plan.tags.joinToString(" · "))
                                Text(stringResource(R.string.json_summary, timeline.steps.size, formatDuration(timeline.knownMillis)))
                                if (timeline.hasOpenEnded) Text(stringResource(R.string.json_manual_warning))
                            }
                        }
                    }
                    itemsIndexed(if (showAll) timeline.steps else timeline.steps.take(12), key = { i, _ -> "step-$index-$i" }) { _, step ->
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                                Text(step.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                PhaseChip(kind = step.kind, name = null)
                            }
                            Text(if (step.isIndefinite) stringResource(R.string.json_manual) else formatDuration(step.durationMillis))
                            if (step.groupBreadcrumb.isNotEmpty()) Text(step.groupBreadcrumb.joinToString(" / "), style = MaterialTheme.typography.labelMedium)
                            step.reps?.let { Text(stringResource(R.string.json_reps, it)) }
                            // Never ellipsize AI coaching instructions: review must expose the full content.
                            step.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            step.speechText?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            step.equipment?.let { Text(stringResource(R.string.json_equipment, it)) }
                            step.sideLabel?.let { Text(it) }
                            PulseDivider()
                        }
                    }
                }
                if (preview.timelines.any { it.steps.size > 12 }) item {
                    SecondaryActionButton(stringResource(if (showAll) R.string.json_show_less else R.string.json_show_all),
                        { showAll = !showAll }, Modifier.fillMaxWidth())
                }
                item {
                    PrimaryActionButton(stringResource(R.string.json_import_count, preview.plans.size), model::save,
                        Modifier.fillMaxWidth(), enabled = !state.busy)
                }
            }
        }
    }
}
