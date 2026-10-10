package com.arjun.mooary.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.calendar_next_month
import com.arjun.mooary.resources.calendar_previous_month
import com.arjun.mooary.resources.date_picker_cancel
import com.arjun.mooary.resources.date_picker_confirm
import com.arjun.mooary.resources.date_selector_label
import com.arjun.mooary.resources.tab_calendar
import com.arjun.mooary.resources.weekday_fri_short
import com.arjun.mooary.resources.weekday_mon_short
import com.arjun.mooary.resources.weekday_sat_short
import com.arjun.mooary.resources.weekday_sun_short
import com.arjun.mooary.resources.weekday_thu_short
import com.arjun.mooary.resources.weekday_tue_short
import com.arjun.mooary.resources.weekday_wed_short
import com.arjun.mooary.ui.labels.entryDateLabel
import com.arjun.mooary.ui.labels.monthLabel
import com.arjun.mooary.ui.theme.DiaryShapes
import com.arjun.mooary.ui.theme.Elevation
import com.arjun.mooary.ui.theme.JournalCard
import com.arjun.mooary.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A shared local-date picker; UTC keeps calendar dates stable across device time zones. */
@Composable
fun DateSelector(
    label: String,
    date: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showingPicker = true }, modifier = modifier) {
        AnimatedIcon(painterResource(Res.drawable.tab_calendar), contentDescription = null)
        Text(stringResource(Res.string.date_selector_label, label, date.toShortLabel()))
    }

    if (showingPicker) {
        JournalDatePickerDialog(
            initialDate = date,
            today = today,
            onConfirm = { selected ->
                onDateSelected(selected)
                showingPicker = false
            },
            onDismiss = { showingPicker = false },
        )
    }
}

/**
 * The app's own calendar dialog, drawn in the same language as the Calendar screen's month
 * grid so the two read as one component rather than a hand-rolled screen next to a stock
 * Material picker.
 *
 * Six rows are always drawn, empty tail cells included, because a grid that grows when a
 * month needs six weeks moves the footer buttons under your thumb between taps.
 */
@Composable
private fun JournalDatePickerDialog(
    initialDate: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialDate) }
    var shownMonth by remember { mutableStateOf(LocalDates.monthStart(initialDate)) }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(120)),
        ) {
            JournalCard(
                // Capped rather than merely filling: on a desktop window a dialog that spans
                // the screen makes a seven-day grid unreadably wide, and the calendar is the
                // one thing here whose cell size should not grow with the window.
                modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                shape = DiaryShapes.extraLarge,
                elevation = Elevation.overlay,
            ) {
                Column(modifier = Modifier.padding(Spacing.gutter)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonthStepButton(
                            onClick = { shownMonth = LocalDates.minusDays(LocalDates.monthStart(shownMonth), 1) },
                            contentDescription = stringResource(Res.string.calendar_previous_month),
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = monthLabel(shownMonth.month.number, shownMonth.year),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(Spacing.xSmall))
                            Box(
                                Modifier
                                    .size(width = 28.dp, height = 2.dp)
                                    .background(MaterialTheme.colorScheme.primary, DiaryShapes.extraSmall),
                            )
                        }
                        MonthStepButton(
                            onClick = { shownMonth = LocalDates.plusDays(LocalDates.monthEnd(shownMonth), 1) },
                            contentDescription = stringResource(Res.string.calendar_next_month),
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.small, vertical = Spacing.medium),
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

                    val gridStart = LocalDates.weekStart(LocalDates.monthStart(shownMonth))
                    val cells = List(GRID_CELL_COUNT) { LocalDates.plusDays(gridStart, it) }
                    cells.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                val isCurrentMonth = day.month.number == shownMonth.month.number
                                DayCell(
                                    day = day,
                                    selected = day == selected,
                                    isToday = day == today,
                                    faded = !isCurrentMonth,
                                    onClick = {
                                        selected = day
                                        if (!isCurrentMonth) shownMonth = LocalDates.monthStart(day)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    Text(
                        text = entryDateLabel(selected),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.medium),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.End),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.date_picker_cancel))
                        }
                        Button(onClick = { onConfirm(selected) }) {
                            Text(stringResource(Res.string.date_picker_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthStepButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), CircleShape),
    ) {
        AnimatedIcon(icon, contentDescription = contentDescription)
    }
}

/**
 * One date. The number is the only content, so a tap target is a square and the selected
 * state is a circle drawn inside it — the same three-colour rule as the Calendar grid, with
 * days outside the displayed month merely dimmed rather than disabled: tapping them is how a
 * reader moves to that month.
 */
@Composable
private fun DayCell(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    faded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        selected -> scheme.primaryContainer
        else -> Color.Transparent
    }
    val content = when {
        selected -> scheme.onPrimaryContainer
        faded -> scheme.onSurfaceVariant.copy(alpha = 0.35f)
        else -> scheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(fill)
            // Today keeps a mark that selection would overwrite, so it is a ring around the
            // circle rather than a second background.
            .then(
                when {
                    selected -> Modifier.border(2.dp, scheme.primary, CircleShape)
                    isToday -> Modifier.border(1.5.dp, scheme.primary.copy(alpha = 0.55f), CircleShape)
                    else -> Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.day.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

/** Six weeks, always: the footer must not move when a month is long. */
private const val GRID_CELL_COUNT = 42

private val WEEKDAY_INITIALS = listOf(
    Res.string.weekday_mon_short,
    Res.string.weekday_tue_short,
    Res.string.weekday_wed_short,
    Res.string.weekday_thu_short,
    Res.string.weekday_fri_short,
    Res.string.weekday_sat_short,
    Res.string.weekday_sun_short,
)
