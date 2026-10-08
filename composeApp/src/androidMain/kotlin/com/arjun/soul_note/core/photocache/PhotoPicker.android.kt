package com.arjun.soul_note.core.photocache

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPhotoPicker(maxBytes: Int, onPicked: (ByteArray?) -> Unit): PhotoPickerHandle {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        onPicked(uri?.let { readBounded(context, it, maxBytes) })
    }
    return remember(launcher) {
        PhotoPickerHandle {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
}

/**
 * Reads at most [maxBytes] from [uri], or null when it is larger, unreadable, or the user
 * cancels.
 *
 * The size is enforced while reading rather than after: a modern camera image is tens of
 * megabytes, and decoding one into a `ByteArray` only to discover it is too big is exactly
 * the allocation this is meant to avoid. `available()` is a hint, so the read is bounded
 * independently of it.
 */
private fun readBounded(context: Context, uri: Uri, maxBytes: Int): ByteArray? = runCatching {
    val resolver = context.contentResolver
    val declared = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    if (declared != null && declared > maxBytes) return null

    val stream = resolver.openInputStream(uri) ?: return null
    stream.use { input ->
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        buffer.toByteArray().takeIf { it.isNotEmpty() }
    }
}.getOrNull()

/** Read granularity; independent of any size hint the provider gives. */
private const val READ_CHUNK_BYTES = 64 * 1024
