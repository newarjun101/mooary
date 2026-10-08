package com.arjun.soul_note.core.platform

import com.arjun.soul_note.domain.repository.AppLanguage
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.notifications.NotificationManager
import com.arjun.soul_note.data.export.IosFileExporter

actual fun createFileExporter(logger: AppLogger): FileExporter = IosFileExporter(logger)

actual fun createSecureTokenStore(logger: AppLogger): SecureTokenStore = InMemoryTokenStore()

actual fun createNotificationManager(logger: AppLogger): NotificationManager =
    UnavailableNotificationManager(logger)

// iOS follows the per-app language selected in Settings; this keeps the common control safe.
actual fun applyAppLanguage(language: AppLanguage) = Unit
