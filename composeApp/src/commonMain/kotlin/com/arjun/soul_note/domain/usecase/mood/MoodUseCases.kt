package com.arjun.soul_note.domain.usecase.mood

import com.arjun.soul_note.core.analytics.AnalyticsEvent
import com.arjun.soul_note.core.analytics.AnalyticsTracker
import com.arjun.soul_note.core.validation.ValidationErrors
import com.arjun.soul_note.core.validation.ValidationResult
import com.arjun.soul_note.core.validation.Validators
import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.core.validation.Limits
import com.arjun.soul_note.domain.model.MoodFilter
import com.arjun.soul_note.domain.model.Page
import com.arjun.soul_note.domain.model.SyncStatus
import com.arjun.soul_note.domain.repository.MoodRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

class MoodValidationException(val errors: ValidationErrors) : Exception("validation_failed")

/** A mood check-in as the UI produces it, before identifiers are assigned. */
data class MoodEntryDraft(
    val mood: Mood,
    val note: String?,
    val emotions: List<String> = emptyList(),
    val date: kotlinx.datetime.LocalDate,
)

class RecordMoodUseCase(
    private val repository: MoodRepository,
    private val analytics: AnalyticsTracker,
    private val now: () -> Instant,
    private val newId: () -> String,
) {
    suspend operator fun invoke(userId: String, draft: MoodEntryDraft): Result<MoodEntry> {
        val errors = validate(draft)
        if (errors.hasErrors) return Result.failure(MoodValidationException(errors))

        val timestamp = now()
        val entry = MoodEntry(
            id = newId(),
            userId = userId,
            mood = draft.mood,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            emotions = draft.emotions.map(Emotion::fromId),
            date = draft.date,
            createdAt = timestamp,
            updatedAt = timestamp,
            syncStatus = SyncStatus.PENDING_CREATE,
        )
        return repository.recordMood(entry)
            .onSuccess { analytics.track(AnalyticsEvent.MOOD_RECORDED) }
    }

    internal fun validate(draft: MoodEntryDraft): ValidationErrors {
        var errors = ValidationErrors.None
        (Validators.moodNote(draft.note) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.emotions(draft.emotions) as? ValidationResult.Invalid)?.let { errors += it.errors }
        return errors
    }
}

class UpdateMoodUseCase(
    private val repository: MoodRepository,
    private val analytics: AnalyticsTracker,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(userId: String, existing: MoodEntry, draft: MoodEntryDraft): Result<MoodEntry> {
        val errors = validate(draft)
        if (errors.hasErrors) return Result.failure(MoodValidationException(errors))

        val updated = existing.copy(
            mood = draft.mood,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            emotions = draft.emotions.map(Emotion::fromId),
            date = draft.date,
            updatedAt = now(),
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        return repository.updateMood(updated)
            .onSuccess { analytics.track(AnalyticsEvent.MOOD_UPDATED) }
    }

    internal fun validate(draft: MoodEntryDraft): ValidationErrors {
        var errors = ValidationErrors.None
        (Validators.moodNote(draft.note) as? ValidationResult.Invalid)?.let { errors += it.errors }
        (Validators.emotions(draft.emotions) as? ValidationResult.Invalid)?.let { errors += it.errors }
        return errors
    }
}

class DeleteMoodUseCase(
    private val repository: MoodRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(userId: String, moodId: String): Result<Unit> =
        repository.deleteMood(userId, moodId)
            .onSuccess { analytics.track(AnalyticsEvent.MOOD_DELETED) }
}

class GetMoodHistoryUseCase(
    private val repository: MoodRepository,
) {
    operator fun invoke(userId: String): Flow<List<MoodEntry>> = repository.observeEntries(userId)

    /**
     * Paged mood history.
     *
     * [filter] defaults to no filtering, which is the case the SQL `LIMIT/OFFSET` path can
     * serve straight from the index.
     */
    fun page(
        userId: String,
        limit: Int = Limits.DIARY_PAGE_SIZE,
        offset: Int = 0,
        filter: MoodFilter = MoodFilter(),
    ): Flow<Page<MoodEntry>> = repository.observeEntriesPage(userId, filter, limit, offset)
}

class ObserveFilteredMoodsUseCase(
    private val repository: MoodRepository,
) {
    operator fun invoke(userId: String, filter: MoodFilter): Flow<List<MoodEntry>> =
        repository.observeFilteredEntries(userId, filter)
}

class ObserveRecentMoodsUseCase(
    private val repository: MoodRepository,
) {
    operator fun invoke(userId: String, limit: Int): Flow<List<MoodEntry>> =
        repository.observeRecentEntries(userId, limit)
}

class ObserveMoodsForDateUseCase(
    private val repository: MoodRepository,
) {
    operator fun invoke(userId: String, date: kotlinx.datetime.LocalDate): Flow<List<MoodEntry>> =
        repository.observeEntriesForDate(userId, date)
}

class ObserveDayMoodSummariesUseCase(
    private val repository: MoodRepository,
) {
    operator fun invoke(userId: String) = repository.observeDaySummaries(userId)
}

class GetMoodsForExportUseCase(
    private val repository: MoodRepository,
) {
    suspend operator fun invoke(userId: String): Result<List<MoodEntry>> = repository.getAllForExport(userId)
}

class ObserveMoodCountUseCase(
    private val repository: MoodRepository,
) {
    operator fun invoke(userId: String): Flow<Int> = repository.observeCount(userId)
}
