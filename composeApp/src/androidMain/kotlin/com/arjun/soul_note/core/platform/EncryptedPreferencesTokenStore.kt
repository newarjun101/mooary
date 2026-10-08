package com.arjun.soul_note.core.platform

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.arjun.soul_note.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android token store backed by `EncryptedSharedPreferences`.
 *
 * The auth token is the only credential the app holds at rest, so it is written through a
 * `MasterKey` held in the Android Keystore: the bytes on disk are AES-GCM ciphertext and
 * the key never leaves the keystore. A backup of the preferences file is useless without
 * the device's keystore entry.
 *
 * Every operation is wrapped in `runCatching`: a keystore failure (a restored backup onto
 * a different device, a corrupted master key) must degrade to "signed out", never to a crash
 * on launch. Losing the token is recoverable; failing to start is not.
 */
class EncryptedPreferencesTokenStore(
    context: Context,
    private val logger: AppLogger,
) : SecureTokenStore {

    private val appContext = context.applicationContext

    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        runCatching { preferences.getString(KEY_TOKEN, null) }
            .onFailure { logger.warn(TAG) { "could not read the stored token" } }
            .getOrNull()
    }

    override suspend fun write(token: String) {
        withContext(Dispatchers.IO) {
            runCatching { preferences.edit().putString(KEY_TOKEN, token).apply() }
                .onFailure { logger.error(TAG) { "could not store the token" } }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            // The key too, not just the value: clearing the entry keeps a revoked token from
            // lingering in a readable preferences file.
            runCatching { preferences.edit().clear().apply() }
                .onFailure { logger.warn(TAG) { "could not clear the stored token" } }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "diary_secure_session"
        const val KEY_TOKEN = "firebase_id_token"
        const val TAG = "EncryptedTokenStore"
    }
}
