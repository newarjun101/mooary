package com.arjun.mooary.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arjun.mooary.core.platform.PlatformEnvironment
import com.arjun.mooary.core.notifications.DeviceRegistration
import com.arjun.mooary.data.seed.SeedDemoDataUseCase
import com.arjun.mooary.domain.repository.SettingsRepository
import com.arjun.mooary.domain.repository.ThemeMode
import com.arjun.mooary.ui.base.SessionHolder
import com.arjun.mooary.ui.navigation.DiaryApp
import com.arjun.mooary.ui.navigation.EditorTarget
import com.arjun.mooary.ui.permissions.RequestNotificationPermissionOnAppEntry
import com.arjun.mooary.ui.theme.DiaryTheme
import com.arjun.mooary.ui.theme.PaperSurface
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
    val deviceRegistration: DeviceRegistration = koinInject()

    val session by sessionHolder.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState(initial = null)

    // Demo content for design review, so the UI can be judged against a populated journal
    // rather than only empty states. Debug builds only, and the use case itself refuses to
    // write into a diary that already holds real entries.
    LaunchedEffect(session.userId) {
        if (environment.isDebugBuild) {
            seedDemoData(session.userId)
        }
    }

    LaunchedEffect(Unit) {
        deviceRegistration.register()
    }

    RequestNotificationPermissionOnAppEntry()

    val darkTheme = when (settings?.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT, null -> false
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
