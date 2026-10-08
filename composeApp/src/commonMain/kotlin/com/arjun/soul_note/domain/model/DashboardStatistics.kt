package com.arjun.soul_note.domain.model

import kotlinx.datetime.LocalDate

/**
 * One bucket on the dashboard charts: a slice of time and everything recorded in it.
 *
 * [averageMood] is `null` for a bucket with no mood records rather than `0`, because a
 * bucket with no data and a bucket where the user felt neutral are very different things.
 * The trend chart draws a gap for the former instead of dropping to the floor.
 */
data class TrendPoint(
    val bucketStart: LocalDate,
    val averageMood: Double?,
    val entryCount: Int,
    val moodCount: Int,
) {
    val isEmpty: Boolean get() = averageMood == null && entryCount == 0
}

/**
 * Everything the dashboard charts need for one [range].
 *
 * [mood] carries the distribution, top emotions and extremes, reusing
 * [MoodStatistics] rather than re-deriving them. [trend] is the per-bucket series, which
 * [MoodStatistics.dailyAverages] cannot provide: that list only contains days that have
 * records, so it silently omits the gaps a line chart needs to draw honestly.
 */
data class DashboardStatistics(
    val range: TimeRange,
    val mood: MoodStatistics,
    val trend: List<TrendPoint>,
) {
    /** Diary entries inside the window. */
    val totalEntries: Int get() = trend.sumOf { it.entryCount }

    /** Mood records inside the window. */
    val totalMoodRecords: Int get() = mood.totalRecords

    /**
     * Buckets with no mood record, which are the gaps the trend line breaks over.
     *
     * Deliberately not the same as "recorded nothing": a bucket can hold a diary entry but
     * no mood, and for the mood chart that still reads as a gap. Conflating the two made a
     * chart with 14 visible breaks claim only 3 were missing.
     */
    val bucketsWithoutMood: Int get() = trend.count { it.averageMood == null }

    /** Buckets with no diary entry, which are the empty columns in the frequency chart. */
    val bucketsWithoutEntries: Int get() = trend.count { it.entryCount == 0 }

    /** `true` only when the window holds neither an entry nor a mood record. */
    val hasData: Boolean get() = totalEntries > 0 || mood.hasData

    val busiestBucket: TrendPoint? get() = trend.maxByOrNull { it.entryCount }

    companion object {
        fun empty(range: TimeRange, today: kotlinx.datetime.LocalDate): DashboardStatistics {
            val starts = range.bucketStarts(today)
            return DashboardStatistics(
                range = range,
                mood = MoodStatistics.empty(range.windowStart(today), today),
                trend = starts.map { TrendPoint(it, null, 0, 0) },
            )
        }
    }
}
