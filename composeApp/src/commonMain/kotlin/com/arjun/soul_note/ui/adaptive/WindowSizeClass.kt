package com.arjun.soul_note.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much horizontal room the window has, in the three buckets the layouts actually react to.
 *
 * The widths follow the Material 3 window size classes — compact below 600dp, medium to 840dp,
 * expanded beyond — because those cut points are where a phone layout stops working: a five-item
 * bottom bar needs about 320dp before its labels collide, and a single column of diary text is
 * unreadable past roughly 700dp.
 *
 * This is deliberately hand-rolled instead of pulling in `material3-adaptive`. The adaptive
 * artifact would have to resolve against this project's Compose Multiplatform version, and the
 * only thing needed from it is a breakpoint calculation, which is a handful of lines here.
 */
enum class WindowWidthSizeClass {
    /** Phones, and a desktop window dragged narrow. Bottom bar, one column. */
    Compact,

    /** Small tablets, split-screen desktop. Navigation rail, wider grids. */
    Medium,

    /** Tablets in landscape, desktop. Navigation rail, multi-column content. */
    Expanded,
    ;

    /** True when there is room for a permanent navigation rail beside the content. */
    val hasRoomForRail: Boolean get() = this != Compact

    companion object {
        val CompactMax: Dp = 600.dp
        val MediumMax: Dp = 840.dp

        fun from(width: Dp): WindowWidthSizeClass = when {
            width < CompactMax -> Compact
            width < MediumMax -> Medium
            else -> Expanded
        }
    }
}

/**
 * The width class of the window the content is being laid out in.
 *
 * Provided once at the app shell and read anywhere below, so a screen does not have to wrap
 * itself in [BoxWithConstraints] just to learn how wide it is. Defaults to
 * [WindowWidthSizeClass.Compact], which is the safe answer: it is the layout that works
 * anywhere, so a screen rendered in isolation or in a preview still lays out correctly.
 */
val LocalWindowWidthClass = compositionLocalOf { WindowWidthSizeClass.Compact }

@Composable
@ReadOnlyComposable
fun currentWindowWidthClass(): WindowWidthSizeClass = LocalWindowWidthClass.current

/**
 * Publishes the width class for everything below.
 *
 * [BoxWithConstraints] is used rather than the platform configuration because the desktop window
 * is resizable: the configuration is only re-read on a configuration change, whereas
 * `maxWidth` re-reports on every relayout, so dragging a window edge moves the layout live.
 */
@Composable
fun ProvideWindowWidthClass(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthClass = WindowWidthSizeClass.from(maxWidth)
        CompositionLocalProvider(LocalWindowWidthClass provides widthClass) {
            content()
        }
    }
}

/**
 * Picks a column count for a grid from the width class.
 *
 * A single screen rarely wants the same count at every size — the dashboard's four stat tiles
 * are a 2x2 block on a phone and a single row on a tablet — so each caller states all three and
 * this resolves the one that fits.
 *
 * Kept as a plain function on the enum so the breakpoints can be tested without a composition;
 * the composable below is only the bridge that reads the ambient class.
 */
fun WindowWidthSizeClass.columns(compact: Int, medium: Int, expanded: Int): Int = when (this) {
    WindowWidthSizeClass.Compact -> compact
    WindowWidthSizeClass.Medium -> medium
    WindowWidthSizeClass.Expanded -> expanded
}

@Composable
fun adaptiveColumns(compact: Int, medium: Int, expanded: Int): Int =
    currentWindowWidthClass().columns(compact = compact, medium = medium, expanded = expanded)
