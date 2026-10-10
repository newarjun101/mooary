package com.arjun.mooary.data.repository

import com.arjun.mooary.core.common.DispatcherProvider
import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.core.datetime.TimeProvider
import com.arjun.mooary.core.error.suspendRunCatchingApp
import com.arjun.mooary.core.validation.Limits
import com.arjun.mooary.data.local.LocalStore
import com.arjun.mooary.domain.model.DiaryEntry
import com.arjun.mooary.domain.model.DiaryFilter
import com.arjun.mooary.domain.model.DiarySortOrder
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.MoodEntry
import com.arjun.mooary.domain.model.Page
import com.arjun.mooary.domain.model.SyncStatus
import com.arjun.mooary.domain.model.UserProfile
import com.arjun.mooary.domain.model.windowedPage
import com.arjun.mooary.domain.repository.DiaryRepository
import com.arjun.mooary.domain.repository.MoodRepository
import com.arjun.mooary.domain.repository.UserRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Diary repository.
 *
 * Reads come from the local database and emit on every change. Writes are applied locally
 * first. This app intentionally keeps diary content on this device, so no write starts a
 * background network operation.
 */
class DefaultDiaryRepository(
    private val local: LocalStore,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : DiaryRepository {


    override fun observeEntries(userId: String): Flow<List<DiaryEntry>> =
        local.observeVisibleDiary(userId).distinctUntilChanged()

    override fun observeFilteredEntries(userId: String, filter: DiaryFilter): Flow<List<DiaryEntry>> =
        combine(
            local.observeVisibleDiary(userId),
            local.observeAllTags(userId),
        ) { entries, _ -> entries }
            .map { entries -> applyFilter(entries, filter) }

    /**
     * Pages the diary list, applying [filter] rather than ignoring it.
     *
     * Two paths, because the two cases have genuinely different costs:
     *
     *  * **Unfiltered and in default order** — read straight from SQL with `LIMIT/OFFSET` via
     *    [com.arjun.mooary.data.local.db.Diary_entries_user_date], so only the requested
     *    window is read no matter how long the journal is.
     *  * **Filtered** — read the matching set, filter and sort it with the existing
     *    [DiaryFilter.matches]/[DiaryFilter.sort], then slice the window in Kotlin.
     *
     * The filtered path could be pushed into SQL, but all six filter dimensions would then
     * need expressing there, and the same predicates would have to be repeated in the count
     * query — the classic place for "12 of 40" to quietly become "12 of 12". Filtering in
     * Kotlin keeps one definition of "what matches", and a journal is a local SQLite table
     * where the full read is cheap; the win from paging is that the list composes a
     * window's worth of rows instead of all of them.
     */
    override fun observeEntriesPage(
        userId: String,
        filter: DiaryFilter,
        limit: Int,
        offset: Int,
    ): Flow<Page<DiaryEntry>> {
        val pageLimit = limit.coerceIn(Limits.PAGE_MIN_LIMIT, Limits.PAGE_MAX_LIMIT)
        val safeOffset = offset.coerceAtLeast(0)

        if (canPageInSql(filter)) {
            // One row more than requested is the whole trick behind an exact hasMore: it
            // stays correct when the rows remaining happen to be an exact multiple of the
            // page size, where an `items.size == limit` test invents a phantom final page.
            return combine(
                local.observeDiaryPage(userId, pageLimit + 1, safeOffset),
                local.observeDiaryCount(userId),
            ) { rows, total -> Page.fromOverfetched(rows, pageLimit, total) }
        }

        return local.observeVisibleDiary(userId)
            .map { entries -> applyFilter(entries, filter) }
            .map { all -> all.windowedPage(pageLimit, safeOffset) }
    }

    /**
     * Whether [filter] can be honoured by the `selectVisiblePage` statement as written.
     *
     * That query hard-codes `ORDER BY entry_date DESC, created_at DESC`, which is exactly
     * [DiarySortOrder.NEWEST_ENTRY_DATE] — so a different sort must go through the Kotlin
     * path or the window would be ordered differently from the full set it came from.
     */
    private fun canPageInSql(filter: DiaryFilter): Boolean =
        !filter.hasActiveFilters && filter.sort == DiarySortOrder.NEWEST_ENTRY_DATE

    override fun observeEntry(userId: String, entryId: String): Flow<DiaryEntry?> =
        local.observeDiaryById(userId, entryId)

    override fun observeFavorites(userId: String): Flow<List<DiaryEntry>> =
        local.observeFavorites(userId).distinctUntilChanged()

    override fun observeRecentEntries(userId: String, limit: Int): Flow<List<DiaryEntry>> =
        local.observeRecentDiary(userId, limit).distinctUntilChanged()

    override fun observeEntriesForDate(userId: String, date: LocalDate): Flow<List<DiaryEntry>> =
        local.observeDiaryForDate(userId, date).distinctUntilChanged()

    override fun observeCount(userId: String): Flow<Int> =
        local.observeDiaryCount(userId).distinctUntilChanged()

    override suspend fun getEntry(userId: String, entryId: String): Result<DiaryEntry?> =
        suspendRunCatchingApp { local.getDiary(userId, entryId) }

    override fun observeAllTags(userId: String): Flow<List<String>> =
        local.observeAllTags(userId).distinctUntilChanged()


    override suspend fun createEntry(entry: DiaryEntry): Result<DiaryEntry> = suspendRunCatchingApp {
        val stored = entry.copy(syncStatus = SyncStatus.SYNCED)
        local.upsertDiary(stored)
        stored
    }

    override suspend fun updateEntry(entry: DiaryEntry): Result<DiaryEntry> = suspendRunCatchingApp {
        val stored = entry.copy(
            updatedAt = timeProvider.now(),
            syncStatus = SyncStatus.SYNCED,
        )
        local.upsertDiary(stored)
        stored
    }

    /**
     * Soft-deletes locally so an accidental removal can still be restored in this process.
     */
    override suspend fun deleteEntry(userId: String, entryId: String): Result<Unit> = suspendRunCatchingApp {
        local.tombstoneDiary(userId, entryId, timeProvider.now(), SyncStatus.SYNCED)
    }

    override suspend fun restoreEntry(userId: String, entryId: String): Result<Unit> = suspendRunCatchingApp {
        val existing = local.getDiary(userId, entryId)
            ?: return@suspendRunCatchingApp
        val restored = existing.copy(
            deletedAt = null,
            updatedAt = timeProvider.now(),
            syncStatus = SyncStatus.SYNCED,
        )
        local.upsertDiary(restored)
    }

    override suspend fun setFavorite(
        userId: String,
        entryId: String,
        isFavorite: Boolean,
    ): Result<Unit> = suspendRunCatchingApp {
        local.setDiaryFavorite(
            userId = userId,
            entryId = entryId,
            isFavorite = isFavorite,
            updatedAt = timeProvider.now(),
            syncStatus = SyncStatus.SYNCED,
        )
    }

    /**
     * Search runs against the local cache, so it is instant and works offline. The
     * `limit` keeps the result set bounded; a dedicated server-side index is the
     * documented seam for a much larger journal.
     */
    override suspend fun search(userId: String, query: String, limit: Int): Result<List<DiaryEntry>> =
        suspendRunCatchingApp {
            val normalised = query.trim()
            if (normalised.length < Limits.SEARCH_MIN_QUERY_LENGTH) return@suspendRunCatchingApp emptyList()
            local.searchDiary(userId, normalised, limit.coerceIn(1, Limits.SEARCH_PAGE_SIZE))
        }

    /**
     * Export reads the local cache, so it works offline and never uploads anything.
     * `first()` takes the current snapshot and cancels the query immediately, which is
     * what stops a long-lived Flow from being leaked by a one-shot call.
     */
    override suspend fun getAllForExport(userId: String): Result<List<DiaryEntry>> = suspendRunCatchingApp {
        local.observeVisibleDiary(userId)
            .first()
            .take(Limits.MAX_EXPORT_ENTRIES)
    }


    /**
     * Filtering happens in memory over already-cached rows, so a filter change costs no
     * query and no network call — which is what makes the debounced search feel instant.
     */
    private fun applyFilter(entries: List<DiaryEntry>, filter: DiaryFilter): List<DiaryEntry> {
        // The early return is only safe when the requested order is the order the rows
        // already arrived in. Returning early on "no active filters" alone silently dropped
        // a non-default sort, so the sort is checked independently of the filtering.
        val needsSort = filter.sort != DiarySortOrder.NEWEST_ENTRY_DATE
        if (!filter.hasActiveFilters && !needsSort) return entries
        val today = LocalDates.today(timeProvider.now(), timeZone)
        val weekStart = LocalDates.weekStart(today)
        val filtered =
            if (filter.hasActiveFilters) entries.filter { filter.matches(it, today, weekStart) } else entries
        return filter.sort(filtered)
    }

}
