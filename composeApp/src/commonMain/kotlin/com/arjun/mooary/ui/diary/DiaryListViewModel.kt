package com.arjun.mooary.ui.diary

import com.arjun.mooary.core.error.AppError
import com.arjun.mooary.core.error.toAppError
import com.arjun.mooary.core.validation.Limits
import com.arjun.mooary.domain.model.DiaryEntry
import com.arjun.mooary.domain.model.DiaryFilter
import com.arjun.mooary.domain.model.DiarySortOrder
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.usecase.diary.ObserveAllTagsUseCase
import com.arjun.mooary.domain.usecase.diary.ObserveDiaryPageUseCase
import com.arjun.mooary.domain.usecase.diary.ToggleFavoriteEntryUseCase
import com.arjun.mooary.ui.base.BaseViewModel
import com.arjun.mooary.ui.base.PageWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything the diary list screen renders. */
data class DiaryListState(
    val entries: List<DiaryEntry> = emptyList(),
    val filter: DiaryFilter = DiaryFilter.None,
    val availableTags: List<String> = emptyList(),
    /** Rows matching [filter] in total, which is more than [entries].size until paging catches up. */
    val totalCount: Int = 0,
    /** Whether [loadMore] would extend the list. False once every match is loaded. */
    val hasMore: Boolean = false,
    val isLoading: Boolean = true,
    val error: AppError? = null,
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()

    /** Rows loaded so far, for the "showing X of Y" line. */
    val loadedCount: Int get() = entries.size
}

/**
 * Backing state for the diary list.
 *
 * Filtering and sorting are delegated to the repository rather than repeated here, so
 * [DiaryFilter] stays the single definition of "what this list means" and the week/month
 * boundaries come from the same [com.arjun.mooary.core.datetime.TimeProvider] the rest of
 * the app uses.
 *
 * Paging grows a *window* rather than accumulating separate pages: the query always re-reads
 * rows `0 until wanted`, and scrolling raises [wanted]. Re-reading a few already-visible rows
 * from a local SQLite table costs far less than the bookkeeping an append-based scheme needs,
 * and it is automatically correct when the filter changes, when a row is deleted, and when
 * new entries arrive — none of which can leave stale or duplicated rows in the list.
 */
class DiaryListViewModel(
    observePage: ObserveDiaryPageUseCase,
    observeTags: ObserveAllTagsUseCase,
    private val toggleFavorite: ToggleFavoriteEntryUseCase,
    private val userId: String,
) : BaseViewModel() {

    private val filter = MutableStateFlow(DiaryFilter.None)
    private val failure = MutableStateFlow<AppError?>(null)

    private val paging = PageWindow<DiaryEntry>(PAGE_SIZE)

    private val page = paging.window(filter) { limit, activeFilter ->
        observePage(userId = userId, limit = limit, offset = 0, filter = activeFilter)
    }

    val state: StateFlow<DiaryListState> = combine(
        page,
        filter,
        observeTags(userId),
        failure,
    ) { current, activeFilter, tags, error ->
        DiaryListState(
            entries = current.items,
            filter = activeFilter,
            availableTags = tags,
            totalCount = current.totalCount ?: current.items.size,
            hasMore = current.hasMore,
            isLoading = false,
            error = error,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DiaryListState())

    /**
     * Extends the window by one page, or does nothing at the end of the list.
     *
     * Safe to call from a scroll callback: repeated calls before the new page lands are no-ops.
     */
    fun loadMore() = paging.loadMore()

    fun setQuery(query: String) = applyFilter { copy(query = query) }

    fun setSortOrder(sort: DiarySortOrder) = applyFilter { copy(sort = sort) }

    fun toggleFavoritesOnly() = applyFilter { copy(favoritesOnly = !this.favoritesOnly) }

    fun toggleMoodFilter(mood: Mood) = applyFilter {
        val current = moodFilter
        copy(moodFilter = if (mood in current) current - mood else current + mood)
    }

    fun toggleTagFilter(tag: String) = applyFilter {
        val current = tagFilter
        copy(tagFilter = if (tag in current) current - tag else current + tag)
    }

    fun clearFilters() {
        filter.value = DiaryFilter.None
        resetWindow()
    }

    /**
     * Applies a filter change and drops back to a single page.
     *
     * Resetting matters: keeping the old window would show rows that no longer match, and
     * the count would be read against a filter the list is no longer using.
     */
    private fun applyFilter(transform: DiaryFilter.() -> DiaryFilter) {
        val updated = filter.value.transform()
        if (updated == filter.value) return
        filter.value = updated
        resetWindow()
    }

    private fun resetWindow() {
        paging.reset()
    }

    fun onFavouriteToggled(entry: DiaryEntry) {
        launch {
            toggleFavorite(userId, entry, !entry.isFavorite)
                .onFailure { failure.value = it.toAppError() }
        }
    }

    fun dismissError() {
        failure.value = null
    }

    private companion object {
        const val PAGE_SIZE = Limits.DIARY_PAGE_SIZE
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
