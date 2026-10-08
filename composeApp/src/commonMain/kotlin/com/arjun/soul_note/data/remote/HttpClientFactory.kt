package com.arjun.soul_note.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The application's only HTTP client.
 *
 * Timeouts are explicit and short: the app is offline-first, so a slow request must fail
 * fast and let the local database keep serving the UI rather than holding a spinner. No
 * logging plugin is installed, because request bodies here are journal text.
 */
object HttpClientFactory {

    /** Builds the client with an explicit engine (OkHttp / CIO / Darwin). */
    fun create(engine: HttpClientEngine): HttpClient = HttpClient(engine) { configure() }

    /** Builds the client using Ktor's engine discovery. Used by tests and tooling. */
    fun create(): HttpClient = HttpClient { configure() }

    private fun io.ktor.client.HttpClientConfig<*>.configure() {
        expectSuccess = false

        install(ContentNegotiation) {
            json(jsonFormat)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }

        defaultRequest {
            headers.append("X-Client-Version", CLIENT_VERSION)
        }
    }

    /** Shared JSON codec. */
    val jsonFormat: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = true
        encodeDefaults = true
    }

    const val CLIENT_VERSION = "diary-kmp/1.0.0"
    const val REQUEST_TIMEOUT_MS = 15_000L
    const val CONNECT_TIMEOUT_MS = 10_000L
    const val SOCKET_TIMEOUT_MS = 20_000L
}
