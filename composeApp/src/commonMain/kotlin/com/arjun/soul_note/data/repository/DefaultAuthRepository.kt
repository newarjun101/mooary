package com.arjun.soul_note.data.repository

import com.arjun.soul_note.core.error.AppException
import com.arjun.soul_note.core.error.suspendRunCatchingApp
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.notifications.PushRegistrar
import com.arjun.soul_note.data.remote.RemoteAuthDataSource
import com.arjun.soul_note.domain.model.User
import com.arjun.soul_note.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Auth repository.
 *
 * Holds the authoritative in-memory session and republishes it as a [StateFlow], so the
 * navigation graph reacts to sign-in, sign-out and token refresh without polling. All
 * Firebase specifics stay behind [RemoteAuthDataSource].
 *
 * [push] is optional and nullable so this stays testable without a messaging SDK, and so that a
 * build with no transport simply passes `null` instead of having to be told to tolerate a
 * failing one.
 */
class DefaultAuthRepository(
    private val remote: RemoteAuthDataSource,
    private val push: PushRegistrar? = null,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
) : AuthRepository {

    private val _user = MutableStateFlow<User?>(null)

    val user: StateFlow<User?> = _user.asStateFlow()

    override suspend fun signIn(email: String, password: String): Result<User> =
        suspendRunCatchingApp {
            val identity = remote.signIn(email, password).getOrThrow()
            val user = identity.toUser()
            _user.value = user
            registerForPush(user)
            user
        }

    override suspend fun signUp(email: String, password: String): Result<User> =
        suspendRunCatchingApp {
            val identity = remote.signUp(email, password).getOrThrow()
            val user = identity.toUser()
            _user.value = user
            registerForPush(user)
            user
        }

    override suspend fun signOut(): Result<Unit> = suspendRunCatchingApp {
        remote.signOut().getOrThrow()
        _user.value = null
    }

    override suspend fun resetPassword(email: String): Result<Unit> =
        suspendRunCatchingApp { remote.resetPassword(email).getOrThrow() }

    override suspend fun deleteAccount(): Result<Unit> = suspendRunCatchingApp {
        remote.deleteAccount().getOrThrow()
        _user.value = null
    }

    /**
     * Emits the signed-in user, or `null`. On collection it first attempts to restore a
     * persisted session so a returning user is not bounced to the login screen.
     */
    override fun observeAuthState(): Flow<User?> = flowOfUser()

    override suspend fun restoreSession(): User? {
        val restoredResult = remote.restoreSession()
        val restored = restoredResult.getOrNull()
        if (restored == null && restoredResult.isFailure) {
            logger.warn(TAG) {
                "first-launch Firebase session unavailable: ${restoredResult.exceptionOrNull()?.let { it::class.simpleName }}"
            }
        }
        val restoredUser = restored?.toUser()
        // An anonymous Firebase identity authorizes the device-registration write but is not a
        // user-visible account. Keep the app in guest mode until a real email account signs in.
        _user.value = restoredUser?.takeIf { it.email.isNotBlank() }
        // A returning user is exactly the case that needs this: they signed in once, perhaps
        // before Cloud Messaging was switched on, and will not type a password again for days.
        restoredUser?.let { registerForPush(it) }
        return _user.value
    }

    override suspend fun refreshUser(): Result<User> = suspendRunCatchingApp {
        val identity = remote.refreshSession().getOrThrow()
        val user = identity.toUser()
        _user.value = user
        user
    }

    override suspend fun sendEmailVerification(): Result<Unit> =
        suspendRunCatchingApp { remote.sendEmailVerification().getOrThrow() }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        suspendRunCatchingApp { remote.changePassword(currentPassword, newPassword).getOrThrow() }

    override fun currentUserId(): String? = _user.value?.id

    private fun flowOfUser(): Flow<User?> = _user.asStateFlow()

    /**
     * Tells a future sender that this device exists, without making sign-in wait for it.
     *
     * Fired on [scope] and its result deliberately dropped. Awaiting it would put a network
     * round trip and a messaging SDK in front of the login screen, and a user whose project
     * has no messaging would watch the app hang on a spinner for a feature they never asked
     * about. The rules elsewhere in this file apply here too: a local write is immediate and
     * the network is an enhancement, never a prerequisite.
     */
    private fun registerForPush(user: User) {
        val registrar = push ?: return
        if (!registrar.isSupported) return
        scope.launch {
            registrar.register(user.id).onFailure { error ->
                // Logged by the registrar itself; this line records only that it was attempted,
                // because a silent failure here is indistinguishable from a feature that does
                // not exist. The AppError, when there is one, is enum-derived and safe to
                // print where a raw exception message would not be.
                val detail = (error as? AppException)?.error ?: error::class.simpleName
                logger.info(TAG) { "push registration not completed: $detail" }
            }
        }
    }

    private fun com.arjun.soul_note.data.remote.RemoteIdentity.toUser() = User(
        id = userId,
        email = email,
        isEmailVerified = emailVerified,
        createdAtMillis = createdAtEpochMillis,
    )

    private companion object {
        const val TAG = "DefaultAuthRepository"
    }
}
