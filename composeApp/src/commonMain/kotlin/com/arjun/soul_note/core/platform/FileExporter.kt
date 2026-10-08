package com.arjun.soul_note.core.platform

/** An exported file that is ready to be handed to the user. */
data class ExportedFile(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val mimeType: String,
)

/**
 * Reads and writes user-visible files, and hands them to the operating system's share
 * sheet. Export never leaves the device: there is deliberately no upload path.
 */
interface FileExporter {
    suspend fun writeText(fileName: String, content: String, mimeType: String): ExportedFile
    suspend fun share(file: ExportedFile, subject: String)
}

/** Desktop-friendly "save as"/open behaviour without a share sheet. */
interface FileOpener {
    suspend fun open(file: ExportedFile)
}
