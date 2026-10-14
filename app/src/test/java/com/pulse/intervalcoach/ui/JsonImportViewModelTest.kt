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

    @Test fun `paste previews immediately but never saves without confirmation`() = runTest(dispatcher) {
        var writes = 0
        val model = JsonImportViewModel({ writes++; listOf("new-id") }, dispatcher)
        model.paste(WorkoutImport.example)
        runCurrent()
        assertEquals(1, model.state.value.preview!!.plans.size)
        assertFalse(model.state.value.validating)
        assertEquals(0, writes)
    }

    @Test fun `editing invalidates preview immediately and prevents saving stale input`() = runTest(dispatcher) {
        var writes = 0
        val model = JsonImportViewModel({ writes++; listOf("new-id") }, dispatcher)
        model.paste(WorkoutImport.example)
        advanceUntilIdle()
        model.edit("invalid")
        assertNull(model.state.value.preview)
        model.save()
        advanceUntilIdle()
        assertNull(model.state.value.preview)
        assertEquals(0, writes)
        assertNotNull(model.state.value.error)
    }

    @Test fun `rapid edits debounce and only preview the latest document`() = runTest(dispatcher) {
        val model = JsonImportViewModel({ listOf("new-id") }, dispatcher)
        model.edit(WorkoutImport.example)
        advanceTimeBy(150)
        val latest = WorkoutImport.example.replace("Beginner bodyweight intervals", "Latest workout")
        model.edit(latest)
        advanceTimeBy(299)
        assertTrue(model.state.value.validating)
        assertNull(model.state.value.preview)
        advanceUntilIdle()
        assertEquals("Latest workout", model.state.value.preview!!.plans.single().name)
        assertNull(model.state.value.error)
    }

    @Test fun `clearing input cancels pending validation without an error`() = runTest(dispatcher) {
        val model = JsonImportViewModel({ error("Must not save") }, dispatcher)
        model.edit(WorkoutImport.example)
        advanceTimeBy(150)
        model.edit("")
        advanceUntilIdle()
        assertNull(model.state.value.preview)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.validating)
    }

    @Test fun `paste supersedes a pending manual edit`() = runTest(dispatcher) {
        val model = JsonImportViewModel({ listOf("new-id") }, dispatcher)
        model.edit("bad JSON")
        advanceTimeBy(150)
        model.paste(WorkoutImport.example)
        advanceUntilIdle()
        assertNotNull(model.state.value.preview)
        assertNull(model.state.value.error)
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
        model.paste(WorkoutImport.example)
        advanceUntilIdle()
        model.save()
        model.save()
        runCurrent()
        model.edit("cannot change input during save")
        model.paste("cannot paste during save")
        model.load { error("Must not open file during save") }
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
        model.paste(WorkoutImport.example)
        advanceUntilIdle()
        model.load { error("File permission lost") }
        advanceUntilIdle()
        assertNull(model.state.value.preview)
        assertEquals("", model.state.value.text)
        assertEquals("File permission lost", model.state.value.error)
    }

    @Test fun `file load supersedes pending validation and blocks concurrent edits`() = runTest(dispatcher) {
        val model = JsonImportViewModel({ listOf("saved") }, dispatcher)
        model.edit("old invalid input")
        model.load { WorkoutImport.example }
        model.paste("must not replace file")
        advanceUntilIdle()
        assertEquals(WorkoutImport.example, model.state.value.text)
        assertNotNull(model.state.value.preview)
        assertNull(model.state.value.error)
    }

    @Test fun `oversized paste and multibyte input cannot save a truncated document`() = runTest(dispatcher) {
        val model = JsonImportViewModel({ error("Must not save") }, dispatcher)
        model.paste(WorkoutImport.example)
        advanceUntilIdle()
        model.paste("x".repeat(WorkoutImport.MAX_BYTES + 20))
        assertEquals(WorkoutImport.MAX_BYTES + 1, model.state.value.text.length)
        assertNull(model.state.value.preview)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.validating)
        model.paste("é".repeat(WorkoutImport.MAX_BYTES / 2 + 1))
        advanceUntilIdle()
        model.save()
        assertTrue(model.state.value.error!!.contains("256 KiB"))
        assertNull(model.state.value.preview)
    }

    @Test fun `batch save exposes all IDs for automatic library navigation`() = runTest(dispatcher) {
        val document = """{"format":"pulse.workouts","version":1,"workouts":[
            {"name":"One","steps":[{"name":"Work","kind":"WORK","seconds":30}]},
            {"name":"Two","steps":[{"name":"Rest","kind":"REST","seconds":10}]}
        ]}"""
        val model = JsonImportViewModel({ listOf("one", "two") }, dispatcher)
        model.paste(document)
        advanceUntilIdle()
        assertEquals(2, model.state.value.preview!!.plans.size)
        model.save()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), model.state.value.savedIds)
        assertNull(model.state.value.preview)
    }
}
