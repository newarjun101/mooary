package com.arjun.soul_note.ui.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.arjun.soul_note.core.notifications.NotificationManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * iOS authorization, which presents the system prompt at most once and then reports the
 * stored decision.
 *
 * The manager is injected rather than constructed because it holds the scheduling logic the
 * use case will use a moment later; two instances would still agree, since the notification
 * centre is a process-wide singleton, but one source of truth is easier to reason about.
 */
@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    val notificationManager = koinInject<NotificationManager>()
    val scope = rememberCoroutineScope()

    return remember(notificationManager, scope) {
        NotificationPermissionRequester { callback ->
            scope.launch { callback(notificationManager.requestPermission()) }
        }
    }
}
