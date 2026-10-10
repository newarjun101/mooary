package com.arjun.mooary.core.platform

import com.arjun.mooary.core.datetime.TimeZoneProvider

/**
 * Firebase project wiring, resolved per platform from the mechanism that platform
 * already uses. It is never hardcoded and never reaches business logic: only the
 * Firebase clients in `data.remote.firebase` read it.
 */
data class FirebaseConfig(
    val apiKey: String,
    val projectId: String,
    val appId: String,
    val authDomain: String = "$projectId.firebaseapp.com",
    val gcmSenderId: String? = null,
    val firestoreBaseUrl: String = "https://firestore.googleapis.com/v1",
    val identityToolkitBaseUrl: String = "https://identitytoolkit.googleapis.com/v1",
    val secureTokenBaseUrl: String = "https://securetoken.googleapis.com/v1",
    val storageBucket: String? = null,
    /**
     * The Storage object API, up to and including `/v0/b`. A platform loader points this at
     * `http://127.0.0.1:9199/v0/b` to reach the emulator, exactly as it does for
     * [firestoreBaseUrl].
     */
    val storageBaseUrl: String = "https://firebasestorage.googleapis.com/v0/b",
    val isEmulator: Boolean = false,
) {
    val isValid: Boolean
        get() = apiKey.isNotBlank() && projectId.isNotBlank() && appId.isNotBlank()

    /** Null until a bucket is known; Storage is not enabled on every project. */
    val storageRoot: String?
        get() = storageBucket?.takeIf { it.isNotBlank() }?.let { "$storageBaseUrl/$it" }

    /**
     * The single object this app stores. The name is chosen here rather than by the caller,
     * so a caller cannot pick a path the rules would refuse.
     */
    fun avatarPath(userId: String): String = "users/$userId/avatar"

    val documentsRoot: String
        get() = "$firestoreBaseUrl/projects/$projectId/databases/(default)/documents"

    /**
     * The same root as a resource name rather than a URL, for the `name` of a
     * commit write. Firestore rejects a full URL there with "Document name ...
     * lacks ... at index 0", and it rejects the bare relative path too, so the
     * only accepted form is `projects/{p}/databases/(default)/documents/...`
     * with no scheme, host, or API version prefix.
     */
    val documentsResourceRoot: String
        get() = "projects/$projectId/databases/(default)/documents"
}

/** Which backend environment the build is talking to. */
enum class Environment { DEVELOPMENT, STAGING, PRODUCTION }

/** Everything the app needs that only a platform can provide. */
interface PlatformEnvironment {
    val environment: Environment
    val timeZone: TimeZoneProvider
    val appVersion: String
    val isDebugBuild: Boolean
}

/**
 * Loads [FirebaseConfig] from the platform's own configuration store.
 *
 * * Android — parsed from `res/raw/firebase_config.json`, generated from
 *   `google-services.json` by the `firebase_config` Gradle task. Not committed.
 * * iOS — parsed from `FirebaseConfig.plist` in the app bundle.
 * * Desktop — read from `DIARY_FIREBASE_*` environment variables.
 *
 * When none is present the app still starts and still works fully offline, which is what
 * makes the emulator and unit-test story possible.
 */
expect fun loadFirebaseConfig(): FirebaseConfig?

expect fun platformEnvironment(): PlatformEnvironment

/** A stable platform label stored with a notification registration. */
expect fun platformOperatingSystem(): String
