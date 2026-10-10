package com.arjun.mooary.core.notifications

import com.arjun.mooary.core.logging.AppLogger

actual fun createDeviceIdentity(@Suppress("UNUSED_PARAMETER") logger: AppLogger): DeviceIdentity =
    DeviceIdentity { Result.failure(UnsupportedOperationException("No device identity on desktop")) }
