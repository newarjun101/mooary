package com.arjun.soul_note.core.notifications

/**
 * The contract between the alarm scheduler and the broadcast receiver that posts the
 * notification.
 *
 * Extras live here, in the module that schedules them, because the receiver is owned by
 * the Android application module: it needs the notification icon and channel resources,
 * and a library target does not generate an `R` class to draw them from. The receiver
 * reads these same names, so the two ends cannot drift apart.
 */
object ReminderIntents {
    /** Stable id of the reminder that fired, used to key the notification. */
    const val EXTRA_ID = "reminder_id"

    /** [ReminderKind] name; an unknown value degrades to a daily diary nudge. */
    const val EXTRA_KIND = "kind"

    /** Resolved title, so the receiver does not have to re-derive the copy. */
    const val EXTRA_TITLE = "title"

    /** Resolved body text. */
    const val EXTRA_BODY = "body"

    /**
     * Local hour of the reminder, so the receiver can arm the next occurrence after this
     * one fires. `AlarmManager` has no repeating weekday trigger, so the alarm is one-shot
     * and the receiver re-arms it.
     */
    const val EXTRA_HOUR = "hour"

    /** Local minute of the reminder, for the same reason as [EXTRA_HOUR]. */
    const val EXTRA_MINUTE = "minute"

    /**
     * ISO weekday numbers the reminder repeats on; empty means every day. Carried so a
     * weekly reminder keeps firing weekly instead of becoming a daily one after the first
     * delivery.
     */
    const val EXTRA_DAYS = "days"
}
