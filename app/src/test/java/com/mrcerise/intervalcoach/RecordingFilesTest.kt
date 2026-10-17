package com.mrcerise.intervalcoach

import com.mrcerise.intervalcoach.data.ownedRecordingFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordingFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `owned recording with spaces resolves for deletion`() {
        val folder = temporary.newFolder("recordings")
        val file = File(folder, "cue with spaces.m4a")
        assertEquals(file.canonicalFile, ownedRecordingFile(file.toURI().toString(), "RECORDING", folder))
    }

    @Test fun `imported and outside files cannot be deleted as recordings`() {
        val folder = temporary.newFolder("recordings")
        assertNull(ownedRecordingFile(File(folder, "cue.m4a").toURI().toString(), "USER_FILE", folder))
        assertNull(ownedRecordingFile(File(temporary.root, "private.m4a").toURI().toString(), "RECORDING", folder))
        assertNull(ownedRecordingFile(File(folder, "../private.m4a").toURI().toString(), "RECORDING", folder))
    }

    @Test fun `content URIs malformed URIs and directories are not owned recordings`() {
        val folder = temporary.newFolder("recordings")
        listOf("content://media/1", "file://remote/clip.m4a", "not a URI", folder.toURI().toString()).forEach {
            assertNull(ownedRecordingFile(it, "RECORDING", folder))
        }
    }
}
