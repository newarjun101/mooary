package com.arjun.mooary.domain.model

import com.arjun.mooary.core.datetime.LocalDates
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * The time windows the dashboard charts can show, coarsest last.
 *
 * Every range is a fixed number of equal-width buckets ending today, and the bucket width
 * is chosen so the chart never tries to draw more marks than a phone can show legibly:
 * a 7-day view is seven daily bars, while a year is twelve monthly ones.
 *
 * The arithmetic lives here, in the domain, rather than in the view model so that "which
 * days belong in this bar" is decided in exactly one place and can be unit-tested without
 * a clock or a UI.
 */
sealed class TimeRange {
    /** Seven daily buckets, today included. */
    data object Last7Days : TimeRange()

    /** Thirty daily buckets, today included. */
    data object Last30Days : TimeRange()

    /** Twelve weekly buckets, each starting Monday, the last one the current week. */
    data object Last12Weeks : TimeRange()

    /** Twelve monthly buckets, each starting on the 1st, the last one the current month. */
    data object Last12Months : TimeRange()

    /** A user-selected inclusive period. Custom periods use one daily bucket per day. */
    data class Custom(val start: LocalDate, val end: LocalDate) : TimeRange() {
        init { require(start <= end) { "Custom range start must not be after its end" } }
    }

    /** How many bars/points the charts draw for this range. */
    val bucketCount: Int
        get() = when (this) {
            Last7Days -> 7
            Last30Days -> 30
            Last12Weeks -> 12
            Last12Months -> 12
            is Custom -> LocalDates.daysBetween(start, end) + 1
        }

    /**
     * The first day of each bucket, oldest first. Always [bucketCount] long.
     *
     * For the weekly and monthly ranges the newest bucket is the *current* week/month, so
     * the window deliberately starts mid-period: the last bar is always partial, and the
     * charts must not treat a partial bucket as if the period were over.
     */
    fun bucketStarts(today: LocalDate): List<LocalDate> = when (this) {
        Last7Days, Last30Days ->
            (bucketCount - 1 downTo 0).map { daysAgo -> LocalDates.minusDays(today, daysAgo) }

        Last12Weeks ->
            (bucketCount - 1 downTo 0).map { weeksAgo ->
                LocalDates.minusDays(LocalDates.weekStart(today), weeksAgo * DAYS_PER_WEEK)
            }

        Last12Months ->
            (bucketCount - 1 downTo 0).map { monthsAgo -> monthStartMonthsAgo(today, monthsAgo) }

        is Custom -> (0 until bucketCount).map { LocalDates.plusDays(start, it) }
    }

    /** First day covered, inclusive. The last day covered is always [today]. */
    fun windowStart(today: LocalDate): LocalDate = when (this) {
        is Custom -> start
        else -> bucketStarts(today).first()
    }

    /** The inclusive day range this range covers. */
    fun window(today: LocalDate): ClosedRange<LocalDate> = windowStart(today)..when (this) {
        is Custom -> end
        else -> today
    }

    /**
     * Which bucket [date] belongs to, or `null` when it falls outside the window.
     *
     * The last bucket extends to [today] rather than to a full period, so a date between
     * the final bucket's start and today still lands in it.
     */
    fun bucketIndexOf(date: LocalDate, today: LocalDate): Int? {
        val starts = bucketStarts(today)
        if (date < starts.first() || date > window(today).endInclusive) return null
        return starts.indexOfLast { it <= date }
    }

    /** The inclusive bounds of one bucket, clamped to [today] for the newest bucket. */
    fun bucketBounds(index: Int, today: LocalDate): ClosedRange<LocalDate> {
        val starts = bucketStarts(today)
        require(index in starts.indices) { "bucket $index out of range for $this" }
        val start = starts[index]
        val end = starts.getOrNull(index + 1)?.let { LocalDates.minusDays(it, 1) } ?: window(today).endInclusive
        return start..end
    }

    companion object {
        const val DAYS_PER_WEEK = 7
        const val MONTHS_PER_YEAR = 12

        /**
         * The 1st of the month [monthsAgo] months before [today]'s month.
         *
         * Steps in whole months via year/month arithmetic rather than
         * `LocalDate.minus(1, DateTimeUnit.MONTH)`, which clamps to the length of the
         * target month and so would silently skip a bucket when starting from e.g. the
         * 31st. Anchoring on the 1st makes every step the same length.
         */
        fun monthStartMonthsAgo(today: LocalDate, monthsAgo: Int): LocalDate {
            val anchor = LocalDates.monthStart(today)
            val totalMonths = anchor.year * MONTHS_PER_YEAR + (anchor.month.number - 1) - monthsAgo
            val year = totalMonths / MONTHS_PER_YEAR
            // Truncating division rounds towards zero, so the remainder can be negative
            // for dates before year 0; normalise it into 0..11 before turning it into a
            // 1-based month number.
            val remainder = (totalMonths - year * MONTHS_PER_YEAR + MONTHS_PER_YEAR) % MONTHS_PER_YEAR
            return LocalDate(year, remainder + 1, 1)
        }

        // Nested singleton ranges are initialized lazily on Android. Delaying this list until
        // it is first requested guarantees every entry is a fully initialized range.
        val presets: List<TimeRange> by lazy {
            listOf(Last7Days, Last30Days, Last12Weeks, Last12Months)
        }
    }
}
