package com.arjun.soul_note.domain.usecase.stats

import com.arjun.soul_note.core.datetime.LocalDates
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.domain.model.Streak
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * Consecutive-day journaling streak.
 *
 * A day counts when the user wrote a diary entry **or** recorded a mood. The rule is
 * deliberately forgiving: today not being journaled yet must not zero out a live streak,
 * so the walk starts at `today` and immediately falls back to yesterday. Missing days
 * break the chain but never reset the all-time best.
 *
 * Pure function — no clock, no I/O — which is what makes the boundary cases (empty
 * history, single day, gaps, streaks ending in the past) exhaustively testable.
 */
class CalculateStreakUseCase {

    operator fun invoke(
        diaryEntries: List<DiaryEntry>,
        moodEntries: List<MoodEntry>,
        today: LocalDate,
        /** How far back the activity heat-map is drawn. */
        historyMonths: Int = 12,
    ): Streak {
        val activity = LinkedHashMap<LocalDate, Int>()

        diaryEntries.asSequence()
            .filter { it.isVisible() }
            .forEach { entry -> activity[entry.entryDate] = (activity[entry.entryDate] ?: 0) + 1 }

        moodEntries.asSequence()
            .filter { it.isVisible() }
            .forEach { entry -> activity[entry.date] = (activity[entry.date] ?: 0) + 1 }

        if (activity.isEmpty()) return Streak.None

        // The walk only ever moves backwards, so future-dated rows cannot inflate it.
        val anchor = if (activity.containsKey(today)) today else LocalDates.minusDays(today, 1)

        var current = 0
        var cursor = anchor
        while (activity.containsKey(cursor) && current < MAX_STREAK) {
            current++
            cursor = LocalDates.minusDays(cursor, 1)
        }

        // Longest run anywhere in history, computed by a single forward walk over the
        // sorted active days. `current` is included here, so the result is >= current.
        val sortedActiveDays = activity.keys.sorted()
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in sortedActiveDays) {
            val prior = previous
            run = if (prior != null && LocalDates.daysBetween(prior, date) == 1) run + 1 else 1
            if (run > longest) longest = run
            previous = date
        }
        longest = longest.coerceAtLeast(current)

        val windowStart = LocalDates.monthStart(today).let { firstOfMonthMinusMonths(it, historyMonths) }
        val activityByDate = activity.filterKeys { it in windowStart..today }

        return Streak(
            current = current,
            longest = longest,
            activityByDate = activityByDate,
            totalActiveDays = activityByDate.size,
        )
    }

    private fun firstOfMonthMinusMonths(date: LocalDate, months: Int): LocalDate {
        val targetMonthIndex = date.year * 12 + (date.month.number - 1) - months
        val year = targetMonthIndex / 12
        val month = (targetMonthIndex % 12 + 12) % 12 + 1
        return LocalDate(year, month, 1)
    }

    private companion object {
        /** 100 years of days: a safety valve that cannot be reached by a real user. */
        const val MAX_STREAK = 36_600
    }
}
