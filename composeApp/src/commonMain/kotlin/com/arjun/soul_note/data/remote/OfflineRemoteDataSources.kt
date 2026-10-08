package com.arjun.soul_note.data.remote

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.AppException
import com.arjun.soul_note.core.error.ErrorKeys
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.data.remote.firebase.FirestoreWrite
import com.arjun.soul_note.data.remote.firebase.RemoteDocument

/**
 * The remote bindings used when no Firebase configuration is present.
 *
 * This is the "local-only build" path — a fresh clone, a unit test, or a desktop run with
 * no `DIARY_FIREBASE_*` variables. Reads still work (they come from the SQLDelight cache)
 * and every write still lands locally; only the network half is refused, and it is refused
 * with a typed error rather than a crash, so the UI can explain itself and keep going.
 *
 * The auth stub is the important one: it reports a signed-out session instead of pretending
 * a local user is authenticated, which keeps the rules model honest.
 */
internal class OfflineRemoteDataSources(
    private val logger: AppLogger,
) {
    private val offline = {
        logger.info("RemoteDataSources") { "no Firebase configuration; running local-only" }
        AppException(
            AppError.Unexpected(ErrorKeys.REMOTE_NOT_CONFIGURED),
        )
    }

    val diary: RemoteDiaryDataSource = object : RemoteDiaryDataSource {
        override suspend fun listEntries(userId: String, updatedAfterMillis: Long?): List<RemoteDocument> =
            emptyList()

        override suspend fun getEntry(userId: String, entryId: String): RemoteDocument? = null

        override suspend fun pushEntries(writes: List<FirestoreWrite>): Result<Unit> =
            Result.failure(offline())

        override suspend fun deleteUserDiary(userId: String): Result<Unit> =
            Result.failure(offline())
    }

    val mood: RemoteMoodDataSource = object : RemoteMoodDataSource {
        override suspend fun listMoods(userId: String, updatedAfterMillis: Long?): List<RemoteDocument> =
            emptyList()

        override suspend fun getMood(userId: String, moodId: String): RemoteDocument? = null

        override suspend fun pushMoods(writes: List<FirestoreWrite>): Result<Unit> =
            Result.failure(offline())

        override suspend fun deleteUserMoods(userId: String): Result<Unit> =
            Result.failure(offline())
    }

    val user: RemoteUserDataSource = object : RemoteUserDataSource {
        override suspend fun getProfile(userId: String): RemoteDocument? = null

        override suspend fun pushProfile(writes: List<FirestoreWrite>): Result<Unit> =
            Result.failure(offline())

        override suspend fun deleteProfile(userId: String): Result<Unit> =
            Result.failure(offline())
    }

    val auth: RemoteAuthDataSource = object : RemoteAuthDataSource {
        override suspend fun signIn(email: String, password: String): Result<RemoteIdentity> =
            Result.failure(offline())

        override suspend fun signUp(email: String, password: String): Result<RemoteIdentity> =
            Result.failure(offline())

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun resetPassword(email: String): Result<Unit> =
            Result.failure(offline())

        override suspend fun deleteAccount(): Result<Unit> = Result.failure(offline())

        override suspend fun restoreSession(): Result<RemoteIdentity?> = Result.success(null)

        override suspend fun refreshSession(): Result<RemoteIdentity> = Result.failure(offline())

        override suspend fun sendEmailVerification(): Result<Unit> = Result.failure(offline())

        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
            Result.failure(offline())

        override suspend fun currentUserId(): String? = null

        /** There is never a remote session to authenticate with in a local-only build. */
        override suspend fun idToken(): String? = null
    }
}
