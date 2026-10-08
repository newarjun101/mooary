package com.arjun.soul_note.ui.permissions

import androidx.compose.runtime.Composable

/**
 * Asks the user for permission to post notifications.
 *
 * This exists as a separate seam because the answer cannot come from a background-capable
 * service: on Android 13+ `POST_NOTIFICATIONS` is granted from an Activity, and on iOS the
 * authorization prompt is only presented once. The scheduler can check permission
 * ([com.arjun.soul_note.core.notifications.NotificationManager.requestPermission]) but it can
 * never obtain it, so the request has to start at the switch the user actually touched.
 */
fun interface NotificationPermissionRequester {

    /**
     * Requests permission if it is not already held, then reports the outcome.
     *
     * The callback is asynchronous on every platform because both underlying prompts are.
     * Calling this when permission is already held resolves immediately with the current
     * answer rather than prompting again.
     */
    fun request(onResult: (granted: Boolean) -> Unit)
}

/** Remembers a requester bound to the current composition and Activity. */
@Composable
expect fun rememberNotificationPermissionRequester(): NotificationPermissionRequester
