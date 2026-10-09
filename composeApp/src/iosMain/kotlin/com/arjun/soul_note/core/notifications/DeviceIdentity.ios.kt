package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.KeychainTokenStore
import platform.Foundation.NSUUID

/**
 * A generated identifier held in Keychain. Unlike identifierForVendor it survives deleting and
 * reinstalling this app in normal iOS operation, but is device-only and never restored to a new
 * phone. If Keychain is cleared, treating the next launch as a new device is the safe answer.
 */
actual fun createDeviceIdentity(logger: AppLogger): DeviceIdentity {
    val store = KeychainTokenStore(
        logger = logger,
        service = "com.arjun.soul_note.device",
        account = "stable-device-id",
    )
    return DeviceIdentity {
        store.read()?.let { return@DeviceIdentity Result.success(it) }
        val generated = NSUUID().UUIDString
        store.write(generated)
        store.read()
            ?.takeIf { it == generated }
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("iOS device identity could not be stored"))
    }
}
