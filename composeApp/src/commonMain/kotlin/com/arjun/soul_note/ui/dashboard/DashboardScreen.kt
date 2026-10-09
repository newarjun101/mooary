package com.arjun.soul_note.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import com.arjun.soul_note.ui.components.AnimatedIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arjun.soul_note.domain.model.DashboardStatistics
import com.arjun.soul_note.domain.model.TimeRange
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.dashboard_average_mood
import com.arjun.soul_note.resources.dashboard_busiest
import com.arjun.soul_note.resources.dashboard_entry_gaps
import com.arjun.soul_note.resources.dashboard_empty_action
import com.arjun.soul_note.resources.dashboard_empty_title
import com.arjun.soul_note.resources.dashboard_emotions
import com.arjun.soul_note.resources.dashboard_entries
import com.arjun.soul_note.resources.dashboard_entry_frequency
import com.arjun.soul_note.resources.dashboard_favourites
import com.arjun.soul_note.resources.dashboard_hello
import com.arjun.soul_note.resources.dashboard_hello_no_name
import com.arjun.soul_note.resources.dashboard_mood_distribution
import com.arjun.soul_note.resources.dashboard_mood_gaps
import com.arjun.soul_note.resources.dashboard_mood_records
import com.arjun.soul_note.resources.dashboard_mood_trend
import com.arjun.soul_note.resources.dashboard_no_emotions
import com.arjun.soul_note.resources.dashboard_no_entries
import com.arjun.soul_note.resources.dashboard_no_moods
import com.arjun.soul_note.resources.dashboard_offline_notice
import com.arjun.soul_note.resources.dashboard_range_12w
import com.arjun.soul_note.resources.dashboard_range_1y
import com.arjun.soul_note.resources.dashboard_range_30d
import com.arjun.soul_note.resources.dashboard_range_7d
import com.arjun.soul_note.resources.dashboard_range_custom
import com.arjun.soul_note.resources.dashboard_range_end
import com.arjun.soul_note.resources.dashboard_range_start
import com.arjun.soul_note.resources.dashboard_range_dates
import com.arjun.soul_note.resources.dashboard_recent_entries
import com.arjun.soul_note.resources.dashboard_records
import com.arjun.soul_note.resources.dashboard_see_all
import com.arjun.soul_note.resources.dashboard_sign_in
import com.arjun.soul_note.resources.dashboard_streak
import com.arjun.soul_note.resources.dashboard_streak_days
import com.arjun.soul_note.resources.diary_new_entry
import com.arjun.soul_note.resources.state_loading_diary
import com.arjun.soul_note.resources.tab_calendar
import com.arjun.soul_note.ui.base.SessionState
import com.arjun.soul_note.ui.base.rememberViewModel
import com.arjun.soul_note.ui.components.DiaryEntryCard
import com.arjun.soul_note.ui.components.DateSelector
import com.arjun.soul_note.ui.components.LoadingState
import com.arjun.soul_note.ui.components.SectionHeader
import com.arjun.soul_note.ui.components.Stat
import com.arjun.soul_note.ui.components.StatTileGrid
import com.arjun.soul_note.ui.theme.JournalCard
import com.arjun.soul_note.ui.theme.Elevation
import com.arjun.soul_note.ui.navigation.EditorTarget
import com.arjun.soul_note.ui.theme.DiaryShapes
import com.arjun.soul_note.ui.theme.paperCard
import com.arjun.soul_note.ui.theme.Spacing
import com.arjun.soul_note.ui.labels.entryDateLabel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import com.arjun.soul_note.ui.adaptive.ContentWidth
import com.arjun.soul_note.ui.adaptive.adaptiveColumns
import com.arjun.soul_note.ui.adaptive.contentWidth

/**
 * The dashboard: how the last few weeks actually felt, and how often you wrote.
 *
 * The four charts answer four different questions, which is why they are separate cards
 * rather than one combined figure: the mix of moods, whether that mix is trending, how
 * consistently you journalled, and which emotions dominate. Every one of them is scoped by
 * the same range chips, so the cards always agree with each other.
 */
@Composable
fun DashboardScreen(
    session: SessionState,
    today: LocalDate,
    editorTarget: EditorTarget,
    onOpenDiary: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    onOpenAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel<DashboardViewModel>(session.userId, today) {
        parametersOf(session.userId, today, session.user?.email?.substringBefore('@'))
    }
    val state by viewModel.state.collectAsState()

    if (state.isLoading) {
        LoadingState(modifier = modifier, label = stringResource(Res.string.state_loading_diary))
        return
    }

    val statistics = state.statistics
    val chartColumns = adaptiveColumns(compact = 1, medium = 1, expanded = 2)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item {
            // A single calm invitation gives the home screen a focal point. The dashboard used
            // to begin with a title and immediately turn into controls, which made it feel like
            // an old utility screen rather than a place to reflect.
            JournalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = DiaryShapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                elevation = Elevation.card,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.large),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    AnimatedIcon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                                CircleShape,
                            )
                            .padding(8.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xSmall)) {
                        Text(
                            text = state.greetingName?.let {
                                stringResource(Res.string.dashboard_hello, it)
                            } ?: stringResource(Res.string.dashboard_hello_no_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(Res.string.dashboard_empty_action),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        )
                    }
                    Button(onClick = { onOpenEditor(null) }) {
                        AnimatedIcon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.small))
                        Text(stringResource(Res.string.diary_new_entry))
                    }
                    if (session.isLocalOnly) {
                        Text(
                            text = stringResource(Res.string.dashboard_offline_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.70f),
                        )
                        OutlinedButton(onClick = onOpenAuth) {
                            Text(stringResource(Res.string.dashboard_sign_in))
                        }
                    }
                }
            }
        }

        item {
            RangePicker(
                selected = state.range,
                today = today,
                onSelect = viewModel::onRangeSelected,
            )
        }

        item {
            state.overview?.let { overview ->
                StatTileGrid(
                    stats = listOf(
                        Stat(
                            label = stringResource(Res.string.dashboard_streak),
                            value = stringResource(
                                Res.string.dashboard_streak_days,
                                overview.currentStreak,
                            ),
                        ),
                        Stat(
                            label = stringResource(Res.string.dashboard_entries),
                            value = (statistics?.totalEntries ?: 0).toString(),
                        ),
                        Stat(
                            label = stringResource(Res.string.dashboard_average_mood),
                            value = statistics?.mood?.displayAverage?.toString() ?: "—",
                        ),
                        Stat(
                            label = stringResource(Res.string.dashboard_favourites),
                            value = overview.favoriteCount.toString(),
                        ),
                    ),
                    columns = adaptiveColumns(compact = 2, medium = 2, expanded = 4),
                )
            }
        }

        if (statistics == null || !statistics.hasData) {
            item { EmptyPeriodCard(onWrite = { onOpenEditor(null) }) }
        } else {
            // A chart is the one element that is genuinely better wider rather than taller, so
            // past the expanded breakpoint they pair up instead of stacking. The cards stay
            // lazy items — one per row — rather than a single eager Column, so the rows below
            // the fold are still not composed.
            val cards: List<@Composable () -> Unit> = listOf(
                {
                    ChartCard(
                        title = stringResource(Res.string.dashboard_mood_trend),
                        subtitle = stringResource(
                            Res.string.dashboard_mood_gaps,
                            statistics.bucketsWithoutMood,
                        ),
                    ) {
                        MoodTrendChart(points = statistics.trend, range = statistics.range)
                    }
                },
                {
                    ChartCard(
                        title = stringResource(Res.string.dashboard_mood_distribution),
                        subtitle = if (statistics.mood.hasData) {
                            stringResource(
                                Res.string.dashboard_mood_records,
                                statistics.mood.totalRecords,
                            )
                        } else {
                            stringResource(Res.string.dashboard_no_moods)
                        },
                    ) {
                        if (statistics.mood.hasData) {
                            MoodPieChart(
                                distribution = statistics.mood.distribution,
                                totalRecords = statistics.mood.totalRecords,
                                caption = stringResource(Res.string.dashboard_records),
                            )
                        } else {
                            ChartEmpty(stringResource(Res.string.dashboard_no_moods))
                        }
                    }
                },
                {
                    ChartCard(
                        title = stringResource(Res.string.dashboard_entry_frequency),
                        subtitle = frequencySubtitle(statistics),
                    ) {
                        if (statistics.totalEntries > 0) {
                            EntryFrequencyChart(points = statistics.trend, range = statistics.range)
                        } else {
                            ChartEmpty(stringResource(Res.string.dashboard_no_entries))
                        }
                    }
                },
                {
                    ChartCard(title = stringResource(Res.string.dashboard_emotions)) {
                        if (statistics.mood.topEmotions.isNotEmpty()) {
                            EmotionBreakdownChart(shares = statistics.mood.topEmotions)
                        } else {
                            ChartEmpty(stringResource(Res.string.dashboard_no_emotions))
                        }
                    }
                },
            )
            cards.chunked(chartColumns).forEachIndexed { rowIndex, row ->
                item(key = "charts-$rowIndex") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                        row.forEach { card ->
                            Box(modifier = Modifier.weight(1f)) { card() }
                        }
                        // Keeps a lone card in the last row at half width rather than letting it
                        // stretch across the full row and look like a different kind of card.
                        repeat(chartColumns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(Res.string.dashboard_recent_entries),
                trailing = {
                    TextButton(onClick = onOpenDiary) {
                        Text(stringResource(Res.string.dashboard_see_all))
                    }
                },
            )
        }

        if (state.recentEntries.isEmpty()) {
            item { EmptyPeriodCard(onWrite = { onOpenEditor(null) }) }
        } else {
            items(state.recentEntries, key = { it.id }) { entry ->
                DiaryEntryCard(
                    entry = entry,
                    onClick = { onOpenEditor(entry.id) },
                    onToggleFavorite = { viewModel.onFavouriteToggled(entry) },
                )
            }
        }
    }
}

/** The period selector. Every chart on the screen is scoped to whichever chip is active. */
@Composable
private fun RangePicker(
    selected: TimeRange,
    today: LocalDate,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustomFields by remember(selected) { mutableStateOf(selected is TimeRange.Custom) }
    var customStart by remember(selected, today) { mutableStateOf((selected as? TimeRange.Custom)?.start ?: TimeRange.Last30Days.windowStart(today)) }
    var customEnd by remember(selected, today) { mutableStateOf((selected as? TimeRange.Custom)?.end ?: today) }
    val selectedDates = stringResource(
        Res.string.dashboard_range_dates,
        entryDateLabel(selected.windowStart(today)),
        entryDateLabel(selected.window(today).endInclusive),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .paperCard(
                shape = DiaryShapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().padding(Spacing.xSmall),
        ) {
            // The tab asset, not a Material vector: this is the calendar the app draws
            // everywhere else, and it is multi-coloured, so it carries no tint.
            AnimatedIcon(painterResource(Res.drawable.tab_calendar), contentDescription = null)
            Spacer(Modifier.width(Spacing.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(selected.shortLabel()), style = MaterialTheme.typography.labelLarge)
                Text(selectedDates, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedIcon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeRange.presets.filterNotNull().forEach { range ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(range.shortLabel()))
                            Text(
                                stringResource(Res.string.dashboard_range_dates,
                                    entryDateLabel(range.windowStart(today)), entryDateLabel(today)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { AnimatedIcon(painterResource(Res.drawable.tab_calendar), contentDescription = null) },
                    onClick = { expanded = false; onSelect(range) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.dashboard_range_custom)) },
                leadingIcon = { AnimatedIcon(painterResource(Res.drawable.tab_calendar), contentDescription = null) },
                onClick = { expanded = false; showCustomFields = true },
            )
        }
    }
    if (showCustomFields) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Box(Modifier.weight(1f)) {
                DateSelector(
                    label = stringResource(Res.string.dashboard_range_start),
                    date = customStart,
                    today = today,
                    onDateSelected = { start ->
                        customStart = start
                        if (customEnd < start) customEnd = start
                        onSelect(TimeRange.Custom(customStart, customEnd))
                    },
                )
            }
            Box(Modifier.weight(1f)) {
                DateSelector(
                    label = stringResource(Res.string.dashboard_range_end),
                    date = customEnd,
                    today = today,
                    onDateSelected = { end ->
                        customEnd = if (end < customStart) customStart else end
                        onSelect(TimeRange.Custom(customStart, customEnd))
                    },
                )
            }
        }
    }
    }
}

private fun TimeRange.shortLabel(): StringResource = when (this) {
    is TimeRange.Last7Days -> Res.string.dashboard_range_7d
    is TimeRange.Last30Days -> Res.string.dashboard_range_30d
    is TimeRange.Last12Weeks -> Res.string.dashboard_range_12w
    is TimeRange.Last12Months -> Res.string.dashboard_range_1y
    is TimeRange.Custom -> Res.string.dashboard_range_custom
    else -> Res.string.dashboard_range_custom
}

@Composable
private fun frequencySubtitle(statistics: DashboardStatistics): String {
    val gaps = statistics.bucketsWithoutEntries
    val busiest = stringResource(
        Res.string.dashboard_busiest,
        statistics.busiestBucket?.entryCount ?: 0,
    )
    return if (gaps == 0) busiest else "$busiest · " + stringResource(Res.string.dashboard_entry_gaps, gaps)
}

@Composable
private fun ChartEmpty(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.large),
    )
}

@Composable
private fun EmptyPeriodCard(onWrite: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedIcon(
                imageVector = Icons.Default.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(Res.string.dashboard_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onWrite,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.dashboard_empty_action))
        }
    }
}
