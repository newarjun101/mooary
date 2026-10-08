package com.arjun.soul_note.ui.mood

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.core.validation.Limits
import com.arjun.soul_note.domain.model.DayMoodSummary
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.domain.usecase.mood.DeleteMoodUseCase
import com.arjun.soul_note.domain.usecase.mood.MoodEntryDraft
import com.arjun.soul_note.domain.usecase.mood.MoodValidationException
import com.arjun.soul_note.domain.usecase.mood.GetMoodHistoryUseCase
import com.arjun.soul_note.domain.usecase.mood.RecordMoodUseCase
import com.arjun.soul_note.domain.usecase.mood.UpdateMoodUseCase
import com.arjun.soul_note.ui.base.BaseViewModel
import com.arjun.soul_note.ui.base.PageWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Mood history plus the state of the check-in sheet. */
data class MoodState(
    val recent: List<MoodEntry> = emptyList(),
    val summaries: List<DayMoodSummary> = emptyList(),
    val selectedMood: Mood = Mood.NEUTRAL,
    val note: String = "",
    val date: LocalDate,
    /** Check-ins in total, which is more than [recent].size until paging catches up. */
    val totalCount: Int = 0,
    /** Whether [loadMore] would extend the list. False once every check-in is loaded. */
    val hasMore: Boolean = false,
    val isRecording: Boolean = false,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    /**
     * The check-in being edited, or null when the sheet records a new one.
     *
     * The whole entry is carried rather than just its id because `UpdateMoodUseCase` writes
     * from an existing row: the SQL `update` sets every mutable column, so the created-at and
     * the emotion list have to come from the stored entry rather than from the sheet.
     */
    val editing: MoodEntry? = null,
) {
    val isEmpty: Boolean get() = !isLoading && recent.isEmpty()
    val todaySummary: DayMoodSummary? get() = summaries.firstOrNull { it.date == date }
    val isEditing: Boolean get() = editing != null
}

/**
 * Backing state for mood check-ins.
 *
 * A day is not unique here — the domain allows several check-ins per day because a mood
 * genuinely changes within one — so the screen records a new entry rather than editing the
 * existing one.
 *
 * History is paged with the same growing-window scheme as [com.arjun.soul_note.ui.diary.DiaryListViewModel]
 * rather than a fixed cap: the previous `RECENT_LIMIT` silently hid everything past the 60th
 * check-in with no way to reach it.
 */
class MoodViewModel(
    observeHistory: GetMoodHistoryUseCase,
    private val recordMood: RecordMoodUseCase,
    private val updateMood: UpdateMoodUseCase,
    private val deleteMood: DeleteMoodUseCase,
    private val userId: String,
    // Retained because cancelling an edit has to restore the record card to the session's
    // today; the sheet's own date is whatever the edited entry happened to carry.
    private val today: LocalDate,
) : BaseViewModel() {

    private val sheet = MutableStateFlow(MoodSheetState(date = today))
    private val failure = MutableStateFlow<AppError?>(null)

    private val paging = PageWindow<MoodEntry>(PAGE_SIZE)

    private val page = paging.window { limit -> observeHistory.page(userId = userId, limit = limit) }

    val state: StateFlow<MoodState> = combine(
        page,
        sheet,
        failure,
    ) { current, sheetState, error ->
        MoodState(
            recent = current.items,
            selectedMood = sheetState.mood,
            note = sheetState.note,
            date = sheetState.date,
            totalCount = current.totalCount ?: current.items.size,
            hasMore = current.hasMore,
            isRecording = sheetState.isRecording,
            isLoading = false,
            error = error,
            editing = sheetState.editing,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MoodState(date = today))

    /**
     * Extends the window by one page, or does nothing at the end of the list.
     *
     * Capped at the known total so the window cannot grow past the end of the history.
     */
    fun loadMore() = paging.loadMore()

    private data class MoodSheetState(
        val mood: Mood = Mood.NEUTRAL,
        val note: String = "",
        val date: LocalDate,
        val isRecording: Boolean = false,
        val editing: MoodEntry? = null,
    )

    fun onMoodSelected(mood: Mood) = sheet.update { it.copy(mood = mood) }

    fun onNoteChanged(note: String) = sheet.update { it.copy(note = note) }

    fun onDateChanged(date: LocalDate) = sheet.update { it.copy(date = date) }

    /**
     * Loads a stored check-in into the sheet so the same card edits it.
     *
     * Reuses the record card rather than opening a second surface, because the fields are
     * already there and a separate editor would need its own route and its own copy of the
     * picker. The mood is taken from the entry rather than the sheet default: editing starts
     * from what was recorded, not from Neutral.
     */
    fun onEdit(entry: MoodEntry) = sheet.update {
        it.copy(
            editing = entry,
            mood = entry.mood,
            note = entry.note.orEmpty(),
            date = entry.date,
        )
    }

    /**
     * Leaves edit mode and restores the blank record card.
     *
     * The date goes back to the session's today rather than whatever the edited entry carried,
     * so a cancel does not silently leave the sheet recording into someone else's day.
     */
    fun cancelEdit() = sheet.update {
        it.copy(editing = null, mood = Mood.NEUTRAL, note = "", date = today)
    }

    /** Records a new check-in, or updates the one being edited. */
    fun record() {
        val current = sheet.value
        if (current.isRecording) return
        sheet.update { it.copy(isRecording = true) }
        launch {
            val draft = MoodEntryDraft(
                mood = current.mood,
                note = current.note.trim().ifEmpty { null },
                date = current.date,
            )
            val result = current.editing
                ?.let { existing -> updateMood(userId, existing, draft) }
                ?: recordMood(userId, draft)
            result
                .onSuccess {
                    // A successful edit clears the sheet: leaving the entry attached would
                    // mean a second tap on Save wrote the same row again.
                    sheet.update {
                        if (it.editing != null) {
                            it.copy(isRecording = false, editing = null, mood = Mood.NEUTRAL, note = "", date = today)
                        } else {
                            it.copy(isRecording = false, note = "")
                        }
                    }
                }
                .onFailure { throwable ->
                    sheet.update { it.copy(isRecording = false) }
                    failure.value = when (throwable) {
                        is MoodValidationException -> throwable.errors.errors.firstOrNull()?.let {
                            AppError.Validation(it.field.key, it.reasonKey)
                        } ?: AppError.Unexpected("error_unexpected")

                        else -> throwable.toAppError()
                    }
                }
        }
    }

    /**
     * Deletes a check-in.
     *
     * Tombstones locally rather than removing the row, so the delete still reaches the server
     * on the next sync. Cancelling the entry being edited happens here rather than on the tap
     * that opened the confirmation: the sheet must not keep a row that no longer exists.
     */
    fun delete(entry: MoodEntry) {
        launch {
            deleteMood(userId, entry.id)
                .onSuccess {
                    if (sheet.value.editing?.id == entry.id) cancelEdit()
                }
                .onFailure { failure.value = it.toAppError() }
        }
    }

    fun dismissError() {
        failure.value = null
    }

    private companion object {
        const val PAGE_SIZE = Limits.DIARY_PAGE_SIZE
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
