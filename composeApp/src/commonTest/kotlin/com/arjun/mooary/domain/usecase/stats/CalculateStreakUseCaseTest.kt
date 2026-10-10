package com.arjun.mooary.domain.usecase.stats

import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.domain.model.DiaryEntry
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.MoodEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class CalculateStreakUseCaseTest {

    private val useCase = CalculateStreakUseCase()
    private val today = LocalDate(2024, 3, 14)
    private val writtenAt = Instant.parse("2024-03-14T12:00:00Z")

    private fun diary(date: LocalDate, id: String = "d-${date}"): DiaryEntry =
        DiaryEntry(
            id = id,
            userId = "local_user",
            title = "Entry",
            content = "Content",
            entryDate = date,
            mood = Mood.GOOD,
            emotions = emptyList(),
            tags = emptyList(),
            isFavorite = false,
            createdAt = writtenAt,
            updatedAt = writtenAt,
        )

    private fun mood(date: LocalDate, id: String = "m-${date}"): MoodEntry =
        MoodEntry(
            id = id,
            userId = "local_user",
            mood = Mood.GOOD,
            note = null,
            emotions = emptyList(),
            date = date,
            createdAt = writtenAt,
            updatedAt = writtenAt,
        )

    private fun days(vararg offsets: Int): List<LocalDate> =
        offsets.map { LocalDates.minusDays(today, it) }

    @Test
    fun `no activity at all is the empty streak`() {
        assertEquals(0, useCase(emptyList(), emptyList(), today).current)
        assertEquals(0, useCase(emptyList(), emptyList(), today).longest)
        assertTrue(useCase(emptyList(), emptyList(), today).activityByDate.isEmpty())
    }

    @Test
    fun `a mood check-in alone keeps the streak alive`() {
        val streak = useCase(emptyList(), listOf(mood(today)), today)
        assertEquals(1, streak.current)
    }

    @Test
    fun `consecutive days count in either order in the input`() {
        val streak = useCase(
            diaryEntries = days(2, 0).map { diary(it) },
            moodEntries = days(1).map { mood(it) },
            today = today,
        )
        assertEquals(3, streak.current)
        assertEquals(3, streak.longest)
    }

    @Test
    fun `today missing does not zero a live streak`() {
        val streak = useCase(days(1, 2, 3).map { diary(it) }, emptyList(), today)
        assertEquals(3, streak.current)
    }

    @Test
    fun `a gap breaks the chain but not the best run`() {
        // Active: today, then a two-day gap, then a four-day run ending yesterday.
        val entries = days(0, 5, 6, 7, 8).map { diary(it) }
        val streak = useCase(entries, emptyList(), today)
        assertEquals(1, streak.current)
        assertEquals(4, streak.longest)
    }

    @Test
    fun `a streak that ended in the past is not reported as current`() {
        val streak = useCase(days(10, 11, 12).map { diary(it) }, emptyList(), today)
        assertEquals(0, streak.current)
        assertEquals(3, streak.longest)
    }

    @Test
    fun `future-dated rows cannot inflate the current streak`() {
        val streak = useCase(days(-1, -2, 0).map { diary(it) }, emptyList(), today)
        // The current streak only ever walks backwards from today, so tomorrow and the
        // day after are ignored no matter how they are dated. The all-time best is a
        // forward walk over every active day, so it still counts them — a misdated entry
        // should be visible in the record rather than silently dropped.
        assertEquals(1, streak.current)
        assertEquals(3, streak.longest)
    }

    @Test
    fun `the same day counted twice is one active day`() {
        val streak = useCase(
            diaryEntries = listOf(diary(today, "a"), diary(today, "b")),
            moodEntries = listOf(mood(today)),
            today = today,
        )
        assertEquals(1, streak.current)
        assertEquals(1, streak.totalActiveDays)
        // Two diary rows plus one mood row on the same day.
        assertEquals(3, streak.activityByDate[today])
    }

    @Test
    fun `deleted entries do not sustain a streak`() {
        val deleted = diary(today).copy(deletedAt = Instant.parse("2024-03-14T13:00:00Z"))
        val streak = useCase(listOf(deleted), emptyList(), today)
        assertEquals(0, streak.current)
        assertTrue(streak.activityByDate.isEmpty())
    }

    @Test
    fun `the activity window excludes days older than the requested history`() {
        val old = LocalDates.minusDays(today, 400)
        val streak = useCase(listOf(diary(old)), emptyList(), today, historyMonths = 12)
        assertTrue(streak.activityByDate.isEmpty())
        // The all-time best still remembers it.
        assertEquals(1, streak.longest)
    }
}
