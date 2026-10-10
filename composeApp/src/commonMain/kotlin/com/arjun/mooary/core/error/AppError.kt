package com.arjun.mooary.core.error

/**
 * The single, closed set of failures the UI is allowed to react to.
 *
 * Rule: no Firebase/Ktor/SQLite exception ever escapes a repository. Everything is
 * translated into one of these cases by [toAppError] so that the presentation layer
 * never depends on a backend SDK.
 */
sealed class AppError {

    /** No usable network connection, or the request timed out. */
    data object Network : AppError()

    /** Authenticated, but Firestore rules rejected the operation. */
    data object PermissionDenied : AppError()

    /** The referenced document does not exist. */
    data object NotFound : AppError()

    /** Client- or server-side validation failed. [field] identifies the offender. */
    data class Validation(val field: String?, val reasonKey: String) : AppError()

    /** The server rejected a write because the same field already exists. */
    data class Conflict(val reasonKey: String) : AppError()

    /** A dependency (local database, HTTP client) failed unexpectedly. */
    data class Unexpected(val reasonKey: String) : AppError()

    /** Reading/writing the on-device cache failed. */
    data class Storage(val reasonKey: String) : AppError()

    /** Export could not be written to disk or handed to the share sheet. */
    data class Export(val reasonKey: String) : AppError()
}

/**
 * Stable, backend-agnostic reason keys. The UI resolves them through the localisation
 * resources, so no English text is baked into the domain layer.
 */
object ErrorKeys {
    const val NETWORK = "error_network"
    const val PERMISSION_DENIED = "error_permission_denied"
    const val NOT_FOUND = "error_not_found"
    const val STORAGE = "error_storage"
    const val EXPORT_FAILED = "error_export_failed"
    const val UNEXPECTED = "error_unexpected"
    const val VALIDATION_REQUIRED = "error_validation_required"
    const val TITLE_TOO_LONG = "error_title_too_long"
    const val CONTENT_TOO_LONG = "error_content_too_long"
    const val TOO_MANY_TAGS = "error_too_many_tags"
    const val TOO_MANY_EMOTIONS = "error_too_many_emotions"
    const val INVALID_MOOD = "error_invalid_mood"
    const val INVALID_DATE_RANGE = "error_invalid_date_range"
    const val WRITE_CONFLICT = "error_write_conflict"
    const val NOT_SUPPORTED = "error_not_supported"

}
