package com.arjun.soul_note.data.remote

import com.arjun.soul_note.data.remote.firebase.RemoteDocument
import com.arjun.soul_note.data.remote.firebase.FirestoreWrite

/**
 * Remote data sources: the boundary between the app and the backend.
 *
 * Everything above this layer works in domain terms, so swapping Firestore for another
 * backend — or replacing it with a fake in tests — is a one-file change. The Firebase
 * implementations live in `data.remote.firebase` and nothing else in the app imports an
 * HTTP client, a Firestore wire type, or an Identity Toolkit payload.
 */
interface RemoteDiaryDataSource {

    suspend fun listEntries(userId: String, updatedAfterMillis: Long? = null): List<RemoteDocument>

    suspend fun getEntry(userId: String, entryId: String): RemoteDocument?

    suspend fun pushEntries(writes: List<FirestoreWrite>): Result<Unit>

    suspend fun deleteUserDiary(userId: String): Result<Unit>
}

interface RemoteMoodDataSource {

    suspend fun listMoods(userId: String, updatedAfterMillis: Long? = null): List<RemoteDocument>

    suspend fun getMood(userId: String, moodId: String): RemoteDocument?

    suspend fun pushMoods(writes: List<FirestoreWrite>): Result<Unit>

    suspend fun deleteUserMoods(userId: String): Result<Unit>
}

interface RemoteUserDataSource {

    suspend fun getProfile(userId: String): RemoteDocument?

    suspend fun pushProfile(writes: List<FirestoreWrite>): Result<Unit>

    suspend fun deleteProfile(userId: String): Result<Unit>
}

interface RemoteAuthDataSource {

    suspend fun signIn(email: String, password: String): Result<RemoteIdentity>

    suspend fun signUp(email: String, password: String): Result<RemoteIdentity>

    suspend fun signOut(): Result<Unit>

    suspend fun resetPassword(email: String): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>

    suspend fun restoreSession(): Result<RemoteIdentity?>

    suspend fun refreshSession(): Result<RemoteIdentity>

    suspend fun sendEmailVerification(): Result<Unit>

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    suspend fun currentUserId(): String?

    /**
     * The OAuth bearer token to attach to authorised backend requests, or null when there is
     * no usable session.
     *
     * This is deliberately distinct from [currentUserId]: Firestore authenticates with the
     * caller's *ID token*, and a request carrying a uid instead is rejected with a 401 that
     * is indistinguishable from a security-rules fault.
     *
     * Implementations must return a token that is valid *now* — refreshing internally if
     * needed — because callers cannot tell a stale token from a bad one.
     */
    suspend fun idToken(): String?
}

/** A signed-in identity, free of any backend SDK type. */
data class RemoteIdentity(
    val userId: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String?,
    val photoUrl: String?,
    val createdAtEpochMillis: Long,
)
