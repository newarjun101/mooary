package com.arjun.soul_note.core.notifications

/**
 * Desktop has no push transport and is not a target anyone expects to be notified on.
 *
 * `null` rather than a failing implementation, so the registrar is constructed as
 * [UnsupportedPushRegistrar] and the reason lives in one place.
 */
actual fun createPushTokenSource(): PushTokenSource? = null
