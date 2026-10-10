package com.arjun.mooary.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The paging arithmetic behind both list screens.
 *
 * These are the cases that make paging *look* like it works: the boundary where the last page
 * is exactly full, and the boundary where the requested window runs past the end. Both used
 * to report a phantom final page, so they are pinned here rather than left to integration
 * tests that need a database.
 */
class PageTest {

    private val rows = (1..10).toList()


    @Test
    fun `overfetched page reports another page when the sentinel row is present`() {
        // The query asked for 4, so 5 rows come back: 4 to show and 1 proof of more.
        val page = Page.fromOverfetched(rows.take(5), limit = 4, totalCount = 10)

        assertEquals(rows.take(4), page.items)
        assertTrue(page.hasMore)
        assertEquals(10, page.totalCount)
    }

    @Test
    fun `overfetched page reports no more when exactly one page came back`() {
        // 3 rows for a limit of 3: a full page, but the sentinel never arrived, so the
        // result set ends here. The old `items.size == limit` test would claim otherwise.
        val page = Page.fromOverfetched(rows.take(3), limit = 3, totalCount = 3)

        assertEquals(rows.take(3), page.items)
        assertFalse(page.hasMore)
        assertEquals(3, page.totalCount)
    }

    @Test
    fun `overfetched page handles a short final page`() {
        // `LIMIT 5` returned only 3 rows, so the set ends inside this page. Fewer rows than
        // the limit can never mean "more to come", however full it looks.
        val page = Page.fromOverfetched(rows.take(3), limit = 4, totalCount = 3)

        assertEquals(3, page.items.size)
        assertEquals(rows.take(3), page.items)
        assertFalse(page.hasMore)
        assertEquals(3, page.totalCount)
    }

    @Test
    fun `overfetched page of an empty result is not loading`() {
        val page = Page.fromOverfetched<Int>(emptyList(), limit = 4, totalCount = 0)
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
        assertEquals(0, page.totalCount)
    }

    @Test
    fun `overfetched total is not derived from the page size`() {
        // Guards the specific regression: a total computed from the window grows every time
        // the user scrolls, so the "12 of 12" line becomes "12 of 24" and then stops.
        val first = Page.fromOverfetched(rows.take(5), limit = 4, totalCount = 10)
        val second = Page.fromOverfetched(rows.drop(4).take(5), limit = 4, totalCount = 10)

        assertEquals(first.totalCount, second.totalCount)
        assertEquals(4, second.items.size)
    }


    @Test
    fun `window starts at the requested offset`() {
        val page = rows.windowedPage(limit = 3, offset = 3)

        assertEquals(listOf(4, 5, 6), page.items)
        assertTrue(page.hasMore)
        assertEquals(10, page.totalCount)
    }

    @Test
    fun `window covers the whole set when it is smaller than the limit`() {
        val page = rows.windowedPage(limit = 50, offset = 0)

        assertEquals(rows, page.items)
        assertFalse(page.hasMore)
        assertEquals(10, page.totalCount)
    }

    @Test
    fun `window landing exactly on the end is the last page`() {
        // Offset 6 + limit 4 reaches row 10 precisely. There is no row 11, so this is done.
        val page = rows.windowedPage(limit = 4, offset = 6)

        assertEquals(listOf(7, 8, 9, 10), page.items)
        assertFalse(page.hasMore)
    }

    @Test
    fun `window one row short of the end still has more`() {
        val page = rows.windowedPage(limit = 4, offset = 5)

        assertEquals(listOf(6, 7, 8, 9), page.items)
        assertTrue(page.hasMore)
    }

    @Test
    fun `window past the end is empty rather than an error`() {
        val page = rows.windowedPage(limit = 4, offset = 99)

        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
        assertEquals(10, page.totalCount)
    }

    @Test
    fun `negative offset is clamped instead of throwing`() {
        // Offsets originate from a scroll position, so they must never crash a list.
        val page = rows.windowedPage(limit = 3, offset = -5)

        assertEquals(listOf(1, 2, 3), page.items)
        assertTrue(page.hasMore)
    }

    @Test
    fun `zero limit yields no rows and no phantom continuation`() {
        val page = rows.windowedPage(limit = 0, offset = 0)

        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `walking every window in order yields each row exactly once`() {
        val seen = mutableListOf<Int>()
        var offset = 0
        while (true) {
            val page = rows.windowedPage(limit = 3, offset = offset)
            seen += page.items
            if (!page.hasMore) break
            offset += page.items.size
        }

        assertEquals(rows, seen)
    }

    @Test
    fun `walking windows of a set that is an exact multiple of the limit terminates`() {
        // 9 rows, limit 3: without the sentinel the final page would report hasMore and this
        // loop would never end.
        val nine = (1..9).toList()
        val seen = mutableListOf<Int>()
        var offset = 0
        var guard = 0
        while (guard++ < 10) {
            val page = nine.windowedPage(limit = 3, offset = offset)
            seen += page.items
            if (!page.hasMore) break
            offset += page.items.size
        }

        assertEquals(nine, seen)
        assertTrue(guard < 10, "paging did not terminate")
    }


    @Test
    fun `a page carries no next-offset because it cannot know where it started`() {
        // Paging grows a window from the start; nothing threads an offset forward, so there is
        // no accessor that could report one incorrectly.
        val page = rows.windowedPage(limit = 4, offset = 4)

        // rows is 1..10, so offset 4 skips the first four and yields 5..8.
        assertEquals(listOf(5, 6, 7, 8), page.items)
    }

    @Test
    fun `empty page is a complete result rather than an unknown one`() {
        val page = Page.empty<Int>()

        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
        assertEquals(0, page.totalCount)
    }

    @Test
    fun `single page is never a continuation`() {
        val page = Page.single(listOf(1, 2, 3))

        assertEquals(3, page.items.size)
        assertFalse(page.hasMore)
        assertEquals(3, page.totalCount)
    }
}
