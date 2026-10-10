package com.arjun.mooary.data.export

import com.arjun.mooary.core.platform.ExportedFile
import com.arjun.mooary.core.platform.FileExporter
import kotlin.time.Clock

/** Common helpers for the per-platform exporters. */
internal object ExportSupport {
    fun timestamp(): String = com.arjun.mooary.core.datetime.Iso8601.formatTimestamp(Clock.System.now())
    fun toExportedFile(fileName: String, path: String, content: String, mimeType: String): ExportedFile =
        ExportedFile(
            fileName = fileName,
            absolutePath = path,
            sizeBytes = content.encodeToByteArray().size.toLong(),
            mimeType = mimeType,
        )
}
