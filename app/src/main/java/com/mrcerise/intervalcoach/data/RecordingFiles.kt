package com.mrcerise.intervalcoach.data

import java.io.File
import java.net.URI

/** A database URI is untrusted: only a recording directly inside our private folder is owned. */
internal fun ownedRecordingFile(uri: String, source: String, recordingsDir: File): File? {
    if (source != "RECORDING") return null
    return runCatching {
        val parsed = URI(uri)
        if (parsed.scheme != "file" || parsed.authority != null) return null
        val file = File(parsed).canonicalFile
        file.takeIf { it.parentFile == recordingsDir.canonicalFile && it.extension == "m4a" && !it.isDirectory }
    }.getOrNull()
}
