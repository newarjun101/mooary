package com.arjun.mooary.data.remote.firebase

import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.platform.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Short-lived credential used only to authorise one device-registration write. */
data class AnonymousRegistrationSession(val idToken: String)

/**
 * Creates a transient anonymous credential for device registration. The credential is never
 * persisted and the app exposes no account, profile, or sign-in functionality.
 */
class AnonymousDeviceAuthClient(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val logger: AppLogger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createRegistrationCredential(): AnonymousRegistrationSession {
        val response = http.post("${config.identityToolkitBaseUrl}/accounts:signUp?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("returnSecureToken", JsonPrimitive(true)) }.toString())
        }
        val payload = runCatching {
            json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        }.getOrNull()
        val token = (payload?.get("idToken") as? JsonPrimitive)?.content
        if (!response.status.isSuccess() || token.isNullOrBlank()) {
            logger.warn("DeviceRegistration") { "anonymous registration credential request failed" }
            error("Anonymous device-registration credential unavailable")
        }
        return AnonymousRegistrationSession(token)
    }
}
