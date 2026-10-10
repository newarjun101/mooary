package com.arjun.mooary.core.platform

import com.arjun.mooary.domain.repository.AppLanguage
import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.notifications.NotificationManager
import com.arjun.mooary.core.notifications.IosNotificationManager
import com.arjun.mooary.data.export.IosFileExporter

actual fun createFileExporter(logger: AppLogger): FileExporter = IosFileExporter(logger)

actual fun createNotificationManager(logger: AppLogger): NotificationManager =
    IosNotificationManager(logger)

// iOS follows the per-app language selected in Settings; this keeps the common control safe.
actual fun applyAppLanguage(language: AppLanguage) = Unit
