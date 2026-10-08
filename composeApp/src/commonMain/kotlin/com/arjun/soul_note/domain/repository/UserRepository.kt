package com.arjun.soul_note.domain.repository

import com.arjun.soul_note.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** The private `users/{userId}` document. */
interface UserRepository {

    /** Always resolves from the local cache; created lazily on first write. */
    fun observeProfile(userId: String): Flow<UserProfile?>

    suspend fun getProfile(userId: String): Result<UserProfile?>

    /** Creates the profile if it does not exist, otherwise patches it. Offline-safe. */
    suspend fun upsertProfile(profile: UserProfile): Result<Unit>

    suspend fun updateDisplayName(userId: String, displayName: String?): Result<Unit>

    suspend fun updatePhotoUrl(userId: String, photoUrl: String?): Result<Unit>

    /** Removes the profile and every owned collection on the backend, then locally. */
    suspend fun deleteAllUserData(userId: String): Result<Unit>

    /** Locally purges everything belonging to [userId], used on sign-out. */
    suspend fun purgeLocalData(userId: String)
}
