package com.arjun.mooary.data.repository

import com.arjun.mooary.core.datetime.TimeProvider
import com.arjun.mooary.core.error.suspendRunCatchingApp
import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.validation.Limits
import com.arjun.mooary.core.validation.ValidationResult
import com.arjun.mooary.core.validation.Validators
import com.arjun.mooary.data.local.LocalStore
import com.arjun.mooary.domain.model.UserProfile
import com.arjun.mooary.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * Profile repository.
 *
 * The profile is stored and read only on this device.
 */
class DefaultUserRepository(
    private val local: LocalStore,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
) : UserRepository {

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        local.observeProfile(userId).distinctUntilChanged()

    override suspend fun getProfile(userId: String): Result<UserProfile?> = suspendRunCatchingApp {
        local.observeProfile(userId).first()
    }

    override suspend fun upsertProfile(profile: UserProfile): Result<Unit> = suspendRunCatchingApp {
        val validation = Validators.displayName(profile.displayName)
        if (validation is ValidationResult.Invalid) {
            throw com.arjun.mooary.core.error.AppException(
                com.arjun.mooary.core.error.AppError.Validation(
                    field = "displayName",
                    reasonKey = com.arjun.mooary.core.error.ErrorKeys.VALIDATION_REQUIRED,
                ),
            )
        }
        val stored = profile.copy(
            displayName = profile.displayName?.take(Limits.DISPLAY_NAME_MAX_LENGTH),
            updatedAt = timeProvider.now(),
        )
        local.upsertProfile(stored)
    }

    override suspend fun updateDisplayName(userId: String, displayName: String?): Result<Unit> =
        suspendRunCatchingApp {
            local.upsertProfile(withDisplayName(userId, displayName))
        }

    override suspend fun updatePhotoUrl(userId: String, photoUrl: String?): Result<Unit> =
        suspendRunCatchingApp {
            local.upsertProfile(withPhotoUrl(userId, photoUrl))
        }

    /**
     * Reads the profile, or builds a bare one when there is nothing to read.
     *
     * The profile row is created lazily when the user first edits local settings.
     */
    private suspend fun profileFor(userId: String): UserProfile =
        local.observeProfile(userId).first()
            ?: UserProfile(
                id = userId,
                email = "",
                displayName = null,
                photoUrl = null,
                createdAt = timeProvider.now(),
                updatedAt = timeProvider.now(),
            )

    private suspend fun withDisplayName(userId: String, displayName: String?): UserProfile =
        profileFor(userId).copy(displayName = displayName, updatedAt = timeProvider.now())

    private suspend fun withPhotoUrl(userId: String, photoUrl: String?): UserProfile =
        profileFor(userId).copy(photoUrl = photoUrl, updatedAt = timeProvider.now())

    override suspend fun purgeLocalData(userId: String) {
        runCatching { local.purgeUser(userId) }
            .onFailure { logger.error("DefaultUserRepository") { "local purge failed" } }
    }
}
