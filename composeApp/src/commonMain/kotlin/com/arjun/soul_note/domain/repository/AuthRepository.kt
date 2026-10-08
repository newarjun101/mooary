package com.arjun.soul_note.domain.repository

import com.arjun.soul_note.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Authentication, expressed in domain terms only.
 *
 * The UI never sees a Firebase type, and the interface is implemented by a REST client
 * in `data.remote.firebase`, which makes the whole auth flow testable against the
 * Firebase Auth emulator.
 */
interface AuthRepository {

    suspend fun signIn(email: String, password: String): Result<User>

    suspend fun signUp(email: String, password: String): Result<User>

    suspend fun signOut(): Result<Unit>

    suspend fun resetPassword(email: String): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>

    fun observeAuthState(): Flow<User?>

    /** Restores a persisted session on cold start. Emits `null` when signed out. */
    suspend fun restoreSession(): User?

    /** Re-checks verification status with the backend. */
    suspend fun refreshUser(): Result<User>

    suspend fun sendEmailVerification(): Result<Unit>

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /** The uid of the currently signed-in user, or `null`. */
    fun currentUserId(): String?
}
