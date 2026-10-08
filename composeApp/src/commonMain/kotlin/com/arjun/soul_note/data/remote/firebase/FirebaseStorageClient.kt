package com.arjun.soul_note.data.remote.firebase

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.AppException
import com.arjun.soul_note.core.error.AuthenticationReason
import com.arjun.soul_note.core.error.ErrorKeys
import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Firebase Cloud Storage over its object REST API — no SDK, the same choice as
 * [FirestoreClient]. One object per account: `users/{uid}/avatar`.
 *
 * Every call is on the **`v0`** endpoint, and that is not a stylistic preference. It was
 * measured against the Storage emulator with two accounts: a `v1` multipart upload answers
 * `200` for *every* case — another user's path, `text/plain`, a 6 MB body — so rules are not
 * evaluated there at all and a green `v1` run proves nothing. `v1` DELETE is `501` as well.
 * `v0` enforces the rules, which is the only reason this class can be trusted.
 *
 * The cost of staying on `v0` is that it records `application/octet-stream` whatever
 * `Content-Type` header is sent, so [storage.rules] asserts ownership and size and nothing
 * about the type. That is affordable here because the object name is chosen by
 * [FirebaseConfig.avatarPath] rather than by a caller.
 */
class FirebaseStorageClient(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val tokenProvider: suspend () -> String?,
    private val logger: AppLogger,
) {
    private val root: String? = config.storageRoot

    /**
     * Writes [bytes] as this account's avatar, replacing any existing object.
     *
     * The `name` query parameter carries the object path; Ktor percent-encodes it, which
     * matters because the slashes must arrive encoded rather than as URL separators.
     */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, contentType: String = "image/jpeg") {
        val root = requireRoot()
        val path = config.avatarPath(userId)
        val response = guarded<HttpResponse> {
            http.post("$root/o") {
                parameter("name", path)
                tokenProvider()?.let { bearer(it) }
                contentType(ContentType.parse(contentType))
                setBody(bytes)
            }
        }
        ensureSuccess(response, "avatar upload")
    }

    /**
     * The avatar bytes, or null when the account has not set one.
     *
     * A missing object is a normal state, not a failure, so `404` becomes null rather than
     * an [AppError.NotFound]. `alt=media` is what returns the bytes; without it the same URL
     * answers with the JSON metadata.
     */
    suspend fun downloadAvatar(userId: String): ByteArray? {
        val root = requireRoot()
        val response = guarded<HttpResponse> {
            http.get("$root/o/${objectSegment(config.avatarPath(userId))}") {
                parameter("alt", "media")
                tokenProvider()?.let { bearer(it) }
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        ensureSuccess(response, "avatar download")
        return response.body<ByteArray>()
    }

    /** Removes the avatar. Succeeds quietly when there was nothing to remove. */
    suspend fun deleteAvatar(userId: String) {
        val root = requireRoot()
        val response = guarded<HttpResponse> {
            http.delete("$root/o/${objectSegment(config.avatarPath(userId))}") {
                tokenProvider()?.let { bearer(it) }
            }
        }
        if (response.status == HttpStatusCode.NotFound) return
        ensureSuccess(response, "avatar delete")
    }

    /**
     * A Storage object name is one opaque segment. The slashes inside it have to be
     * percent-encoded, or the emulator parses the prefix as part of the name and answers
     * `No such object: v0/b/users/{uid}/avatar` — which reads like a missing file rather
     * than the malformed request it is.
     */
    private fun objectSegment(path: String): String = path.encodeURLPathPart()

    private fun requireRoot(): String = root ?: throw AppException(
        AppError.Unexpected(ErrorKeys.STORAGE_UNAVAILABLE),
    )

    private suspend fun ensureSuccess(response: HttpResponse, operation: String) {
        if (response.status.isSuccess()) return
        val error = when (response.status) {
            HttpStatusCode.Unauthorized -> AppError.Authentication(AuthenticationReason.SESSION_EXPIRED)
            HttpStatusCode.Forbidden -> AppError.PermissionDenied
            HttpStatusCode.NotFound -> AppError.NotFound
            HttpStatusCode.TooManyRequests -> AppError.Authentication(AuthenticationReason.TOO_MANY_REQUESTS)
            else -> AppError.Unexpected(ErrorKeys.STORAGE)
        }
        // The path is the app's own fixed `users/{uid}/avatar`, but the uid is a credential
        // adjacent identifier, so only the status and operation are logged -- never the
        // object name, the body, or the bytes.
        logger.warn("FirebaseStorageClient") { "$operation rejected with ${response.status.value}" }
        throw AppException(error)
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (app: AppException) {
        throw app
    } catch (throwable: Throwable) {
        throw AppException(throwable.toAppError(logger))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

}
