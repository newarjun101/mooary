package com.arjun.mooary.core.notifications

/**
 * Desktop has no push transport and is not a target anyone expects to be notified on.
 *
 * `null` rather than a failing implementation keeps device registration unavailable here.
 */
actual fun createPushTokenSource(): PushTokenSource? = null
