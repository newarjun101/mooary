package com.arjun.mooary.core.platform

import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.notifications.NotificationManager
import com.arjun.mooary.core.notifications.ReminderRequest
import com.arjun.mooary.core.notifications.ScheduledReminder
import com.arjun.mooary.domain.repository.AppLanguage

/**
 * Platform bindings for services that have a genuinely per-platform implementation.
 *
 * These live in their own file (rather than beside the interfaces) so the common code
 * depends only on the interfaces and each target supplies exactly one binding.
 */
expect fun createFileExporter(logger: AppLogger): FileExporter

/**
 * Reminders. A target without a scheduler binding gets [UnavailableNotificationManager],
 * which reports the refusal instead of pretending the reminder was scheduled.
 */
expect fun createNotificationManager(logger: AppLogger): NotificationManager

/** Applies the user's persisted language choice where the platform supports an app locale. */
expect fun applyAppLanguage(language: AppLanguage)

/** Reports every scheduling request as unsupported, and never claims success. */
class UnavailableNotificationManager(
    private val logger: AppLogger,
) : NotificationManager {
    override suspend fun scheduleReminder(request: ReminderRequest): Result<ScheduledReminder> {
        logger.warn("NotificationManager") { "reminders unsupported on this platform" }
        return Result.failure(UnsupportedOperationException("reminders unsupported"))
    }

    override suspend fun cancelReminder(id: String) = Unit

    override suspend fun cancelAll() = Unit

    override suspend fun scheduled(): List<ScheduledReminder> = emptyList()

    override suspend fun requestPermission(): Boolean = false
}
