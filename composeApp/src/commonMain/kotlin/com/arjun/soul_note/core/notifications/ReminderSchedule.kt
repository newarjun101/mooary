package com.arjun.soul_note.core.notifications

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * When a reminder should next fire.
 *
 * Shared by both native schedulers so Android's alarm and iOS's calendar trigger cannot
 * disagree about what "every Monday at 21:00" means, and so the rule is testable without
 * a device.
 */
object ReminderSchedule {

    /**
     * A week plus one day. Long enough to cover any weekly set, short enough that a
     * permanently-expired reminder is reported as unschedulable instead of looping
     * forever.
     */
    private const val MAX_LOOKAHEAD_DAYS = 8

    /**
     * The first future instant at which [request] should fire, or null if it never will.
     *
     * A daily reminder (no [ReminderRequest.daysOfWeek]) fires every day. A weekly set
     * fires on the listed ISO weekdays, and returns null when the set is empty or holds
     * only values outside 1..7.
     */
    fun nextTriggerAt(
        request: ReminderRequest,
        now: Instant,
        zone: TimeZone,
    ): Instant? {
        val today: LocalDate = now.toLocalDateTime(zone).date
        val days = request.activeDays()
        if (days.isEmpty()) return null

        for (offset in 0..MAX_LOOKAHEAD_DAYS) {
            val date = today.plus(offset, DateTimeUnit.DAY)
            if (date.dayOfWeek.isoDayNumber !in days) continue

            val candidate: Instant = LocalDateTime(date, request.time).toInstant(zone)
            if (candidate > now) return candidate
        }
        return null
    }

    /**
     * The ISO weekdays this request actually fires on, with out-of-range values dropped.
     *
     * An empty [ReminderRequest.daysOfWeek] is the daily default and expands to all seven
     * days; a set holding only invalid values is left empty, meaning "never", which
     * [nextTriggerAt] reports as unschedulable rather than silently treating it as daily.
     */
    fun ReminderRequest.activeDays(): Set<Int> = when {
        daysOfWeek.isEmpty() -> ALL_WEEKDAYS
        else -> daysOfWeek.filter { it in 1..7 }.toSet()
    }

    /**
     * Whether this request fires every day rather than on a particular weekly set.
     *
     * Both platforms need this decision and it is easy to get wrong: a check that only
     * validates that every value is in 1..7 passes for *any* weekly set, so a
     * once-a-week reminder would quietly become a daily one. Since [activeDays] already maps
     * the unset default onto all seven days, equality with that set is the whole test — and
     * a set of only out-of-range values is deliberately not daily, so an unschedulable
     * request cannot be expanded into a daily one.
     */
    fun ReminderRequest.repeatsDaily(): Boolean = activeDays() == ALL_WEEKDAYS

    private val ALL_WEEKDAYS: Set<Int> = (1..7).toSet()
}
