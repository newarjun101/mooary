package com.arjun.soul_note.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.nav_calendar
import com.arjun.soul_note.resources.nav_diary
import com.arjun.soul_note.resources.nav_dashboard
import com.arjun.soul_note.resources.nav_mood
import com.arjun.soul_note.resources.nav_profile
import com.arjun.soul_note.resources.nav_settings
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

    data object Auth : Destination {
        override val route: String = "auth"
    }

    /**
     * Profile, password and the destructive account actions. Reached from the profile
     * screen rather than the shell's navigation, so it is not a tab: leaving it must not
     * change which destination the rail highlights.
     */
    data object Settings : Destination {
        override val route: String = "settings"
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
 */
enum class TopLevelDestination(
    val destination: Destination,
    val labelRes: StringResource,
    val icon: ImageVector,
) {
    DASHBOARD(Destination.Dashboard, Res.string.nav_dashboard, Icons.Default.Home),
    DIARY(Destination.Diary, Res.string.nav_diary, Icons.Default.EditNote),
    MOOD(Destination.Mood, Res.string.nav_mood, Icons.Default.InsertEmoticon),
    CALENDAR(Destination.Calendar, Res.string.nav_calendar, Icons.Default.CalendarMonth),
    // The overview contains app preferences (theme, reminders, sync and export), so it is
    // labelled Settings. Its account editor is opened as the Profile page.
    PROFILE(Destination.Profile, Res.string.nav_settings, Icons.Default.Settings),
    ;

    companion object {
        fun forRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.destination.route == route }
    }
}
