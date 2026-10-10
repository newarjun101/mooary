package com.arjun.mooary.ui.diary

import com.arjun.mooary.core.error.AppError
import com.arjun.mooary.core.error.toAppError
import com.arjun.mooary.domain.model.DiaryDraft
import com.arjun.mooary.domain.model.DiaryEntry
import com.arjun.mooary.domain.model.Emotion
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.countWords
import com.arjun.mooary.domain.repository.DraftRepository
import com.arjun.mooary.domain.usecase.diary.CreateDiaryEntryUseCase
import com.arjun.mooary.domain.usecase.diary.DeleteDiaryEntryUseCase
import com.arjun.mooary.domain.usecase.diary.DiaryEntryDraft
import com.arjun.mooary.domain.usecase.diary.DiaryValidationException
import com.arjun.mooary.domain.usecase.diary.ObserveDiaryEntryUseCase
import com.arjun.mooary.domain.usecase.diary.UpdateDiaryEntryUseCase
import com.arjun.mooary.ui.base.BaseViewModel
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** What the editor is currently showing. */
data class EditorState(
    val entryId: String? = null,
    val title: String = "",
    val content: String = "",
    val entryDate: LocalDate,
    val mood: Mood = Mood.NEUTRAL,
    val emotions: Set<String> = emptySet(),
    val tagsInput: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
    val error: AppError? = null,
) {
    val isEditing: Boolean get() = entryId != null
    val wordCount: Int get() = content.countWords()
    val canSave: Boolean get() = !isSaving && (title.isNotBlank() || content.isBlank().not())
}

/**
 * Backing state for the diary editor.
 *
 * The draft is mirrored into the local database on every change, so a crash, a background
 * kill or an accidental navigation loses nothing. That is why this screen does not simply
 * hold its text in memory: the whole premise of the app is that an unsaved entry survives.
 */
class DiaryEditorViewModel(
    private val observeEntry: ObserveDiaryEntryUseCase,
    private val createEntry: CreateDiaryEntryUseCase,
    private val updateEntry: UpdateDiaryEntryUseCase,
    private val deleteEntry: DeleteDiaryEntryUseCase,
    private val drafts: DraftRepository,
    private val userId: String,
    private val today: LocalDate,
    private val entryIdToEdit: String?,
) : BaseViewModel() {

    private val _state = MutableStateFlow(
        EditorState(entryId = entryIdToEdit, entryDate = today, isLoading = entryIdToEdit != null),
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** The record being edited, needed as the update base. Null while creating. */
    private var loaded: DiaryEntry? = null

    init {
        val id = entryIdToEdit
        if (id == null) {
            restoreDraft()
        } else {
            launch {
                observeEntry(userId, id).collect { entry ->
                    // Ignore echoes while a save is in flight: the observed row is the one
                    // being written, and applying it mid-edit would move the cursor.
                    if (entry != null && !_state.value.isSaving) {
                        loaded = entry
                        _state.value = EditorState(
                            entryId = entry.id,
                            title = entry.title,
                            content = entry.content,
                            entryDate = entry.entryDate,
                            mood = entry.mood,
                            emotions = entry.emotions.mapTo(mutableSetOf()) { it.id },
                            tagsInput = entry.tags.joinToString(", "),
                        )
                    }
                }
            }
        }
    }

    fun onTitleChanged(value: String) = edit { it.copy(title = value) }

    fun onContentChanged(value: String) = edit { it.copy(content = value) }

    fun onDateChanged(value: LocalDate) = edit { it.copy(entryDate = value) }

    fun onMoodSelected(mood: Mood) = edit { it.copy(mood = mood) }

    fun onEmotionToggled(id: String) = edit { current ->
        val next = current.emotions.toMutableSet()
        if (!next.add(id)) next.remove(id)
        current.copy(emotions = next)
    }

    fun onTagsChanged(value: String) = edit { it.copy(tagsInput = value) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true, validationErrors = emptyMap(), error = null) }

        launch {
            val result = if (loaded == null) {
                createEntry(userId, current.toDraft())
            } else {
                updateEntry(userId, requireNotNull(loaded), current.toDraft())
            }
            result
                .onSuccess { entry ->
                    loaded = entry
                    runCatching { drafts.clearDraft(userId) }
                    _state.update {
                        it.copy(isSaving = false, isSaved = true, entryId = entry.id)
                    }
                }
                .onFailure(::handleSaveFailure)
        }
    }

    fun delete() {
        val id = _state.value.entryId ?: return
        launch {
            deleteEntry(userId, id)
                .onSuccess { runCatching { drafts.clearDraft(userId) } }
                .onFailure { throwable -> _state.update { it.copy(error = throwable.toAppError()) } }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Applies an edit and mirrors it to the on-device draft. */
    private inline fun edit(block: (EditorState) -> EditorState) {
        val updated = block(_state.value).copy(isSaved = false)
        _state.value = updated
        persistDraft(updated)
    }

    private fun persistDraft(current: EditorState) {
        if (current.entryId != null) return
        if (current.title.isBlank() && current.content.isBlank()) return
        launch {
            runCatching {
                drafts.saveDraft(
                    DiaryDraft(
                        userId = userId,
                        entryId = null,
                        title = current.title,
                        content = current.content,
                        entryDate = current.entryDate,
                        mood = current.mood,
                        emotions = current.emotions.map { Emotion.fromId(it) },
                        tags = current.tagsFromInput(),
                        updatedAt = Clock.System.now(),
                    ),
                )
            }.onFailure { report(it.toAppError()) }
        }
    }

    private fun restoreDraft() {
        launch {
            val draft = runCatching { drafts.getDraft(userId) }.getOrNull() ?: return@launch
            _state.update { current ->
                current.copy(
                    title = draft.title,
                    content = draft.content,
                    entryDate = draft.entryDate,
                    mood = draft.mood,
                    emotions = draft.emotions.mapTo(mutableSetOf()) { it.id },
                    tagsInput = draft.tags.joinToString(", "),
                )
            }
        }
    }

    private fun handleSaveFailure(throwable: Throwable) {
        if (throwable is DiaryValidationException) {
            _state.update { current ->
                current.copy(
                    isSaving = false,
                    validationErrors = throwable.errors.errors.associate { error ->
                        error.field.key to error.reasonKey
                    },
                )
            }
        } else {
            _state.update { it.copy(isSaving = false, error = throwable.toAppError()) }
        }
    }
}

private fun EditorState.toDraft() = DiaryEntryDraft(
    title = title.trim(),
    content = content.trim(),
    entryDate = entryDate,
    mood = mood,
    emotions = emotions.toList(),
    tags = tagsFromInput(),
)

private fun EditorState.tagsFromInput(): List<String> =
    tagsInput.split(',').map { it.trim() }.filter { it.isNotEmpty() }
