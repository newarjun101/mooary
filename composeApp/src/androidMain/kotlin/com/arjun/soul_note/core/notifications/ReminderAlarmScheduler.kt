package com.arjun.soul_note.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

/**
 * The one place an Android reminder alarm is armed.
 *
 * `AlarmManager` has no repeating weekday trigger, so a reminder is armed as a single
 * one-shot alarm and [ReminderReceiver] re-arms it after each delivery. Both ends call
 * [scheduleReminderAlarm] so the two can never disagree about how the next occurrence is
 * computed, and both go through [ReminderSchedule] so the re-armed time stays correct
 * across daylight-saving changes — a fixed 24-hour interval would drift, and a monthly
 * offset would land on the wrong weekday.
 *
 * `setWindow` rather than `setExactAndAllowWhileIdle`: a diary reminder that arrives within
 * a few minutes of the requested time is as useful as one that arrives exactly on time, and
 * exact alarms cost battery and, since Android 12, a permission the user would have to grant
 * for no visible benefit.
 */
fun scheduleReminderAlarm(
    context: Context,
    request: ReminderRequest,
    title: String,
    body: String,
): ScheduledReminder? {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return null
    val next = ReminderSchedule.nextTriggerAt(
        request = request,
        now = Clock.System.now(),
        zone = TimeZone.currentSystemDefault(),
    ) ?: return null

    manager.setWindow(
        AlarmManager.RTC_WAKEUP,
        next.toEpochMilliseconds(),
        WINDOW_MILLIS,
        reminderPendingIntent(context, request, title, body),
    )
    return ScheduledReminder(request.id, next)
}

/**
 * The intent the receiver is woken with, carrying everything needed to post the
 * notification and to arm the following occurrence.
 *
 * The resolved title and body travel in the intent so the receiver never re-reads
 * resources, and the schedule travels as plain fields so re-arming needs no database.
 */
fun reminderIntent(
    context: Context,
    request: ReminderRequest,
    title: String,
    body: String,
): Intent = Intent().setComponent(receiverComponent(context)).apply {
    putExtra(ReminderIntents.EXTRA_ID, request.id)
    putExtra(ReminderIntents.EXTRA_KIND, request.kind.name)
    putExtra(ReminderIntents.EXTRA_TITLE, title)
    putExtra(ReminderIntents.EXTRA_BODY, body)
    putExtra(ReminderIntents.EXTRA_HOUR, request.time.hour)
    putExtra(ReminderIntents.EXTRA_MINUTE, request.time.minute)
    putExtra(ReminderIntents.EXTRA_DAYS, request.daysOfWeek.toIntArray())
}

/**
 * The alarm the receiver is woken by.
 *
 * Keyed on the reminder id with [PendingIntent.FLAG_UPDATE_CURRENT], so rescheduling the
 * same reminder replaces its alarm rather than stacking a second copy of it.
 */
fun reminderPendingIntent(
    context: Context,
    request: ReminderRequest,
    title: String,
    body: String,
): PendingIntent = PendingIntent.getBroadcast(
    context,
    request.id.hashCode(),
    reminderIntent(context, request, title, body),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

/**
 * The receiver lives in the application module because it owns the notification icon and
 * channel resources, and a library target has no `R` to draw from. Addressing it by explicit
 * component name keeps the coupling to one literal, and `context.packageName` is used so the
 * debug `applicationIdSuffix` is respected.
 */
fun receiverComponent(context: Context): ComponentName =
    ComponentName(context.packageName, REMINDER_RECEIVER_CLASS)

/** Slack handed to the platform instead of demanding an exact wake-up. */
private const val WINDOW_MILLIS = 15 * 60 * 1000L

private const val REMINDER_RECEIVER_CLASS =
    "com.arjun.soul_note.core.notifications.ReminderReceiver"
