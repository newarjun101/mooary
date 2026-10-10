package com.arjun.mooary.core.platform

import com.arjun.mooary.domain.repository.AppLanguage
import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.notifications.NotificationManager
import com.arjun.mooary.data.export.DesktopFileExporter

actual fun createFileExporter(logger: AppLogger): FileExporter = DesktopFileExporter(logger)

actual fun createNotificationManager(logger: AppLogger): NotificationManager =
    UnavailableNotificationManager(logger)

// Desktop follows its host locale; the choice remains saved for a future platform override.
actual fun applyAppLanguage(language: AppLanguage) = Unit
