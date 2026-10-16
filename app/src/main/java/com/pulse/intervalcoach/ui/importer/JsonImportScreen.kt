package com.pulse.intervalcoach.ui.importer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.pulse.intervalcoach.data.WorkoutCategories
import com.pulse.intervalcoach.ui.components.pageContentPadding
import com.pulse.intervalcoach.ui.components.CategoryChip
import com.pulse.intervalcoach.ui.components.InfoBanner
import com.pulse.intervalcoach.ui.components.LoadingBox
import com.pulse.intervalcoach.ui.components.PhaseChip
import com.pulse.intervalcoach.ui.components.PrimaryActionButton
import com.pulse.intervalcoach.ui.components.PulseCard
import com.pulse.intervalcoach.ui.components.PulseDivider
import com.pulse.intervalcoach.ui.components.PulseIconButton
import com.pulse.intervalcoach.ui.components.PulsePageHeader
import com.pulse.intervalcoach.ui.components.PulseScaffold
import com.pulse.intervalcoach.ui.components.GhostActionButton
import com.pulse.intervalcoach.ui.components.PulseTextField
import com.pulse.intervalcoach.ui.components.PulseTone
import com.pulse.intervalcoach.ui.components.SecondaryActionButton
import com.pulse.intervalcoach.ui.components.SectionHeader
import com.pulse.intervalcoach.ui.theme.LocalPulseColors
import com.pulse.intervalcoach.ui.theme.LocalPulseDimens
import com.pulse.intervalcoach.ui.theme.LocalPulseMotion
import java.io.IOException

@Composable
fun JsonImportScreen(container: AppContainer, onBack: () -> Unit, onOpenWorkout: (String) -> Unit, onOpenLibrary: () -> Unit) {
    val model: JsonImportViewModel = viewModel(initializer = { JsonImportViewModel(container.workouts::importJson) })
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalPulseColors.current
    val dimens = LocalPulseDimens.current
    val motion = LocalPulseMotion.current
    var copied by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showAll by remember(state.preview) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            inputError = null
            model.load {
                context.contentResolver.openInputStream(uri)?.use(WorkoutImport::readText)
                    ?: throw IOException(context.getString(R.string.json_file_error))
            }
        }
    }

    // Saving already confirms the import. Go straight to the result, without a second View button.
    LaunchedEffect(state.savedIds) {
        if (state.savedIds.size == 1) onOpenWorkout(state.savedIds.single())
        else if (state.savedIds.isNotEmpty()) onOpenLibrary()
    }
    LaunchedEffect(state.error) {
        if (state.error != null && state.text.isNotBlank() && state.preview == null) showEditor = true
    }

    fun copyPrompt() {
        inputError = null
        copied = runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Pulse Workout prompt", WorkoutImport.aiPrompt(state.request)))
        }.isSuccess
        if (!copied) inputError = context.getString(R.string.json_clipboard_error)
    }

    PulseScaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            state.preview?.let { preview ->
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = dimens.pagePadding, vertical = dimens.s),
                    verticalArrangement = Arrangement.spacedBy(dimens.s),
                ) {
                    Text(stringResource(R.string.json_safety), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    PrimaryActionButton(
                        text = if (state.busy) stringResource(R.string.json_working) else stringResource(R.string.json_import_count, preview.plans.size),
                        onClick = model::save,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy && !state.validating,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = pageContentPadding(),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap),
        ) {
            item { PulsePageHeader(title = stringResource(R.string.json_title), onBack = onBack) }
            if (state.text.isBlank() && !state.busy) item {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
                PulseTextField(
                    value = state.request, onValueChange = { model.request(it); copied = false },
                    label = stringResource(R.string.json_request), placeholder = stringResource(R.string.json_request_hint), minLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    PrimaryActionButton(
                        text = stringResource(R.string.json_send_prompt),
                        onClick = {
                            inputError = null
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, WorkoutImport.aiPrompt(state.request))
                            }
                            // Native sharesheet, never a hidden request to an AI service.
                            runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.json_send_prompt))) }
                                .onFailure { copyPrompt() }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PulseIconButton(
                        icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = stringResource(if (copied) R.string.json_copied else R.string.json_copy_prompt),
                        onClick = { copyPrompt() },
                        tint = if (copied) colors.success else colors.textSecondary,
                    )
                }
                Text(stringResource(R.string.json_ai_help), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.s)) {
                    SecondaryActionButton(
                        text = stringResource(R.string.json_paste),
                        onClick = {
                            inputError = null
                            // Read only on this explicit gesture. Do not inspect the clipboard on resume.
                            runCatching {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text else null
                                require(!text.isNullOrBlank()) { context.getString(R.string.json_clipboard_empty) }
                                model.paste(text.take(WorkoutImport.MAX_BYTES + 1).toString())
                                showEditor = false
                            }.onFailure { inputError = it.message ?: context.getString(R.string.json_paste_error) }
                        },
                        modifier = Modifier.weight(1f), enabled = !state.busy,
                    )
                    PulseIconButton(
                        icon = Icons.Filled.FolderOpen,
                        contentDescription = stringResource(R.string.json_choose_file),
                        onClick = { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                        enabled = !state.busy,
                    )
                }
            }
            item {
                Column {
                    GhostActionButton(
                        text = stringResource(if (showEditor) R.string.json_hide_editor else R.string.json_edit),
                        onClick = { showEditor = !showEditor },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = showEditor,
                        enter = fadeIn(tween(motion.fast())) + expandVertically(tween(motion.standard())),
                        exit = fadeOut(tween(motion.fast())) + shrinkVertically(tween(motion.standard())),
                    ) {
                        PulseTextField(
                            value = state.text, onValueChange = { inputError = null; model.edit(it) },
                            label = stringResource(R.string.json_input),
                            placeholder = "{\"format\":\"pulse.workouts\",\"version\":1,\"workouts\":[…]}",
                            enabled = !state.busy, minLines = 4, isError = state.error != null,
                        )
                    }
                }
            }
            (inputError ?: state.error)?.let { error -> item { InfoBanner(error, tone = PulseTone.WARNING) } }
            if (state.validating || (state.busy && state.preview == null)) item { LoadingBox(label = stringResource(R.string.json_working)) }
            state.preview?.let { preview ->
                item { SectionHeader(stringResource(R.string.json_preview_title, preview.plans.size)) }
                preview.plans.forEachIndexed { index, plan ->
                    val timeline = preview.timelines[index]
                    item(key = "workout-$index") {
                        PulseCard {
                            Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
                                Text(plan.name, style = MaterialTheme.typography.titleLarge)
                                CategoryChip(category = WorkoutCategories.forType(plan.type))
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
                            step.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            step.speechText?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            step.equipment?.let { Text(stringResource(R.string.json_equipment, it)) }
                            step.sideLabel?.let { Text(it) }
                            PulseDivider()
                        }
                    }
                }
                if (preview.timelines.any { it.steps.size > 12 }) item {
                    SecondaryActionButton(
                        stringResource(if (showAll) R.string.json_show_less else R.string.json_show_all),
                        { showAll = !showAll }, Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
