package com.arjun.soul_note.core.notifications

import kotlin.time.Instant
import kotlinx.datetime.LocalTime

/** What a scheduled reminder is meant to prompt the user to do. */
enum class ReminderKind {
    DAILY_DIARY,
    DAILY_MOOD_CHECK_IN,
    WEEKLY_REFLECTION,
}

/** A single scheduled reminder. */
data class ReminderRequest(
    val id: String,
    val kind: ReminderKind,
    /** Local wall-clock time the reminder should fire at. */
    val time: LocalTime,
    /** ISO weekday numbers (1 = Monday … 7 = Sunday); ignored for daily reminders. */
    val daysOfWeek: Set<Int> = emptySet(),
    val enabled: Boolean = true,
)

/** The next fire time a reminder will actually use, in absolute terms. */
data class ScheduledReminder(
    val id: String,
    val nextTriggerAt: Instant,
)

/**
 * Reminder scheduling, defined in the core but implemented per platform.
 *
 * This type is intentionally *not* referenced by the domain layer: reminders are a
 * platform capability, and the use cases stay free of it.
 */
interface NotificationManager {
    suspend fun scheduleReminder(request: ReminderRequest): Result<ScheduledReminder>
    suspend fun cancelReminder(id: String)
    suspend fun cancelAll()
    suspend fun scheduled(): List<ScheduledReminder>
    suspend fun requestPermission(): Boolean
}

/** Where a notification request was routed. Surfaced in the profile screen. */
enum class ReminderBackend { PLATFORM_ALARMS, UNSUPPORTED }
