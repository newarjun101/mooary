package com.arjun.soul_note.core.photocache

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * `PHPickerViewController` rather than `UIImagePickerController`: it runs out of process, so
 * the app needs no photo-library permission and a user who declines access still gets the
 * picker. `UIViewControllerRepresentable` is not available in the shared UI source set, so
 * the controller is presented from the key window instead.
 */
@Composable
actual fun rememberPhotoPicker(maxBytes: Int, onPicked: (ByteArray?) -> Unit): PhotoPickerHandle = remember {
    PhotoPickerHandle { presentPicker(maxBytes, onPicked) }
}

private const val IMAGE_TYPE = "public.image"

private fun presentPicker(maxBytes: Int, onPicked: (ByteArray?) -> Unit) {
    val host = keyWindowRoot()
    if (host == null) {
        onPicked(null)
        return
    }
    // `configuration` is immutable in the binding -- PHPickerViewController only takes it as a
    // constructor argument, so it cannot be assigned after construction.
    val controller = PHPickerViewController(PHPickerConfiguration().apply { selectionLimit = 1 })
    // Assigned after construction: the lambda below closes over `controller`, which cannot be
    // referenced from inside its own initialiser.
    controller.delegate = PhotoPickerDelegate(maxBytes) { bytes ->
        onPicked(bytes)
        controller.dismissViewControllerAnimated(true, completion = null)
    }
    host.presentViewController(controller, animated = true, completion = null)
}

private fun keyWindowRoot(): UIViewController? {
    val app = UIApplication.sharedApplication
    val windows = app.windows.filterIsInstance<UIWindow>()
    val window = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
    return window?.rootViewController
}

/** `NSData` is not a `ByteArray`; the bytes are copied out under the opt-in. */
@OptIn(ExperimentalForeignApi::class)
private fun bytesOf(data: NSData?, maxBytes: Int): ByteArray? {
    if (data == null) return null
    val size = data.length.toInt()
    if (size <= 0 || size > maxBytes) return null
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, size.toULong()) }
    return out
}

/**
 * `PHPickerViewController` reports through an ObjC protocol, so this has to be an `NSObject`
 * subclass. The bytes arrive asynchronously, and the picker is dismissed by the caller once
 * this has answered -- a picker left on screen after a choice reads as a hang.
 */
private class PhotoPickerDelegate(
    private val maxBytes: Int,
    private val onResult: (ByteArray?) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        val provider = didFinishPicking.firstOrNull() as? NSItemProvider
        if (provider == null) {
            onResult(null)
            return
        }
        provider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, _ ->
            onResult(bytesOf(data, maxBytes))
        }
    }
}
