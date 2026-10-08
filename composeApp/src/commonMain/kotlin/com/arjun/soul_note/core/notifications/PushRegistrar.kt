package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.core.datetime.Iso8601
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.data.remote.firebase.FirestoreCodec
import com.arjun.soul_note.data.remote.firebase.FirestoreClient
import com.arjun.soul_note.data.remote.firebase.FirestoreWrite
import com.arjun.soul_note.domain.model.FirestorePaths
import kotlin.time.Clock

/**
 * Registration of this device as a target for remote notifications.
 *
 * Deliberately not a notification *sender*. Nothing in this app can push to a user: there is
 * no Cloud Function and no server here, so the only thing a client can do is tell a future
 * sender that this device exists. An interface that implied otherwise would be a promise this
 * project cannot keep.
 */
interface PushRegistrar {

    /** Whether this platform has a push transport at all. */
    val isSupported: Boolean

    /**
     * Records this device under [userId] so a sender can address it later.
     *
     * Idempotent: one document per device, and a token that rotates replaces its predecessor
     * rather than accumulating alongside it.
     *
     * Fails with [UnsupportedOperationException] where there is no transport, and with the
     * transport's own error where there is one but the project is not configured for it. The
     * second case is reported rather than swallowed, because "push silently does nothing" is
     * the failure nobody can diagnose.
     */
    suspend fun register(userId: String): Result<Unit>
}

/**
 * Where a registration token comes from.
 *
 * An interface so the common layer never names the messaging SDK, and so a test can supply a
 * token without one. Returns `null` from [createPushTokenSource] on a platform that has no
 * transport, which is how the unsupported case is distinguished from a working one.
 */
fun interface PushTokenSource {
    suspend fun currentToken(): Result<String>
}

/**
 * A registrar that reports "no transport here" without pretending to work.
 */
class UnsupportedPushRegistrar(
    @Suppress("unused") private val logger: AppLogger,
) : PushRegistrar {

    override val isSupported: Boolean = false

    override suspend fun register(userId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("No push transport on this platform"))
}

/**
 * Registers this device's Cloud Messaging token under the signed-in user.
 *
 * The token is a credential and is handled as one. It is never logged, never used as a
 * document id, and never placed in an error message: a path that turns up in a log identifies
 * the device without being usable to address it, which is the whole reason the document is
 * keyed by a hash.
 */
class FirebasePushRegistrar(
    private val firestore: FirestoreClient,
    private val transport: PushTokenSource,
    @Suppress("unused") private val logger: AppLogger,
) : PushRegistrar {

    override val isSupported: Boolean = true

    override suspend fun register(userId: String): Result<Unit> {
        val token = transport.currentToken().getOrElse { error ->
            // Only the exception's type, never its text: the SDK's own message can quote the
            // token it failed to return.
            logger.warn(TAG) { "no push token available: ${error::class.simpleName}" }
            return Result.failure(error)
        }

        val write = FirestoreWrite.Set(
            path = FirestorePaths.device(userId, hashPushToken(token)),
            fields = mapOf(
                "token" to FirestoreCodec.string(token),
                "updatedAt" to FirestoreCodec.timestamp(
                    Iso8601.formatTimestamp(Clock.System.now()),
                ),
            ),
        )
        return runCatching { firestore.commit(listOf(write)) }
            .fold(
                onSuccess = {
                    logger.info(TAG) { "registered this device for push" }
                    Result.success(Unit)
                },
                onFailure = { error ->
                    logger.error(TAG) { "could not register this device for push" }
                    Result.failure(error)
                },
            )
    }

    private companion object {
        const val TAG = "FirebasePushRegistrar"
    }
}

/**
 * The transport for this platform, or `null` where there is none.
 *
 * Android's answer additionally depends on the messaging SDK being usable at runtime, so a
 * build without the flag, a device where the SDK failed to initialise, and a platform with no
 * SDK at all all land on the same honest answer: unsupported.
 */
expect fun createPushTokenSource(): PushTokenSource?

/**
     * Picks the registrar that matches what this build can actually do.
     *
     * [firestore] is nullable because there are two independent reasons not to register and
     * neither implies the other: no messaging transport (iOS, or Android with the flag off) and
     * no Firebase configuration (a local-only build). Taking a non-null client made a
     * local-only build *construct* one, and that definition exists precisely to refuse being
     * built without a config -- so the app died at startup on the one platform that has no
     * transport and never needed the client at all.
     */
    fun createPushRegistrar(firestore: FirestoreClient?, logger: AppLogger): PushRegistrar =
        createPushTokenSource()
            ?.let { transport -> firestore?.let { FirebasePushRegistrar(it, transport, logger) } }
            ?: UnsupportedPushRegistrar(logger)
