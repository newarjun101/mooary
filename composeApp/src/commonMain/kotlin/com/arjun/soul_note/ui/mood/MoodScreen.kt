package com.arjun.soul_note.ui.mood

import com.arjun.soul_note.resources.mood_edit
import com.arjun.soul_note.resources.mood_edit_entry
import com.arjun.soul_note.resources.mood_edit_cancel
import com.arjun.soul_note.resources.mood_edit_save
import com.arjun.soul_note.resources.mood_delete
import com.arjun.soul_note.resources.mood_delete_entry
import com.arjun.soul_note.resources.mood_delete_confirm_body
import com.arjun.soul_note.resources.mood_delete_confirm_title
import com.arjun.soul_note.resources.mood_editing
import com.arjun.soul_note.resources.auth_error_title
import com.arjun.soul_note.resources.common_dismiss
import com.arjun.soul_note.resources.profile_cancel
import com.arjun.soul_note.resources.mood_today_summary
import com.arjun.soul_note.resources.mood_date_field
import com.arjun.soul_note.ui.labels.entryDateLabel
import com.arjun.soul_note.ui.labels.moodLabel
import com.arjun.soul_note.ui.labels.moodName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.mood_empty_body
import com.arjun.soul_note.resources.mood_empty_title
import com.arjun.soul_note.resources.mood_entry_line
import com.arjun.soul_note.resources.mood_history
import com.arjun.soul_note.resources.mood_note_field
import com.arjun.soul_note.resources.mood_record
import com.arjun.soul_note.resources.mood_saving
import com.arjun.soul_note.resources.mood_prompt
import com.arjun.soul_note.resources.state_loading_moods
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.arjun.soul_note.ui.components.AnimatedIcon
import com.arjun.soul_note.ui.components.ActionImageIcon
import com.arjun.soul_note.ui.components.EntryActionIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.ui.base.SessionState
import com.arjun.soul_note.ui.base.rememberViewModel
import com.arjun.soul_note.ui.components.LoadingState
import com.arjun.soul_note.ui.components.PagingFooter
import com.arjun.soul_note.ui.components.RequestNextPageOnApproach
import com.arjun.soul_note.ui.components.MessageState
import com.arjun.soul_note.ui.components.MoodPicker
import com.arjun.soul_note.ui.components.DateSelector
import com.arjun.soul_note.ui.components.MoodIllustration
import com.arjun.soul_note.ui.components.EmotionIllustration
import com.arjun.soul_note.ui.components.SectionHeader
import com.arjun.soul_note.ui.components.toShortLabel
import com.arjun.soul_note.ui.components.userMessage
import com.arjun.soul_note.ui.theme.JournalCard
import com.arjun.soul_note.ui.theme.DiaryShapes
import com.arjun.soul_note.ui.theme.moodColors
import com.arjun.soul_note.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import org.koin.core.parameter.parametersOf

/**
 * Mood check-ins and their history.
 *
 * A day is not unique here: the screen records a new check-in rather than editing the
 * last one, because a mood genuinely changes within a day and overwriting it would lose
 * that.
 */
@Composable
fun MoodScreen(
    session: SessionState,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel<MoodViewModel>(session.userId, today) {
        parametersOf(session.userId, today)
    }
    val state by viewModel.state.collectAsState()

    // The entry awaiting delete confirmation. Held here rather than in the view model because
    // it is purely a question for the UI; nothing is written until Confirm is tapped.
    var pendingDelete by remember { mutableStateOf<MoodEntry?>(null) }

    if (state.isLoading) {
        LoadingState(modifier = modifier, label = stringResource(Res.string.state_loading_moods))
        return
    }

    val listState = rememberLazyListState()
    // The editor lives in the first list item. Editing an older check-in should not make the
    // user hunt back through the history to find the fields that just changed mode.
    LaunchedEffect(state.editing?.id) {
        if (state.editing != null) listState.animateScrollToItem(0)
    }
    listState.RequestNextPageOnApproach(
        enabled = state.hasMore,
        itemCount = state.recent.size,
        onLoadMore = viewModel::loadMore,
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item {
            JournalCard(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = DiaryShapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    SectionHeader(
                        title = if (state.isEditing) {
                            stringResource(Res.string.mood_editing)
                        } else {
                            stringResource(Res.string.mood_prompt)
                        },
                    )
                    MoodPicker(
                        selected = state.selectedMood,
                        onSelect = viewModel::onMoodSelected,
                    )
                    DateSelector(
                        label = stringResource(Res.string.mood_date_field),
                        date = state.date,
                        today = today,
                        onDateSelected = viewModel::onDateChanged,
                    )
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::onNoteChanged,
                        label = { Text(stringResource(Res.string.mood_note_field)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Cancel sits beside Save rather than in the card header: the card is
                    // already a scroll's worth tall, and an abandon action that sits next to
                    // the commit it abandons is the one people look for when they change
                    // their mind.
                    BoxWithConstraints {
                        // Translations make the two actions wider than a narrow phone. Stack
                        // them there instead of truncating either the escape or save action.
                        if (state.isEditing && maxWidth < 380.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                OutlinedButton(
                                    onClick = viewModel::cancelEdit,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text(stringResource(Res.string.mood_edit_cancel)) }
                                Button(
                                    onClick = viewModel::record,
                                    enabled = !state.isRecording,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text(if (state.isRecording) {
                                        stringResource(Res.string.mood_saving)
                                    } else {
                                        stringResource(Res.string.mood_edit_save)
                                    })
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (state.isEditing) {
                                    OutlinedButton(
                                        onClick = viewModel::cancelEdit,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) { Text(stringResource(Res.string.mood_edit_cancel)) }
                                }
                                Button(
                                    onClick = viewModel::record,
                                    enabled = !state.isRecording,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        when {
                                            state.isRecording -> stringResource(Res.string.mood_saving)
                                            state.isEditing -> stringResource(Res.string.mood_edit_save)
                                            else -> stringResource(Res.string.mood_record)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        state.todaySummary?.let { summary ->
            item {
                // Resolved before buildString: the label is a composable read, and hiding it
                // inside the string assembly made the English literal hard to spot.
                val dominantLabel = summary.dominantMood?.let { moodLabel(it) }
                Text(
                    text = buildString {
                        append(stringResource(Res.string.mood_today_summary, summary.count))
                        if (dominantLabel != null) append(" · $dominantLabel")
                        summary.averageScore?.let { append(" · $it / 5") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader(title = stringResource(Res.string.mood_history)) }
        if (state.recent.isEmpty()) {
            item {
                MessageState(
                    title = stringResource(Res.string.mood_empty_title),
                    message = stringResource(Res.string.mood_empty_body),
                    icon = Icons.Default.InsertEmoticon,
                )
            }
        } else {
            items(state.recent, key = { it.id }) { entry ->
                JournalCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = moodColors(entry.mood).container,
                    shape = DiaryShapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xSmall),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xSmall),
                            ) {
                                MoodIllustration(entry.mood, Modifier.size(28.dp))
                                Text(
                                    text = stringResource(
                                        Res.string.mood_entry_line,
                                        moodName(entry.mood),
                                    ) + " · ${entry.date.toShortLabel()}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            val day = entryDateLabel(entry.date)
                            MoodRowAction(
                                action = EntryActionIcon.EDIT,
                                contentDescription = stringResource(Res.string.mood_edit_entry, day),
                                onClick = { viewModel.onEdit(entry) },
                            )
                            MoodRowAction(
                                action = EntryActionIcon.DELETE,
                                contentDescription = stringResource(Res.string.mood_delete_entry, day),
                                onClick = { pendingDelete = entry },
                            )
                        }
                        entry.note?.let { note ->
                            Text(text = note, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (entry.emotions.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xSmall)) {
                                entry.emotions.forEach { emotion ->
                                    EmotionIllustration(emotion, Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.hasMore) {
            item(key = "mood_paging_footer") {
                PagingFooter()
            }
        }

        // Inline rather than the full-screen MessageState the other screens use: that one is
        // `fillMaxSize()`, so on a list screen it would replace the history the user needs to
        // see in order to retry. A failed delete is reported here and the list stays.
        state.error?.let { error ->
            item(key = "mood_error") {
                JournalCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(Spacing.medium),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.auth_error_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            // The title alone tells the user nothing they can act on. Every
                            // failure here is something they cannot guess -- a disk that filled,
                            // a sync that went away mid-delete -- so the reason is shown, the
                            // same way `SettingsScreen` renders `state.error`.
                            Text(
                                text = error.userMessage(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = viewModel::dismissError) {
                            Text(stringResource(Res.string.common_dismiss))
                        }
                    }
                }
            }
        }
    }

    // Confirmation before the write, not after. Delete is a tombstone that syncs onward, so
    // there is no undo to fall back on, and a mis-tap on a card full of buttons is easy.
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.mood_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.mood_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.delete(entry)
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.mood_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(Res.string.profile_cancel))
                }
            },
        )
    }
}

/**
 * A row's secondary action keeps a full touch target while the supplied artwork owns its
 * rounded background.
 *
 * The *touch* target stays [TOUCH_TARGET] -- an outer box that is transparent and does the
 * clicking -- so the compact artwork remains easy to use.
 *
 * The interaction source is passed in with no indication, so the image remains crisp and the
 * scale itself is the press response.
 */
@Composable
private fun MoodRowAction(
    action: EntryActionIcon,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(TOUCH_TARGET)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ActionImageIcon(
            action = action,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    val scale = if (pressed) PRESSED_SCALE else 1f
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/** Kept out of the call site so the touch target is one number, not three copies of one. */
private val TOUCH_TARGET = 28.dp
private const val PRESSED_SCALE = 0.9f
