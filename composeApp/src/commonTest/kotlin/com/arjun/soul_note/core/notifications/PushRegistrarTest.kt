package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.FirebaseConfig
import com.arjun.soul_note.data.remote.firebase.FirestoreClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests for device registration.
 *
 * The load-bearing assertion is the negative one: a registration token is a credential, and
 * the document name is the one part of a Firestore write that leaks freely into logs, error
 * messages and rules traces. So the token is checked *absent* from the path while present in
 * the body, and that pair is what the hash exists to make true.
 */
class PushRegistrarTest {

    private val userId = "user-1"
    private val token = "fcm-token-abcdef0123456789"
    private val deviceId = "device-identity-abcdef0123456789"

    private val config = FirebaseConfig(
        apiKey = "key",
        projectId = "diary-b1954",
        appId = "app",
    )

    /** What the fake Firestore saw. */
    private class Fake {
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<String>()

        /**
         * The document each commit addressed, read out of `writes[].update.name`.
         *
         * The `:commit` endpoint puts the whole batch in one body, so the document name is not
         * in the URL at all -- which is worth knowing for its own sake, since it means the name
         * is the only part of this write that a log line would carry.
         */
        val names: List<String>
            get() = bodies.map { body ->
                Regex(""""name"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1).orEmpty()
            }
    }

    private fun firestore(fake: Fake, status: HttpStatusCode = HttpStatusCode.OK): FirestoreClient {
        val engine = MockEngine { request: HttpRequestData ->
            fake.urls += request.url.toString()
            fake.bodies += (request.body as io.ktor.http.content.TextContent).text
            respond(
                content = """{"writeResults":[{"updateTime":"2026-01-01T00:00:00Z"}]}""",
                status = status,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        return FirestoreClient(
            http = HttpClient(engine),
            config = config,
            tokenProvider = { "id-token" },
            logger = RecordingLogger(),
        )
    }

    private fun registrar(
        fake: Fake = Fake(),
        source: PushTokenSource = PushTokenSource { Result.success(token) },
        device: DeviceIdentity = DeviceIdentity { Result.success(deviceId) },
    ): FirebasePushRegistrar = FirebasePushRegistrar(
        firestore = firestore(fake),
        transport = source,
        deviceIdentity = device,
        logger = RecordingLogger(),
    )

    @Test
    fun `a device registers under the user and stable device identity`() = runTest {
        val fake = Fake()
        registrar(fake).register(userId).getOrThrow()

        val expected = "users/$userId/devices/${hashDeviceIdentity(deviceId)}"
        assertEquals(1, fake.urls.size)
        assertTrue(
            fake.names.single().endsWith(expected),
            "expected the write to address $expected but it was ${fake.names.single()}",
        )
    }

    @Test
    fun `the token never appears in the path but does appear in the body`() = runTest {
        val fake = Fake()
        registrar(fake).register(userId).getOrThrow()

        assertFalse(token in fake.names.single(), "the token must not be in the document name")
        assertTrue(token in fake.bodies.single(), "the token has to reach Firestore somehow")
    }

    @Test
    fun `registering twice keeps one document`() = runTest {
        val fake = Fake()
        val push = registrar(fake)
        push.register(userId).getOrThrow()
        push.register(userId).getOrThrow()

        assertEquals(2, fake.urls.size)
        assertEquals(fake.names.first(), fake.names.last(), "the same device must reuse its document")
    }

    @Test
    fun `a rotated token overwrites the same device document`() = runTest {
        val first = Fake()
        val second = Fake()
        registrar(first, PushTokenSource { Result.success("token-one") }).register(userId)
        registrar(second, PushTokenSource { Result.success("token-two") }).register(userId)

        assertEquals(
            first.names.single(),
            second.names.single(),
            "a rotated token must overwrite the existing device record",
        )
    }

    @Test
    fun `a token that cannot be read fails without writing anything`() = runTest {
        val fake = Fake()
        val push = registrar(fake, PushTokenSource { Result.failure(IllegalStateException("no")) })

        val result = push.register(userId)

        assertTrue(result.isFailure)
        assertTrue(fake.bodies.isEmpty(), "nothing should be written without a token")
    }

    @Test
    fun `a refused write is reported rather than swallowed`() = runTest {
        val fake = Fake()
        val push = FirebasePushRegistrar(
            firestore = firestore(fake, HttpStatusCode(403, "PERMISSION_DENIED")),
            transport = PushTokenSource { Result.success(token) },
            deviceIdentity = DeviceIdentity { Result.success(deviceId) },
            logger = RecordingLogger(),
        )

        assertTrue(push.register(userId).isFailure)
    }

    @Test
    fun `a platform with no transport says so instead of pretending`() = runTest {
        val push = UnsupportedPushRegistrar(RecordingLogger())

        assertFalse(push.isSupported)
        assertTrue(push.register(userId).isFailure)
    }

    @Test
    fun `nothing logged during a registration contains the token`() = runTest {
        val logger = RecordingLogger()
        val fake = Fake()
        FirebasePushRegistrar(
            firestore = firestore(fake),
            transport = PushTokenSource { Result.success(token) },
            deviceIdentity = DeviceIdentity { Result.success(deviceId) },
            logger = logger,
        ).register(userId).getOrThrow()

        assertTrue(logger.lines.isNotEmpty(), "a successful registration should say so")
        assertFalse(
            logger.lines.any { token in it },
            "a credential must not reach a log line: ${logger.lines}",
        )
    }

    @Test
    fun `a failed token read logs no part of the sdk's message`() = runTest {
        val logger = RecordingLogger()
        val noise = "token $token was rejected"
        FirebasePushRegistrar(
            firestore = firestore(Fake()),
            transport = PushTokenSource { Result.failure(IllegalStateException(noise)) },
            deviceIdentity = DeviceIdentity { Result.success(deviceId) },
            logger = logger,
        ).register(userId)

        assertFalse(
            logger.lines.any { token in it },
            "the SDK's own text can quote the token: ${logger.lines}",
        )
    }

    @Test
    fun `the device hash is stable and never leaks the raw identity`() {
        assertEquals(hashDeviceIdentity(deviceId), hashDeviceIdentity(deviceId))
        assertEquals(16, hashDeviceIdentity(deviceId).length)
        assertFalse(deviceId in hashDeviceIdentity(deviceId))
        assertFalse(hashDeviceIdentity("a") == hashDeviceIdentity("b"))
    }
}

/** Captures log lines so a test can assert that a credential never reached one. */
class RecordingLogger : AppLogger {
    val lines = mutableListOf<String>()
    override fun debug(tag: String, message: () -> String) { lines += message() }
    override fun info(tag: String, message: () -> String) { lines += message() }
    override fun warn(tag: String, throwable: Throwable?, message: () -> String) { lines += message() }
    override fun error(tag: String, throwable: Throwable?, message: () -> String) { lines += message() }
}
