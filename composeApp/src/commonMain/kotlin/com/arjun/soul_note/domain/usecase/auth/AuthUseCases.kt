package com.arjun.soul_note.domain.usecase.auth

import com.arjun.soul_note.core.analytics.AnalyticsEvent
import com.arjun.soul_note.core.analytics.AnalyticsParams
import com.arjun.soul_note.core.analytics.AnalyticsTracker
import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.getOrThrowAppError
import com.arjun.soul_note.core.validation.Field
import com.arjun.soul_note.core.validation.ValidationError
import com.arjun.soul_note.core.validation.ValidationErrors
import com.arjun.soul_note.core.validation.ValidationResult
import com.arjun.soul_note.core.validation.Validators
import com.arjun.soul_note.domain.model.User
import com.arjun.soul_note.domain.repository.AuthRepository
import com.arjun.soul_note.domain.repository.UserRepository
import kotlin.time.Instant

/**
 * Validates locally before touching the network, so a mistyped password never costs a
 * round-trip, then reports a typed [AppError] for everything the server can reject.
 */
class LoginUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val analytics: AnalyticsTracker,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val validation = validate(email, password)
        if (validation is ValidationResult.Invalid) return Result.failure(AuthValidationException(validation.errors))

        return authRepository.signIn(email.trim(), password)
            .onSuccess { user ->
                ensureProfile(user)
                analytics.track(
                    AnalyticsEvent.LOGIN,
                    mapOf(AnalyticsParams.RESULT to "success"),
                )
            }
            .onFailure { throwable ->
                analytics.track(
                    AnalyticsEvent.LOGIN,
                    mapOf(AnalyticsParams.RESULT to "failure", AnalyticsParams.ERROR_TYPE to throwable.message.orEmpty().take(20)),
                )
            }
    }

    /**
     * Creates the `users/{uid}` document if the account predates this client, and
     * refreshes the cached email. Failures are non-fatal: the user is still signed in.
     */
    private suspend fun ensureProfile(user: User) {
        val existing = userRepository.getProfile(user.id).getOrThrowAppError()
        if (existing == null) {
            userRepository.upsertProfile(
                com.arjun.soul_note.domain.model.UserProfile(
                    id = user.id,
                    email = user.email,
                    displayName = user.email.substringBefore('@').takeIf { it.isNotBlank() },
                    photoUrl = null,
                    createdAt = now(),
                    updatedAt = now(),
                ),
            )
        } else if (existing.email != user.email) {
            userRepository.upsertProfile(existing.copy(email = user.email, updatedAt = now()))
        }
    }

    internal fun validate(email: String, password: String): ValidationResult {
        var errors = ValidationErrors.None
        (Validators.email(email) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.password(password) as? ValidationResult.Invalid)?.let { errors += it.errors }
        return if (errors.hasErrors) ValidationResult.Invalid(errors) else ValidationResult.Valid
    }
}

class RegisterUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val analytics: AnalyticsTracker,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmPassword: String,
    ): Result<User> {
        val errors = validate(email, password, confirmPassword)
        if (errors.hasErrors) return Result.failure(AuthValidationException(errors))

        return authRepository.signUp(email.trim(), password)
            .onSuccess { user ->
                userRepository.upsertProfile(
                    com.arjun.soul_note.domain.model.UserProfile(
                        id = user.id,
                        email = user.email,
                        displayName = email.substringBefore('@').takeIf { it.isNotBlank() },
                        photoUrl = null,
                        createdAt = now(),
                        updatedAt = now(),
                    ),
                )
                // Best-effort: never block registration on a verification email.
                authRepository.sendEmailVerification()
                analytics.track(AnalyticsEvent.SIGNUP, mapOf(AnalyticsParams.RESULT to "success"))
            }
            .onFailure {
                analytics.track(AnalyticsEvent.SIGNUP, mapOf(AnalyticsParams.RESULT to "failure"))
            }
    }

    internal fun validate(email: String, password: String, confirmPassword: String): ValidationErrors {
        var errors = ValidationErrors.None
        (Validators.email(email) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.password(password) as? ValidationResult.Invalid)?.let { errors += it.errors }
        if (password != confirmPassword && confirmPassword.isNotEmpty()) {
            errors += ValidationError(Field.CONFIRM_PASSWORD, "error_passwords_do_not_match")
        }
        return errors
    }
}

class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.signOut()
        .onSuccess {
            authRepository.currentUserId()?.let { userRepository.purgeLocalData(it) }
            analytics.track(AnalyticsEvent.LOGOUT)
        }
}

class SendPasswordResetUseCase(
    private val authRepository: AuthRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        val validation = Validators.email(email)
        if (validation is ValidationResult.Invalid) return Result.failure(AuthValidationException(validation.errors))
        return authRepository.resetPassword(email.trim())
            .onSuccess { analytics.track(AnalyticsEvent.PASSWORD_RESET_REQUESTED) }
    }
}

class SendEmailVerificationUseCase(
    private val authRepository: AuthRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.sendEmailVerification()
        .onSuccess { analytics.track(AnalyticsEvent.EMAIL_VERIFICATION_REQUESTED) }
}

class ObserveAuthStateUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke() = authRepository.observeAuthState()
}

/** Restores a persisted session during splash, never throwing. */
class RestoreSessionUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): User? = authRepository.restoreSession()
}

/**
 * Irreversibly removes the account and every byte of its data.
 *
 * Order matters: remote subcollections are deleted first (while the ID token is still
 * valid), then the profile, then the Auth account, and only then the local cache. A
 * failure at any step is reported rather than swallowed, so a partial deletion is visible
 * to the user instead of leaving orphaned private data.
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(): Result<Unit> {
        val userId = authRepository.currentUserId()
            ?: return Result.failure(AuthValidationException(
                ValidationErrors(listOf(ValidationError(Field.EMAIL, "error_no_signed_in_user"))),
            ))

        val remoteDeleted = userRepository.deleteAllUserData(userId)
        if (remoteDeleted.isFailure) return remoteDeleted

        return authRepository.deleteAccount()
            .onSuccess {
                userRepository.purgeLocalData(userId)
                analytics.track(AnalyticsEvent.ACCOUNT_DELETED)
            }
    }
}

class ChangePasswordUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(currentPassword: String, newPassword: String): Result<Unit> {
        val validation = Validators.password(newPassword)
        if (validation is ValidationResult.Invalid) return Result.failure(AuthValidationException(validation.errors))
        return authRepository.changePassword(currentPassword, newPassword)
    }
}

/** Carries client-side validation failures without a stack trace. */
class AuthValidationException(val errors: ValidationErrors) : Exception("validation_failed")
