package com.arjun.mooary.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android's runtime `POST_NOTIFICATIONS` prompt, which only an Activity can present.
 *
 * Below API 33 the permission does not exist and is granted at install time, so the answer
 * is `true` without a dialog — prompting there would be a no-op the user cannot see.
 */
@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    val context = LocalContext.current
    // Held in a mutable state holder rather than captured directly, so the callback the
    // remembered launcher invokes is the one from the current composition and not a stale
    // one captured the first time this composed.
    val callback = remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> callback.value(granted) }

    return remember(launcher, context) {
        NotificationPermissionRequester { onResult ->
            callback.value = onResult
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val alreadyHeld = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            if (alreadyHeld) onResult(true) else launcher.launch(permission)
        }
    }
}

@Composable
actual fun RequestNotificationPermissionOnAppEntry() {
    val requester = rememberNotificationPermissionRequester()
    LaunchedEffect(requester) { requester.request { } }
}
