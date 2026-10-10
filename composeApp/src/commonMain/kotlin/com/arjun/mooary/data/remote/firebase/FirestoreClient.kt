package com.arjun.mooary.data.remote.firebase

import com.arjun.mooary.core.error.AppError
import com.arjun.mooary.core.error.AppException
import com.arjun.mooary.core.error.ErrorKeys
import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.platform.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The one Firestore operation Mooary permits: overwrite a device-registration document. */
data class FirestoreDeviceRegistrationWrite(
    val path: String,
    val fields: Map<String, JsonElement>,
)

/**
 * Minimal Firestore REST transport for anonymous device registration. It has no read, query,
 * delete, sync, profile, diary, or mood APIs.
 */
class FirestoreClient(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val tokenProvider: suspend () -> String?,
    private val logger: AppLogger,
) {
    suspend fun upsertDeviceRegistration(write: FirestoreDeviceRegistrationWrite) {
        val token = tokenProvider()
            ?: throw AppException(AppError.PermissionDenied)
        val payload = buildJsonObject {
            put("writes", buildJsonArray {
                add(buildJsonObject {
                    put("update", buildJsonObject {
                        put("name", "${config.documentsResourceRoot}/${write.path}")
                        put("fields", buildJsonObject {
                            write.fields.forEach { (key, value) -> put(key, value) }
                        })
                    })
                })
            })
        }
        val response = http.post("${config.documentsRoot}:commit") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        if (response.status.isSuccess()) return
        response.bodyAsText()
        logger.warn("DeviceRegistration") { "Firestore registration write rejected: ${response.status.value}" }
        throw AppException(
            when (response.status) {
                HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized -> AppError.PermissionDenied
                HttpStatusCode.TooManyRequests -> AppError.Network
                else -> AppError.Unexpected(ErrorKeys.UNEXPECTED)
            },
        )
    }
}
