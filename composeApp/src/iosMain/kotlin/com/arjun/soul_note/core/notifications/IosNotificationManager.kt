package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.notifications.ReminderSchedule.activeDays
import com.arjun.soul_note.core.notifications.ReminderSchedule.repeatsDaily
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS reminders, scheduled through `UNUserNotificationCenter`.
 *
 * Every reminder becomes a repeating `UNCalendarNotificationTrigger`, because that is what
 * survives the app being suspended: a time-interval trigger would also fire while the
 * device is asleep for a week, which is not a diary nudge anyone wants.
 *
 * iOS can only repeat on a *single* weekday, so a weekly set is split into one request
 * per weekday. All of them are derived from [identifierFor], which means cancelling a
 * reminder can name every request it owns — iOS does not allow enumerating pending
 * requests, so deriving the names is the only way to be sure nothing is left behind.
 */
class IosNotificationManager(
    private val logger: AppLogger,
) : NotificationManager {

    private val center: UNUserNotificationCenter
        get() = UNUserNotificationCenter.currentNotificationCenter()

    /**
     * Ids scheduled through this instance, so [cancelAll] has something to iterate.
     *
     * iOS provides no "cancel everything I created" call, and `removeAllPendingNotificationRequests`
     * would also delete requests belonging to other parts of the app. Only a cold start has
     * nothing to cancel yet, which is harmless: nothing has been scheduled in that process
     * either.
     */
    private val scheduledIds = mutableSetOf<String>()

    override suspend fun scheduleReminder(request: ReminderRequest): Result<ScheduledReminder> {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val first = ReminderSchedule.nextTriggerAt(request, now, zone)
            ?: return Result.failure(
                IllegalArgumentException("Reminder ${request.id} has no future occurrence"),
            )
        val copy = request.kind.localisedCopy()

        val content = UNMutableNotificationContent().apply {
            setTitle(copy.title)
            setBody(copy.body)
            setSound(UNNotificationSound.defaultSound)
        }

        return runCatching {
            val requests = buildRequests(request, content, now, zone)
            check(requests.isNotEmpty()) {
                "Reminder ${request.id} expanded to no notification request"
            }
            for (notificationRequest in requests) {
                center.addNotificationRequest(notificationRequest, withCompletionHandler = null)
            }
            scheduledIds += request.id
            logger.info(TAG) { "scheduled ${requests.size} request(s) for ${request.id}" }
            ScheduledReminder(request.id, first)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                logger.error(TAG) { "could not schedule ${request.id}" }
                Result.failure(error)
            },
        )
    }

    override suspend fun cancelReminder(id: String) {
        val identifiers = identifiersFor(id)
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        center.removeDeliveredNotificationsWithIdentifiers(identifiers)
        logger.info(TAG) { "cancelled ${identifiers.size} possible request(s) for $id" }
    }

    /**
     * Cancels the ids this manager has scheduled. iOS offers no "cancel everything I
     * created" call, and `removeAllPendingNotificationRequests` would also delete requests
     * belonging to other parts of the app.
     */
    override suspend fun cancelAll() {
        for (id in scheduledIds) cancelReminder(id)
        scheduledIds.clear()
    }

    /**
     * Always empty: `UNUserNotificationCenter` cannot enumerate pending requests, and
     * reporting a schedule that was never verified would be worse than admitting it.
     */
    override suspend fun scheduled(): List<ScheduledReminder> = emptyList()

    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ ->
            continuation.resume(granted)
        }
    }

    /**
     * The user's language where it can be read, English otherwise.
     *
     * Resource loading is wrapped for the same reason as on Android: a missed reminder is
     * recoverable, an exception out of the scheduler is not.
     */
    private suspend fun ReminderKind.localisedCopy(): ReminderCopy {
        val resources = copyResources() ?: return copyOrNull() ?: ReminderCopy("", "")
        return runCatching { ReminderCopy(getString(resources.title), getString(resources.body)) }
            .getOrElse {
                logger.warn(TAG) { "falling back to English reminder copy" }
                copyOrNull() ?: ReminderCopy("", "")
            }
    }

    /**
     * Ids scheduled through this instance, so [cancelAll] has something to iterate. Purely
     * a convenience: cancelling a *known* id works from derived names alone, and a cold
     * start has simply nothing to cancel yet.
     */
    private fun buildRequests(
        request: ReminderRequest,
        content: UNMutableNotificationContent,
        now: Instant,
        zone: TimeZone,
    ): List<UNNotificationRequest> {
        // The daily-versus-weekly decision is shared with Android and covered by tests,
        // rather than re-derived here.
        if (request.repeatsDaily()) {
            val next = ReminderSchedule.nextTriggerAt(request, now, zone) ?: return emptyList()
            return listOf(
                UNNotificationRequest.requestWithIdentifier(
                    identifierFor(request.id, null),
                    content,
                    dailyTrigger(next),
                ),
            )
        }

        return request.activeDays()
            .sorted()
            .mapNotNull { isoDay ->
                val perDay = request.copy(daysOfWeek = setOf(isoDay))
                val next = ReminderSchedule.nextTriggerAt(perDay, now, zone) ?: return@mapNotNull null
                UNNotificationRequest.requestWithIdentifier(
                    identifierFor(request.id, isoDay),
                    content,
                    weeklyTrigger(next),
                )
            }
    }

    /** Hour and minute of [next], repeating every day. */
    private fun dailyTrigger(next: Instant): UNCalendarNotificationTrigger {
        val components = NSCalendar.currentCalendar.components(
            NSCalendarUnitHour or NSCalendarUnitMinute,
            fromDate = next.toNSDate(),
        )
        return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, true)
    }

    /** Weekday, hour and minute of [next], repeating every week. */
    private fun weeklyTrigger(next: Instant): UNCalendarNotificationTrigger {
        val components = NSCalendar.currentCalendar.components(
            NSCalendarUnitWeekday or NSCalendarUnitHour or NSCalendarUnitMinute,
            fromDate = next.toNSDate(),
        )
        return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, true)
    }

    private fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)

    private fun identifierFor(id: String, isoDay: Int?): String =
        if (isoDay == null) id else "$id-$isoDay"

    /**
     * The eight names a reminder can own: one for a daily request, seven for a weekly
     * split. Names that were never scheduled are simply removed, which iOS ignores.
     */
    private fun identifiersFor(id: String): List<String> =
        buildList {
            add(identifierFor(id, null))
            for (day in 1..7) add(identifierFor(id, day))
        }

    private companion object {
        const val TAG = "IosNotificationManager"
    }
}
