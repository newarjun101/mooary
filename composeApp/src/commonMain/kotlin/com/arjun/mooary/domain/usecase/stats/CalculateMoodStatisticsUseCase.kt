package com.arjun.mooary.domain.usecase.stats

import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.domain.model.DailyMoodAverage
import com.arjun.mooary.domain.model.EmotionShare
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.MoodDistribution
import com.arjun.mooary.domain.model.MoodEntry
import com.arjun.mooary.domain.model.MoodExtremes
import com.arjun.mooary.domain.model.MoodStatistics
import kotlinx.datetime.LocalDate

/**
 * Mood analytics for an arbitrary inclusive day range.
 *
 * Deliberately a pure function over already-loaded records: the repository hands over the
 * rows for the period (a bounded range, not the whole table) and this class does the
 * maths. That keeps the hardest-to-get-right logic — averaging, rounding, tie-breaking,
 * empty periods — completely deterministic and unit-testable.
 */
class CalculateMoodStatisticsUseCase {

    operator fun invoke(
        entries: List<MoodEntry>,
        from: LocalDate,
        to: LocalDate,
        /** How many emotion rows the "most common emotions" list shows. */
        topEmotionCount: Int = 5,
    ): MoodStatistics {
        val visible = entries
            .filter { it.isVisible() && it.date in from..to }
            .sortedWith(compareBy({ it.date }, { it.createdAt }))

        if (visible.isEmpty()) return MoodStatistics.empty(from, to)

        val average = visible.sumOf { it.mood.score }.toDouble() / visible.size

        val distribution = Mood.entries
            .map { mood -> mood to visible.count { it.mood == mood } }
            .filter { (_, count) -> count > 0 }
            .sortedWith(
                compareByDescending<Pair<Mood, Int>> { it.second }
                    .thenByDescending { it.first.score }
                    .thenBy { it.first.ordinal },
            )
            .map { (mood, count) ->
                MoodDistribution(
                    mood = mood,
                    count = count,
                    ratio = count.toDouble() / visible.size,
                )
            }

        val topEmotions = visible
            .flatMap { it.emotions }
            .groupingBy { it.id }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(topEmotionCount)
            .map { (id, count) ->
                EmotionShare(
                    emotion = com.arjun.mooary.domain.model.Emotion.fromId(id),
                    count = count,
                    ratio = count.toDouble() / visible.size,
                )
            }

        val dailyAverages = visible
            .groupBy { it.date }
            .map { (date, dayEntries) ->
                DailyMoodAverage(
                    date = date,
                    averageScore = dayEntries.sumOf { it.mood.score }.toDouble() / dayEntries.size,
                    recordCount = dayEntries.size,
                    dominantMood = dayEntries.maxByOrNull { it.createdAt }?.mood,
                )
            }
            .sortedBy { it.date }

        val withData = dailyAverages.filter { it.averageScore != null }
        val highest = withData.maxByOrNull { it.averageScore!! }
        val lowest = withData.minByOrNull { it.averageScore!! }

        val mostCommon = distribution.maxByOrNull { it.count }?.mood

        return MoodStatistics(
            from = from,
            to = to,
            totalRecords = visible.size,
            averageMood = average,
            distribution = distribution,
            topEmotions = topEmotions,
            dailyAverages = dailyAverages,
            extremes = MoodExtremes(highest, lowest),
            mostCommonMood = mostCommon,
        )
    }

    /** Seven-day window ending on [today], inclusive. */
    fun lastWeek(entries: List<MoodEntry>, today: LocalDate): MoodStatistics =
        invoke(entries, LocalDates.minusDays(today, 6), today)

    /** The calendar month containing [today]. */
    fun currentMonth(entries: List<MoodEntry>, today: LocalDate): MoodStatistics =
        invoke(entries, LocalDates.monthStart(today), LocalDates.monthEnd(today))
}
