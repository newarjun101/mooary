package com.arjun.mooary

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arjun.mooary.di.diaryModules
import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.app_name
import org.jetbrains.compose.resources.stringResource
import com.arjun.mooary.ui.App
import org.koin.core.context.startKoin

/**
 * Desktop entry point.
 *
 * Koin is started before the window so the first frame already has its dependencies;
 * [App] then resolves everything through `koinInject`, so there is nothing to hand over.
 *
 * The window opens wider than a phone on purpose. It is resizable, and the layout switches to a
 * navigation rail with multi-column content past 600dp, so starting at 480dp would hide every
 * one of those branches behind a resize the user has to know to try.
 */
fun main() {
    startKoin { modules(diaryModules) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            state = rememberWindowState(size = DpSize(1100.dp, 820.dp)),
        ) {
            App()
        }
    }
}
