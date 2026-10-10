package com.arjun.mooary.domain.usecase.stats

import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.domain.model.DiaryEntry
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.MoodEntry
import com.arjun.mooary.domain.model.SyncStatus
import com.arjun.mooary.domain.model.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class CalculateDashboardStatisticsUseCaseTest {

    private val useCase = CalculateDashboardStatisticsUseCase(CalculateMoodStatisticsUseCase())

    /** A Wednesday, so weekly buckets are not aligned with the daily ranges. */
    private val today = LocalDate(2024, 5, 15)

    private fun mood(
        id: String,
        mood: Mood,
        date: LocalDate,
    ) = MoodEntry(
        id = id,
        userId = "local_user",
        mood = mood,
        note = null,
        emotions = emptyList(),
        date = date,
        createdAt = Instant.parse("2024-05-15T12:00:00Z"),
        updatedAt = Instant.parse("2024-05-15T12:00:00Z"),
    )

    private fun entry(id: String, date: LocalDate) = DiaryEntry(
        id = id,
        userId = "local_user",
        title = "Entry $id",
        content = "content",
        entryDate = date,
        mood = Mood.HAPPY,
        emotions = emptyList(),
        tags = emptyList(),
        isFavorite = false,
        createdAt = Instant.parse("2024-05-15T12:00:00Z"),
        updatedAt = Instant.parse("2024-05-15T12:00:00Z"),
    )

    @Test
    fun `an empty window still produces one empty point per bucket`() {
        val stats = useCase(emptyList(), emptyList(), today, TimeRange.Last7Days)

        assertFalse(stats.hasData)
        assertEquals(7, stats.trend.size)
        assertEquals(7, stats.bucketsWithoutMood)
        assertEquals(7, stats.bucketsWithoutEntries)
        assertEquals(0, stats.totalEntries)
        assertNull(stats.mood.averageMood)
        assertTrue(stats.trend.all { it.averageMood == null && it.entryCount == 0 })
    }

    @Test
    fun `records land in the bucket that contains their date`() {
        val stats = useCase(
            entries = listOf(
                entry("a", LocalDate(2024, 5, 9)),
                entry("b", LocalDate(2024, 5, 9)),
                entry("c", LocalDate(2024, 5, 15)),
            ),
            moods = emptyList(),
            today = today,
            range = TimeRange.Last7Days,
        )

        assertEquals(listOf(2, 0, 0, 0, 0, 0, 1), stats.trend.map { it.entryCount })
        assertEquals(3, stats.totalEntries)
    }

    @Test
    fun `buckets with no records keep a null average instead of dropping to zero`() {
        val stats = useCase(
            entries = emptyList(),
            moods = listOf(
                mood("m1", Mood.HAPPY, LocalDate(2024, 5, 9)),
                mood("m2", Mood.HAPPY, LocalDate(2024, 5, 15)),
            ),
            today = today,
            range = TimeRange.Last7Days,
        )

        // This gap-preserving series is the whole reason this use case exists:
        // MoodStatistics.dailyAverages omits 10-14 May, which would make a line chart
        // connect two points that are really five days apart.
        assertEquals(7, stats.trend.size)
        assertEquals(4.0, stats.trend.first().averageMood)
        assertTrue(stats.trend.subList(1, 6).all { it.averageMood == null })
        assertEquals(4.0, stats.trend.last().averageMood)
        assertEquals(5, stats.bucketsWithoutMood)
    }

    @Test
    fun `a bucket average is the mean of the mood scores inside it`() {
        // HAPPY scores 4, GOOD scores 3.
        val stats = useCase(
            entries = emptyList(),
            moods = listOf(
                mood("m1", Mood.HAPPY, LocalDate(2024, 5, 9)),
                mood("m2", Mood.GOOD, LocalDate(2024, 5, 9)),
            ),
            today = today,
            range = TimeRange.Last7Days,
        )

        assertEquals(3.5, stats.trend.first().averageMood)
        assertEquals(2, stats.trend.first().moodCount)
    }

    @Test
    fun `soft-deleted and out-of-window records are excluded`() {
        val stats = useCase(
            entries = listOf(
                entry("keep", LocalDate(2024, 5, 10)),
                entry("tooOld", LocalDate(2024, 5, 1)),
                entry("deleted", LocalDate(2024, 5, 11)).copy(
                    deletedAt = Instant.parse("2024-05-12T00:00:00Z"),
                ),
                entry("pendingDelete", LocalDate(2024, 5, 12)).copy(
                    syncStatus = SyncStatus.PENDING_DELETE,
                ),
            ),
            moods = listOf(
                mood("keep", Mood.HAPPY, LocalDate(2024, 5, 10)),
                // The excluded rows are deliberately the most extreme mood in the set, so
                // a leak cannot hide behind an identical average: it would show up as a
                // lower mean and a non-null lowest.
                mood("deleted", Mood.VERY_SAD, LocalDate(2024, 5, 11)).copy(
                    deletedAt = Instant.parse("2024-05-12T00:00:00Z"),
                ),
                mood("tooOld", Mood.VERY_SAD, LocalDate(2024, 4, 30)),
            ),
            today = today,
            range = TimeRange.Last7Days,
        )

        assertEquals(1, stats.totalEntries)
        assertEquals(1, stats.totalMoodRecords)
        assertEquals(4.0, stats.mood.averageMood)
        // The only visible record is the HAPPY one, so best and worst are both 4.0. A
        // leaked VERY_SAD (score 0) would drag the worst day to 0.0.
        assertEquals(4.0, stats.mood.extremes.lowest?.averageScore)
        assertEquals(4.0, stats.mood.extremes.highest?.averageScore)
        assertEquals(Mood.HAPPY, stats.mood.mostCommonMood)
    }

    @Test
    fun `weekly buckets group a whole week together`() {
        val stats = useCase(
            entries = listOf(
                entry("mon", LocalDate(2024, 5, 13)),
                entry("wed", LocalDate(2024, 5, 15)),
                // Previous week, so a different bucket.
                entry("prevSun", LocalDate(2024, 5, 12)),
            ),
            moods = emptyList(),
            today = today,
            range = TimeRange.Last12Weeks,
        )

        assertEquals(12, stats.trend.size)
        assertEquals(1, stats.trend[10].entryCount)
        assertEquals(2, stats.trend[11].entryCount)
    }

    @Test
    fun `monthly buckets group a whole month together`() {
        val stats = useCase(
            entries = listOf(
                entry("may1", LocalDate(2024, 5, 1)),
                entry("may15", LocalDate(2024, 5, 15)),
                entry("april", LocalDate(2024, 4, 20)),
            ),
            moods = emptyList(),
            today = today,
            range = TimeRange.Last12Months,
        )

        assertEquals(12, stats.trend.size)
        assertEquals(1, stats.trend[10].entryCount)
        assertEquals(2, stats.trend[11].entryCount)
        assertEquals(3, stats.totalEntries)
    }

    @Test
    fun `every bucket end lines up with the next bucket start so nothing is double counted`() {
        val entries = (0 until 40).map {
            entry("e$it", LocalDates.plusDays(LocalDate(2024, 4, 20), it % 26))
        }
        val stats = useCase(entries, emptyList(), today, TimeRange.Last30Days)

        assertEquals(30, stats.trend.size)
        // Each record is counted by exactly one bucket, so the totals must agree.
        assertEquals(entries.size, stats.totalEntries)
        assertEquals(entries.size, stats.trend.sumOf { it.entryCount })
    }

    @Test
    fun `mood statistics are reused for the donut and the emotion bars`() {
        val stats = useCase(
            entries = emptyList(),
            moods = listOf(
                mood("m1", Mood.HAPPY, LocalDate(2024, 5, 9)),
                mood("m2", Mood.HAPPY, LocalDate(2024, 5, 10)),
                mood("m3", Mood.SAD, LocalDate(2024, 5, 11)),
            ),
            today = today,
            range = TimeRange.Last7Days,
        )

        assertEquals(3, stats.mood.totalRecords)
        assertEquals(2, stats.mood.distribution.size)
        assertEquals(3.0, stats.mood.averageMood)
        assertEquals(Mood.HAPPY, stats.mood.mostCommonMood)
        // The donut must be able to fill a full circle.
        assertEquals(1.0, stats.mood.distribution.sumOf { it.ratio })
    }

    @Test
    fun `mood gaps and entry gaps are counted separately`() {
        // Moods on the 9th, 10th and 11th; an entry only on the 9th. The window is seven
        // days, so the trend line has four gaps while the frequency chart has six empty
        // columns. A single "recorded nothing" counter would report one number for two
        // visibly different charts.
        val stats = useCase(
            entries = listOf(entry("a", LocalDate(2024, 5, 9))),
            moods = listOf(
                mood("m1", Mood.HAPPY, LocalDate(2024, 5, 9)),
                mood("m2", Mood.HAPPY, LocalDate(2024, 5, 10)),
                mood("m3", Mood.HAPPY, LocalDate(2024, 5, 11)),
            ),
            today = today,
            range = TimeRange.Last7Days,
        )

        assertEquals(4, stats.bucketsWithoutMood)
        assertEquals(6, stats.bucketsWithoutEntries)
        // The only bucket with an entry also has a mood, so every mood gap is also a
        // bucket with neither kind of record.
        assertEquals(4, stats.trend.count { it.averageMood == null && it.entryCount == 0 })
        assertTrue(stats.hasData)
    }

    @Test
    fun `the busiest bucket is reported for the summary line`() {
        val stats = useCase(
            entries = listOf(
                entry("a", LocalDate(2024, 5, 12)),
                entry("b", LocalDate(2024, 5, 12)),
                entry("c", LocalDate(2024, 5, 12)),
                entry("d", LocalDate(2024, 5, 14)),
            ),
            moods = emptyList(),
            today = today,
            range = TimeRange.Last7Days,
        )

        val busiest = assertNotNull(stats.busiestBucket)
        assertEquals(3, busiest.entryCount)
        assertEquals(LocalDate(2024, 5, 12), busiest.bucketStart)
    }
}
