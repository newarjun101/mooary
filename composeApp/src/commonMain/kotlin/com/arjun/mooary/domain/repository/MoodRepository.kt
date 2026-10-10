package com.arjun.mooary.domain.repository

import com.arjun.mooary.domain.model.DayMoodSummary
import com.arjun.mooary.domain.model.MoodEntry
import com.arjun.mooary.domain.model.MoodFilter
import com.arjun.mooary.domain.model.Page
import kotlinx.coroutines.flow.Flow

/** Mood check-ins. Same offline-first contract as [DiaryRepository]. */
interface MoodRepository {

    fun observeEntries(userId: String): Flow<List<MoodEntry>>

    fun observeFilteredEntries(userId: String, filter: MoodFilter): Flow<List<MoodEntry>>

    /**
     * A page of check-ins matching [filter], newest first.
     *
     * [offset] is the number of rows already consumed, so pages concatenate into the full
     * result set when walked in order. The returned [Page.totalCount] counts every match,
     * not just this window.
     */
    fun observeEntriesPage(
        userId: String,
        filter: MoodFilter,
        limit: Int,
        offset: Int,
    ): Flow<Page<MoodEntry>>

    fun observeEntry(userId: String, moodId: String): Flow<MoodEntry?>

    /** Newest first, bounded by [limit] — this is the home screen's "recent moods". */
    fun observeRecentEntries(userId: String, limit: Int): Flow<List<MoodEntry>>

    fun observeEntriesForDate(userId: String, date: kotlinx.datetime.LocalDate): Flow<List<MoodEntry>>

    /** Per-day roll-up for the calendar grid. */
    fun observeDaySummaries(userId: String): Flow<List<DayMoodSummary>>

    /** Entries falling inside an inclusive day range. */
    fun observeEntriesInRange(
        userId: String,
        from: kotlinx.datetime.LocalDate,
        to: kotlinx.datetime.LocalDate,
    ): Flow<List<MoodEntry>>

    fun observeCount(userId: String): Flow<Int>

    suspend fun getEntry(userId: String, moodId: String): Result<MoodEntry?>

    suspend fun recordMood(entry: MoodEntry): Result<MoodEntry>

    suspend fun updateMood(entry: MoodEntry): Result<MoodEntry>

    suspend fun deleteMood(userId: String, moodId: String): Result<Unit>

    suspend fun getAllForExport(userId: String): Result<List<MoodEntry>>

    suspend fun getAllInRange(
        userId: String,
        from: kotlinx.datetime.LocalDate,
        to: kotlinx.datetime.LocalDate,
    ): Result<List<MoodEntry>>
}
