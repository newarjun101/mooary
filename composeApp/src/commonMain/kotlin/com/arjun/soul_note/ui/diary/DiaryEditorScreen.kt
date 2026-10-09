package com.arjun.soul_note.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.editor_content_field
import com.arjun.soul_note.resources.editor_delete
import com.arjun.soul_note.resources.editor_emotions_section
import com.arjun.soul_note.resources.editor_mood_section
import com.arjun.soul_note.resources.editor_save
import com.arjun.soul_note.resources.editor_saving
import com.arjun.soul_note.resources.editor_tags_field
import com.arjun.soul_note.resources.editor_title_field
import com.arjun.soul_note.resources.editor_update_cancel
import com.arjun.soul_note.resources.editor_update_confirm
import com.arjun.soul_note.resources.editor_update_message
import com.arjun.soul_note.resources.editor_update_title
import com.arjun.soul_note.resources.editor_word_count
import com.arjun.soul_note.resources.editor_date_field
import com.arjun.soul_note.resources.state_loading_entry
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import com.arjun.soul_note.ui.components.AnimatedIcon
import com.arjun.soul_note.ui.components.ActionImageIcon
import com.arjun.soul_note.ui.components.EntryActionIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arjun.soul_note.domain.model.EmotionCatalog
import com.arjun.soul_note.ui.base.SessionState
import com.arjun.soul_note.ui.base.rememberViewModel
import com.arjun.soul_note.ui.components.EmotionPicker
import com.arjun.soul_note.ui.components.DateSelector
import com.arjun.soul_note.ui.components.LoadingState
import com.arjun.soul_note.ui.components.MoodPicker
import com.arjun.soul_note.ui.components.SectionHeader
import com.arjun.soul_note.ui.components.toShortLabel
import com.arjun.soul_note.ui.components.userMessage
import com.arjun.soul_note.ui.theme.DiaryShapes
import com.arjun.soul_note.ui.theme.JournalCard
import com.arjun.soul_note.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import org.koin.core.parameter.parametersOf

/**
 * Write or edit one entry.
 *
 * The word count is shown live because it is the number people actually care about in a
 * journal, and the mood picker is above the fold for the same reason: it is the one field
 * almost every entry sets.
 */
@Composable
fun DiaryEditorScreen(
    session: SessionState,
    today: LocalDate,
    entryId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel<DiaryEditorViewModel>(session.userId, entryId) {
        parametersOf(session.userId, today, entryId)
    }
    val state by viewModel.state.collectAsState()
    var confirmingUpdate by remember(entryId) { mutableStateOf(false) }

    // A save that succeeds ends the task; a save that fails leaves the text untouched so
    // nothing is lost while the user deals with the error.
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    if (state.isLoading) {
        LoadingState(modifier = modifier, label = stringResource(Res.string.state_loading_entry))
        return
    }

    if (confirmingUpdate) {
        DiaryUpdateDialog(
            onDismiss = { confirmingUpdate = false },
            onConfirm = {
                confirmingUpdate = false
                viewModel.save()
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        // The title is set as a document heading rather than a form field: no box, no
        // floating label, serif at headline size. Everything below it is annotation, so the
        // page needs one thing that is obviously the title.
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChanged,
            label = { Text(stringResource(Res.string.editor_title_field)) },
            singleLine = true,
            isError = state.validationErrors.containsKey("field_title"),
            textStyle = MaterialTheme.typography.headlineSmall,
            shape = DiaryShapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateSelector(
                label = stringResource(Res.string.editor_date_field),
                date = state.entryDate,
                today = today,
                onDateSelected = viewModel::onDateChanged,
            )
            Text(
                text = stringResource(Res.string.editor_word_count, state.wordCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SectionHeader(title = stringResource(Res.string.editor_mood_section))
            MoodPicker(
                selected = state.mood,
                onSelect = viewModel::onMoodSelected,
            )
        }

        // Prose at body size, 1.6x leading, on a paper-coloured card. This is the one
        // surface in the app that is meant to be looked at for minutes, so it gets the
        // reading type and the room, and the metadata sits outside it rather than in it.
        OutlinedTextField(
            value = state.content,
            onValueChange = viewModel::onContentChanged,
            label = { Text(stringResource(Res.string.editor_content_field)) },
            minLines = 8,
            isError = state.validationErrors.containsKey("field_content"),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = DiaryShapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SectionHeader(title = stringResource(Res.string.editor_emotions_section))
            EmotionPicker(
                groups = EmotionCatalog.groups,
                selected = state.emotions,
                onToggle = viewModel::onEmotionToggled,
            )
        }

        OutlinedTextField(
            value = state.tagsInput,
            onValueChange = viewModel::onTagsChanged,
            label = { Text(stringResource(Res.string.editor_tags_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { error ->
            Text(
                text = error.userMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (state.isEditing) confirmingUpdate = true else viewModel.save()
                },
                enabled = state.canSave,
                modifier = Modifier.weight(1f),
            ) {
                AnimatedIcon(Icons.Default.Save, contentDescription = null)
                Text(
                    text = stringResource(
                        if (state.isSaving) Res.string.editor_saving else Res.string.editor_save,
                    ),
                    modifier = Modifier.padding(start = Spacing.small),
                )
            }
            if (state.isEditing) {
                IconButton(onClick = {
                    viewModel.delete()
                    onDone()
                }) {
                    ActionImageIcon(
                        action = EntryActionIcon.DELETE,
                        contentDescription = stringResource(Res.string.editor_delete),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiaryUpdateDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(120)),
        ) {
            JournalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = DiaryShapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.large),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    Text(
                        text = stringResource(Res.string.editor_update_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(Res.string.editor_update_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.End),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.editor_update_cancel))
                        }
                        Button(onClick = onConfirm) {
                            AnimatedIcon(Icons.Default.Save, contentDescription = null)
                            Text(
                                text = stringResource(Res.string.editor_update_confirm),
                                modifier = Modifier.padding(start = Spacing.small),
                            )
                        }
                    }
                }
            }
        }
    }
}
