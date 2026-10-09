package com.arjun.soul_note.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeRangeTest {

    // A Wednesday, so weekly buckets are not accidentally aligned with a 7-day view.
    private val today = LocalDate(2024, 5, 15)

    @Test
    fun `daily ranges end today and count back the right number of days`() {
        assertEquals(7, TimeRange.Last7Days.bucketStarts(today).size)
        assertEquals(30, TimeRange.Last30Days.bucketStarts(today).size)

        val week = TimeRange.Last7Days.bucketStarts(today)
        assertEquals(LocalDate(2024, 5, 9), week.first())
        assertEquals(today, week.last())
        assertEquals(6, TimeRange.Last7Days.windowStart(today).daysUntil(today))
        assertEquals(29, TimeRange.Last30Days.windowStart(today).daysUntil(today))

        val month = TimeRange.Last30Days.bucketStarts(today)
        assertEquals(LocalDate(2024, 4, 16), month.first())
        assertEquals(today, month.last())
    }

    @Test
    fun `daily buckets are consecutive days`() {
        val starts = TimeRange.Last7Days.bucketStarts(today)
        starts.zipWithNext().forEach { (earlier, later) ->
            assertEquals(1, earlier.daysUntil(later), "$earlier -> $later should be one day apart")
        }
    }

    @Test
    fun `weekly buckets start on monday and are seven days apart`() {
        val starts = TimeRange.Last12Weeks.bucketStarts(today)
        assertEquals(12, starts.size)
        assertTrue(starts.all { it.dayOfWeek == DayOfWeek.MONDAY })
        starts.zipWithNext().forEach { (earlier, later) ->
            assertEquals(7, earlier.daysUntil(later))
        }
        // The newest bucket is the current week, so it starts on or before today.
        assertEquals(LocalDate(2024, 5, 13), starts.last())
        assertEquals(LocalDate(2024, 2, 26), starts.first())
    }

    @Test
    fun `monthly buckets start on the first and cover twelve distinct months`() {
        val starts = TimeRange.Last12Months.bucketStarts(today)
        assertEquals(12, starts.size)
        assertTrue(starts.all { it.day == 1 })
        assertEquals(12, starts.map { it.year * 12 + it.month.number }.distinct().size)
        assertEquals(LocalDate(2024, 5, 1), starts.last())
        assertEquals(LocalDate(2023, 6, 1), starts.first())
    }

    @Test
    fun `monthly stepping is stable from the end of a month`() {
        // 31 Jan would clamp under LocalDate.minus(1, MONTH); anchoring on the 1st
        // must still produce a full 12 buckets with no skips or duplicates.
        val endOfMonth = LocalDate(2024, 1, 31)
        val starts = TimeRange.Last12Months.bucketStarts(endOfMonth)
        assertEquals(12, starts.size)
        assertEquals(12, starts.distinct().size)
        assertEquals(LocalDate(2024, 1, 1), starts.last())
        assertEquals(LocalDate(2023, 2, 1), starts.first())
    }

    @Test
    fun `bucket bounds are inclusive and the newest bucket stops at today`() {
        val bounds = TimeRange.Last7Days.bucketBounds(0, today)
        assertEquals(LocalDate(2024, 5, 9), bounds.start)
        assertEquals(LocalDate(2024, 5, 9), bounds.endInclusive)

        val newest = TimeRange.Last7Days.bucketBounds(6, today)
        assertEquals(today, newest.start)
        assertEquals(today, newest.endInclusive)
    }

    @Test
    fun `weekly bucket bounds run to the sunday before the next week`() {
        val bounds = TimeRange.Last12Weeks.bucketBounds(0, today)
        assertEquals(LocalDate(2024, 2, 26), bounds.start)
        assertEquals(LocalDate(2024, 3, 3), bounds.endInclusive)
    }

    @Test
    fun `bucket index finds the containing bucket for every day in the window`() {
        val range = TimeRange.Last7Days
        val starts = range.bucketStarts(today)
        starts.forEachIndexed { expectedIndex, start ->
            assertEquals(expectedIndex, range.bucketIndexOf(start, today), "$start")
        }
        // Every day of the window lands in the bucket that starts on or before it.
        var day = range.windowStart(today)
        while (day <= today) {
            val expected = starts.indexOfLast { it <= day }
            assertEquals(expected, range.bucketIndexOf(day, today), "$day")
            day = LocalDate.fromEpochDays(day.toEpochDays() + 1)
        }
        // The newest bucket is the only one that contains today itself.
        assertEquals(range.bucketCount - 1, range.bucketIndexOf(today, today))
        // The window is 09..15 May, so the 10th is the second day of the first bucket.
        assertEquals(0, range.bucketIndexOf(LocalDate(2024, 5, 9), today))
        assertEquals(1, range.bucketIndexOf(LocalDate(2024, 5, 10), today))
        assertEquals(3, range.bucketIndexOf(LocalDate(2024, 5, 12), today))
    }

    @Test
    fun `bucket index covers every day of a full month bucket set`() {
        val range = TimeRange.Last12Months
        var day = range.windowStart(today)
        while (day <= today) {
            val index = requireNotNull(range.bucketIndexOf(day, today)) { "$day had no bucket" }
            assertTrue(index in 0 until range.bucketCount, "$day had no bucket")
            val bounds = range.bucketBounds(index, today)
            assertTrue(day in bounds, "$day escaped bucket bounds $bounds")
            day = LocalDate.fromEpochDays(day.toEpochDays() + 1)
        }
    }

    @Test
    fun `dates outside the window have no bucket`() {
        val range = TimeRange.Last7Days
        assertNull(range.bucketIndexOf(LocalDate(2024, 5, 8), today))
        assertNull(range.bucketIndexOf(LocalDate(2024, 5, 16), today))
        // A date before the window start is excluded even for the monthly range.
        assertNull(TimeRange.Last12Months.bucketIndexOf(LocalDate(2023, 5, 31), today))
        assertNull(TimeRange.Last12Months.bucketIndexOf(LocalDate(2024, 5, 16), today))
    }

    @Test
    fun `every range reports one bucket per chart mark and never a zero`() {
        TimeRange.presets.forEach { range ->
            assertTrue(range.bucketCount > 0, "$range")
            assertEquals(range.bucketCount, range.bucketStarts(today).size, "$range")
        }
    }
}
