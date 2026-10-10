package com.arjun.mooary.core.notifications

import com.arjun.mooary.core.datetime.Iso8601
import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.platform.FirebaseConfig
import com.arjun.mooary.core.platform.platformOperatingSystem
import com.arjun.mooary.data.remote.firebase.AnonymousDeviceAuthClient
import com.arjun.mooary.data.remote.firebase.FirestoreClient
import com.arjun.mooary.data.remote.firebase.FirestoreCodec
import com.arjun.mooary.data.remote.firebase.FirestoreDeviceRegistrationWrite
import com.arjun.mooary.domain.model.FirestorePaths
import io.ktor.client.HttpClient
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Registers this installation for notifications without enabling diary synchronisation. */
fun interface DeviceRegistration {
    suspend fun register(): Result<Unit>
}

object UnsupportedDeviceRegistration : DeviceRegistration {
    override suspend fun register(): Result<Unit> = Result.success(Unit)
}

/**
 * Creates a short-lived anonymous identity solely to authorise a write of the current token.
 * The document key is derived from the stable device identity, so reinstalling replaces the
 * existing document instead of creating another token record for the same device.
 */
class FirebaseDeviceRegistration(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val logger: AppLogger,
) : DeviceRegistration {

    override suspend fun register(): Result<Unit> {
        return runCatching {
            val deviceId = createDeviceIdentity(logger).value().getOrThrow()
            // Android emulators can leave Firebase's token task unresolved because no usable
            // Play-services registration is available. Registration of the anonymous device
            // must not wait forever: store the device and OS now, then add a real token on a
            // later launch when Firebase supplies one.
            val token = withTimeoutOrNull(5.seconds) {
                createPushTokenSource()?.currentToken()?.getOrNull()
            }
            val session = AnonymousDeviceAuthClient(http, config, logger).createRegistrationCredential()
            val firestore = FirestoreClient(
                http = http,
                config = config,
                tokenProvider = { session.idToken },
                logger = logger,
            )
            firestore.upsertDeviceRegistration(
                FirestoreDeviceRegistrationWrite(
                    path = FirestorePaths.deviceRegistration(hashDeviceIdentity(deviceId)),
                    fields = buildMap {
                        put("os", FirestoreCodec.string(platformOperatingSystem()))
                        put("updatedAt", FirestoreCodec.timestamp(Iso8601.formatTimestamp(Clock.System.now())))
                        token?.let { put("token", FirestoreCodec.string(it)) }
                    },
                ),
            )
            Unit
        }.onSuccess {
            logger.info(TAG) { "device registration stored" }
        }.onFailure { error ->
            logger.warn(TAG) { "device registration failed: ${error::class.simpleName}" }
        }
    }

    private companion object {
        const val TAG = "DeviceRegistration"
    }
}
