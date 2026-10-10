package com.arjun.mooary.core.notifications

import com.arjun.mooary.core.logging.AppLogger
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

/**
 * A generated identifier kept in the app's local preferences. It is immediately hashed before
 * becoming a Firestore document path and never appears in application logs.
 */
actual fun createDeviceIdentity(logger: AppLogger): DeviceIdentity {
    val defaults = NSUserDefaults.standardUserDefaults
    return DeviceIdentity {
        defaults.stringForKey(DEVICE_ID_KEY)?.let { return@DeviceIdentity Result.success(it) }
        val generated = NSUUID().UUIDString
        defaults.setObject(generated, DEVICE_ID_KEY)
        defaults.stringForKey(DEVICE_ID_KEY)
            ?.takeIf { it == generated }
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("iOS device identity could not be stored"))
    }
}

private const val DEVICE_ID_KEY = "mooary.device.identity"
