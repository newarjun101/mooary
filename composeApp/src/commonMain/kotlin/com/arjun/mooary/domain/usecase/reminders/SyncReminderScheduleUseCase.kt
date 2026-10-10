package com.arjun.mooary.domain.usecase.reminders

import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.notifications.NotificationManager
import com.arjun.mooary.core.notifications.ReminderKind
import com.arjun.mooary.core.notifications.ReminderRequest
import com.arjun.mooary.domain.repository.SettingsRepository
import kotlinx.datetime.LocalTime

/** What actually happened when the reminder preference was applied. */
enum class ReminderOutcome {
    /** Reminders are armed. */
    ARMED,

    /** Reminders are switched off and every alarm was cleared. */
    DISABLED,

    /** The user has not allowed notifications, so nothing was armed. */
    PERMISSION_DENIED,

    /** This platform has no scheduler at all, so nothing was armed. */
    UNSUPPORTED,
}

/**
 * Whether this outcome is worth telling the user about.
 *
 * Armed and disabled are the two things the switch already shows, so a message would only
 * be noise. A refusal is different: the user asked for reminders and did not get them, and
 * silence would leave them believing otherwise.
 */
val ReminderOutcome.needsNotice: Boolean
    get() = this == ReminderOutcome.PERMISSION_DENIED || this == ReminderOutcome.UNSUPPORTED

/**
 * Turns the reminders preference into actual alarms.
 *
 * The preference alone schedules nothing: a stored `remindersEnabled = true` is a claim
 * about intent, and it is the platform scheduler that has to act on it. Keeping that
 * translation in one place is what stops the setting and the schedule from drifting apart
 * when one of them changes — a switch that reports "on" while no alarm exists is the
 * failure users notice, and it is invisible without a device.
 *
 * Reminder ids are derived from the kind rather than generated, so applying this twice is
 * idempotent: scheduling again replaces the same alarm instead of stacking a second one,
 * and cancelling still works after a cold start that remembers no ids.
 */
class SyncReminderScheduleUseCase(
    private val settingsRepository: SettingsRepository,
    private val notificationManager: NotificationManager,
    private val logger: AppLogger,
) {

    /**
     * Brings the platform schedule in line with the stored preference.
     *
     * Called when the preference changes and again on start-up, because platform alarms do
     * not survive a reboot, an app update or a force-stop, while the preference does.
     */
    suspend operator fun invoke(): ReminderOutcome {
        val settings = settingsRepository.current()
        if (!settings.remindersEnabled) {
            cancelAll()
            return ReminderOutcome.DISABLED
        }
        return arm(settings.dailyDiaryReminderHour, settings.dailyMoodReminderHour)
    }

    /**
     * Applies a new preference value and reports what the platform actually did.
     *
     * The returned [ReminderOutcome] is what lets the UI tell the truth: a switch that
     * silently reverts itself looks broken, and one that stays on while scheduling was
     * refused is worse.
     */
    suspend fun setEnabled(enabled: Boolean): ReminderOutcome {
        settingsRepository.setRemindersEnabled(enabled)
        return if (enabled) {
            val settings = settingsRepository.current()
            arm(settings.dailyDiaryReminderHour, settings.dailyMoodReminderHour)
        } else {
            cancelAll()
            ReminderOutcome.DISABLED
        }
    }

    private suspend fun arm(diaryHour: Int, moodHour: Int): ReminderOutcome {
        // Permission may have been revoked since the preference was set, so it is
        // re-checked rather than assumed from the switch.
        if (!notificationManager.requestPermission()) {
            logger.warn(TAG) { "reminders are enabled but notifications are not permitted" }
            return ReminderOutcome.PERMISSION_DENIED
        }

        val outcomes = listOf(
            schedule(ReminderKind.DAILY_DIARY, diaryHour),
            schedule(ReminderKind.DAILY_MOOD_CHECK_IN, moodHour),
            // The reflection is a Sunday-evening prompt; the other two are daily.
            schedule(ReminderKind.WEEKLY_REFLECTION, diaryHour, daysOfWeek = setOf(7)),
        )
        return when {
            outcomes.any { it == ReminderOutcome.UNSUPPORTED } -> ReminderOutcome.UNSUPPORTED
            else -> ReminderOutcome.ARMED
        }
    }

    private suspend fun schedule(
        kind: ReminderKind,
        hour: Int,
        daysOfWeek: Set<Int> = emptySet(),
    ): ReminderOutcome {
        val request = ReminderRequest(
            id = idFor(kind),
            kind = kind,
            time = LocalTime(hour.coerceIn(0, 23), 0),
            daysOfWeek = daysOfWeek,
        )
        return notificationManager.scheduleReminder(request).fold(
            onSuccess = { scheduled ->
                logger.info(TAG) { "reminder ${request.id} armed for ${scheduled.nextTriggerAt}" }
                ReminderOutcome.ARMED
            },
            onFailure = { error ->
                logger.error(TAG) { "could not arm ${request.id}: ${error.message}" }
                if (error is UnsupportedOperationException) {
                    ReminderOutcome.UNSUPPORTED
                } else {
                    ReminderOutcome.PERMISSION_DENIED
                }
            },
        )
    }

    private suspend fun cancelAll() {
        runCatching { notificationManager.cancelAll() }
            .onFailure { logger.warn(TAG) { "could not clear reminders" } }
    }

    companion object {
        private const val TAG = "SyncReminderSchedule"

        /** Stable per kind, so rescheduling replaces rather than duplicates. */
        fun idFor(kind: ReminderKind): String = "reminder.${kind.name.lowercase()}"
    }
}
