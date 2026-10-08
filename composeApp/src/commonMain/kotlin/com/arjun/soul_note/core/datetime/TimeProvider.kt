package com.arjun.soul_note.core.datetime

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * The single source of "now".
 *
 * Every use case reads the current time from here, so journal dates, streak maths and
 * sync timestamps are deterministic under test and never touch a platform clock
 * directly.
 */
fun interface TimeProvider {
    fun now(): Instant
}

/** Production clock. */
class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}

/** Fixed, mutable clock for tests and previews. */
class FixedTimeProvider(
    private var instant: Instant,
) : TimeProvider {
    override fun now(): Instant = instant

    fun setTo(newInstant: Instant) {
        instant = newInstant
    }

    fun advanceBy(duration: Duration) {
        instant += duration
    }
}

/**
 * The user's journal timezone, resolved once and shared.
 *
 * Entry dates are calendar days in *local* time while every stored timestamp is UTC.
 * Pinning the zone in one place prevents the classic "an entry silently moved to the
 * previous day" bug when the user travels.
 */
class TimeZoneProvider(
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun timeZone(): TimeZone = zone
}

/** Pure calendar arithmetic. No platform date APIs, no hidden time-zone lookups. */
object LocalDates {

    fun today(now: Instant, timeZone: TimeZone): LocalDate = now.toLocalDateTime(timeZone).date

    fun startOfDay(date: LocalDate, timeZone: TimeZone): Instant = date.atStartOfDayIn(timeZone)

    /** Last representable instant of [date] in [timeZone]. */
    fun endOfDay(date: LocalDate, timeZone: TimeZone): Instant =
        startOfDay(plusDays(date, 1), timeZone) - Duration.parse("1ms")

    fun plusDays(date: LocalDate, days: Int): LocalDate = date.plus(DatePeriod(0, 0, days))

    fun minusDays(date: LocalDate, days: Int): LocalDate = date.plus(DatePeriod(0, 0, -days))

    fun daysBetween(from: LocalDate, to: LocalDate): Int = from.daysUntil(to)

    fun isSameDay(a: LocalDate, b: LocalDate): Boolean = a == b

    fun monthStart(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

    fun monthEnd(date: LocalDate): LocalDate =
        monthStart(date).plus(DatePeriod(0, 1, 0)).plus(DatePeriod(0, 0, -1))

    fun yearStart(date: LocalDate): LocalDate = LocalDate(date.year, 1, 1)

    /** Every date of the month containing [date], in order. */
    fun monthDates(date: LocalDate): List<LocalDate> = buildList {
        val end = monthEnd(date)
        var cursor = monthStart(date)
        while (cursor <= end) {
            add(cursor)
            cursor = plusDays(cursor, 1)
        }
    }

    /** The Monday-anchored ISO-8601 week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = minusDays(date, date.dayOfWeek.ordinal)

    /** `yyyy-MM-dd` — the canonical on-disk and wire format for entry dates. */
    fun toIsoString(date: LocalDate): String = buildString {
        append(date.year.toString().padStart(4, '0'))
        append('-')
        append(date.month.number.toString().padStart(2, '0'))
        append('-')
        append(date.day.toString().padStart(2, '0'))
    }

    fun parseIsoString(value: String): LocalDate? = Iso8601.parseDate(value)
}

/** Narrow, dependency-free ISO-8601 codec shared by storage, sync and export. */
object Iso8601 {

    /**
     * Parses an RFC-3339 timestamp with any fractional-second precision, normalising a
     * trailing `Z` or a numeric offset to UTC. Firestore emits up to nine fractional
     * digits, so the nanosecond field is folded into milliseconds.
     */
    fun parseTimestamp(value: String): Instant? = runCatching {
        val text = value.trim()
        require(text.length >= 19) { "timestamp too short" }
        val date = LocalDate(
            year = text.substring(0, 4).toInt(),
            month = text.substring(5, 7).toInt(),
            day = text.substring(8, 10).toInt(),
        )
        val hour = text.substring(11, 13).toInt()
        val minute = text.substring(14, 16).toInt()
        val second = text.substring(17, 19).toInt()

        var fractionMillis = 0L
        if (text.length > 19 && text[19] == '.') {
            val digits = StringBuilder()
            var index = 20
            while (index < text.length && text[index].isDigit()) {
                digits.append(text[index])
                index++
            }
            if (digits.isNotEmpty()) {
                fractionMillis = digits.toString().padEnd(3, '0').take(3).toLong()
            }
        }

        val utc = date.atTime(hour, minute, second).toInstant(TimeZone.UTC)
        Instant.fromEpochMilliseconds(utc.toEpochMilliseconds() - parseOffsetMillis(text) + fractionMillis)
    }.getOrNull()

    /** `yyyy-MM-dd'T'HH:mm:ss.SSSZ`, always UTC, always millisecond precision. */
    fun formatTimestamp(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.UTC)
        return buildString {
            append(local.date.year.toString().padStart(4, '0'))
            append('-')
            append(local.date.month.number.toString().padStart(2, '0'))
            append('-')
            append(local.date.day.toString().padStart(2, '0'))
            append('T')
            append(local.hour.toString().padStart(2, '0'))
            append(':')
            append(local.minute.toString().padStart(2, '0'))
            append(':')
            append(local.second.toString().padStart(2, '0'))
            append('.')
            append(instant.toEpochMilliseconds().mod(1000).toString().padStart(3, '0'))
            append('Z')
        }
    }

    /** `yyyy-MM-dd` for a local calendar day. */
    fun formatDate(date: LocalDate): String = LocalDates.toIsoString(date)

    /** Parses a `yyyy-MM-dd` calendar day. */
    fun parseDate(value: String): LocalDate? = runCatching {
        val text = value.trim()
        require(text.length >= 10) { "date too short" }
        LocalDate(
            year = text.substring(0, 4).toInt(),
            month = text.substring(5, 7).toInt(),
            day = text.substring(8, 10).toInt(),
        )
    }.getOrNull()

    /** Offset of the timestamp from UTC, in milliseconds. `Z` and bare values are zero. */
    private fun parseOffsetMillis(text: String): Long {
        if (text.endsWith("Z") || text.endsWith("z")) return 0
        val signIndex = (19 until text.length).firstOrNull { text[it] == '+' || text[it] == '-' }
            ?: return 0
        val sign = if (text[signIndex] == '+') 1L else -1L
        val offset = text.substring(signIndex + 1)
        if (offset.length < 4) return 0
        val hours = offset.substring(0, 2).toLongOrNull() ?: return 0
        val minutes = offset.substring(2, 4).toLongOrNull() ?: return 0
        return sign * ((hours * 60 + minutes) * 60_000)
    }
}

/** `Month` lookup by 1-based number, used when decoding Firestore month filters. */
fun monthOf(number: Int): Month? = Month.entries.getOrNull(number - 1)
