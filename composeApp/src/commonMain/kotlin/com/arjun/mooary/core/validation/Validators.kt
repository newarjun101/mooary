package com.arjun.mooary.core.validation

/**
 * The single source of truth for every size limit in the product.
 *
 * The same numbers are enforced in three places — here for instant UX feedback, in the
 * repository before a write is queued, and in `firebase/firestore.rules` for security —
 * so a client bug can never produce a document the backend would reject.
 */
object Limits {
    const val TITLE_MAX_LENGTH = 200
    const val CONTENT_MAX_LENGTH = 50_000
    const val TAGS_MAX_COUNT = 20
    const val TAG_MAX_LENGTH = 50
    const val EMOTIONS_MAX_COUNT = 20
    const val MOOD_NOTE_MAX_LENGTH = 1_000
    const val DISPLAY_NAME_MAX_LENGTH = 80

    const val SEARCH_QUERY_MAX_LENGTH = 200
    const val SEARCH_MIN_QUERY_LENGTH = 2
    const val SEARCH_PAGE_SIZE = 50
    const val DIARY_PAGE_SIZE = 30
    const val MAX_EXPORT_ENTRIES = 100_000

    /**
     * How close to the end of a loaded page a list starts fetching the next one.
     *
     * A small prefetch window hides the network/disk round trip behind the user's scroll
     * without loading the entire table. The trade-off is that the first page is rendered
     * before the rest exists, so the list must tolerate items appearing at the tail.
     */
    const val PAGE_PREFETCH_DISTANCE = 8

    /** A page request never asks for less than this many rows. */
    const val PAGE_MIN_LIMIT = 1

    /** A page request never asks for more than this many rows, whatever a caller passes. */
    const val PAGE_MAX_LIMIT = 200
}

/** Field identifiers used by both validation and the localisation layer. */
enum class Field(val key: String) {
    TITLE("field_title"),
    CONTENT("field_content"),
    DISPLAY_NAME("field_display_name"),
    TAG("field_tag"),
    TAGS("field_tags"),
    EMOTIONS("field_emotions"),
    MOOD("field_mood"),
    MOOD_NOTE("field_mood_note"),
    ENTRY_DATE("field_entry_date"),
    DATE_RANGE("field_date_range"),
}

/** A single, user-presentable validation failure. */
data class ValidationError(
    val field: Field,
    val reasonKey: String,
)

/** Accumulates failures so a form can highlight every offending field at once. */
data class ValidationErrors(val errors: List<ValidationError>) {

    val hasErrors: Boolean get() = errors.isNotEmpty()

    operator fun plus(error: ValidationError): ValidationErrors = ValidationErrors(errors + error)

    operator fun plus(other: ValidationErrors): ValidationErrors = ValidationErrors(errors + other.errors)

    fun forField(field: Field): ValidationError? = errors.firstOrNull { it.field == field }

    fun reasonKeys(): List<String> = errors.map { it.reasonKey }

    fun toSet(): Set<ValidationError> = errors.toSet()

    companion object {
        val None = ValidationErrors(emptyList())
    }
}

/** The outcome of a pure validation function: either unit or the accumulated failures. */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: ValidationErrors) : ValidationResult
}

/** Pure, side-effect-free validators. All of them are trivially unit-testable. */
object Validators {

    fun diaryTitle(title: String): ValidationResult = when {
        title.trim().isEmpty() ->
            ValidationResult.Invalid(ValidationErrors(listOf(ValidationError(Field.TITLE, com.arjun.mooary.core.error.ErrorKeys.VALIDATION_REQUIRED))))
        title.length > Limits.TITLE_MAX_LENGTH ->
            ValidationResult.Invalid(ValidationErrors(listOf(ValidationError(Field.TITLE, com.arjun.mooary.core.error.ErrorKeys.TITLE_TOO_LONG))))
        else -> ValidationResult.Valid
    }

    fun diaryContent(content: String): ValidationResult = when {
        content.isBlank() ->
            ValidationResult.Invalid(ValidationErrors(listOf(ValidationError(Field.CONTENT, com.arjun.mooary.core.error.ErrorKeys.VALIDATION_REQUIRED))))
        content.length > Limits.CONTENT_MAX_LENGTH ->
            ValidationResult.Invalid(ValidationErrors(listOf(ValidationError(Field.CONTENT, com.arjun.mooary.core.error.ErrorKeys.CONTENT_TOO_LONG))))
        else -> ValidationResult.Valid
    }

    fun tags(tags: List<String>): ValidationResult = when {
        tags.size > Limits.TAGS_MAX_COUNT ->
            ValidationResult.Invalid(ValidationErrors(listOf(ValidationError(Field.TAGS, com.arjun.mooary.core.error.ErrorKeys.TOO_MANY_TAGS))))
        tags.any { it.trim().length > Limits.TAG_MAX_LENGTH } ->
            ValidationResult.Invalid(ValidationErrors(listOf(ValidationError(Field.TAG, com.arjun.mooary.core.error.ErrorKeys.VALIDATION_REQUIRED))))
        else -> ValidationResult.Valid
    }

    fun emotions(emotions: List<String>): ValidationResult =
        if (emotions.size > Limits.EMOTIONS_MAX_COUNT) {
            ValidationResult.Invalid(
                ValidationErrors(listOf(ValidationError(Field.EMOTIONS, com.arjun.mooary.core.error.ErrorKeys.TOO_MANY_EMOTIONS))),
            )
        } else {
            ValidationResult.Valid
        }

    fun moodNote(note: String?): ValidationResult =
        if (note != null && note.length > Limits.MOOD_NOTE_MAX_LENGTH) {
            ValidationResult.Invalid(
                ValidationErrors(listOf(ValidationError(Field.MOOD_NOTE, com.arjun.mooary.core.error.ErrorKeys.CONTENT_TOO_LONG))),
            )
        } else {
            ValidationResult.Valid
        }

    fun displayName(name: String?): ValidationResult =
        if (name != null && name.length > Limits.DISPLAY_NAME_MAX_LENGTH) {
            ValidationResult.Invalid(
                ValidationErrors(
                    listOf(ValidationError(Field.DISPLAY_NAME, com.arjun.mooary.core.error.ErrorKeys.TITLE_TOO_LONG)),
                ),
            )
        } else {
            ValidationResult.Valid
        }

    fun dateRange(startEpochMillis: Long, endEpochMillis: Long): ValidationResult =
        if (startEpochMillis > endEpochMillis) {
            ValidationResult.Invalid(
                ValidationErrors(listOf(ValidationError(Field.DATE_RANGE, com.arjun.mooary.core.error.ErrorKeys.INVALID_DATE_RANGE))),
            )
        } else {
            ValidationResult.Valid
        }

    /** Normalises a user-typed tag: trimmed, collapsed whitespace, lower-cased, bounded. */
    fun normaliseTag(raw: String): String? {
        val cleaned = raw.trim().replace(WHITESPACE_REGEX, " ").lowercase()
        if (cleaned.isEmpty()) return null
        return cleaned.take(Limits.TAG_MAX_LENGTH)
    }

    fun isValidTag(raw: String): Boolean = normaliseTag(raw) != null

    private val WHITESPACE_REGEX = Regex("\\s+")
}
