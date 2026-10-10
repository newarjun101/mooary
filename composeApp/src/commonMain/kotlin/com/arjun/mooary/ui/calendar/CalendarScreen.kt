package com.arjun.mooary.ui.calendar

import com.arjun.mooary.ui.labels.monthLabel
import com.arjun.mooary.ui.labels.moodName
import com.arjun.mooary.core.datetime.LocalDates
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.calendar_empty_day
import com.arjun.mooary.resources.calendar_mood_line
import com.arjun.mooary.resources.calendar_mood_note
import com.arjun.mooary.resources.calendar_next_month
import com.arjun.mooary.resources.calendar_previous_month
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.arjun.mooary.ui.components.AnimatedIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arjun.mooary.domain.model.CalendarDay
import com.arjun.mooary.ui.base.SessionState
import com.arjun.mooary.ui.base.rememberSessionViewModel
import com.arjun.mooary.ui.components.MoodIllustration
import com.arjun.mooary.ui.theme.JournalCard
import com.arjun.mooary.ui.theme.DiaryShapes
import com.arjun.mooary.ui.theme.Elevation
import com.arjun.mooary.ui.theme.Spacing
import com.arjun.mooary.ui.theme.moodColors
import kotlinx.datetime.LocalDate
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.delay
import com.arjun.mooary.resources.weekday_fri_short
import com.arjun.mooary.resources.weekday_mon_short
import com.arjun.mooary.resources.weekday_sat_short
import com.arjun.mooary.resources.weekday_sun_short
import com.arjun.mooary.resources.weekday_thu_short
import com.arjun.mooary.resources.weekday_tue_short
import com.arjun.mooary.resources.weekday_wed_short

/**
 * Month grid plus the selected day's entries.
 *
 * Every day renders its number, mood illustration and entry count, so the grid is
 * readable without relying on colour — which also means it still works in a monochrome
 * or high-contrast rendering.
 */
@Composable
fun CalendarScreen(
    session: SessionState,
    today: LocalDate,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberSessionViewModel<CalendarViewModel>(session.userId, today) {
        parametersOf(session.userId, today)
    }
    val state by viewModel.state.collectAsState()

    CalendarContent(
        state = state,
        today = today,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onDateSelected = viewModel::selectDate,
        onOpenEntry = onOpenEntry,
        modifier = modifier,
    )
}

/**
 * Stateless calendar rendering. Navigation and selection remain events owned by [CalendarViewModel],
 * which keeps this surface reusable and prevents display code from mutating calendar state directly.
 */
@Composable
private fun CalendarContent(
    state: CalendarState,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    // The table deliberately claims a responsive portion of the viewport. Its rows derive
    // their height from this space, so resizing a desktop window also resizes the calendar.
    var visibleDayCount by remember { mutableStateOf(0) }
    val displayedDays = remember(state.days, state.selectedDate, visibleDayCount) {
        if (visibleDayCount == 0 || state.days.size <= visibleDayCount) state.days else {
            val selectedIndex = state.days.indexOfFirst { it.date == state.selectedDate }.coerceAtLeast(0)
            val start = (selectedIndex - (visibleDayCount / 2)).coerceIn(0, state.days.size - visibleDayCount)
            state.days.subList(start, start + visibleDayCount)
        }
    }
    val tableHeight = when (visibleDayCount) {
        7 -> 150.dp
        15 -> 255.dp
        else -> (maxHeight * 0.52f).coerceAtLeast(300.dp)
    }
    val pageBackward = {
        if (visibleDayCount > 0 && displayedDays.firstOrNull()?.date != state.days.firstOrNull()?.date) {
            val anchor = state.selectedDate ?: displayedDays.first().date
            onDateSelected(LocalDates.minusDays(anchor, visibleDayCount).coerceAtLeast(state.days.first().date))
        } else {
            onPreviousMonth()
        }
    }
    val pageForward = {
        if (visibleDayCount > 0 && displayedDays.lastOrNull()?.date != state.days.lastOrNull()?.date) {
            val anchor = state.selectedDate ?: displayedDays.last().date
            onDateSelected(LocalDates.plusDays(anchor, visibleDayCount).coerceAtMost(state.days.last().date))
        } else {
            onNextMonth()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "calendar_month_header") {
            val scheme = MaterialTheme.colorScheme
            JournalCard(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = DiaryShapes.extraLarge,
                elevation = Elevation.raised,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    IconButton(
                        onClick = pageBackward,
                        modifier = Modifier
                            .size(36.dp)
                            .background(scheme.surface.copy(alpha = 0.72f), CircleShape),
                    ) {
                        AnimatedIcon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(Res.string.calendar_previous_month),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = monthLabel(state.month.month.number, state.month.year),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Box(
                            Modifier
                                .padding(top = Spacing.xSmall)
                                .size(width = 28.dp, height = 2.dp)
                                .background(scheme.primary, DiaryShapes.extraSmall),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xSmall)) {
                            listOf(7, 15, 0).forEach { count ->
                                TextButton(onClick = { visibleDayCount = count }) {
                                    Text(if (count == 0) "Month" else "$count d")
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = pageForward,
                        modifier = Modifier
                            .size(36.dp)
                            .background(scheme.surface.copy(alpha = 0.72f), CircleShape),
                    ) {
                        AnimatedIcon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.calendar_next_month),
                        )
                    }
                }
            }
        }

        item(key = "calendar_grid") {
            Column {
            val weekCount = displayedDays.chunked(7).size
            val rowHeight = ((tableHeight - 42.dp) / weekCount).coerceIn(38.dp, 104.dp)
            JournalCard(
                modifier = Modifier.fillMaxWidth().height(tableHeight),
                shape = DiaryShapes.extraLarge,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.small, end = Spacing.small, top = Spacing.medium),
                ) {
                    WEEKDAY_INITIALS.forEach { day ->
                        Text(
                            text = stringResource(day),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                displayedDays.chunked(7).forEachIndexed { weekIndex, week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xSmall),
                    ) {
                        week.forEachIndexed { dayIndex, day ->
                            // A date, not its grid position, owns the cell state. Without this
                            // key a data-coloured cell in the old month is reused for whatever
                            // date happens to occupy the same position in the new month.
                            key(day.date) {
                                CalendarCell(
                                    day = day,
                                    selected = day.date == state.selectedDate,
                                    isToday = day.date == today,
                                    monthKey = state.month,
                                    animationDelayMillis = ((weekIndex * 7 + dayIndex) * 20).coerceAtMost(240),
                                    rowHeight = rowHeight,
                                    onClick = { onDateSelected(day.date) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(Spacing.small))
            }
            }
        }

        if (state.entriesForDay.isEmpty() && state.moodsForDay.isEmpty()) {
            item {
                JournalCard(modifier = Modifier.fillMaxWidth(), shape = DiaryShapes.large) {
                    Text(
                        text = stringResource(Res.string.calendar_empty_day),
                        modifier = Modifier.padding(Spacing.medium),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(state.entriesForDay, key = { it.id }) { entry ->
            JournalCard(
                modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(entry.id) },
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xSmall),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xSmall),
                    ) {
                        MoodIllustration(entry.mood, Modifier.size(28.dp))
                        Text(text = entry.title, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = entry.preview(maxLength = 120),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.moodsForDay.forEach { mood ->
            item(key = mood.id) {
                JournalCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = moodColors(mood.mood).container,
                    shape = DiaryShapes.large,
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        MoodIllustration(mood.mood, Modifier.size(38.dp))
                        Text(
                            text = mood.note?.let { note ->
                                stringResource(
                                    Res.string.calendar_mood_note,
                                    moodName(mood.mood),
                                    note,
                                )
                            } ?: stringResource(
                                Res.string.calendar_mood_line,
                                moodName(mood.mood),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun CalendarCell(
    day: CalendarDay,
    selected: Boolean,
    isToday: Boolean,
    monthKey: LocalDate,
    animationDelayMillis: Int,
    rowHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mood = day.mood
    val shape = DiaryShapes.small
    val scheme = MaterialTheme.colorScheme

    // Three states, three backgrounds, and each one is a colour the user can name: the
    // day's own mood when it has one, the accent when it is selected, nothing when neither.
    // Backgrounds deliberately switch without an animation when paging. A calendar grid
    // reuses positions between months, so interpolating from the old cell's mood colour looks
    // like data flashing in and out before the new date settles.
    val fill = when {
        selected -> scheme.primaryContainer
        mood != null -> moodColors(mood).container
        else -> Color.Transparent
    }
    val content = when {
        selected -> scheme.onPrimaryContainer
        day.isFuture -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> scheme.onSurface
    }

    Box(
        modifier = modifier
            .height(rowHeight)
            .padding(2.dp)
            .background(fill, shape)
            // Today needs a mark that survives both states, so it is a ring outside the
            // circle rather than a background colour that selection would overwrite.
            .then(
                if (selected) {
                    Modifier.border(2.dp, scheme.primary, shape)
                } else if (isToday) {
                    Modifier.border(1.5.dp, scheme.primary.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = day.hasContent,
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
            if (mood != null) {
                CalendarMoodIllustration(
                    mood = mood,
                    monthKey = monthKey,
                    animationDelayMillis = animationDelayMillis,
                )
            }
            if (day.diaryEntryCount > 0) {
                Text(
                    text = day.diaryEntryCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        scheme.onPrimary
                    } else {
                        scheme.primary
                    },
                )
            }
        }
    }
}

/** Recorded moods arrive in a small sequence after a month switch, making data discovery clear. */
@Composable
private fun CalendarMoodIllustration(
    mood: com.arjun.mooary.domain.model.Mood,
    monthKey: LocalDate,
    animationDelayMillis: Int,
) {
    var visible by remember(monthKey) { mutableStateOf(false) }
    LaunchedEffect(monthKey) {
        delay(animationDelayMillis.toLong())
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "calendarMoodArrival",
    )
    MoodIllustration(
        mood = mood,
        motionSeed = "$monthKey-$animationDelayMillis",
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                // Keep the artwork opaque while the new month is calculated. The prior
                // fade-from-zero made data days look as if they were repeatedly reloading.
                // A tiny scale settle still gives the month change a sense of arrival without
                // making a recorded mood disappear for a frame.
                scaleX = 0.92f + (progress * 0.08f)
                scaleY = 0.92f + (progress * 0.08f)
            },
    )
}

private val WEEKDAY_INITIALS = listOf(
    Res.string.weekday_mon_short,
    Res.string.weekday_tue_short,
    Res.string.weekday_wed_short,
    Res.string.weekday_thu_short,
    Res.string.weekday_fri_short,
    Res.string.weekday_sat_short,
    Res.string.weekday_sun_short,
)
