package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.core.notifications.ReminderSchedule.activeDays
import com.arjun.soul_note.core.notifications.ReminderSchedule.repeatsDaily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class ReminderScheduleTest {

    private val zone = TimeZone.of("Europe/Berlin")
    private val exportedAt = Instant.parse("2024-03-14T12:00:00Z")

    private fun request(
        id: String = "r1",
        kind: ReminderKind = ReminderKind.DAILY_DIARY,
        hour: Int = 21,
        minute: Int = 0,
        days: Set<Int> = emptySet(),
    ) = ReminderRequest(
        id = id,
        kind = kind,
        time = LocalTime(hour, minute),
        daysOfWeek = days,
    )

    @Test
    fun `a daily reminder later today fires today`() {
        val now = Instant.parse("2024-03-14T18:00:00Z") // 19:00 in Berlin
        val next = ReminderSchedule.nextTriggerAt(request(hour = 21), now, zone)

        assertEquals(Instant.parse("2024-03-14T20:00:00Z"), next) // 21:00 CET
    }

    @Test
    fun `a daily reminder whose time has passed rolls over to tomorrow`() {
        val now = Instant.parse("2024-03-14T22:00:00Z") // 23:00 in Berlin
        val next = ReminderSchedule.nextTriggerAt(request(hour = 21), now, zone)

        assertEquals(Instant.parse("2024-03-15T20:00:00Z"), next)
    }

    @Test
    fun `the next fire time is always strictly in the future`() {
        val now = Instant.parse("2024-03-14T20:00:00Z") // exactly 21:00 in Berlin
        val next = ReminderSchedule.nextTriggerAt(request(hour = 21), now, zone)

        assertTrue(next != null && next > now)
    }

    @Test
    fun `a weekly reminder jumps to the next matching weekday`() {
        // 2024-03-14 is a Thursday (ISO 4).
        val now = Instant.parse("2024-03-14T08:00:00Z")
        val next = ReminderSchedule.nextTriggerAt(request(days = setOf(1)), now, zone)

        // Monday 2024-03-18 at 21:00 Berlin.
        assertEquals(Instant.parse("2024-03-18T20:00:00Z"), next)
    }

    @Test
    fun `every day in a multi-day set is reachable within a week`() {
        val now = Instant.parse("2024-03-14T08:00:00Z") // Thursday
        val days = setOf(1, 3, 5) // Monday, Wednesday, Friday

        // This is what the iOS binding does when it splits a weekly set into one
        // repeating trigger per weekday, so each day has to resolve on its own.
        val triggers = days.map { day ->
            ReminderSchedule.nextTriggerAt(request(days = setOf(day)), now, zone)
        }

        assertTrue(triggers.none { it == null }, "every configured day must be schedulable")
        assertEquals(days.size, triggers.toSet().size, "each day must produce a distinct trigger")
        assertTrue(
            triggers.all { it!! - now in 1.minutes..(8 * 24 * 60).minutes },
            "every trigger must land within a week of now",
        )
    }

    @Test
    fun `weekday numbers outside one to seven are ignored`() {
        val now = Instant.parse("2024-03-14T08:00:00Z")
        val next = ReminderSchedule.nextTriggerAt(request(days = setOf(0, 8, -1)), now, zone)
        // Nothing valid remains, so the reminder can never fire.
        assertNull(next)
    }

    @Test
    fun `an empty set of out-of-range days is not silently treated as daily`() {
        val now = Instant.parse("2024-03-14T08:00:00Z")
        assertNull(ReminderSchedule.nextTriggerAt(request(days = setOf(42)), now, zone))
    }

    @Test
    fun `all seven days behaves exactly like a daily reminder`() {
        val now = Instant.parse("2024-03-14T18:00:00Z")
        val daily = ReminderSchedule.nextTriggerAt(request(hour = 21), now, zone)
        val weekly = ReminderSchedule.nextTriggerAt(request(hour = 21, days = (1..7).toSet()), now, zone)
        assertEquals(daily, weekly)
    }

    @Test
    fun `a weekly set is found even when today already matches`() {
        val now = Instant.parse("2024-03-14T08:00:00Z") // Thursday
        val next = ReminderSchedule.nextTriggerAt(request(hour = 21, days = setOf(4)), now, zone)
        assertEquals(Instant.parse("2024-03-14T20:00:00Z"), next)
    }

    private fun kotlin.time.Instant.hoursFrom(other: kotlin.time.Instant): Int =
        (this - other).inWholeHours.toInt()


    @Test
    fun `scheduling survives a daylight-saving transition`() {
        // Europe/Berlin moves to CEST on 2024-03-31. A 21:00 reminder must stay at 21:00
        // local time, which is a different UTC instant on either side of the change.
        val before = Instant.parse("2024-03-30T20:00:00Z") // 21:00 CET
        val after = Instant.parse("2024-04-01T08:00:00Z")

        val next = assertNotNull(ReminderSchedule.nextTriggerAt(request(hour = 21), after, zone))
        // 2024-04-01 21:00 CEST == 19:00Z, an hour earlier in UTC than the CET instant,
        // so the reminder still fires at 21:00 on the wall clock across the change.
        assertEquals(Instant.parse("2024-04-01T19:00:00Z"), next)
        assertTrue(next - before > 1.hours)
        assertTrue(next - after in 1.minutes..(24 * 60).minutes)
    }
}

class ReminderCopyTest {

    @Test
    fun `every kind has copy`() {
        ReminderKind.entries.forEach { kind ->
            val copy = assertNotNull(kind.copyOrNull(), "expected copy for $kind")
            assertTrue(copy.title.isNotBlank(), "empty title for $kind")
            assertTrue(copy.body.isNotBlank(), "empty body for $kind")
        }
    }
}

class ReminderDaySetTest {

    private fun request(days: Set<Int>) = ReminderRequest(
        id = "r1",
        kind = ReminderKind.DAILY_DIARY,
        time = LocalTime(21, 0),
        daysOfWeek = days,
    )

    @Test
    fun `an unset day set is the daily default`() {
        assertTrue(request(emptySet()).repeatsDaily())
        assertEquals((1..7).toSet(), request(emptySet()).activeDays())
    }

    @Test
    fun `naming all seven weekdays is still daily`() {
        assertTrue(request((1..7).toSet()).repeatsDaily())
    }

    @Test
    fun `a single weekday is not daily`() {
        // The regression this guards: accepting any set whose values are all in range made
        // every weekly reminder fire daily, so "once a week" silently became "every day".
        assertFalse(request(setOf(1)).repeatsDaily())
        assertFalse(request(setOf(3, 5)).repeatsDaily())
        assertFalse(request(setOf(1, 2, 3, 4, 5, 6)).repeatsDaily())
    }

    @Test
    fun `out-of-range days are dropped rather than widening the schedule`() {
        assertEquals(setOf(1, 3), request(setOf(1, 3, 0, 8, -2)).activeDays())
        assertFalse(request(setOf(1, 3, 0, 8)).repeatsDaily())
    }

    @Test
    fun `a set of only out-of-range days is unschedulable and not daily`() {
        // Empty after filtering, which must not be mistaken for the unset daily default:
        // that would invent a daily reminder nobody asked for.
        val nonsense = request(setOf(0, 9, 42))
        assertFalse(nonsense.repeatsDaily())
        assertTrue(nonsense.activeDays().isEmpty())
        assertNull(
            ReminderSchedule.nextTriggerAt(nonsense, Instant.parse("2024-04-01T12:00:00Z"), TimeZone.UTC),
        )
    }
}
