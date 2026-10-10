package com.arjun.mooary.data.repository

import com.arjun.mooary.core.common.DispatcherProvider
import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.core.datetime.TimeProvider
import com.arjun.mooary.core.error.suspendRunCatchingApp
import com.arjun.mooary.core.validation.Limits
import com.arjun.mooary.data.local.LocalStore
import com.arjun.mooary.domain.model.DayMoodSummary
import com.arjun.mooary.domain.model.MoodEntry
import com.arjun.mooary.domain.model.MoodFilter
import com.arjun.mooary.domain.model.Page
import com.arjun.mooary.domain.model.SyncStatus
import com.arjun.mooary.domain.model.windowedPage
import com.arjun.mooary.domain.repository.MoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Mood repository.
 *
 * Local reads and local writes. A user may log several moods on one day, so nothing
 * here assumes `date` is unique.
 */
class DefaultMoodRepository(
    private val local: LocalStore,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : MoodRepository {

    override fun observeEntries(userId: String): Flow<List<MoodEntry>> =
        local.observeVisibleMoods(userId).distinctUntilChanged()

    override fun observeFilteredEntries(userId: String, filter: MoodFilter): Flow<List<MoodEntry>> =
        local.observeVisibleMoods(userId)
            .map { entries -> applyFilter(entries, filter) }

    /**
     * Pages mood history, applying [filter] rather than ignoring it.
     *
     * Mirrors [DefaultDiaryRepository.observeEntriesPage]: an unfiltered read is served
     * straight from SQL with `LIMIT/OFFSET` and a real count, while a filtered read filters
     * and sorts in Kotlin and then slices the window, so the count can never disagree with
     * the rows on screen.
     */
    override fun observeEntriesPage(
        userId: String,
        filter: MoodFilter,
        limit: Int,
        offset: Int,
    ): Flow<Page<MoodEntry>> {
        val pageLimit = limit.coerceIn(Limits.PAGE_MIN_LIMIT, Limits.PAGE_MAX_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)

        if (!filter.hasActiveFilters) {
            return combine(
                local.observeMoodPage(userId, pageLimit + 1, safeOffset),
                local.observeMoodCount(userId),
            ) { rows, total -> Page.fromOverfetched(rows, pageLimit, total) }
        }

        return local.observeVisibleMoods(userId)
            .map { entries -> applyFilter(entries, filter) }
            .map { all -> all.windowedPage(pageLimit, safeOffset) }
    }

    override fun observeEntry(userId: String, moodId: String): Flow<MoodEntry?> =
        local.observeMoodById(userId, moodId)

    override fun observeRecentEntries(userId: String, limit: Int): Flow<List<MoodEntry>> =
        local.observeRecentMoods(userId, limit).distinctUntilChanged()

    override fun observeEntriesForDate(userId: String, date: LocalDate): Flow<List<MoodEntry>> =
        local.observeMoodsForDate(userId, date).distinctUntilChanged()

    override fun observeDaySummaries(userId: String): Flow<List<DayMoodSummary>> =
        local.observeDaySummaries(userId).distinctUntilChanged()

    override fun observeEntriesInRange(userId: String, from: LocalDate, to: LocalDate): Flow<List<MoodEntry>> =
        local.observeMoodsInRange(userId, from, to).distinctUntilChanged()

    override fun observeCount(userId: String): Flow<Int> =
        local.observeMoodCount(userId).distinctUntilChanged()

    override suspend fun getEntry(userId: String, moodId: String): Result<MoodEntry?> =
        suspendRunCatchingApp { local.getMood(userId, moodId) }

    override suspend fun recordMood(entry: MoodEntry): Result<MoodEntry> = suspendRunCatchingApp {
        val stored = entry.copy(syncStatus = SyncStatus.SYNCED)
        local.upsertMood(stored)
        stored
    }

    override suspend fun updateMood(entry: MoodEntry): Result<MoodEntry> = suspendRunCatchingApp {
        val stored = entry.copy(
            updatedAt = timeProvider.now(),
            syncStatus = SyncStatus.SYNCED,
        )
        local.upsertMood(stored)
        stored
    }

    override suspend fun deleteMood(userId: String, moodId: String): Result<Unit> = suspendRunCatchingApp {
        local.tombstoneMood(userId, moodId, timeProvider.now(), SyncStatus.SYNCED)
    }

    override suspend fun getAllForExport(userId: String): Result<List<MoodEntry>> = suspendRunCatchingApp {
        local.observeVisibleMoods(userId).first().take(Limits.MAX_EXPORT_ENTRIES)
    }

    override suspend fun getAllInRange(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): Result<List<MoodEntry>> = suspendRunCatchingApp { local.getMoodsInRange(userId, from, to) }

    private fun applyFilter(entries: List<MoodEntry>, filter: MoodFilter): List<MoodEntry> {
        if (!filter.hasActiveFilters) return entries
        val today = LocalDates.today(timeProvider.now(), timeZone)
        val weekStart = LocalDates.weekStart(today)
        return entries.filter { filter.matches(it, today, weekStart) }
    }

}
