package com.arjun.mooary.ui.base

import com.arjun.mooary.domain.model.Page
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach

/**
 * A growing window over a paged read, shared by every list that pages.
 *
 * Instead of appending one fetched page to another, this tracks *how many rows the list wants*
 * and re-reads `0 until wanted` whenever that number changes. Re-reading a handful of
 * already-visible rows from a local database costs far less than the bookkeeping an
 * append-based scheme needs, and it stays correct for free in the three cases that break
 * accumulators:
 *
 *  * the filter changes, so earlier rows stop matching;
 *  * a row is deleted, shifting every later offset;
 *  * a new entry is inserted above the window.
 *
 * Each of those would leave stale, duplicated or skipped rows in an accumulated list. Here the
 * window is the single source of truth, so it cannot drift.
 *
 * [read] receives the row count to fetch and must honour it — implementations are expected to
 * clamp internally, since this class only guarantees a sane starting value.
 */
// `flatMapLatest` is the only experimental coroutines API in the codebase, and paging needs
// it: a wider read has to cancel an in-flight narrower one, which is exactly what switching
// filters mid-load has to do. Opted in here rather than with a compiler-wide flag so the
// dependency stays visible at the one place that has it.
@OptIn(ExperimentalCoroutinesApi::class)
class PageWindow<T>(private val pageSize: Int) {

    /**
     * Rows currently wanted: one page to begin with, one more page per [loadMore], capped at
     * the known total. So it is a multiple of [pageSize] until the cap applies, after which it
     * is whatever the total is.
     */
    private val wanted = MutableStateFlow(pageSize)

    /** The last page emitted, so [loadMore] can read `hasMore`/`totalCount` synchronously. */
    private val lastPage = MutableStateFlow(Page.empty<T>())

    /**
     * The current window for a query that also depends on [filter].
     *
     * Both the wanted size and the filter are inputs to the re-read, so changing either
     * re-subscribes to [read] with the new arguments. Reading the filter from a captured
     * value instead would race: a filter change could be missed, leaving rows on screen that
     * no longer match the chips the user just tapped.
     *
     * A filter change on its own re-reads at the *current* width. Call [reset] alongside it to
     * drop back to one page — the two are separate because a caller may want to keep a wide
     * window across a filter that is merely being edited, and forgetting the reset is the
     * mistake worth documenting rather than hiding.
     */
    fun <F> window(
        filter: Flow<F>,
        read: (limit: Int, filter: F) -> Flow<Page<T>>,
    ): Flow<Page<T>> =
        combine(filter, wanted) { activeFilter, limit -> activeFilter to limit }
            .flatMapLatest { (activeFilter, limit) -> read(limit, activeFilter) }
            .onEach { lastPage.value = it }

    /** [window] for a query with no inputs beyond the row count. */
    fun window(read: (limit: Int) -> Flow<Page<T>>): Flow<Page<T>> =
        window(flowOf(Unit)) { limit, _ -> read(limit) }

    /**
     * Extends the window by one page, or does nothing once everything is loaded.
     *
     * The new size is capped at the known total: without the cap, a journal whose size is an
     * exact multiple of [pageSize] would ask for one row more than exists on every scroll and
     * leave the footer spinner spinning forever.
     *
     * Awaits nothing, so calling it from a scroll callback on every frame is safe. Repeated
     * calls while a wider window is already outstanding are ignored rather than stacked —
     * otherwise five frames between two pages would jump the window by five pages and skip
     * straight past rows the user never saw.
     */
    fun loadMore() {
        val current = lastPage.value
        if (!current.hasMore) return
        // A wider window than the one on screen means its read is still outstanding.
        if (wanted.value > current.items.size) return
        val total = current.totalCount
        val next = wanted.value + pageSize
        wanted.value = if (total != null) next.coerceAtMost(total) else next
    }

    /**
     * Drops back to a single page.
     *
     * Call this whenever the query itself changes — a new filter, a new user. Keeping the old
     * window would keep rows that no longer match, sized against a query the list has left.
     */
    fun reset() {
        wanted.value = pageSize
        lastPage.value = Page.empty()
    }
}
