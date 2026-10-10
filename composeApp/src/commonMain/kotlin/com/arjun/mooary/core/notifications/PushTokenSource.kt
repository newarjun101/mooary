package com.arjun.mooary.core.notifications

/** Source of the current FCM registration token, if this platform supports FCM. */
fun interface PushTokenSource {
    suspend fun currentToken(): Result<String>
}

/** Returns the FCM token source on supported platforms, otherwise `null`. */
expect fun createPushTokenSource(): PushTokenSource?
