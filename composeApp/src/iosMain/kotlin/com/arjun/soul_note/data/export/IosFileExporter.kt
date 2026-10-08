package com.arjun.soul_note.data.export

import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.ExportedFile
import com.arjun.soul_note.core.platform.FileExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * iOS exporter.
 *
 * Writes into the app's `Documents/Diary Exports/` directory and presents
 * `UIActivityViewController` so the file can be dropped into Files, Mail or Messages. The
 * directory is marked excluded-from-backup: an export is user data, and quietly uploading
 * it to iCloud would contradict the app's privacy promise.
 */
class IosFileExporter(
    private val logger: AppLogger,
) : FileExporter {

    /**
     * Resolved from `URLsForDirectory` rather than `currentDirectoryPath`, which on iOS is
     * the process's working directory and is not stable between launches.
     */
    @OptIn(ExperimentalForeignApi::class)
    private val exportDirectory: String by lazy {
        val documents = NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL
            ?: error("Documents directory unavailable")

        val path = requireNotNull(documents.path) + "/" + EXPORT_DIRECTORY_NAME
        NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)

        // The literal Apple key, used directly so the binding does not have to expose it.
        NSURL.fileURLWithPath(path).setResourceValue(true, forKey = BACKUP_EXCLUSION_KEY, error = null)
        path
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun writeText(fileName: String, content: String, mimeType: String): ExportedFile =
        withContext(Dispatchers.Default) {
            val path = exportDirectory + "/" + fileName

            writeUtf8(path, content)

            logger.info("IosFileExporter") { "wrote export $fileName" }
            ExportSupport.toExportedFile(fileName, path, content, mimeType)
        }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun share(file: ExportedFile, subject: String) {
        val url = NSURL.fileURLWithPath(file.absolutePath)
        val controller = UIActivityViewController(
            activityItems = listOf<Any>(url),
            applicationActivities = null,
        )
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root == null) {
            logger.warn("IosFileExporter") { "no view controller to present share sheet" }
            return
        }
        root.topMostViewController().presentViewController(controller, animated = true, completion = null)
    }
}

/**
 * The key window's controller may itself be presenting something — a share sheet opened
 * while a document picker is up, for instance — and iOS refuses to present on a controller
 * that is already presenting one. Walking to the top of the chain avoids that.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.topMostViewController(): UIViewController {
    var current = this
    while (true) {
        val presented = current.presentedViewController ?: return current
        if (presented === current) return current
        current = presented
    }
}

/**
 * Writes the whole string as UTF-8 in one go.
 *
 * Deliberately not `createFileAtPath`, which only *creates* an empty file and would hand
 * the user a zero-byte export, and not `NSString.writeToFile`, whose Kotlin/Native
 * signature has changed across releases. Plain `fputs` over a C `FILE*` is stable, and a
 * Kotlin string is already UTF-8 bytes, so the file is correct for any diary text.
 */
@OptIn(ExperimentalForeignApi::class)
private fun writeUtf8(path: String, content: String) {
    val file: CPointer<FILE>? = fopen(path, "w") ?: error("Could not open $path for writing")
    try {
        if (content.isNotEmpty() && fputs(content, file) == -1) {
            error("Could not write export to $path")
        }
    } finally {
        fclose(file)
    }
}

private const val EXPORT_DIRECTORY_NAME = "Diary Exports"
private const val BACKUP_EXCLUSION_KEY = "NSURLIsExcludedFromBackupKey"
