package com.arjun.mooary.domain.model

import kotlinx.datetime.LocalDate

/** A single bucket of the mood distribution chart. */
data class MoodDistribution(
    val mood: Mood,
    val count: Int,
    val ratio: Double,
)

/** Average mood for one day, used by the weekly/monthly trend charts. */
data class DailyMoodAverage(
    val date: LocalDate,
    val averageScore: Double?,
    val recordCount: Int,
    val dominantMood: Mood?,
)

/** The emotion the user felt most often, with its share of all logged emotions. */
data class EmotionShare(
    val emotion: Emotion,
    val count: Int,
    val ratio: Double,
)

/** The best and worst days in a period, for the "highlight" cards. */
data class MoodExtremes(
    val highest: DailyMoodAverage?,
    val lowest: DailyMoodAverage?,
)

/** Consecutive-day journaling streak. */
data class Streak(
    val current: Int,
    val longest: Int,
    /** Per-day activity for the trailing 12 months, for the heat-map. */
    val activityByDate: Map<LocalDate, Int>,
    val totalActiveDays: Int,
) {
    companion object {
        val None = Streak(0, 0, emptyMap(), 0)
    }
}

/** The complete mood analytics payload for a period. */
data class MoodStatistics(
    val from: LocalDate,
    val to: LocalDate,
    val totalRecords: Int,
    val averageMood: Double?,
    val distribution: List<MoodDistribution>,
    val topEmotions: List<EmotionShare>,
    val dailyAverages: List<DailyMoodAverage>,
    val extremes: MoodExtremes,
    val mostCommonMood: Mood?,
) {
    val hasData: Boolean get() = totalRecords > 0

    /** Rounded, or `null` when there is nothing to average. */
    val displayAverage: Double? get() = averageMood?.let { kotlin.math.round(it * 10) / 10.0 }

    companion object {
        fun empty(from: LocalDate, to: LocalDate) = MoodStatistics(
            from = from,
            to = to,
            totalRecords = 0,
            averageMood = null,
            distribution = emptyList(),
            topEmotions = emptyList(),
            dailyAverages = emptyList(),
            extremes = MoodExtremes(null, null),
            mostCommonMood = null,
        )
    }
}

/** The "Your September" style summary shown on Home and Profile. */
data class OverviewStatistics(
    val periodLabel: String,
    val totalDiaryEntries: Int,
    val totalMoodRecords: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val averageMood: Double?,
    val mostCommonMood: Mood?,
    val favoriteCount: Int,
    val entriesThisWeek: Int,
    val entriesThisMonth: Int,
    val moodRecordsThisMonth: Int,
    val mostProductiveWeekday: String?,
)

/** A calendar cell: the day, whether anything was recorded, and how it felt. */
data class CalendarDay(
    val date: LocalDate,
    val isInMonth: Boolean,
    val diaryEntryCount: Int,
    val moodSummary: DayMoodSummary?,
    val isToday: Boolean,
    val isFuture: Boolean,
) {
    val hasContent: Boolean get() = diaryEntryCount > 0 || (moodSummary?.count ?: 0) > 0
    val mood: Mood? get() = moodSummary?.dominantMood
}
