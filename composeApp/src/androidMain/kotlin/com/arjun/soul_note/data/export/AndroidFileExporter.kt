package com.arjun.soul_note.data.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.ExportedFile
import com.arjun.soul_note.core.platform.FileExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android exporter.
 *
 * Writes to `cacheDir/exports`, which is served through a `FileProvider` (declared with
 * `android:grantUriPermissions="true"`) so the file can be handed to the system share
 * sheet without ever becoming world-readable.
 */
class AndroidFileExporter(
    private val context: Context,
    private val logger: AppLogger,
) : FileExporter {

    private val exportDirectory: File
        get() = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }

    override suspend fun writeText(fileName: String, content: String, mimeType: String): ExportedFile =
        withContext(Dispatchers.IO) {
            val file = File(exportDirectory, fileName)
            file.writeText(content)
            ExportSupport.toExportedFile(fileName, file.absolutePath, content, mimeType)
        }

    override suspend fun share(file: ExportedFile, subject: String) {
        withContext(Dispatchers.IO) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(file.absolutePath),
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = file.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, subject).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(chooser) }
                .onFailure { error ->
                    if (error !is ActivityNotFoundException) {
                        logger.error("AndroidFileExporter") { "share failed" }
                    }
                }
        }
    }
}
