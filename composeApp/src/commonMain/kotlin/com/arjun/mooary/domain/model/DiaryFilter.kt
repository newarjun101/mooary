package com.arjun.mooary.domain.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** The date/scope filter applied to the diary list. */
sealed interface DiaryDateFilter {

    /** No date constraint. */
    data object All : DiaryDateFilter

    data object Today : DiaryDateFilter

    data object ThisWeek : DiaryDateFilter

    data object ThisMonth : DiaryDateFilter

    /** The current month, used as the calendar's default scope. */
    data object ThisCalendarMonth : DiaryDateFilter

    /** Inclusive on both ends, in the user's local timezone. */
    data class CustomRange(val start: LocalDate, val end: LocalDate) : DiaryDateFilter

    /** Resolves this filter to a concrete inclusive day range, or `null` when unbounded. */
    fun resolve(today: LocalDate, weekStart: LocalDate): ClosedRange<LocalDate>? = when (this) {
        All -> null
        Today -> today..today
        ThisWeek -> weekStart..today
        ThisMonth -> com.arjun.mooary.core.datetime.LocalDates.monthStart(today)..today
        ThisCalendarMonth -> com.arjun.mooary.core.datetime.LocalDates.monthStart(today)..
            com.arjun.mooary.core.datetime.LocalDates.monthEnd(today)
        is CustomRange -> start..end
    }
}

/** The complete set of diary list filters. */
data class DiaryFilter(
    val dateFilter: DiaryDateFilter = DiaryDateFilter.All,
    val moodFilter: Set<Mood> = emptySet(),
    val emotionFilter: Set<String> = emptySet(),
    val tagFilter: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val query: String = "",
    val sort: DiarySortOrder = DiarySortOrder.NEWEST_ENTRY_DATE,
) {
    val hasActiveFilters: Boolean
        get() = dateFilter !is DiaryDateFilter.All ||
            moodFilter.isNotEmpty() ||
            emotionFilter.isNotEmpty() ||
            tagFilter.isNotEmpty() ||
            favoritesOnly ||
            query.isNotBlank()

    val activeFilterCount: Int
        get() = (if (dateFilter !is DiaryDateFilter.All) 1 else 0) +
            (if (moodFilter.isNotEmpty()) 1 else 0) +
            (if (emotionFilter.isNotEmpty()) 1 else 0) +
            (if (tagFilter.isNotEmpty()) 1 else 0) +
            (if (favoritesOnly) 1 else 0)

    fun matches(entry: DiaryEntry, today: LocalDate, weekStart: LocalDate): Boolean {
        if (!entry.isVisible()) return false
        if (favoritesOnly && !entry.isFavorite) return false
        dateFilter.resolve(today, weekStart)?.let { range ->
            if (entry.entryDate !in range) return false
        }
        if (moodFilter.isNotEmpty() && entry.mood !in moodFilter) return false
        if (emotionFilter.isNotEmpty() && entry.emotions.none { it.id in emotionFilter }) return false
        if (tagFilter.isNotEmpty() && entry.tags.none { it in tagFilter }) return false
        if (query.isNotBlank() && !entry.matches(query)) return false
        return true
    }

    fun sort(entries: List<DiaryEntry>): List<DiaryEntry> = when (sort) {
        DiarySortOrder.NEWEST_ENTRY_DATE -> entries.sortedWith(
            compareByDescending<DiaryEntry> { it.entryDate }.thenByDescending { it.createdAt },
        )
        DiarySortOrder.OLDEST_ENTRY_DATE -> entries.sortedWith(
            compareBy<DiaryEntry> { it.entryDate }.thenBy { it.createdAt },
        )
        DiarySortOrder.NEWEST_UPDATED -> entries.sortedByDescending { it.updatedAt }
        DiarySortOrder.OLDEST_UPDATED -> entries.sortedBy { it.updatedAt }
        DiarySortOrder.TITLE_ASC -> entries.sortedWith(
            compareBy<DiaryEntry> { it.title.lowercase() }.thenByDescending { it.createdAt },
        )
        DiarySortOrder.MOOD_HIGHEST -> entries.sortedWith(
            compareByDescending<DiaryEntry> { it.mood.score }.thenByDescending { it.entryDate },
        )
        DiarySortOrder.MOOD_LOWEST -> entries.sortedWith(
            compareBy<DiaryEntry> { it.mood.score }.thenByDescending { it.entryDate },
        )
    }

    companion object {
        val None = DiaryFilter()
    }
}

enum class DiarySortOrder {
    NEWEST_ENTRY_DATE,
    OLDEST_ENTRY_DATE,
    NEWEST_UPDATED,
    OLDEST_UPDATED,
    TITLE_ASC,
    MOOD_HIGHEST,
    MOOD_LOWEST,
}

/** The same filter vocabulary applied to mood history. */
data class MoodFilter(
    val dateFilter: DiaryDateFilter = DiaryDateFilter.All,
    val moodFilter: Set<Mood> = emptySet(),
    val emotionFilter: Set<String> = emptySet(),
    val query: String = "",
) {
    val hasActiveFilters: Boolean
        get() = dateFilter !is DiaryDateFilter.All ||
            moodFilter.isNotEmpty() ||
            emotionFilter.isNotEmpty() ||
            query.isNotBlank()

    fun matches(entry: MoodEntry, today: LocalDate, weekStart: LocalDate): Boolean {
        if (!entry.isVisible()) return false
        dateFilter.resolve(today, weekStart)?.let { range ->
            if (entry.date !in range) return false
        }
        if (moodFilter.isNotEmpty() && entry.mood !in moodFilter) return false
        if (emotionFilter.isNotEmpty() && entry.emotions.none { it.id in emotionFilter }) return false
        if (query.isNotBlank()) {
            val needle = query.trim().lowercase()
            val noteMatches = entry.note?.lowercase()?.contains(needle) == true
            val emotionMatches = entry.emotions.any { it.name.lowercase().contains(needle) }
            if (!noteMatches && !emotionMatches && !entry.mood.id.contains(needle)) return false
        }
        return true
    }
}

/**
 * A window over a result set, so a list never has to materialise the whole table.
 *
 * [hasMore] is exact rather than a guess. Producers fetch `limit + 1` rows and treat an
 * over-full result as "there is another page", which stays correct when the remaining row
 * count happens to be an exact multiple of the page size — a `items.size == limit` test
 * reports one phantom page in precisely that case.
 *
 * [totalCount] is the size of the *whole* matching set, not of this window. It is nullable
 * because it is the expensive half of a page: when a caller does not need it (the list just
 * wants to know whether to keep loading) it is left out rather than approximated.
 *
 * There is deliberately no "offset for the next page" accessor. A page does not know the
 * offset it started from, so any such property would be wrong for every caller except the
 * one that began at zero, and it would be wrong silently. List paging here grows a window
 * from the start rather than appending, which needs no offset bookkeeping at all and stays
 * correct when rows are inserted or deleted mid-scroll.
 */
data class Page<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val totalCount: Int? = null,
) {
    companion object {
        fun <T> single(items: List<T>): Page<T> =
            Page(items = items, hasMore = false, totalCount = items.size)

        /** An empty window: the shape a query returns before its first row lands. */
        fun <T> empty(): Page<T> = Page(emptyList(), hasMore = false, totalCount = 0)

        /**
         * Builds a page from a query that deliberately asked for one row too many.
         *
         * [rows] must be the result of `LIMIT limit + 1`. The extra row is the existence
         * proof that makes [hasMore] exact; anything more than [limit] rows back means
         * another page exists, and exactly [limit] means this was the last one.
         *
         * [totalCount] is the count of the whole matching set, which only the count query
         * knows — it is passed in rather than inferred, because inferring it from the page
         * is what made the old implementation report a total that grew with scrolling.
         */
        fun <T> fromOverfetched(rows: List<T>, limit: Int, totalCount: Int): Page<T> = Page(
            items = if (rows.size > limit) rows.take(limit) else rows,
            hasMore = rows.size > limit,
            totalCount = totalCount,
        )
    }
}

/**
 * Slices an already-materialised result set into a [Page].
 *
 * Used by the filtered read paths, where matching and sorting happen in Kotlin and only the
 * window needs trimming. Negative and zero inputs are tolerated because the values arrive
 * from a scroll position rather than a validated request.
 */
fun <T> List<T>.windowedPage(limit: Int, offset: Int): Page<T> {
    val safeLimit = limit.coerceAtLeast(0)
    val safeOffset = offset.coerceAtLeast(0)
    if (safeLimit == 0) {
        // A zero-width window can never make progress, so reporting `hasMore` here would
        // hang any caller that advances by the page size until it stops.
        return Page(items = emptyList(), hasMore = false, totalCount = size)
    }
    val items = drop(safeOffset).take(safeLimit)
    return Page(
        items = items,
        hasMore = safeOffset + items.size < size,
        totalCount = size,
    )
}

/** Where a sync pass currently stands, surfaced in the profile screen. */
data class SyncState(
    val pendingCount: Int = 0,
    val lastSyncedAt: Instant? = null,
    val isSyncing: Boolean = false,
    val conflictCount: Int = 0,
    val lastErrorKey: String? = null,
)
