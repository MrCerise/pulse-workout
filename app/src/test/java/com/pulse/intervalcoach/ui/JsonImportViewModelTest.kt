package com.pulse.intervalcoach.ui

import com.pulse.engine.WorkoutImport
import com.pulse.intervalcoach.ui.importer.JsonImportViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JsonImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `editing invalidates preview and prevents saving stale input`() = runTest(dispatcher) {
        var writes = 0
        val model = JsonImportViewModel({ writes++; listOf("new-id") }, dispatcher)
        model.edit(WorkoutImport.example)
        model.preview()
        advanceUntilIdle()
        assertNotNull(model.state.value.preview)
        model.edit("invalid")
        model.save()
        advanceUntilIdle()
        assertNull(model.state.value.preview)
        assertEquals(0, writes)
        model.preview()
        advanceUntilIdle()
        assertNotNull(model.state.value.error)
    }

    @Test fun `save uses validated document and ignores double taps and repeat save`() = runTest(dispatcher) {
        var writes = 0
        val done = CompletableDeferred<Unit>()
        val model = JsonImportViewModel({ text ->
            assertEquals(WorkoutImport.example, text)
            writes++
            done.await()
            listOf("new-id")
        }, dispatcher)
        model.edit(WorkoutImport.example)
        model.preview()
        advanceUntilIdle()
        model.save()
        model.save()
        runCurrent()
        model.edit("cannot change input during save")
        assertEquals(WorkoutImport.example, model.state.value.text)
        assertTrue(model.state.value.busy)
        done.complete(Unit)
        advanceUntilIdle()
        model.save()
        advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals(listOf("new-id"), model.state.value.savedIds)
        assertFalse(model.state.value.busy)
    }

    @Test fun `load failure clears stale preview and save failure can retry`() = runTest(dispatcher) {
        var fail = true
        val model = JsonImportViewModel({ if (fail) error("Storage full") else listOf("saved") }, dispatcher)
        model.load { WorkoutImport.example }
        advanceUntilIdle()
        model.save()
        advanceUntilIdle()
        assertEquals("Storage full", model.state.value.error)
        assertTrue(model.state.value.savedIds.isEmpty())
        assertNotNull(model.state.value.preview)
        fail = false
        model.save()
        advanceUntilIdle()
        assertEquals(listOf("saved"), model.state.value.savedIds)
        model.edit(WorkoutImport.example)
        model.preview()
        advanceUntilIdle()
        model.load { error("File permission lost") }
        advanceUntilIdle()
        assertNull(model.state.value.preview)
        assertEquals("File permission lost", model.state.value.error)
    }
}
