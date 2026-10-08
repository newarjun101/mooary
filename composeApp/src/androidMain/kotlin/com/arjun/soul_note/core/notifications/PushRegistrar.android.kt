package com.arjun.soul_note.core.notifications

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android push, backed by the Cloud Messaging SDK.
 *
 * This is the only Firebase SDK in the project. Everything else Firebase-shaped is hand-rolled
 * over REST from shared code, and push genuinely cannot be: delivery depends on Google's
 * transport reaching the device, so there is no endpoint to call.
 *
 * Returns `null` — meaning "no transport" — in three separate situations that all deserve the
 * same answer:
 *
 *  - the build was made without `-Pdiary.firebaseMessaging=true`, checked first and against
 *    the compiled-in flag rather than by probing the SDK. A default build must not ask for a
 *    token at all: the service that would receive it is not declared, so the token is not
 *    merely useless, it is a network round trip and a device document written for a feature
 *    the build says is absent. `FirebaseMessaging.getInstance()` answers perfectly happily in
 *    that build — only `android:enabled` on the service is gated — so probing the SDK cannot
 *    detect this case, however much it looks like it should.
 *  - the SDK failed to initialise, which happens when the four manifest meta-data entries are
 *    missing or the project has no Android app registered.
 *  - messaging is switched off on the Firebase project.
 *
 * The second and third are detected by *using* the SDK rather than by inspecting anything,
 * because a flag says what the build intended and only the SDK knows what the device managed
 * to do. The first is the one exception, and the reason is the opposite: there the build
 * intent is the whole answer, and a device probe would only invite a request that must not be
 * made.
 */
actual fun createPushTokenSource(): PushTokenSource? {
    if (!FCM_ENABLED) return null
    val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return null
    // Touching the instance is not enough: an uninitialised SDK throws on first real use, from
    // a background thread, with no message. Asking for a property forces that here instead.
    if (runCatching { messaging.isAutoInitEnabled }.isFailure) return null
    return PushTokenSource { messaging.awaitToken() }
}

/**
 * The SDK's registration token, as a suspending call.
 *
 * `addOnSuccessListener`/`addOnFailureListener` rather than `await()` because the failure case
 * is real and must not become an exception: with Cloud Messaging unavailable the request simply
 * never completes, and a plain `await()` would hold a coroutine open on every sign-in for
 * nothing. A cancelled caller is released here; the SDK's own request is not cancellable and
 * is left to finish on its own.
 */
private suspend fun FirebaseMessaging.awaitToken(): Result<String> =
    suspendCancellableCoroutine { continuation ->
        token
            .addOnSuccessListener { token ->
                if (continuation.isActive) continuation.resume(Result.success(token))
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resume(Result.failure(error))
            }
    }
