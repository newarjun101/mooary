package com.arjun.soul_note.core.error

import com.arjun.soul_note.core.logging.AppLogger
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException

/**
 * Translates any throwable into an [AppError].
 *
 * The mapping is deliberately defensive: the `else` fallback means a bug in a dependency
 * still surfaces as a stable, user-presentable error instead of a crash.
 *
 * Only the status code and the exception message are inspected here — reading a response
 * body is a suspending operation, so the remote data sources parse the body first and
 * throw an [AppException] carrying the already-classified [AppError].
 */
fun Throwable.toAppError(logger: AppLogger? = null): AppError {
    logger?.debug("ErrorMapper") { "classifying ${this::class.simpleName}" }

    if (this is AppException) return error

    return when (this) {
        is SocketTimeoutException -> AppError.Network
        is IOException -> AppError.Network
        is ResponseException -> mapByStatus(response.status.value, message)
        else -> {
            val text = message
            when {
                text.containsAny("permission", "insufficient", ignoreCase = true) ->
                    AppError.PermissionDenied
                text.containsAny("UNAVAILABLE", "deadline", "network", "connection", "timed out", ignoreCase = true) ->
                    AppError.Network
                text.containsAny("NOT_FOUND", "not found", ignoreCase = true) -> AppError.NotFound
                text.containsAny("CONFLICT", "ABORTED", ignoreCase = true) ->
                    AppError.Conflict(ErrorKeys.WRITE_CONFLICT)
                text.containsAny("token", "credential", "unauth", "session", ignoreCase = true) ->
                    AppError.Authentication(AuthenticationReason.SESSION_EXPIRED)
                else -> AppError.Unexpected(ErrorKeys.UNEXPECTED)
            }
        }
    }
}

private fun mapByStatus(status: Int, detail: String?): AppError = when {
    status == HttpStatusCode.Unauthorized.value -> AppError.Authentication(authReasonFrom(detail))
    status == HttpStatusCode.Forbidden.value -> AppError.PermissionDenied
    status == HttpStatusCode.NotFound.value -> AppError.NotFound
    status == HttpStatusCode.Conflict.value -> AppError.Conflict(ErrorKeys.WRITE_CONFLICT)
    status == HttpStatusCode.TooManyRequests.value ->
        AppError.Authentication(AuthenticationReason.TOO_MANY_REQUESTS)
    status in 400..499 -> AppError.Unexpected(ErrorKeys.UNEXPECTED)
    else -> AppError.Unexpected(ErrorKeys.UNEXPECTED)
}

internal fun authReasonFrom(detail: String?): AuthenticationReason = when {
    detail.containsAny("INVALID_LOGIN_CREDENTIALS") -> AuthenticationReason.INVALID_CREDENTIALS
    detail.containsAny("EMAIL_NOT_VERIFIED") -> AuthenticationReason.EMAIL_NOT_VERIFIED
    detail.containsAny("EMAIL_EXISTS") -> AuthenticationReason.EMAIL_ALREADY_IN_USE
    detail.containsAny("WEAK_PASSWORD") -> AuthenticationReason.WEAK_PASSWORD
    detail.containsAny("INVALID_EMAIL") -> AuthenticationReason.INVALID_EMAIL
    detail.containsAny("TOO_MANY_ATTEMPTS", "QUOTA_EXCEEDED") -> AuthenticationReason.TOO_MANY_REQUESTS
    detail.containsAny("USER_DISABLED") -> AuthenticationReason.USER_DISABLED
    detail.containsAny("EMAIL_NOT_FOUND", "USER_NOT_FOUND") -> AuthenticationReason.USER_NOT_FOUND
    // `INVALID_ID_TOKEN` is what a malformed or superseded token comes back as; both mean
    // the same thing to the user, who can only fix it by signing in again. It is checked
    // after the password codes above because `INVALID_LOGIN_CREDENTIALS` is a different
    // failure and must keep its own reason.
    detail.containsAny("TOKEN_EXPIRED", "USER_TOKEN_EXPIRED", "INVALID_ID_TOKEN") -> AuthenticationReason.SESSION_EXPIRED
    // `accounts:delete` answers this rather than refreshing, and it is not a transport
    // problem: the account is only removable from a recent sign-in, so it is a stale
    // session the user has to clear by signing in again.
    detail.containsAny("CREDENTIAL_TOO_OLD_LOGIN_AGAIN", "CREDENTIAL_TOO_OLD") -> AuthenticationReason.SESSION_EXPIRED
    else -> AuthenticationReason.UNKNOWN
}

/** Null-tolerant substring probe; the error body is frequently empty. */
private fun String?.containsAny(vararg needles: String, ignoreCase: Boolean = false): Boolean {
    if (this.isNullOrBlank()) return false
    return needles.any { contains(it, ignoreCase) }
}
