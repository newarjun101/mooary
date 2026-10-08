package com.arjun.soul_note.data.remote.firebase

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.AppException
import com.arjun.soul_note.core.error.AuthenticationReason
import com.arjun.soul_note.core.error.ErrorKeys
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Wire-format tests for the Storage object API.
 *
 * Each one pins a URL or a status code measured against the Storage emulator rather than
 * inferred from documentation. The slashes inside an object name, the `/o/` segment, and the
 * difference between a missing photo and a refused one are all things that compile, run, and
 * return a plausible-looking answer when wrong -- a malformed read comes back as
 * `No such object: v0/b/users/{uid}/avatar`, which reads like an absent photo rather than the
 * bad request it is.
 */
class FirebaseStorageClientTest {

    private val bucket = "diary-b1954.firebasestorage.app"
    private val root = "https://firebasestorage.googleapis.com/v0/b"

    private val config = FirebaseConfig(
        apiKey = "key",
        projectId = "diary-b1954",
        appId = "app",
        storageBucket = bucket,
    )

    /** What the fake service saw, and what it answers with. */
    private class Fake(val status: HttpStatusCode = HttpStatusCode.OK, val content: ByteArray = ByteArray(0)) {
        val urls = mutableListOf<String>()
        val auth = mutableListOf<String?>()
    }

    private fun client(
        fake: Fake = Fake(),
        token: String? = "id-token",
        cfg: FirebaseConfig = config,
    ): FirebaseStorageClient {
        val engine = MockEngine { request: HttpRequestData ->
            fake.urls += request.url.toString()
            fake.auth += request.headers[HttpHeaders.Authorization]
            respond(fake.content, fake.status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return FirebaseStorageClient(HttpClient(engine), cfg, { token }, RecordingLogger())
    }


    @Test
    fun `the object name travels as an encoded name parameter`() = runTest {
        val fake = Fake()
        client(fake).uploadAvatar("uid-1", byteArrayOf(1, 2, 3))

        assertEquals(1, fake.urls.size)
        assertTrue(
            fake.urls.single().contains("name=users%2Fuid-1%2Favatar"),
            "expected an encoded name parameter, got ${fake.urls.single()}",
        )
    }

    @Test
    fun `a read addresses the object through the o segment`() = runTest {
        val fake = Fake()
        client(fake).downloadAvatar("uid-1")

        assertEquals("$root/$bucket/o/users%2Fuid-1%2Favatar?alt=media", fake.urls.single())
    }

    @Test
    fun `a delete addresses the object through the o segment`() = runTest {
        val fake = Fake()
        client(fake).deleteAvatar("uid-1")

        assertEquals("$root/$bucket/o/users%2Fuid-1%2Favatar", fake.urls.single())
    }

    @Test
    fun `the slashes inside the object name stay encoded`() = runTest {
        val fake = Fake()
        client(fake).downloadAvatar("uid-1")

        val path = fake.urls.single().substringAfter("/o/").substringBefore('?')
        assertFalse(path.contains('/'), "an unencoded separator makes the name unaddressable: $path")
    }


    @Test
    fun `every call carries the id token as a bearer`() = runTest {
        val fake = Fake()
        val subject = client(fake, token = "tok-123")

        subject.uploadAvatar("u", byteArrayOf(1))
        subject.downloadAvatar("u")
        subject.deleteAvatar("u")

        assertEquals(
            listOf<String?>("Bearer tok-123", "Bearer tok-123", "Bearer tok-123"),
            fake.auth,
        )
    }


    @Test
    fun `a missing avatar reads as absent rather than as an error`() = runTest {
        val result = client(Fake(HttpStatusCode.NotFound)).downloadAvatar("u")

        assertNull(result, "a 404 is the normal state for an account with no photo")
    }

    @Test
    fun `a refused read is a permission failure and not an absent photo`() = runTest {
        val error = assertFailsWith<AppException> {
            client(Fake(HttpStatusCode.Forbidden)).downloadAvatar("u")
        }

        assertEquals(AppError.PermissionDenied, error.error)
    }

    @Test
    fun `an expired session is translated for the ui to react to`() = runTest {
        val error = assertFailsWith<AppException> {
            client(Fake(HttpStatusCode.Unauthorized)).uploadAvatar("u", byteArrayOf(1))
        }

        assertEquals(AppError.Authentication(AuthenticationReason.SESSION_EXPIRED), error.error)
    }

    @Test
    fun `a rate limited upload is translated for the ui to react to`() = runTest {
        val error = assertFailsWith<AppException> {
            client(Fake(HttpStatusCode.TooManyRequests)).uploadAvatar("u", byteArrayOf(1))
        }

        assertEquals(AppError.Authentication(AuthenticationReason.TOO_MANY_REQUESTS), error.error)
    }

    @Test
    fun `deleting an avatar that is already gone succeeds`() = runTest {
        client(Fake(HttpStatusCode.NotFound)).deleteAvatar("u")
    }

    @Test
    fun `a project with no bucket reports storage unavailable rather than crashing`() = runTest {
        val error = assertFailsWith<AppException> {
            client(cfg = config.copy(storageBucket = null)).uploadAvatar("u", byteArrayOf(1))
        }

        assertEquals(AppError.Unexpected(ErrorKeys.STORAGE_UNAVAILABLE), error.error)
    }

    @Test
    fun `a rejection is logged without the object name or the bytes`() = runTest {
        val logger = RecordingLogger()
        val engine = MockEngine { respond(ByteArray(0), HttpStatusCode.Forbidden) }
        val subject = FirebaseStorageClient(HttpClient(engine), config, { "secret-token" }, logger)

        assertFailsWith<AppException> { subject.uploadAvatar("uid-1", byteArrayOf(1, 2, 3)) }

        val line = logger.lines.single()
        assertFalse(line.contains("uid-1"), "the uid is credential adjacent: $line")
        assertFalse(line.contains("secret-token"), "the token must never be logged: $line")
    }


    @Test
    fun `a read returns the object bytes untouched`() = runTest {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val result = client(Fake(HttpStatusCode.OK, payload)).downloadAvatar("u")

        assertContentEquals(payload, result)
    }

    private class RecordingLogger : AppLogger {
        val lines = mutableListOf<String>()
        override fun debug(tag: String, message: () -> String) { lines += message() }
        override fun info(tag: String, message: () -> String) { lines += message() }
        override fun warn(tag: String, throwable: Throwable?, message: () -> String) { lines += message() }
        override fun error(tag: String, throwable: Throwable?, message: () -> String) { lines += message() }
    }
}
