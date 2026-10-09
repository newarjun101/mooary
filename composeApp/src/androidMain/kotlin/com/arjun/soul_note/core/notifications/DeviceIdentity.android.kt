package com.arjun.soul_note.core.notifications

import android.provider.Settings
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.AndroidPlatform

/**
 * Android scopes ANDROID_ID to the app signing key, device user, and physical device. That is
 * exactly the scope required here: a reinstall of this signed app keeps one device document,
 * while a different phone or Android user receives its own document.
 */
actual fun createDeviceIdentity(@Suppress("UNUSED_PARAMETER") logger: AppLogger): DeviceIdentity =
    DeviceIdentity {
        val value = Settings.Secure.getString(
            AndroidPlatform.requireContext().contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        if (value.isNullOrBlank() || value == "9774d56d682e549c") {
            Result.failure(IllegalStateException("Android device identity is unavailable"))
        } else {
            Result.success(value)
        }
    }
