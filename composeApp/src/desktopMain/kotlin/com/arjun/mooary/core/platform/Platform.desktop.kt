package com.arjun.mooary.core.platform

import com.arjun.mooary.core.datetime.TimeZoneProvider
import java.io.File
import java.util.Properties

/**
 * Desktop configuration.
 *
 * Values come from environment variables or `~/.diary-app/firebase.properties`, so the
 * binary carries no credentials. The same variables switch the client onto the Firebase
 * emulator, which is how a developer runs the whole stack with no production data:
 *
 * ```
 * export DIARY_FIREBASE_API_KEY="fake-api-key"
 * export DIARY_FIREBASE_PROJECT_ID="demo-diary"
 * export DIARY_FIREBASE_APP_ID="1:000000000000:web:0000000000000000000000"
 * export DIARY_FIREBASE_EMULATOR=true      # optional, points at localhost
 * ```
 */
actual fun loadFirebaseConfig(): FirebaseConfig? {
    val properties = readLocalProperties("firebase.properties")
    fun value(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        System.getenv(key)?.takeIf { it.isNotBlank() }
            ?: properties.getProperty(key.lowercase().replace('_', '.'))?.takeIf { it.isNotBlank() }
    }

    val apiKey = value("DIARY_FIREBASE_API_KEY") ?: return null
    val projectId = value("DIARY_FIREBASE_PROJECT_ID") ?: return null
    val appId = value("DIARY_FIREBASE_APP_ID") ?: return null

    val emulator = value("DIARY_FIREBASE_EMULATOR").toBoolean()
    val firestoreHost = value("DIARY_FIRESTORE_EMULATOR_HOST") ?: "127.0.0.1:8080"
    val identityHost = value("DIARY_AUTH_EMULATOR_HOST") ?: "127.0.0.1:9099"
    val tokenHost = value("DIARY_AUTH_EMULATOR_HOST") ?: "127.0.0.1:9099"

    return FirebaseConfig(
        apiKey = apiKey,
        projectId = projectId,
        appId = appId,
        gcmSenderId = value("DIARY_FIREBASE_SENDER_ID"),
        storageBucket = value("DIARY_FIREBASE_STORAGE_BUCKET"),
        firestoreBaseUrl = if (emulator) {
            "http://$firestoreHost/v1"
        } else {
            "https://firestore.googleapis.com/v1"
        },
        identityToolkitBaseUrl = if (emulator) {
            "http://$identityHost/identitytoolkit.googleapis.com/v1"
        } else {
            "https://identitytoolkit.googleapis.com/v1"
        },
        secureTokenBaseUrl = if (emulator) {
            "http://$tokenHost/securetoken.googleapis.com/v1"
        } else {
            "https://securetoken.googleapis.com/v1"
        },
        isEmulator = emulator,
    )
}

actual fun platformEnvironment(): PlatformEnvironment = DesktopEnvironment

actual fun platformOperatingSystem(): String = "Desktop"

private object DesktopEnvironment : PlatformEnvironment {
    override val environment: Environment
        get() = when (System.getenv("DIARY_ENVIRONMENT")?.uppercase()) {
            "PRODUCTION" -> Environment.PRODUCTION
            "STAGING" -> Environment.STAGING
            else -> Environment.DEVELOPMENT
        }

    override val timeZone: TimeZoneProvider = TimeZoneProvider()
    override val appVersion: String = System.getProperty("app.version") ?: "1.0.0"
    override val isDebugBuild: Boolean
        get() = System.getenv("DIARY_ENVIRONMENT")?.uppercase() != "PRODUCTION"
}

/**
 * Reads a `key=value` file from the app's data directory, so a developer can keep local
 * Firebase settings out of the shell environment and out of version control.
 */
internal fun readLocalProperties(fileName: String): Properties {
    val properties = Properties()
    val file = File(System.getProperty("user.home"), ".diary-app/$fileName")
    if (file.exists()) {
        file.inputStream().use { properties.load(it) }
    }
    return properties
}
