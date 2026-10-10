package com.arjun.mooary.ui.base

import com.arjun.mooary.domain.model.Page
import com.arjun.mooary.domain.model.windowedPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The growing-window paging both list screens share.
 *
 * [PageWindow] re-reads a prefix of the result set instead of appending pages, so these tests
 * assert the *observable* effects — how many rows are requested, and what the list shows —
 * rather than internals. The page arithmetic itself is covered in `PageTest`.
 */
class PageWindowTest {

    /** A fake paged source: returns `limit` rows out of `total`, newest first. */
    private class FakeSource(private val total: Int) {
        val requested = mutableListOf<Int>()

        fun read(limit: Int): Flow<Page<Int>> {
            requested += limit
            val rows = (1..total).toList().take(limit)
            return flowOf(
                Page(
                    items = rows,
                    hasMore = rows.size < total,
                    totalCount = total,
                ),
            )
        }
    }

    @Test
    fun `starts by asking for a single page`() = runTest {
        val source = FakeSource(total = 100)
        val window = PageWindow<Int>(pageSize = 30)

        val page = window.window { limit -> source.read(limit) }.first()

        assertEquals(listOf(30), source.requested)
        assertEquals(30, page.items.size)
        assertTrue(page.hasMore)
        assertEquals(100, page.totalCount)
    }

    @Test
    fun `load more asks for one page more`() = runTest {
        val source = FakeSource(total = 100)
        val window = PageWindow<Int>(pageSize = 30)
        val pages = window.window { limit -> source.read(limit) }

        assertEquals(30, pages.first().items.size)
        window.loadMore()
        // Nothing re-reads until something collects: the window is demand-driven.
        assertEquals(listOf(30), source.requested)
        assertEquals(60, pages.first().items.size)
        assertEquals(listOf(30, 60), source.requested)
    }

    @Test
    fun `load more is a no-op at the end of the list`() = runTest {
        val source = FakeSource(total = 30)
        val window = PageWindow<Int>(pageSize = 30)
        val pages = window.window { limit -> source.read(limit) }

        assertFalse(pages.first().hasMore)
        window.loadMore()
        window.loadMore()

        // A list that keeps asking past the end spins forever behind a spinner that never
        // resolves, which is exactly the symptom this guards.
        assertEquals(listOf(30), source.requested)
    }

    @Test
    fun `window stops at the total instead of overshooting it`() = runTest {
        val source = FakeSource(total = 45)
        val window = PageWindow<Int>(pageSize = 30)
        val pages = window.window { limit -> source.read(limit) }

        pages.first()
        window.loadMore()
        assertFalse(pages.first().hasMore)
        // Capped at the total: a 45-row journal never asks for a 60-row window.
        assertEquals(listOf(30, 45), source.requested)

        // A further call after the end must not grow the request again.
        window.loadMore()
        assertEquals(listOf(30, 45), source.requested)
    }

    @Test
    fun `repeated load more calls before the page lands only advance once`() = runTest {
        // A scroll callback fires on every frame, so several calls can land between frames.
        val source = FakeSource(total = 100)
        val window = PageWindow<Int>(pageSize = 30)
        val pages = window.window { limit -> source.read(limit) }
        pages.first()

        repeat(5) { window.loadMore() }
        pages.first()

        assertEquals(listOf(30, 60), source.requested)
    }

    @Test
    fun `reset drops back to a single page`() = runTest {
        val source = FakeSource(total = 100)
        val window = PageWindow<Int>(pageSize = 30)
        val pages = window.window { limit -> source.read(limit) }

        pages.first()
        window.loadMore()
        assertEquals(60, pages.first().items.size)

        window.reset()
        assertEquals(30, pages.first().items.size)
        assertEquals(listOf(30, 60, 30), source.requested)
    }

    @Test
    fun `a filter change re-reads from the first page`() = runTest {
        val source = FakeSource(total = 100)
        val window = PageWindow<Int>(pageSize = 30)
        val filter = MutableStateFlow("all")
        val pages = window.window(filter) { limit, active -> source.read(limit) }

        pages.first()
        window.loadMore()
        assertEquals(60, pages.first().items.size)

        // Narrowing the query re-reads at the current width, which is what the ViewModel
        // then corrects with reset() -- see the next test for the composed behaviour.
        filter.value = "favourites"
        assertEquals(60, pages.first().items.size)

        // Narrowing the query must not leave the old, larger window on screen.
        window.reset()
        assertEquals(30, pages.first().items.size)
        assertEquals(listOf(30, 60, 60, 30), source.requested)
    }

    @Test
    fun `rows deleted above the window cannot skip or duplicate a row`() = runTest {
        // The failure this design exists to avoid: an appended list would still show row 31
        // as the 31st item after row 1 was deleted, silently skipping a real entry.
        val rows = MutableStateFlow((1..100).toList())
        val window = PageWindow<Int>(pageSize = 30)
        val pages = window.window { limit ->
            rows.map { all -> all.windowedPage(limit, 0) }
        }

        assertEquals(1, pages.first().items.first())

        rows.value = rows.value.drop(1)
        assertEquals(2, pages.first().items.first())
        assertEquals(30, pages.first().items.size)
    }

    @Test
    fun `an entry inserted above the window shifts the window rather than duplicating`() = runTest {
        val rows = MutableStateFlow((1..100).toList())
        val window = PageWindow<Int>(pageSize = 30)
        val pages = window.window { limit ->
            rows.map { all -> all.windowedPage(limit, 0) }
        }

        pages.first()
        rows.value = listOf(0) + rows.value

        val items = pages.first().items
        assertEquals(0, items.first())
        assertEquals(items.size, items.distinct().size)
    }
}
