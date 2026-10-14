package com.pulse.intervalcoach.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.engine.WorkoutImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps preview and input together, survives rotation, and serializes load/validate/save actions. */
class JsonImportViewModel(
    private val saveImport: suspend (String) -> List<String>,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    data class State(
        val text: String = "",
        val request: String = "",
        val preview: WorkoutImport.Preview? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val savedIds: List<String> = emptyList(),
    )
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    fun edit(text: String) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(
            text = text.take(WorkoutImport.MAX_BYTES + 1), preview = null, savedIds = emptyList(),
            error = if (text.length > WorkoutImport.MAX_BYTES) "JSON must be at most 256 KiB." else null,
        )
    }

    fun request(text: String) { mutableState.value = state.value.copy(request = text.take(4000)) }

    fun load(read: () -> String) = operation {
        val text = withContext(worker) { read() }
        // Always replace the old preview, including when the new file fails validation.
        mutableState.value = state.value.copy(text = text, preview = null, savedIds = emptyList())
        val preview = withContext(worker) { WorkoutImport.parse(text) }
        mutableState.value = state.value.copy(preview = preview)
    }

    fun preview() = operation {
        val text = state.value.text
        val preview = withContext(worker) { WorkoutImport.parse(text) }
        mutableState.value = state.value.copy(preview = preview)
    }

    fun save() {
        if (state.value.preview == null || state.value.savedIds.isNotEmpty()) return
        operation(clearPreview = false) {
            val text = state.value.text
            val ids = withContext(worker) { saveImport(text) }
            mutableState.value = state.value.copy(savedIds = ids, preview = null)
        }
    }

    private fun operation(clearPreview: Boolean = true, action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null,
            preview = if (clearPreview) null else state.value.preview)
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
