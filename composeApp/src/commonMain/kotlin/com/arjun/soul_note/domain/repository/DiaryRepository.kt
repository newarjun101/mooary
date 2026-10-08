package com.arjun.soul_note.domain.repository

import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.DiaryFilter
import com.arjun.soul_note.domain.model.Page
import kotlinx.coroutines.flow.Flow

/**
 * Diary entries.
 *
 * Every read resolves from the local database, so the UI renders instantly and works
 * with no network at all. Writes land locally first and are reconciled by the sync
 * manager; no method here blocks on Firestore.
 */
interface DiaryRepository {

    /** The full visible set for [userId], newest first. Emits on every local change. */
    fun observeEntries(userId: String): Flow<List<DiaryEntry>>

    /** Filtered and sorted. Kept separate from [observeEntries] so callers stay explicit. */
    fun observeFilteredEntries(userId: String, filter: DiaryFilter): Flow<List<DiaryEntry>>

    /**
     * A page of entries matching [filter], ordered by the filter's sort order.
     *
     * [offset] is the number of rows already consumed, so pages concatenate into the full
     * result set when walked in order. The returned [Page.totalCount] counts every match,
     * not just this window.
     */
    fun observeEntriesPage(
        userId: String,
        filter: DiaryFilter,
        limit: Int,
        offset: Int,
    ): Flow<Page<DiaryEntry>>

    fun observeEntry(userId: String, entryId: String): Flow<DiaryEntry?>

    fun observeFavorites(userId: String): Flow<List<DiaryEntry>>

    fun observeRecentEntries(userId: String, limit: Int): Flow<List<DiaryEntry>>

    fun observeEntriesForDate(userId: String, date: kotlinx.datetime.LocalDate): Flow<List<DiaryEntry>>

    fun observeCount(userId: String): Flow<Int>

    suspend fun getEntry(userId: String, entryId: String): Result<DiaryEntry?>

    suspend fun createEntry(entry: DiaryEntry): Result<DiaryEntry>

    suspend fun updateEntry(entry: DiaryEntry): Result<DiaryEntry>

    suspend fun deleteEntry(userId: String, entryId: String): Result<Unit>

    /** Soft delete locally plus a remote tombstone, so an offline delete still syncs. */
    suspend fun restoreEntry(userId: String, entryId: String): Result<Unit>

    suspend fun setFavorite(userId: String, entryId: String, isFavorite: Boolean): Result<Unit>

    suspend fun search(userId: String, query: String, limit: Int): Result<List<DiaryEntry>>

    suspend fun getAllForExport(userId: String): Result<List<DiaryEntry>>

    /** Every tag the user has ever used, for the filter sheet. */
    fun observeAllTags(userId: String): Flow<List<String>>
}
