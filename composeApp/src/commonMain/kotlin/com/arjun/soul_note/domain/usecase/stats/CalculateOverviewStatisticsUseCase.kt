package com.arjun.soul_note.domain.usecase.stats

import com.arjun.soul_note.core.datetime.LocalDates
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.domain.model.MoodStatistics
import com.arjun.soul_note.domain.model.OverviewStatistics
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * The "Your September" summary, assembled from data the ViewModels already hold.
 *
 * Taking the records as parameters (rather than re-querying) means Home renders from a
 * single local snapshot with no extra database round-trips, and the whole composition is
 * one pure, testable function.
 */
class CalculateOverviewStatisticsUseCase(
    private val calculateStreak: CalculateStreakUseCase,
) {
    operator fun invoke(
        diaryEntries: List<DiaryEntry>,
        moodEntries: List<MoodEntry>,
        today: LocalDate,
        moodStatistics: MoodStatistics,
    ): OverviewStatistics {
        val visibleDiary = diaryEntries.filter { it.isVisible() }
        val visibleMoods = moodEntries.filter { it.isVisible() }

        val streak = calculateStreak(visibleDiary, visibleMoods, today)
        val weekStart = LocalDates.weekStart(today)
        val monthStart = LocalDates.monthStart(today)

        return OverviewStatistics(
            periodLabel = monthStart.year.toString() + "-" + monthStart.month.number.toString().padStart(2, '0'),
            totalDiaryEntries = visibleDiary.count { it.entryDate in monthStart..today },
            totalMoodRecords = visibleMoods.count { it.date in monthStart..today },
            currentStreak = streak.current,
            longestStreak = streak.longest,
            averageMood = moodStatistics.averageMood,
            mostCommonMood = moodStatistics.mostCommonMood,
            favoriteCount = visibleDiary.count { it.isFavorite },
            entriesThisWeek = visibleDiary.count { it.entryDate in weekStart..today },
            entriesThisMonth = visibleDiary.count { it.entryDate in monthStart..today },
            moodRecordsThisMonth = visibleMoods.count { it.date in monthStart..today },
            mostProductiveWeekday = mostProductiveWeekday(visibleDiary),
        )
    }

    /** The weekday with the most entries; a tie resolves to the earlier weekday. */
    internal fun mostProductiveWeekday(entries: List<DiaryEntry>): String? {
        if (entries.isEmpty()) return null
        val counts = entries.groupingBy { it.entryDate.dayOfWeek.ordinal }.eachCount()
        val best = counts.maxByOrNull { it.value }?.key ?: return null
        return DAY_NAMES.getOrNull(best)
    }

    private companion object {
        val DAY_NAMES = listOf(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        )
    }
}
