package com.arjun.soul_note.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Caps how wide a column of content grows, and centres it in the leftover space.
 *
 * Without this, a diary entry on a 1600dp-wide desktop window is one line of text across the
 * whole screen, and the eye has to travel from the start of a line to its end and back. The
 * measure stays comfortable and the extra width becomes margin.
 *
 * These are maximums, not fixed widths: a narrow window still lays out at its own width, so the
 * same modifier is correct on a phone.
 */
object ContentWidth {
    /** Prose and lists: a diary paragraph, the calendar grid, the profile page. */
    val reading: Dp = 720.dp

    /** Grids of cards and charts, which gain a second column instead of extra width. */
    val dashboard: Dp = 1080.dp
}

/**
 * Caps the measure to [max] without centring it.
 *
 * Centring needs a [BoxScope], which a scrolling screen does not have, so a screen that wants
 * centred content wraps its scroller in a `Box` with `contentAlignment = Alignment.TopCenter` and
 * applies this inside.
 */
fun Modifier.contentWidth(max: Dp = ContentWidth.reading): Modifier =
    this.fillMaxWidth().widthIn(max = max)

/**
 * A centred, width-capped column.
 *
 * Screens whose body is a single scrolling column use this instead of a bare [Column] so the cap
 * is applied in one place; the reading and chart widths differ per screen, which is why [max] is
 * a parameter rather than a fixed value. The centring needs a [BoxScope], so the column is
 * wrapped rather than aligned directly.
 */
@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    max: Dp = ContentWidth.reading,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = max),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
