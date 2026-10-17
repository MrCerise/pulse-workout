package com.mrcerise.intervalcoach.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.engine.WorkoutImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Paste/file → automatic preview → explicit save. No input can outlive its validation result. */
class JsonImportViewModel(
    private val saveImport: suspend (String) -> List<String>,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    data class State(
        val text: String = "",
        val request: String = "",
        val preview: WorkoutImport.Preview? = null,
        val busy: Boolean = false,
        val validating: Boolean = false,
        val error: String? = null,
        val savedIds: List<String> = emptyList(),
    )
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var validation: Job? = null
    private var inputVersion = 0L

    /** Manual edits are debounced; clipboard/file imports preview immediately. */
    fun edit(text: String) = updateInput(text, debounce = true)
    fun paste(text: String) = updateInput(text, debounce = false)

    private fun updateInput(text: String, debounce: Boolean) {
        if (state.value.busy) return
        validation?.cancel()
        val version = ++inputVersion
        val oversized = text.length > WorkoutImport.MAX_BYTES
        mutableState.value = state.value.copy(
            text = text.take(WorkoutImport.MAX_BYTES + 1), preview = null, savedIds = emptyList(),
            validating = text.isNotBlank() && !oversized,
            error = if (oversized) "JSON must be at most 256 KiB." else null,
        )
        if (!state.value.validating) return
        validation = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                val preview = withContext(worker) { WorkoutImport.parse(text) }
                if (version == inputVersion) mutableState.value = state.value.copy(preview = preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == inputVersion) mutableState.value = state.value.copy(error = e.message ?: "Invalid workout JSON.")
            } finally {
                if (version == inputVersion) mutableState.value = state.value.copy(validating = false)
            }
        }
    }

    fun request(text: String) { mutableState.value = state.value.copy(request = text.take(4000)) }

    fun load(read: () -> String) {
        if (state.value.busy) return
        validation?.cancel()
        inputVersion++
        mutableState.value = state.value.copy(text = "", preview = null, savedIds = emptyList(), validating = false)
        operation {
            val text = withContext(worker) { read() }
            mutableState.value = state.value.copy(text = text.take(WorkoutImport.MAX_BYTES + 1))
            val preview = withContext(worker) { WorkoutImport.parse(text) }
            mutableState.value = state.value.copy(preview = preview)
        }
    }

    fun save() {
        val current = state.value
        if (current.busy || current.validating || current.preview == null || current.savedIds.isNotEmpty()) return
        val text = current.text
        operation {
            val ids = withContext(worker) { saveImport(text) }
            mutableState.value = state.value.copy(savedIds = ids, preview = null)
        }
    }

    private fun operation(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = state.value.copy(error = e.message ?: "Import failed. Please try again.")
            } finally {
                mutableState.value = state.value.copy(busy = false)
            }
        }
    }
}
