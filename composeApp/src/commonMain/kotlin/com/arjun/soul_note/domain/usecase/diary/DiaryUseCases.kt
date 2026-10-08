package com.arjun.soul_note.domain.usecase.diary

import com.arjun.soul_note.core.analytics.AnalyticsEvent
import com.arjun.soul_note.core.analytics.AnalyticsParams
import com.arjun.soul_note.core.analytics.AnalyticsTracker
import com.arjun.soul_note.core.validation.Limits
import com.arjun.soul_note.core.validation.ValidationErrors
import com.arjun.soul_note.core.validation.ValidationResult
import com.arjun.soul_note.core.validation.Validators
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.DiaryFilter
import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.Page
import com.arjun.soul_note.domain.model.SyncStatus
import com.arjun.soul_note.domain.repository.DiaryRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Field-level failures from a write attempt, so the editor can highlight inputs. */
class DiaryValidationException(val errors: ValidationErrors) : Exception("validation_failed")

/** Draft input, decoupled from transport and sync concerns. */
data class DiaryEntryDraft(
    val title: String,
    val content: String,
    val entryDate: LocalDate,
    val mood: Mood,
    val emotions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val location: String? = null,
    val weather: String? = null,
)

class CreateDiaryEntryUseCase(
    private val repository: DiaryRepository,
    private val analytics: AnalyticsTracker,
    private val now: () -> Instant,
    private val newId: () -> String,
) {
    suspend operator fun invoke(userId: String, draft: DiaryEntryDraft): Result<DiaryEntry> {
        val errors = validate(draft)
        if (errors.hasErrors) return Result.failure(DiaryValidationException(errors))

        val timestamp = now()
        val entry = DiaryEntry(
            id = newId(),
            userId = userId,
            title = draft.title.trim(),
            content = draft.content.trim(),
            entryDate = draft.entryDate,
            mood = draft.mood,
            emotions = draft.emotions.map(Emotion::fromId),
            tags = draft.tags.mapNotNull { Validators.normaliseTag(it) }.distinct(),
            isFavorite = false,
            location = draft.location?.trim()?.takeIf { it.isNotEmpty() },
            weather = draft.weather?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = timestamp,
            updatedAt = timestamp,
            syncStatus = SyncStatus.PENDING_CREATE,
        )

        return repository.createEntry(entry)
            .onSuccess { analytics.track(AnalyticsEvent.DIARY_CREATED) }
    }

    internal fun validate(draft: DiaryEntryDraft): ValidationErrors {
        var errors = ValidationErrors.None
        (Validators.diaryTitle(draft.title) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.diaryContent(draft.content) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.tags(draft.tags) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.emotions(draft.emotions) as? ValidationResult.Invalid)?.let { errors += it.errors }
        return errors
    }
}

class UpdateDiaryEntryUseCase(
    private val repository: DiaryRepository,
    private val analytics: AnalyticsTracker,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(userId: String, existing: DiaryEntry, draft: DiaryEntryDraft): Result<DiaryEntry> {
        val errors = validate(draft.title, draft.content, draft.tags, draft.emotions)
        if (errors.hasErrors) return Result.failure(DiaryValidationException(errors))

        // Immutable fields (id, userId, createdAt) are preserved, never overwritten.
        val updated = existing.copy(
            title = draft.title.trim(),
            content = draft.content.trim(),
            entryDate = draft.entryDate,
            mood = draft.mood,
            emotions = draft.emotions.map(Emotion::fromId),
            tags = draft.tags.mapNotNull { Validators.normaliseTag(it) }.distinct(),
            location = draft.location?.trim()?.takeIf { it.isNotEmpty() },
            weather = draft.weather?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = now(),
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        return repository.updateEntry(updated)
            .onSuccess { analytics.track(AnalyticsEvent.DIARY_UPDATED) }
    }

    internal fun validate(
        title: String,
        content: String,
        tags: List<String>,
        emotions: List<String>,
    ): ValidationErrors {
        var errors = ValidationErrors.None
        (Validators.diaryTitle(title) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.diaryContent(content) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.tags(tags) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.emotions(emotions) as? ValidationResult.Invalid)?.let { errors += it.errors }
        return errors
    }
}

class DeleteDiaryEntryUseCase(
    private val repository: DiaryRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(userId: String, entryId: String): Result<Unit> =
        repository.deleteEntry(userId, entryId)
            .onSuccess { analytics.track(AnalyticsEvent.DIARY_DELETED) }
}

class GetDiaryEntryUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(userId: String, entryId: String): Result<DiaryEntry?> =
        repository.getEntry(userId, entryId)
}

class ObserveDiaryEntriesUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(userId: String): Flow<List<DiaryEntry>> = repository.observeEntries(userId)
}

class ObserveDiaryEntryUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(userId: String, entryId: String): Flow<DiaryEntry?> =
        repository.observeEntry(userId, entryId)
}

class ObserveFilteredDiaryEntriesUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(userId: String, filter: DiaryFilter): Flow<List<DiaryEntry>> =
        repository.observeFilteredEntries(userId, filter)
}

/**
 * Paged read so a journal with thousands of entries never loads all at once.
 *
 * [filter] defaults to no filtering with the default order, which is the case the SQL
 * `LIMIT/OFFSET` path can serve straight from the index.
 */
class ObserveDiaryPageUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(
        userId: String,
        limit: Int = Limits.DIARY_PAGE_SIZE,
        offset: Int = 0,
        filter: DiaryFilter = DiaryFilter.None,
    ): Flow<Page<DiaryEntry>> = repository.observeEntriesPage(userId, filter, limit, offset)
}

class ObserveRecentDiaryEntriesUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(userId: String, limit: Int): Flow<List<DiaryEntry>> =
        repository.observeRecentEntries(userId, limit)
}

class ObserveFavoritesUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(userId: String): Flow<List<DiaryEntry>> = repository.observeFavorites(userId)
}

class ObserveEntriesForDateUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(userId: String, date: LocalDate): Flow<List<DiaryEntry>> =
        repository.observeEntriesForDate(userId, date)
}

class ToggleFavoriteEntryUseCase(
    private val repository: DiaryRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(userId: String, entry: DiaryEntry, isFavorite: Boolean): Result<Unit> =
        repository.setFavorite(userId, entry.id, isFavorite)
            .onSuccess { analytics.track(AnalyticsEvent.DIARY_FAVORITED) }
}

/**
 * Full-text search over title, content, tags and emotions.
 *
 * The query is matched locally against the cached rows, so search is instant and works
 * offline; the repository exposes the seam where a dedicated server-side search index
 * (Algolia/Typesense/Firestore `array-contains` union) can be introduced without
 * touching any caller.
 */
class SearchDiaryEntriesUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(
        userId: String,
        query: String,
        limit: Int = Limits.SEARCH_PAGE_SIZE,
    ): Result<List<DiaryEntry>> {
        val trimmed = query.trim()
        if (trimmed.length < Limits.SEARCH_MIN_QUERY_LENGTH) return Result.success(emptyList())
        return repository.search(userId, trimmed.take(Limits.SEARCH_QUERY_MAX_LENGTH), limit)
    }
}

class ObserveAllTagsUseCase(
    private val repository: DiaryRepository,
) {
    operator fun invoke(userId: String): Flow<List<String>> = repository.observeAllTags(userId)
}

class GetDiaryEntriesForExportUseCase(
    private val repository: DiaryRepository,
) {
    suspend operator fun invoke(userId: String): Result<List<DiaryEntry>> = repository.getAllForExport(userId)
}
