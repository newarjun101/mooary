package com.arjun.soul_note.ui.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowSizeClassTest {

    @Test
    fun `phone widths are compact`() {
        assertEquals(WindowWidthSizeClass.Compact, WindowWidthSizeClass.from(320.dp))
        assertEquals(WindowWidthSizeClass.Compact, WindowWidthSizeClass.from(411.dp))
        assertEquals(WindowWidthSizeClass.Compact, WindowWidthSizeClass.from(599.dp))
    }

    @Test
    fun `tablet widths are medium`() {
        assertEquals(WindowWidthSizeClass.Medium, WindowWidthSizeClass.from(600.dp))
        assertEquals(WindowWidthSizeClass.Medium, WindowWidthSizeClass.from(839.dp))
    }

    @Test
    fun `desktop widths are expanded`() {
        assertEquals(WindowWidthSizeClass.Expanded, WindowWidthSizeClass.from(840.dp))
        assertEquals(WindowWidthSizeClass.Expanded, WindowWidthSizeClass.from(1100.dp))
        assertEquals(WindowWidthSizeClass.Expanded, WindowWidthSizeClass.from(2560.dp))
    }

    @Test
    fun `only compact uses a bottom bar`() {
        assertFalse(WindowWidthSizeClass.Compact.hasRoomForRail)
        assertTrue(WindowWidthSizeClass.Medium.hasRoomForRail)
        assertTrue(WindowWidthSizeClass.Expanded.hasRoomForRail)
    }

    @Test
    fun `columns resolve per width class`() {
        assertEquals(2, WindowWidthSizeClass.Compact.columns(compact = 2, medium = 2, expanded = 4))
        assertEquals(2, WindowWidthSizeClass.Medium.columns(compact = 2, medium = 2, expanded = 4))
        assertEquals(4, WindowWidthSizeClass.Expanded.columns(compact = 2, medium = 2, expanded = 4))
    }

    @Test
    fun `charts stay single column until expanded`() {
        assertEquals(1, WindowWidthSizeClass.Compact.columns(compact = 1, medium = 1, expanded = 2))
        assertEquals(1, WindowWidthSizeClass.Medium.columns(compact = 1, medium = 1, expanded = 2))
        assertEquals(2, WindowWidthSizeClass.Expanded.columns(compact = 1, medium = 1, expanded = 2))
    }

    @Test
    fun `reading width is narrower than dashboard width`() {
        assertTrue(ContentWidth.reading < ContentWidth.dashboard)
    }
}
