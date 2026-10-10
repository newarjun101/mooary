package com.arjun.mooary.core.platform

import android.content.Context
import com.arjun.mooary.core.datetime.TimeZoneProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Android platform environment and Firebase configuration.
 *
 * The Firebase values come from `res/raw/firebase_config.json`, generated at build time from
 * the developer's own `google-services.json` by the `generateFirebaseConfig` Gradle task in
 * `androidApp/build.gradle.kts`. It cannot be copied there by hand: the console's file nests
 * apps under `client` as a JSON *array*, while [loadConfig] expects an object, so an unmodified
 * copy parses to nothing and the app starts offline with no warning.
 *
 * The generated file lands in the build directory rather than `src/main/res/raw`, so it is
 * never in the source tree and never reachable by a commit -- no ignore rule to forget. When
 * it is absent the app still starts and works fully offline, which is what makes unit tests
 * and the emulator story possible.
 */
object AndroidPlatform {
    private const val RAW_RESOURCE_NAME = "firebase_config"

    fun initialise(context: Context) {
        applicationContext = context.applicationContext
    }

    internal var applicationContext: Context? = null
        private set

    internal fun requireContext(): Context = requireNotNull(applicationContext) {
        "AndroidPlatform.initialise(context) must be called before the graph is built."
    }

    internal fun loadConfig(): FirebaseConfig? {
        val context = applicationContext ?: return null
        val root = runCatching {
            val rawId = context.resources.getIdentifier(RAW_RESOURCE_NAME, "raw", context.packageName)
            if (rawId == 0) {
                return@runCatching null
            }
            val text = context.resources.openRawResource(rawId).bufferedReader().use { it.readText() }
            Json.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: return null

        val client = root["client"]?.jsonObject ?: root
        val apiKey = client.string("current_key")
        val projectId = root.string("project_id") ?: client.string("project_id")
        val appId = client.string("mobilesdk_app_id")
        if (apiKey == null || projectId == null || appId == null) return null

        return FirebaseConfig(
            apiKey = apiKey,
            projectId = projectId,
            appId = appId,
            gcmSenderId = client.string("project_number"),
            storageBucket = client.string("storage_bucket"),
            firestoreBaseUrl = root.string("firestore_base_url") ?: DEFAULT_FIRESTORE,
            identityToolkitBaseUrl = root.string("identity_base_url") ?: DEFAULT_IDENTITY,
            secureTokenBaseUrl = root.string("secure_token_base_url") ?: DEFAULT_SECURE_TOKEN,
            isEmulator = root.string("emulator").toBoolean(),
        )
    }
}

private const val DEFAULT_FIRESTORE = "https://firestore.googleapis.com/v1"
private const val DEFAULT_IDENTITY = "https://identitytoolkit.googleapis.com/v1"
private const val DEFAULT_SECURE_TOKEN = "https://securetoken.googleapis.com/v1"

private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

actual fun loadFirebaseConfig(): FirebaseConfig? = AndroidPlatform.loadConfig()

actual fun platformEnvironment(): PlatformEnvironment = AndroidEnvironment

actual fun platformOperatingSystem(): String = "Android"

private object AndroidEnvironment : PlatformEnvironment {
    override val environment: Environment
        get() = if (AndroidPlatform.applicationContext?.let { isDebuggable(it) } == true) {
            Environment.DEVELOPMENT
        } else {
            Environment.PRODUCTION
        }

    override val timeZone: TimeZoneProvider = TimeZoneProvider()
    override val appVersion: String = "1.0.0"
    override val isDebugBuild: Boolean
        get() = AndroidPlatform.applicationContext?.let { isDebuggable(it) } == true
}

private fun isDebuggable(context: Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
