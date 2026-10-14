package com.pulse.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class WorkoutImportTest {
    private val interval = """{"name":"Squats","kind":"WORK","seconds":30}"""
    private fun document(steps: String = interval, fields: String = "") =
        """{"format":"pulse.workouts","version":1,"workouts":[{"name":"Test",$fields"steps":[$steps]}]}"""
    private fun rejected(text: String, message: String) {
        val error = runCatching { WorkoutImport.parse(text) }.exceptionOrNull()
        assertTrue("Expected an input error, got $error", error is IllegalArgumentException)
        assertTrue("Expected $message in ${error?.message}", error?.message.orEmpty().contains(message))
    }

    @Test fun `sample imports exact rounds timings tags and notes`() {
        val preview = WorkoutImport.parse(WorkoutImport.example)
        assertEquals(1, preview.plans.size)
        assertEquals(10, preview.timelines.single().steps.size)
        assertEquals(480000L, preview.timelines.single().knownMillis)
        assertEquals(listOf("bodyweight", "beginner"), preview.plans.single().tags)
        assertEquals("Use a stable chair.", preview.timelines.single().steps[1].notes)
        assertFalse(preview.timelines.single().hasOpenEnded)
    }

    @Test fun `fresh identities each parse and for every node`() {
        val first = WorkoutImport.parse(WorkoutImport.example).plans.single()
        val second = WorkoutImport.parse(WorkoutImport.example).plans.single()
        assertNotEquals(first.id, second.id)
        val ids = first.allIntervals().map { it.id } + second.allIntervals().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(0L, first.createdAt)
        assertNull(first.folderId)
        assertNull(first.voiceProfileId)
        assertFalse(first.isFavorite)
    }

    @Test fun `supports unicode BOM and a single AI code fence`() {
        val text = "\uFEFF  ```json\n${document().replace("Squats", "Étirement 🧘")}\n```"
        assertEquals("Étirement 🧘", WorkoutImport.parse(text).timelines.single().steps.single().name)
        rejected("Here is your workout:\n${document()}", "Invalid JSON")
        rejected("```python\n${document()}\n```", "single json code fence")
    }

    @Test fun `manual intervals require explicit manual flag and no timed duration`() {
        val text = document("""{"name":"Push-ups","kind":"WORK","manual":true,"reps":10,"speechText":"Keep steady","sideLabel":"Left","equipment":"Mat"}""")
        val preview = WorkoutImport.parse(text)
        assertTrue(preview.timelines.single().hasOpenEnded)
        assertEquals(0L, preview.timelines.single().knownMillis)
        assertEquals(10, preview.timelines.single().steps.single().reps)
        assertEquals("Keep steady", preview.timelines.single().steps.single().speechText)
        rejected(document(interval.replace("30", "0")), "seconds")
        rejected(document(interval.replace("30", "-1")), "seconds")
        rejected(document(interval.replace("30", "1.5")), "whole JSON number")
        rejected(document(interval.replace("30", "\"30\"")), "whole JSON number")
        rejected(document(interval.replace("30", "999999999999999999999")), "whole JSON number")
        rejected(document(interval.replace("30", "30,\"manual\":true")), "manual interval")
        rejected(document(interval.replace("30", "30,\"manual\":\"true\"")), "must be true or false")
    }

    @Test fun `wrong formats versions types fields and emptiness have paths`() {
        rejected("", "Paste JSON")
        rejected("[]", "must be an object")
        rejected(document().replace("pulse.workouts", "pulse.backup"), "Settings → Backup")
        rejected(document().replace("\"version\":1", "\"version\":2"), "version")
        rejected(document(fields = "\"type\":\"YOGA\","), "$.workouts[0].type")
        rejected(document(interval.replace("WORK", "WRK")), "$.workouts[0].steps[0].kind")
        rejected(document(fields = "\"id\":\"existing-id\","), "unknown field")
        rejected(document(interval.replace("seconds", "durationMillis")), "unknown field")
        rejected(document().replace("\"Test\"", "\"   \""), "$.workouts[0].name")
        rejected(document(""), "at least one step")
        rejected(document(fields = "\"tags\":[null],"), "tags[0]")
        rejected(document(fields = "\"equipment\":null,"), "equipment")
        rejected(document(interval.replace("30", "30,\"reps\":0")), "reps")
        rejected(document(fields = "\"description\":\"${"x".repeat(2001)}\","), "2000")
    }

    @Test fun `nested repeats expand in order and keep last rest`() {
        val rest = """{"name":"Rest","kind":"REST","seconds":10}"""
        val nested = """{"repeat":2,"steps":[{"repeat":3,"steps":[$interval,$rest]}]}"""
        val timeline = WorkoutImport.parse(document(nested)).timelines.single()
        assertEquals(12, timeline.steps.size)
        assertEquals(240000L, timeline.knownMillis)
        assertEquals("Rest", timeline.steps.last().name)
    }

    @Test fun `limits reject expensive expansions and long workouts`() {
        rejected(document("""{"repeat":0,"steps":[$interval]}"""), "repeat")
        rejected(document("""{"repeat":1001,"steps":[$interval]}"""), "repeat")
        val huge = """{"repeat":1000,"steps":[{"repeat":1000,"steps":[$interval]}]}"""
        rejected(document(huge), "$.workouts[0]")
        rejected(document(interval.replace("30", "86400") + "," + interval), "24")
        var nested = interval
        repeat(8) { nested = """{"repeat":1,"steps":[$nested]}""" }
        rejected(document(nested), "8 nesting levels")
        rejected("[".repeat(10000) + "]".repeat(10000), "nested too deeply")
        rejected(" ".repeat(WorkoutImport.MAX_BYTES + 1), "256 KiB")
        rejected("é".repeat(WorkoutImport.MAX_BYTES / 2 + 1), "256 KiB")
    }

    @Test fun `batch is all validated and bounded`() {
        val one = """{"name":"A","steps":[$interval]}"""
        val batch = """{"format":"pulse.workouts","version":1,"workouts":[$one,$one]}"""
        assertEquals(2, WorkoutImport.parse(batch).plans.size)
        rejected(batch.replace("[$one,$one]", "[$one,{}]"), "$.workouts[1].name")
        rejected(batch.replace("[$one,$one]", "[]"), "1–50")
        rejected(batch.replace("[$one,$one]", "[${List(51) { one }.joinToString()}]"), "1–50")
        val many = """{"name":"B","steps":[{"repeat":1000,"steps":[${interval.replace("30", "1")}]}]}"""
        rejected(batch.replace("[$one,$one]", "[${List(11) { many }.joinToString()}]"), "10,000 expanded intervals in total")
    }

    @Test fun `file reader enforces size and UTF8 before parsing`() {
        assertEquals(WorkoutImport.example, WorkoutImport.readText(ByteArrayInputStream(WorkoutImport.example.toByteArray())))
        assertTrue(runCatching { WorkoutImport.readText(ByteArrayInputStream(ByteArray(WorkoutImport.MAX_BYTES + 1))) }.isFailure)
        assertTrue(runCatching { WorkoutImport.readText(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }.isFailure)
    }

    @Test fun `prompt has complete format example and request`() {
        val prompt = WorkoutImport.aiPrompt("20 minute boxing, no equipment")
        assertTrue(prompt.contains(WorkoutImport.example))
        assertTrue(prompt.contains("20 minute boxing, no equipment"))
        assertTrue(prompt.contains("seconds"))
        PhaseKind.entries.forEach { assertTrue(prompt.contains(it.name)) }
        WorkoutType.entries.forEach { assertTrue(prompt.contains(it.name)) }
    }
}
