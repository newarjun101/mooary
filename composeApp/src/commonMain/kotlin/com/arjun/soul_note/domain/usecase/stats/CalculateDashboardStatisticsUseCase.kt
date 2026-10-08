package com.arjun.soul_note.domain.usecase.stats

import com.arjun.soul_note.domain.model.DashboardStatistics
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.domain.model.TimeRange
import com.arjun.soul_note.domain.model.TrendPoint
import kotlinx.datetime.LocalDate

/**
 * Builds the dashboard's chart payload for a [TimeRange].
 *
 * Like [CalculateMoodStatisticsUseCase] this is a pure function over already-loaded rows,
 * so the bucketing that every chart depends on is deterministic and testable without a
 * database or a clock. The repository loads the window; this class does the maths.
 *
 * Rows outside the window and rows that are soft-deleted are dropped here rather than in
 * the query, so a caller that over-fetches still gets a correct chart.
 */
class CalculateDashboardStatisticsUseCase(
    private val calculateMoodStatistics: CalculateMoodStatisticsUseCase,
) {

    operator fun invoke(
        entries: List<DiaryEntry>,
        moods: List<MoodEntry>,
        today: LocalDate,
        range: TimeRange,
    ): DashboardStatistics {
        val window = range.window(today)

        val visibleEntries = entries.filter { it.isVisible() && it.entryDate in window }
        val visibleMoods = moods.filter { it.isVisible() && it.date in window }

        if (visibleEntries.isEmpty() && visibleMoods.isEmpty()) {
            return DashboardStatistics.empty(range, today)
        }

        val trend = range.bucketStarts(today).mapIndexed { index, bucketStart ->
            val bounds = range.bucketBounds(index, today)
            val bucketEntries = visibleEntries.filter { it.entryDate in bounds }
            val bucketMoods = visibleMoods.filter { it.date in bounds }
            TrendPoint(
                bucketStart = bucketStart,
                averageMood = bucketMoods
                    .takeIf { it.isNotEmpty() }
                    ?.let { records -> records.sumOf { it.mood.score }.toDouble() / records.size },
                entryCount = bucketEntries.size,
                moodCount = bucketMoods.size,
            )
        }

        return DashboardStatistics(
            range = range,
            // Reuse the existing, already-tested mood analytics for distribution,
            // top emotions and extremes; only the per-bucket series is new here.
            mood = calculateMoodStatistics(moods, range.windowStart(today), today),
            trend = trend,
        )
    }
}
