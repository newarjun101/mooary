package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.core.logging.AppLogger

/**
 * Stable identity for one app installation on a physical device.
 *
 * It deliberately is not the FCM token: tokens rotate, while this identity keeps a reinstalled
 * app writing back to the same Firestore device document. Platform implementations never expose
 * the raw value outside this process; the registrar hashes it before it becomes a path.
 */
fun interface DeviceIdentity {
    suspend fun value(): Result<String>
}

/** Creates the best platform-supported identity for a device registration. */
expect fun createDeviceIdentity(logger: AppLogger): DeviceIdentity
