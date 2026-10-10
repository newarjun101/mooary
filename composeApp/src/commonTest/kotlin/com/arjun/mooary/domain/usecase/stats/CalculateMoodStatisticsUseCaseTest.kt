package com.arjun.mooary.domain.usecase.stats

import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.domain.model.Emotion
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.MoodEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class CalculateMoodStatisticsUseCaseTest {

    private val useCase = CalculateMoodStatisticsUseCase()
    private val from = LocalDate(2024, 3, 1)
    private val to = LocalDate(2024, 3, 31)

    private fun entry(
        id: String,
        mood: Mood,
        date: LocalDate,
        at: Instant = Instant.parse("2024-03-10T12:00:00Z"),
        emotions: List<Emotion> = emptyList(),
        note: String? = null,
    ) = MoodEntry(
        id = id,
        userId = "local_user",
        mood = mood,
        note = note,
        emotions = emotions,
        date = date,
        createdAt = at,
        updatedAt = at,
    )

    @Test
    fun `an empty period produces empty statistics rather than zeroes`() {
        val stats = useCase(emptyList(), from, to)
        assertFalse(stats.hasData)
        assertEquals(0, stats.totalRecords)
        assertNull(stats.averageMood)
        assertNull(stats.mostCommonMood)
        assertTrue(stats.distribution.isEmpty())
        assertTrue(stats.dailyAverages.isEmpty())
        assertNull(stats.extremes.highest)
        assertNull(stats.extremes.lowest)
    }

    @Test
    fun `records outside the range and soft-deleted records are ignored`() {
        val stats = useCase(
            entries = listOf(
                entry("in", Mood.HAPPY, LocalDate(2024, 3, 5)),
                entry("before", Mood.VERY_SAD, LocalDate(2024, 2, 28)),
                entry("after", Mood.VERY_SAD, LocalDate(2024, 4, 1)),
                entry("deleted", Mood.VERY_SAD, LocalDate(2024, 3, 6)).copy(
                    deletedAt = Instant.parse("2024-03-07T00:00:00Z"),
                ),
            ),
            from = from,
            to = to,
        )
        assertEquals(1, stats.totalRecords)
        assertEquals(4.0, stats.averageMood)
    }

    @Test
    fun `the average is a plain mean over visible records`() {
        val stats = useCase(
            entries = listOf(
                entry("a", Mood.VERY_HAPPY, LocalDate(2024, 3, 1)),
                entry("b", Mood.HAPPY, LocalDate(2024, 3, 2)),
                entry("c", Mood.VERY_SAD, LocalDate(2024, 3, 3)),
            ),
            from = from,
            to = to,
        )
        assertEquals(3, stats.totalRecords)
        assertEquals((5 + 4 + 0) / 3.0, stats.averageMood)
    }

    @Test
    fun `distribution is ordered by count then by score and ratios sum to one`() {
        val stats = useCase(
            entries = listOf(
                entry("a", Mood.NEUTRAL, LocalDate(2024, 3, 1)),
                entry("b", Mood.NEUTRAL, LocalDate(2024, 3, 2)),
                entry("c", Mood.VERY_HAPPY, LocalDate(2024, 3, 3)),
                entry("d", Mood.EXCITED, LocalDate(2024, 3, 4)),
            ),
            from = from,
            to = to,
        )

        // Count first, so the most frequent mood leads even when a rarer mood scores higher.
        assertEquals(listOf(Mood.NEUTRAL, Mood.VERY_HAPPY, Mood.EXCITED), stats.distribution.map { it.mood })
        assertEquals(2, stats.distribution[0].count)
        assertEquals(1, stats.distribution[1].count)
        assertEquals(1, stats.distribution[2].count)
        assertEquals(1.0, stats.distribution.sumOf { it.ratio })
        // EXCITED and VERY_HAPPY share a score, so the final tie breaks on declaration
        // order, which keeps the chart stable between runs.
        assertEquals(Mood.NEUTRAL, stats.mostCommonMood)
    }

    @Test
    fun `daily averages group several check-ins into one day and newest wins the day`() {
        val stats = useCase(
            entries = listOf(
                entry("morning", Mood.SAD, LocalDate(2024, 3, 4), Instant.parse("2024-03-04T08:00:00Z")),
                entry("evening", Mood.VERY_HAPPY, LocalDate(2024, 3, 4), Instant.parse("2024-03-04T21:00:00Z")),
                entry("next", Mood.GOOD, LocalDate(2024, 3, 5)),
            ),
            from = from,
            to = to,
        )

        assertEquals(2, stats.dailyAverages.size)
        val first = stats.dailyAverages.first()
        assertEquals(LocalDate(2024, 3, 4), first.date)
        assertEquals(2, first.recordCount)
        assertEquals(3.0, first.averageScore)
        assertEquals(Mood.VERY_HAPPY, first.dominantMood)
        assertEquals(listOf(LocalDate(2024, 3, 4), LocalDate(2024, 3, 5)), stats.dailyAverages.map { it.date })
    }

    @Test
    fun `extremes pick the best and worst day not the best and worst record`() {
        val stats = useCase(
            entries = listOf(
                entry("a", Mood.VERY_SAD, LocalDate(2024, 3, 1)),
                entry("b", Mood.SAD, LocalDate(2024, 3, 1)),
                entry("c", Mood.GOOD, LocalDate(2024, 3, 2)),
                entry("d", Mood.EXCITED, LocalDate(2024, 3, 3)),
            ),
            from = from,
            to = to,
        )

        assertEquals(LocalDate(2024, 3, 3), stats.extremes.highest?.date)
        assertEquals(LocalDate(2024, 3, 1), stats.extremes.lowest?.date)
        assertEquals(0.5, stats.extremes.lowest?.averageScore)
    }

    @Test
    fun `top emotions are ranked by frequency then id and respect the limit`() {
        val stats = useCase(
            entries = listOf(
                entry(
                    "a",
                    Mood.GOOD,
                    LocalDate(2024, 3, 1),
                    emotions = listOf(Emotion.fromId("joy"), Emotion.fromId("calm")),
                ),
                entry("b", Mood.GOOD, LocalDate(2024, 3, 2), emotions = listOf(Emotion.fromId("joy"))),
                entry("c", Mood.GOOD, LocalDate(2024, 3, 3), emotions = listOf(Emotion.fromId("calm"))),
            ),
            from = from,
            to = to,
            topEmotionCount = 5,
        )

        assertEquals(listOf("calm", "joy"), stats.topEmotions.map { it.emotion.id })
        assertEquals(2, stats.topEmotions.first().count)
        // Emotion share is a fraction of the number of *records*, not of all emotions.
        assertEquals(2.0 / 3.0, stats.topEmotions.first().ratio)

        val limited = useCase(
            entries = listOf(
                entry("a", Mood.GOOD, LocalDate(2024, 3, 1), emotions = listOf(Emotion.fromId("joy"))),
                entry("b", Mood.GOOD, LocalDate(2024, 3, 2), emotions = listOf(Emotion.fromId("calm"))),
                entry("c", Mood.GOOD, LocalDate(2024, 3, 3), emotions = listOf(Emotion.fromId("tired"))),
            ),
            from = from,
            to = to,
            topEmotionCount = 1,
        )
        // All three appear once, so the alphabetical tie-break picks the first alone.
        assertEquals(listOf("calm"), limited.topEmotions.map { it.emotion.id })
    }

    @Test
    fun `the seven-day window includes today and the six days before it`() {
        val today = LocalDate(2024, 3, 14)
        val entries = (0..9).map { entry("e$it", Mood.GOOD, LocalDates.minusDays(today, it)) }

        val week = useCase.lastWeek(entries, today)
        assertEquals(7, week.totalRecords)
        assertEquals(LocalDates.minusDays(today, 6), week.from)
        assertEquals(today, week.to)
    }

    @Test
    fun `the current-month window is bounded by the month not by thirty days`() {
        val today = LocalDate(2024, 3, 31)
        val entries = listOf(
            entry("in", Mood.HAPPY, LocalDate(2024, 3, 1)),
            entry("prev", Mood.HAPPY, LocalDate(2024, 2, 29)),
            entry("next", Mood.HAPPY, LocalDate(2024, 4, 1)),
        )

        val month = useCase.currentMonth(entries, today)
        assertEquals(1, month.totalRecords)
        assertEquals(LocalDate(2024, 3, 1), month.from)
        assertEquals(LocalDate(2024, 3, 31), month.to)
    }
}
