package com.arjun.soul_note

import androidx.compose.ui.window.ComposeUIViewController
import com.arjun.soul_note.di.diaryModules
import com.arjun.soul_note.ui.App
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * iOS entry point, called from Swift as `MainViewControllerKt.MainViewController()`.
 *
 * The Swift side can present this more than once, so Koin is started at most once: a second
 * `startKoin` would throw and take the whole app down.
 */
fun MainViewController(): UIViewController {
    startKoinOnce()
    return ComposeUIViewController { App() }
}

private var koinStarted = false

private fun startKoinOnce() {
    if (!koinStarted) {
        startKoin { modules(diaryModules) }
        koinStarted = true
    }
}
