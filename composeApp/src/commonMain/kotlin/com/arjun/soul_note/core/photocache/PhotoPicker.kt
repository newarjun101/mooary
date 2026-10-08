package com.arjun.soul_note.core.photocache

import androidx.compose.runtime.Composable

/**
 * Opens the platform's image picker and hands back the chosen bytes.
 *
 * The picker returns **bytes, not a path**. A content uri is only meaningful inside the
 * activity that received it, so a path would be dead by the time the upload coroutine reads
 * it; the file-name-based cache behind [writePhotoCacheFile] is the only path the app ever
 * keeps.
 *
 * [onPicked] is called with null when the user cancels, so the caller can clear its
 * "choosing" state without a separate channel.
 */
@Composable
expect fun rememberPhotoPicker(
    maxBytes: Int = 8 * 1024 * 1024,
    onPicked: (ByteArray?) -> Unit,
): PhotoPickerHandle

/** Lets the caller open the picker from a button rather than on first composition. */
class PhotoPickerHandle internal constructor(private val opener: () -> Unit) {
    fun open(): Unit = opener()
}
