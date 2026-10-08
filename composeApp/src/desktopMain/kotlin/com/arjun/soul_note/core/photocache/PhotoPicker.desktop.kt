package com.arjun.soul_note.core.photocache

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun rememberPhotoPicker(maxBytes: Int, onPicked: (ByteArray?) -> Unit): PhotoPickerHandle = remember {
    PhotoPickerHandle {
        // AWT dialogs are modal to the whole process, so the result is read synchronously
        // and handed back on the calling thread. The file is read on the UI thread here,
        // which is acceptable only because the read is bounded and the desktop build is
        // single-window.
        val dialog = FileDialog(null as Frame?, "Select a photo", FileDialog.LOAD).apply {
            isMultipleMode = false
            filenameFilter = java.io.FilenameFilter { _, name ->
                name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
            }
        }
        dialog.isVisible = true
        val dir = dialog.directory ?: return@PhotoPickerHandle
        val name = dialog.file ?: return@PhotoPickerHandle
        val bytes = java.io.File(dir, name).takeIf { it.isFile && it.length() <= maxBytes }?.readBytes()
        onPicked(bytes?.takeIf { it.isNotEmpty() })
    }
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic")
