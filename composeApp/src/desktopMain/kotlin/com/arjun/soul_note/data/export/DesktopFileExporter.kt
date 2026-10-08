package com.arjun.soul_note.data.export

import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.ExportedFile
import com.arjun.soul_note.core.platform.FileExporter
import com.arjun.soul_note.core.error.suspendRunCatchingApp
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop exporter.
 *
 * Writes to `~/Diary Exports/` and, when asked to share, opens the file with the system
 * default application via `java.awt.Desktop`. There is no upload path — the data leaves
 * the machine only if the user explicitly moves or sends the file themselves.
 */
class DesktopFileExporter(
    private val logger: AppLogger,
    private val opener: DesktopFileOpener? = DesktopFileOpener(),
) : FileExporter {

    override suspend fun writeText(fileName: String, content: String, mimeType: String): ExportedFile =
        withContext(Dispatchers.IO) {
            val directory = File(System.getProperty("user.home"), "Diary Exports")
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, fileName)
            file.writeText(content)
            ExportSupport.toExportedFile(fileName, file.absolutePath, content, mimeType)
        }

    override suspend fun share(file: ExportedFile, subject: String) {
        withContext(Dispatchers.IO) {
            opener?.open(file.absolutePath)
                ?: logger.info("DesktopFileExporter") { "no desktop opener available" }
        }
    }
}

/** Opens a path with the OS, guarded because AWT can be headless. */
class DesktopFileOpener {
    fun open(path: String): Boolean = runCatching {
        if (java.awt.GraphicsEnvironment.isHeadless()) return@runCatching false
        val desktop = java.awt.Desktop.getDesktop()
        if (!desktop.isSupported(java.awt.Desktop.Action.OPEN)) return@runCatching false
        desktop.open(File(path))
        true
    }.getOrDefault(false)
}
