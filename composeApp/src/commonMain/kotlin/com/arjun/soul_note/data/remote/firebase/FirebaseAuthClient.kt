package com.arjun.soul_note.data.remote.firebase

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.AppException
import com.arjun.soul_note.core.error.AuthenticationReason
import com.arjun.soul_note.core.error.authReasonFrom
import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/** The identity the app cares about, decoupled from the Identity Toolkit payload. */
data class AuthenticatedUser(
    val id: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val emailVerified: Boolean,
    val createdAt: Instant,
)

/** The persisted session: an id token plus the refresh token needed to mint new ones. */
data class AuthSession(
    val idToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresAtEpochSeconds: Long,
    val localId: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String?,
    val photoUrl: String?,
) {
    fun isExpired(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= expiresAtEpochSeconds - REFRESH_MARGIN_SECONDS

    private companion object {
        /** Refresh a minute early so a request never races the expiry. */
        const val REFRESH_MARGIN_SECONDS = 60
    }
}

/**
 * Firebase Authentication over the Identity Toolkit REST API.
 *
 * Supports exactly what the product needs — email/password sign-in and registration,
 * password reset, email verification, persistent sessions with silent token refresh,
 * password change and account deletion — using the same endpoints the official SDKs
 * call. Because these are the emulator's REST endpoints too, the whole auth flow is
 * covered by integration tests against a real Auth emulator.
 */
class FirebaseAuthClient(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val logger: AppLogger,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val identity = config.identityToolkitBaseUrl
    private val secureToken = config.secureTokenBaseUrl
    private val keyQuery = "?key=${config.apiKey}"


    suspend fun signUp(email: String, password: String): AuthSession =
        request(
            url = "$identity/accounts:signUp$keyQuery",
            payload = mapOf(
                "email" to JsonPrimitive(email),
                "password" to JsonPrimitive(password),
                "returnSecureToken" to JsonPrimitive(true),
            ),
        )

    suspend fun signInWithPassword(email: String, password: String): AuthSession =
        request(
            url = "$identity/accounts:signInWithPassword$keyQuery",
            payload = mapOf(
                "email" to JsonPrimitive(email),
                "password" to JsonPrimitive(password),
                "returnSecureToken" to JsonPrimitive(true),
            ),
        )

    /** Creates the restricted first-launch identity used only to register this device for push. */
    suspend fun signInAnonymously(): AuthSession =
        request(
            url = "$identity/accounts:signUp$keyQuery",
            payload = mapOf("returnSecureToken" to JsonPrimitive(true)),
        )

    /** Exchanges credentials for a fresh id token, used by "change password". */
    suspend fun signInForReauth(email: String, password: String): AuthSession =
        signInWithPassword(email, password)


    /**
     * Every authenticated Identity Toolkit call passes the id token in the request body
     * and *not* as an `Authorization: Bearer` header. The header is validated as an OAuth 2
     * access token, and a Firebase id token is not one, so sending it there fails with
     * "Request had invalid authentication credentials. Expected OAuth 2 access token" --
     * a 401 on a perfectly good token, with the body field never consulted. Firestore
     * accepts the same token as a bearer, which is what makes the mistake easy to miss.
     */

    /** Re-validates a stored id token and returns the current identity. */
    suspend fun lookup(idToken: String): AuthSession =
        request(
            url = "$identity/accounts:lookup$keyQuery",
            payload = mapOf("idToken" to JsonPrimitive(idToken)),
        )

    /** Mints a new id token from a refresh token. */
    suspend fun refresh(refreshToken: String): AuthSession {
        val response = http.submitForm(
            url = "$secureToken/token?key=${config.apiKey}",
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        )
        val body = readBody(response)
        val userId = body.string("user_id").orEmpty()
        return AuthSession(
            idToken = body.string("id_token").orEmpty(),
            refreshToken = body.string("refresh_token") ?: refreshToken,
            userId = userId,
            expiresAtEpochSeconds = epochSeconds() + (body.long("expires_in") ?: 3600L),
            localId = userId,
            email = body.string("email").orEmpty(),
            emailVerified = body.boolean("email_verified") ?: false,
            displayName = body.string("name"),
            photoUrl = body.string("picture"),
        ).also { validateIdentity(it, body) }
    }


    suspend fun sendPasswordResetEmail(email: String) {
        post(
            url = "$identity/accounts:sendOobCode$keyQuery",
            payload = mapOf(
                "requestType" to JsonPrimitive("PASSWORD_RESET"),
                "email" to JsonPrimitive(email),
            ),
        )
    }

    suspend fun sendEmailVerification(idToken: String) {
        post(
            url = "$identity/accounts:sendOobCode$keyQuery",
            payload = mapOf(
                "requestType" to JsonPrimitive("VERIFY_EMAIL"),
                "idToken" to JsonPrimitive(idToken),
            ),
        )
    }

    /** Applies an oobCode produced by a password-reset email. */
    suspend fun confirmPasswordReset(oobCode: String, newPassword: String) {
        post(
            url = "$identity/accounts:resetPassword$keyQuery",
            payload = mapOf(
                "oobCode" to JsonPrimitive(oobCode),
                "newPassword" to JsonPrimitive(newPassword),
            ),
        )
    }


    suspend fun deleteAccount(idToken: String) {
        post(
            url = "$identity/accounts:delete$keyQuery",
            payload = mapOf("idToken" to JsonPrimitive(idToken)),
        )
    }

    /** Updates the Auth-side display name. Pass `null` to clear it. */
    suspend fun updateProfile(idToken: String, displayName: String?): AuthSession =
        request(
            url = "$identity/accounts:update$keyQuery",
            payload = buildMap {
                put("idToken", JsonPrimitive(idToken))
                put("returnSecureToken", JsonPrimitive(true))
                if (displayName != null) {
                    put("displayName", JsonPrimitive(displayName))
                    put("email", JsonPrimitive(""))
                }
            },
        )

    /**
     * Changes the password after the caller has re-authenticated.
     *
     * Identity Toolkit requires a fresh, valid id token before it will accept a new
     * password, which is exactly why the data source signs in again first — it both
     * proves the current password and yields the token this call needs.
     */
    suspend fun changePassword(idToken: String, newPassword: String): AuthSession =
        request(
            url = "$identity/accounts:update$keyQuery",
            payload = mapOf(
                "idToken" to JsonPrimitive(idToken),
                "password" to JsonPrimitive(newPassword),
                "returnSecureToken" to JsonPrimitive(true),
            ),
        )


    private suspend fun request(
        url: String,
        payload: Map<String, kotlinx.serialization.json.JsonElement>,
    ): AuthSession {
        val body = post(url, payload)
        return sessionFrom(body).also { validateIdentity(it, body) }
    }

    private suspend fun post(
        url: String,
        payload: Map<String, kotlinx.serialization.json.JsonElement>,
    ): JsonObject = try {
        val response = http.post(url) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObjectOf(payload).toString(),
            )
        }
        readBody(response, url)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (app: AppException) {
        throw app
    } catch (throwable: Throwable) {
        throw AppException(throwable.toAppError(logger))
    }

    private suspend fun readBody(response: HttpResponse, url: String = ""): JsonObject {
        val text = response.bodyAsText()
        val body = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())

        if (response.status.isSuccess()) return body

        // Identity Toolkit always answers with `{"error":{"message":"CODE: text"}}`.
        val message = body.errorMessage()
        // Name the operation, not the url: every call carries `?key=<api key>`, and the
        // api key is a credential. The operation alone is what says which call was refused.
        val operation = url.substringBefore('?').substringAfterLast('/')
        // Only the code, e.g. `INVALID_ID_TOKEN`. The rest of the message is free text from
        // the server and can echo back the request, so it is not logged here.
        val code = message.orEmpty().substringBefore(':').ifBlank { "?" }
        logger.warn("FirebaseAuthClient") {
            "auth request failed with status ${response.status.value} on $operation ($code)"
        }
        throw AppException(
            when (response.status) {
                HttpStatusCode.TooManyRequests ->
                    AppError.Authentication(AuthenticationReason.TOO_MANY_REQUESTS)
                in HttpStatusCode.BadRequest..HttpStatusCode.Unauthorized ->
                    AppError.Authentication(authReasonFrom(message))
                HttpStatusCode.Forbidden -> AppError.PermissionDenied
                HttpStatusCode.NotFound -> AppError.Authentication(AuthenticationReason.USER_NOT_FOUND)
                else -> AppError.Unexpected(com.arjun.soul_note.core.error.ErrorKeys.UNEXPECTED)
            },
        )
    }

    private fun sessionFrom(body: JsonObject): AuthSession {
        val idToken = body.string("idToken")
            ?: throw AppException(AppError.Authentication(AuthenticationReason.SESSION_EXPIRED))
        val expiresIn = body.long("expiresIn") ?: 3600L
        return AuthSession(
            idToken = idToken,
            refreshToken = body.string("refreshToken").orEmpty(),
            userId = body.string("localId").orEmpty(),
            expiresAtEpochSeconds = epochSeconds() + expiresIn,
            localId = body.string("localId").orEmpty(),
            email = body.string("email").orEmpty(),
            emailVerified = body.boolean("emailVerified") ?: false,
            displayName = body.string("displayName"),
            photoUrl = body.string("photoUrl"),
        )
    }

    private fun validateIdentity(session: AuthSession, body: JsonObject) {
        if (session.localId.isBlank()) {
            throw AppException(AppError.Authentication(AuthenticationReason.SESSION_EXPIRED))
        }
        if (body.string("error") != null) {
            throw AppException(AppError.Authentication(authReasonFrom(body.string("error"))))
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.errorMessage(): String? =
        ((this["error"] as? JsonObject)?.get("message") as? JsonPrimitive)?.content

    private fun buildJsonObjectOf(payload: Map<String, kotlinx.serialization.json.JsonElement>): JsonObject =
        kotlinx.serialization.json.buildJsonObject { payload.forEach { (key, value) -> put(key, value) } }

    private fun epochSeconds(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
}
