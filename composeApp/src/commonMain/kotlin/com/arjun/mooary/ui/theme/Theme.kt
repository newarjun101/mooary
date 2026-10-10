package com.arjun.mooary.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Corners are cut, but on an even 4dp progression. An irregular scale is easy to write and
// impossible to keep consistent by eye: one screen ends up on 18dp cards while the next uses
// 24dp for the same role, and the mismatch reads as sloppiness rather than as a choice.
internal val DiaryShapes = Shapes(
    // These shapes are deliberately generous: the same family reaches text fields, filter
    // chips, dialogs and cards through MaterialTheme, so the wellness feel belongs to the
    // whole app instead of only the dashboard.
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** One spacing scale so padding never drifts between screens. */
object Spacing {
    val none: Dp = 0.dp
    val xSmall: Dp = 3.dp
    val small: Dp = 6.dp
    val medium: Dp = 12.dp
    val large: Dp = 18.dp
    val xLarge: Dp = 24.dp

    /**
     * Horizontal gutter used by every scrolling screen.
     *
     * Enough to keep text off the screen edge without turning the margin into the subject.
     * A wider gutter reads as luxurious on one line of prose and as wasted space once there
     * are ten cards on the screen, which is most of this app.
     */
    val gutter: Dp = 16.dp

    /** Gutter for a first-run or centred screen, which has no list to align to. */
    val gutterWide: Dp = 20.dp
}

/**
 * The three elevations the app uses, and no others.
 *
 * An ad-hoc value per call site (3dp here, 5dp there, 8dp on a dialog) is the reason a screen
 * can look assembled rather than designed: two cards side by side end up at visibly different
 * weights and nothing in the layout explains why. Three named levels, used consistently, is
 * what makes the depth read as a system.
 */
object Elevation {
    /** Cards and rows sitting on the page. Barely there -- the hairline does the work. */
    val card: Dp = 1.dp

    /** Things that float above the page: the FAB, a selected segment, a raised control. */
    val raised: Dp = 4.dp

    /** True overlays: menus, dialogs, anything that must sit above another surface. */
    val overlay: Dp = 10.dp
}

@Composable
fun DiaryTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = DiaryTypography,
        shapes = DiaryShapes,
    ) {
        // MaterialTheme publishes a colour scheme but does NOT set LocalContentColor, which
        // stays at its Color.Black default. Only Material containers (Surface, Button, Card,
        // TextField, AlertDialog) push a value inward, so a Text with no explicit color that
        // sat in a plain Box/Column inherited black -- indistinguishable from ink on paper in
        // light, and unreadable on espresso in dark. That is why the empty-state titles were
        // the visible offenders and every other label looked fine: they are inside cards.
        // Seeding it here means "no explicit color" resolves to the theme, which is what all
        // the call sites already assume, and a future screen cannot repeat the trap.
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            content()
        }
    }
}
