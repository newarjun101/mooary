package com.arjun.soul_note.data.repository

import com.arjun.soul_note.core.datetime.TimeProvider
import com.arjun.soul_note.core.error.suspendRunCatchingApp
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.validation.Limits
import com.arjun.soul_note.core.validation.ValidationResult
import com.arjun.soul_note.core.validation.Validators
import com.arjun.soul_note.data.local.LocalStore
import com.arjun.soul_note.data.remote.RemoteUserDataSource
import com.arjun.soul_note.data.sync.SyncManager
import com.arjun.soul_note.domain.model.UserProfile
import com.arjun.soul_note.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * Profile repository.
 *
 * The profile is small and always cached, so it is read locally with no network call
 * unless the user explicitly asks for a fresh copy. Deletion deliberately fans out to
 * every subcollection *before* the profile document, which is what prevents orphaned
 * private data from surviving an account deletion.
 */
class DefaultUserRepository(
    private val local: LocalStore,
    private val remote: RemoteUserDataSource,
    private val timeProvider: TimeProvider,
    private val logger: AppLogger,
    private val syncManager: SyncManager? = null,
) : UserRepository {

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        local.observeProfile(userId).distinctUntilChanged()

    override suspend fun getProfile(userId: String): Result<UserProfile?> = suspendRunCatchingApp {
        local.observeProfile(userId).first()
    }

    override suspend fun upsertProfile(profile: UserProfile): Result<Unit> = suspendRunCatchingApp {
        val validation = Validators.displayName(profile.displayName)
        if (validation is ValidationResult.Invalid) {
            throw com.arjun.soul_note.core.error.AppException(
                com.arjun.soul_note.core.error.AppError.Validation(
                    field = "displayName",
                    reasonKey = com.arjun.soul_note.core.error.ErrorKeys.VALIDATION_REQUIRED,
                ),
            )
        }
        val stored = profile.copy(
            displayName = profile.displayName?.take(Limits.DISPLAY_NAME_MAX_LENGTH),
            updatedAt = timeProvider.now(),
        )
        local.upsertProfile(stored)
        syncManager?.requestSync()
    }

    override suspend fun updateDisplayName(userId: String, displayName: String?): Result<Unit> =
        suspendRunCatchingApp {
            local.upsertProfile(withDisplayName(userId, displayName))
            syncManager?.requestSync()
        }

    override suspend fun updatePhotoUrl(userId: String, photoUrl: String?): Result<Unit> =
        suspendRunCatchingApp {
            local.upsertProfile(withPhotoUrl(userId, photoUrl))
            syncManager?.requestSync()
        }

    /**
     * Reads the profile, or builds a bare one when there is nothing to read.
     *
     * A signed-out user still reaches this screen -- the local diary is fully usable with no
     * account -- and their profile row does not exist until they sign in. Failing here would
     * make "edit my name" the one action on this screen that only works once you have
     * registered. `email` is empty for those rows, which is what [UserProfile] already
     * carries for a profile that has never been on an account.
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

    /**
     * Remote-first account wipe.
     *
     * The subcollections are removed by the sync manager (which knows the collection
     * paths) and the profile document is deleted here. A failure returns a failed
     * [Result] so the caller can warn the user instead of leaving data behind silently.
     */
    override suspend fun deleteAllUserData(userId: String): Result<Unit> = suspendRunCatchingApp {
        syncManager?.deleteRemoteData(userId)
            ?: error("sync manager required to delete remote data")
        remote.deleteProfile(userId).getOrThrow()
        logger.info("DefaultUserRepository") { "remote profile data deleted" }
    }

    override suspend fun purgeLocalData(userId: String) {
        runCatching { local.purgeUser(userId) }
            .onFailure { logger.error("DefaultUserRepository") { "local purge failed" } }
    }
}
