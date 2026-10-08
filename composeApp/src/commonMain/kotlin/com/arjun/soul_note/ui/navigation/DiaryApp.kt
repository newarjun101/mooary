package com.arjun.soul_note.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.nav_account
import com.arjun.soul_note.resources.nav_back
import com.arjun.soul_note.resources.nav_entry
import com.arjun.soul_note.resources.nav_profile
import com.arjun.soul_note.resources.nav_settings
import com.arjun.soul_note.resources.diary_search
import com.arjun.soul_note.ui.adaptive.ProvideWindowWidthClass
import com.arjun.soul_note.ui.theme.PaperSurface
import com.arjun.soul_note.ui.theme.DiaryShapes
import com.arjun.soul_note.ui.theme.Spacing
import com.arjun.soul_note.ui.adaptive.currentWindowWidthClass
import com.arjun.soul_note.ui.auth.AuthScreen
import com.arjun.soul_note.ui.base.SessionState
import com.arjun.soul_note.ui.calendar.CalendarScreen
import com.arjun.soul_note.ui.diary.DiaryEditorScreen
import com.arjun.soul_note.ui.diary.DiaryListScreen
import com.arjun.soul_note.ui.dashboard.DashboardScreen
import com.arjun.soul_note.ui.mood.MoodScreen
import com.arjun.soul_note.ui.profile.ProfileScreen
import com.arjun.soul_note.ui.settings.SettingsScreen
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import androidx.compose.ui.Alignment
import com.arjun.soul_note.ui.adaptive.ContentWidth
import com.arjun.soul_note.ui.adaptive.contentWidth

/**
 * The whole app's navigation graph.
 *
 * The session is passed down rather than read from a global, because every screen's data
 * depends on the active user id: a change of account has to rebuild them, and the type
 * system should make that visible instead of leaving it to a runtime lookup.
 *
 * The shell has two shapes. A phone gets a bottom bar and nothing beside the content; anything
 * wider gets a navigation rail, because a five-item bottom bar on a wide window puts the
 * navigation a long way from the content it controls, and leaves an empty band underneath. The
 * rail replaces the bar rather than joining it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryApp(
    session: SessionState,
    editorTarget: EditorTarget,
    navController: NavHostController = rememberNavController(),
) {
    val today = remember(session.userId) { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = TopLevelDestination.forRoute(currentRoute)
    // This navigation version has no previousBackStackEntry, and it does not need one:
    // every destination outside the tab bar (editor, auth) was pushed on top of a tab, so
    // "not a tab" is exactly the set of places a back arrow belongs.
    val showBackArrow = topLevel == null
    var diarySearchQuery by rememberSaveable(session.userId) { mutableStateOf("") }

    // The width class has to be known before the shell is assembled, because it decides where
    // the navigation lives, so the provider wraps the shell rather than sitting inside it.
    // The page background live here, behind the navigation chrome, rather than in
    // each screen. One place to restyle, and no screen has to remember to opt in.
    PaperSurface {
    if (currentWindowWidthClass().hasRoomForRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                // The rail leads, so it sits on the left in a left-to-right language and on the
                // right in a right-to-left one, which is where a start-anchored navigation is
                // expected to be.
                Row {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        TopLevelDestination.entries.forEach { item ->
                            // NavigationRailItem is a ColumnScope extension, so it has to be
                            // called from inside the rail rather than a shared helper.
                            NavigationRailItem(
                                selected = item == topLevel,
                                onClick = { navController.switchTab(item, topLevel) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                    // The rail is an opaque panel against a flat page, so a hairline is
                    // the only thing that keeps the two apart where they meet.
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Scaffold(
                    // Weighted, not fillMaxSize: inside a Row an unweighted child claims the
                    // whole width and pushes the rail off the edge of the window.
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    containerColor = Color.Transparent,
                    topBar = {
                        AppTopBar(
                            topLevel, showBackArrow, currentRoute, navController,
                            diarySearchQuery, { diarySearchQuery = it },
                        )
                    },
                ) { padding ->
                    DiaryNavHost(
                        modifier = Modifier.padding(padding),
                        navController = navController,
                        session = session,
                        today = today,
                        editorTarget = editorTarget,
                        currentRoute = currentRoute,
                        diarySearchQuery = diarySearchQuery,
                        onDiarySearchQueryChange = { diarySearchQuery = it },
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    AppTopBar(
                        topLevel, showBackArrow, currentRoute, navController,
                        diarySearchQuery, { diarySearchQuery = it },
                    )
                },
                bottomBar = {
                    // The editor is a focused task, so it gets the full screen without a tab bar.
                    if (topLevel != null) {
                        CompactNavigationBar(topLevel, navController)
                    }
                },
            ) { padding ->
                DiaryNavHost(
                    modifier = Modifier.padding(padding),
                    navController = navController,
                    session = session,
                    today = today,
                    editorTarget = editorTarget,
                    currentRoute = currentRoute,
                    diarySearchQuery = diarySearchQuery,
                    onDiarySearchQueryChange = { diarySearchQuery = it },
                )
            }
        }
    }
}

@Composable
private fun CompactNavigationBar(
    topLevel: TopLevelDestination,
    navController: NavHostController,
) {
    BoxWithConstraints {
        // Five labels need more than a 360dp phone can give them. Keep the full accessible
        // names on the icons, but use an icon-first bar until there is room for each label.
        val showLabels = maxWidth >= 390.dp
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = Spacing.small)
                .clip(DiaryShapes.extraLarge)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = DiaryShapes.extraLarge,
                ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
        ) {
            TopLevelDestination.entries.forEach { item ->
                NavigationBarItem(
                    selected = item == topLevel,
                    onClick = { navController.switchTab(item, topLevel) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                        )
                    },
                    label = if (showLabels) {
                        { Text(stringResource(item.labelRes), maxLines = 1) }
                    } else {
                        null
                    },
                    alwaysShowLabel = showLabels,
                )
            }
        }
    }
}

/** Switches tabs, keeping one instance per tab and restoring where the user left it. */
private fun NavHostController.switchTab(
    item: TopLevelDestination,
    current: TopLevelDestination?,
) {
    if (item == current) return
    navigate(item.destination.route) {
        popUpTo(Destination.Dashboard.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    topLevel: TopLevelDestination?,
    showBackArrow: Boolean,
    currentRoute: String?,
    navController: NavHostController,
    diarySearchQuery: String,
    onDiarySearchQueryChange: (String) -> Unit,
) {
    Column {
    TopAppBar(
        // Transparent so the paper texture runs to the top edge; the divider below does the
        // separating that a tonal fill used to.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        title = {
            if (currentRoute == Destination.Diary.route) {
                TextField(
                    value = diarySearchQuery,
                    onValueChange = onDiarySearchQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.diary_search),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = DiaryShapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val label = topLevel?.let { stringResource(it.labelRes) } ?: titleFor(currentRoute)
                Text(text = label, style = MaterialTheme.typography.titleLarge)
            }
        },
        navigationIcon = {
            if (showBackArrow) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.nav_back),
                    )
                }
            }
        },
    )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}

@Composable
private fun DiaryNavHost(
    modifier: Modifier,
    navController: NavHostController,
    session: SessionState,
    today: LocalDate,
    editorTarget: EditorTarget,
    currentRoute: String?,
    diarySearchQuery: String,
    onDiarySearchQueryChange: (String) -> Unit,
) {
    // Capping the measure here rather than in each screen keeps one rule in one place: prose and
    // lists stop at a comfortable line length and become centred margins, while the dashboard
    // is allowed to be wider because past the expanded breakpoint its charts gain a column
    // instead of gaining line length. On a phone the cap never binds and nothing changes.
    val maxWidth = if (currentRoute == Destination.Dashboard.route) {
        ContentWidth.dashboard
    } else {
        ContentWidth.reading
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
    NavHost(
        navController = navController,
        startDestination = Destination.Dashboard.route,
        modifier = Modifier.contentWidth(maxWidth),
    ) {
        composable(Destination.Dashboard.route) {
            DashboardScreen(
                session = session,
                today = today,
                editorTarget = editorTarget,
                // “See all” is another way of selecting the Diary tab. Going through the
                // same state-restoring path as the navigation bar keeps a single instance of
                // each top-level screen, so Dashboard remains selectable after this shortcut.
                onOpenDiary = {
                    navController.switchTab(
                        item = TopLevelDestination.DIARY,
                        current = TopLevelDestination.DASHBOARD,
                    )
                },
                onOpenEditor = { id ->
                    editorTarget.open(id)
                    navController.navigate(Destination.Editor.route)
                },
                onOpenAuth = { navController.navigate(Destination.Auth.route) },
            )
        }

        composable(Destination.Diary.route) {
            DiaryListScreen(
                session = session,
                today = today,
                editorTarget = editorTarget,
                searchQuery = diarySearchQuery,
                onSearchQueryChange = onDiarySearchQueryChange,
                onOpenEditor = { id ->
                    editorTarget.open(id)
                    navController.navigate(Destination.Editor.route)
                },
            )
        }

        composable(Destination.Editor.route) {
            // Read once per arrival. Consuming during composition would hand out the
            // id on the first pass and null on the next, quietly turning an edit into
            // a blank new entry.
            val entryId = remember { editorTarget.consume() }
            DiaryEditorScreen(
                session = session,
                today = today,
                entryId = entryId,
                onDone = { navController.popBackStack() },
            )
        }

        composable(Destination.Mood.route) {
            MoodScreen(session = session, today = today)
        }

        composable(Destination.Calendar.route) {
            CalendarScreen(
                session = session,
                today = today,
                onOpenEntry = { id ->
                    editorTarget.open(id)
                    navController.navigate(Destination.Editor.route)
                },
            )
        }

        composable(Destination.Profile.route) {
            ProfileScreen(
                session = session,
                onSignIn = { navController.navigate(Destination.Auth.route) },
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
            )
        }

        composable(Destination.Auth.route) {
            AuthScreen(onAuthenticated = { navController.popBackStack() })
        }

        composable(Destination.Settings.route) {
            SettingsScreen(
                userId = session.userId,
                email = session.user?.email,
                onOpenSignIn = { navController.navigate(Destination.Auth.route) },
                onSignedOut = { navController.popBackStack(Destination.Dashboard.route, false) },
            )
        }
    }
    }
}

@Composable
private fun titleFor(route: String?): String = when (route) {
    Destination.Auth.route -> stringResource(Res.string.nav_account)
    Destination.Settings.route -> stringResource(Res.string.nav_profile)
    Destination.Profile.route -> stringResource(Res.string.nav_settings)
    else -> stringResource(Res.string.nav_entry)
}
