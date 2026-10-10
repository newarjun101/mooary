package com.arjun.mooary.core.notifications

/**
 * iOS has no push transport wired up.
 *
 * APNs needs a push key uploaded to Firebase and an `iosApp/` Xcode project to receive the
 * token, and neither exists in this repository. Returning `null` means the app reports push as
 * unsupported on iOS, which is true, rather than accepting a registration that nothing can
 * ever use.
 */
actual fun createPushTokenSource(): PushTokenSource? = null
