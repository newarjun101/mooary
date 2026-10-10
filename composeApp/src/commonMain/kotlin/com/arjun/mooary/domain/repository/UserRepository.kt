package com.arjun.mooary.domain.repository

import com.arjun.mooary.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** The on-device profile. */
interface UserRepository {

    /** Always resolves from the local cache; created lazily on first write. */
    fun observeProfile(userId: String): Flow<UserProfile?>

    suspend fun getProfile(userId: String): Result<UserProfile?>

    /** Creates the local profile if it does not exist, otherwise patches it. */
    suspend fun upsertProfile(profile: UserProfile): Result<Unit>

    suspend fun updateDisplayName(userId: String, displayName: String?): Result<Unit>

    suspend fun updatePhotoUrl(userId: String, photoUrl: String?): Result<Unit>

    /** Locally purges every diary record, mood record, draft, and profile. */
    suspend fun purgeLocalData(userId: String)
}
