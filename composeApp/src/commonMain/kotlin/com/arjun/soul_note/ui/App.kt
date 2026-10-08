package com.arjun.soul_note.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arjun.soul_note.core.platform.PlatformEnvironment
import com.arjun.soul_note.data.seed.SeedDemoDataUseCase
import com.arjun.soul_note.domain.repository.SettingsRepository
import com.arjun.soul_note.domain.repository.ThemeMode
import com.arjun.soul_note.ui.base.SessionHolder
import com.arjun.soul_note.ui.navigation.DiaryApp
import com.arjun.soul_note.ui.navigation.EditorTarget
import com.arjun.soul_note.ui.theme.DiaryTheme
import com.arjun.soul_note.ui.theme.PaperSurface
import org.koin.compose.koinInject

/**
 * The app's composition root.
 *
 * Everything above this line is a platform concern (Koin, a window, an activity); this is
 * the first shared function, which is why each platform entry point can be three lines long
 * and still boot the identical app.
 *
 * The graph is resolved through [koinInject] rather than passed in, so a screen can never be
 * launched against half a dependency graph by accident.
 */
@Composable
fun App() {
    val sessionHolder: SessionHolder = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val editorTarget: EditorTarget = koinInject()
    val seedDemoData: SeedDemoDataUseCase = koinInject()
    val environment: PlatformEnvironment = koinInject()

    val session by sessionHolder.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState(initial = null)
    val systemIsDark = isSystemInDarkTheme()

    // Demo content for design review, so the UI can be judged against a populated journal
    // rather than only empty states. Debug builds only, and the use case itself refuses to
    // write into a diary that already holds real entries.
    LaunchedEffect(session.userId) {
        if (environment.isDebugBuild) {
            seedDemoData(session.userId)
        }
    }

    // Until the stored settings arrive the chosen theme is not "the system theme", it is
    // unknown, and those are different things. Resolving it to the system paints a frame of
    // the wrong palette before the first real one: a DARK preference on a light system puts
    // light-theme ink on paper for the length of the settings read, which reads as an app
    // that ignored dark mode. Measured, that window is a blank page plus a single transition
    // frame, so the content gate below is worth the blank page -- it removes the text frame.
    val darkTheme = when (settings?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM, null -> systemIsDark
    }

    // Language preference stored in settings; applied at system level on Android if needed later.
    DiaryTheme(darkTheme = darkTheme) {
        if (settings != null) {
            DiaryApp(session = session, editorTarget = editorTarget)
        } else {
            PaperSurface { }
        }
    }
}
