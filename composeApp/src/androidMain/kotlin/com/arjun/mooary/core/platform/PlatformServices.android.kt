package com.arjun.mooary.core.platform

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.notifications.AndroidNotificationManager
import com.arjun.mooary.core.notifications.NotificationManager
import com.arjun.mooary.data.export.AndroidFileExporter
import com.arjun.mooary.domain.repository.AppLanguage

actual fun createFileExporter(logger: AppLogger): FileExporter =
    AndroidFileExporter(AndroidPlatform.requireContext(), logger)

/**
 * Real `AlarmManager` scheduling, not the unavailable placeholder.
 *
 * The placeholder is what reminders silently fell back to before, which made the whole
 * reminder subsystem — the scheduler, the receiver, the use case, the settings switch —
 * unreachable while every test still passed. `setWindow` in [AndroidNotificationManager]
 * is inexact on purpose; see there for why this stays off `SCHEDULE_EXACT_ALARM`.
 */
actual fun createNotificationManager(logger: AppLogger): NotificationManager =
    AndroidNotificationManager(AndroidPlatform.requireContext(), logger)

actual fun applyAppLanguage(language: AppLanguage) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val tags = if (language == AppLanguage.BURMESE) "my" else "en"
    AndroidPlatform.requireContext()
        .getSystemService(LocaleManager::class.java)
        .applicationLocales = LocaleList.forLanguageTags(tags)
}
