package com.arjun.soul_note.core.platform

/**
 * Persistent, per-user secret storage.
 *
 * Auth tokens are the only credential the app holds at rest. Android uses
 * `EncryptedSharedPreferences`, iOS the Keychain, and desktop an OS-protected file in the
 * user's home directory. Implementations must never return a value to a log sink.
 */
interface SecureTokenStore {
    suspend fun read(): String?
    suspend fun write(token: String)
    suspend fun clear()
}

/** In-memory store for tests and for the "no persistence" case. */
class InMemoryTokenStore(initial: String? = null) : SecureTokenStore {
    private var value: String? = initial

    override suspend fun read(): String? = value
    override suspend fun write(token: String) {
        value = token
    }

    override suspend fun clear() {
        value = null
    }
}
