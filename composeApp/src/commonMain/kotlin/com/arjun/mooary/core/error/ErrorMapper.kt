package com.arjun.mooary.core.error

import com.arjun.mooary.core.logging.AppLogger
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
                else -> AppError.Unexpected(ErrorKeys.UNEXPECTED)
            }
        }
    }
}

private fun mapByStatus(status: Int, detail: String?): AppError = when {
    status == HttpStatusCode.Unauthorized.value -> AppError.PermissionDenied
    status == HttpStatusCode.Forbidden.value -> AppError.PermissionDenied
    status == HttpStatusCode.NotFound.value -> AppError.NotFound
    status == HttpStatusCode.Conflict.value -> AppError.Conflict(ErrorKeys.WRITE_CONFLICT)
    status == HttpStatusCode.TooManyRequests.value -> AppError.Network
    status in 400..499 -> AppError.Unexpected(ErrorKeys.UNEXPECTED)
    else -> AppError.Unexpected(ErrorKeys.UNEXPECTED)
}

private fun String?.containsAny(vararg needles: String, ignoreCase: Boolean = false): Boolean {
    if (this.isNullOrBlank()) return false
    return needles.any { contains(it, ignoreCase) }
}

