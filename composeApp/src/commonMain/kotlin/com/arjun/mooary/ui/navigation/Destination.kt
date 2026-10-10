package com.arjun.mooary.ui.navigation

import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.nav_calendar
import com.arjun.mooary.resources.nav_diary
import com.arjun.mooary.resources.nav_dashboard
import com.arjun.mooary.resources.nav_mood
import com.arjun.mooary.resources.nav_profile
import com.arjun.mooary.resources.nav_settings
import com.arjun.mooary.resources.tab_calendar
import com.arjun.mooary.resources.tab_diary
import com.arjun.mooary.resources.tab_home
import com.arjun.mooary.resources.tab_mood
import com.arjun.mooary.resources.tab_settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hands the id of the entry to edit from whoever opened the editor to the editor itself.
 *
 * A single-slot mailbox rather than a navigation argument: the value is written before
 * navigating and read once on arrival, which cannot drift out of sync with the route the
 * way a stale argument can.
 */
class EditorTarget {
    private val pending = MutableStateFlow<String?>(null)

    /** Records which entry the next editor visit should open; null means "new entry". */
    fun open(entryId: String?) {
        pending.value = entryId
    }

    /** Reads and clears the pending id, so re-entering the editor starts fresh. */
    fun consume(): String? = pending.value.also { pending.value = null }
}

/**
 * Every place the app can navigate to.
 *
 * Routes are declared as data with a builder rather than as loose strings, so a route and
 * its arguments cannot drift apart: adding a parameter to [Editor] without adding it to
 * the route breaks the compiler instead of producing a runtime "destination not found".
 */
sealed interface Destination {

    val route: String

    data object Dashboard : Destination {
        override val route: String = "dashboard"
    }

    data object Diary : Destination {
        override val route: String = "diary"
    }

    data object Mood : Destination {
        override val route: String = "mood"
    }

    data object Calendar : Destination {
        override val route: String = "calendar"
    }

    data object Profile : Destination {
        override val route: String = "profile"
    }

    /**
     * The editor. Which entry it opens is handed over through [EditorTarget] rather than a
     * route argument: one editor destination with an explicit handoff keeps the navigation
     * graph free of nullable argument defaults that would silently open the wrong record.
     */
    data object Editor : Destination {
        override val route: String = "editor"
    }
}

/**
 * The bottom-bar items, in display order. The editor is not one of them.
 *
 * [labelRes] rather than a string: this enum is not a composable, so it cannot call
 * `stringResource`, and it hands the resource to the two places that can.
 *
 * [iconRes] rather than an [androidx.compose.ui.graphics.vector.ImageVector]: the tab artwork
 * is bitmap, drawn in the app's own palette, so a Material vector would be the one icon on the
 * bar that did not match. The same reason [labelRes] exists applies here — this is not a
 * composable, so it cannot call `painterResource`, and it hands the resource to the one place
 * that can.
 */
enum class TopLevelDestination(
    val destination: Destination,
    val labelRes: StringResource,
    val iconRes: DrawableResource,
) {
    DASHBOARD(Destination.Dashboard, Res.string.nav_dashboard, Res.drawable.tab_home),
    DIARY(Destination.Diary, Res.string.nav_diary, Res.drawable.tab_diary),
    MOOD(Destination.Mood, Res.string.nav_mood, Res.drawable.tab_mood),
    CALENDAR(Destination.Calendar, Res.string.nav_calendar, Res.drawable.tab_calendar),
    // The overview contains app preferences (theme, reminders, sync and export), so it is
    // labelled Settings. Its account editor is opened as the Profile page.
    PROFILE(Destination.Profile, Res.string.nav_settings, Res.drawable.tab_settings),
    ;

    companion object {
        fun forRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.destination.route == route }
    }
}
