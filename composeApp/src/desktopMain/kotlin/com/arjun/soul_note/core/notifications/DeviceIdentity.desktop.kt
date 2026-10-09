package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.core.logging.AppLogger

actual fun createDeviceIdentity(@Suppress("UNUSED_PARAMETER") logger: AppLogger): DeviceIdentity =
    DeviceIdentity { Result.failure(UnsupportedOperationException("No device identity on desktop")) }
