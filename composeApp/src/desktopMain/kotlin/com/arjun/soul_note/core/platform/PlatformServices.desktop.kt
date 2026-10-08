package com.arjun.soul_note.core.platform

import com.arjun.soul_note.domain.repository.AppLanguage
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.notifications.NotificationManager
import com.arjun.soul_note.data.export.DesktopFileExporter

actual fun createFileExporter(logger: AppLogger): FileExporter = DesktopFileExporter(logger)

actual fun createSecureTokenStore(logger: AppLogger): SecureTokenStore = InMemoryTokenStore()

actual fun createNotificationManager(logger: AppLogger): NotificationManager =
    UnavailableNotificationManager(logger)

// Desktop follows its host locale; the choice remains saved for a future platform override.
actual fun applyAppLanguage(language: AppLanguage) = Unit
