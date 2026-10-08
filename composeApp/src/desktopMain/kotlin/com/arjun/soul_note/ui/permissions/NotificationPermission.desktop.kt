package com.arjun.soul_note.ui.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Desktop has no notification permission and no scheduler.
 *
 * Reports `false` so the caller takes the same path it would when a user declines, leaving
 * the reminders switch off. Claiming `true` would instead push the failure one step later,
 * into a scheduler that cannot run, and the user would be told their reminders were on.
 */
@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester =
    remember { NotificationPermissionRequester { callback -> callback(false) } }
