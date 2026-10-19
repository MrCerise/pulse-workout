package com.pulse.sync

import com.pulse.engine.IntervalNode
import com.pulse.engine.IntervalSpec
import com.pulse.engine.PhaseKind
import com.pulse.engine.TimelineExpander
import com.pulse.engine.Validation
import com.pulse.engine.WorkoutImport
import com.pulse.engine.WorkoutPlan
import com.pulse.library.StarterWorkouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class ShareCodeTest {

    @Test
    fun `every bundled workout survives a round trip with the same timeline`() {
        for (template in StarterWorkouts.templates) {
            val plan = template.plan
            val decoded = ShareCode.decode(ShareCode.encode(plan))
            assertNotNull("${plan.name} did not decode", decoded)
            decoded!!
            assertEquals(plan.name, decoded.name)
            assertEquals(plan.type, decoded.type)
            assertEquals(plan.copy(id = "", createdAt = 0, updatedAt = 0).nodes, decoded.nodes)
            assertEquals(TimelineExpander.expand(plan).steps.size, TimelineExpander.expand(decoded).steps.size)
            assertEquals(TimelineExpander.expand(plan).totalMillis, TimelineExpander.expand(decoded).totalMillis)
            assertEquals(emptyList<String>(), Validation.validate(decoded))
        }
    }

    @Test
    fun `every bundled workout fits a QR code with room to spare`() {
        // 1,273 bytes is what a version-40 QR code holds at the highest error-correction level.
        for (template in StarterWorkouts.templates) {
            val code = ShareCode.encode(template.plan)
            assertTrue("${template.plan.name} is ${code.length} characters", code.length <= 1_273)
        }
    }

    @Test
    fun `device-local details are not shared`() {
        val plan = WorkoutPlan(
            id = "private-id",
            name = "Mine",
            nodes = listOf(
                IntervalNode(
                    "n1",
                    IntervalSpec("n1", "Squat", PhaseKind.WORK, 30_000, imageUri = "content://media/external/images/1"),
                ),
            ),
            folderId = "folder",
            voiceProfileId = "voice",
            isFavorite = true,
            createdAt = 42,
            updatedAt = 43,
            revision = 7,
        )
        val code = ShareCode.encode(plan)
        val decoded = ShareCode.decode(code)!!
        assertEquals("", decoded.id)
        assertNull(decoded.folderId)
        assertNull(decoded.voiceProfileId)
        assertEquals(false, decoded.isFavorite)
        assertEquals(1, decoded.revision)
        assertNull((decoded.nodes.single() as IntervalNode).interval.imageUri)
        assertTrue("the code must be a pulse:// link", code.startsWith("pulse://import?w=1."))
        assertTrue("the code must stay URL-safe", code.removePrefix(ShareCode.PREFIX).all { it.isLetterOrDigit() || it in "-_." })
    }

    @Test
    fun `anything that is not a share code decodes to null`() {
        assertNull(ShareCode.decode(""))
        assertNull(ShareCode.decode("https://example.com"))
        assertNull(ShareCode.decode("{\"name\":\"plain json\"}"))
        assertNull(ShareCode.decode(ShareCode.PREFIX))
        assertNull(ShareCode.decode(ShareCode.PREFIX + "2.abc"))
        assertNull(ShareCode.decode(ShareCode.PREFIX + "1.!!!not-base64!!!"))
        val valid = ShareCode.encode(StarterWorkouts.examplePlan)
        assertNull("a truncated code must not decode", ShareCode.decode(valid.dropLast(12)))
        assertNull(ShareCode.decode(ShareCode.PREFIX + "1." + "A".repeat(ShareCode.MAX_CODE_CHARS)))
    }

    @Test
    fun `a code that inflates beyond the import limit is refused`() {
        val bomb = ByteArray(WorkoutImport.MAX_BYTES + 1024) { ' '.code.toByte() }
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(bomb)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        val code = ShareCode.PREFIX + "1." + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        assertNull(ShareCode.decode(code))
    }

    @Test
    fun `surrounding whitespace and extra query parameters are tolerated`() {
        val code = ShareCode.encode(StarterWorkouts.examplePlan)
        assertEquals(StarterWorkouts.examplePlan.name, ShareCode.decode("  $code&utm=x \n")?.name)
        assertTrue(ShareCode.isShareCode(" $code"))
    }
}
