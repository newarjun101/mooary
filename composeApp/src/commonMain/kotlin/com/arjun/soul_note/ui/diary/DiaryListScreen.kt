package com.arjun.soul_note.ui.diary

import com.arjun.soul_note.ui.labels.moodName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.diary_clear_filters
import com.arjun.soul_note.resources.diary_empty_body
import com.arjun.soul_note.resources.diary_empty_filtered_body
import com.arjun.soul_note.resources.diary_empty_filtered_title
import com.arjun.soul_note.resources.diary_empty_title
import com.arjun.soul_note.resources.diary_favourites_filter
import com.arjun.soul_note.resources.diary_new_entry
import com.arjun.soul_note.resources.diary_result_count
import com.arjun.soul_note.resources.diary_search
import com.arjun.soul_note.resources.state_loading_entries
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilterChip
import com.arjun.soul_note.ui.components.AnimatedIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arjun.soul_note.domain.model.DiarySortOrder
import com.arjun.soul_note.domain.model.MoodCatalog
import com.arjun.soul_note.ui.base.SessionState
import com.arjun.soul_note.ui.components.PagingFooter
import com.arjun.soul_note.ui.components.RequestNextPageOnApproach
import com.arjun.soul_note.ui.base.rememberViewModel
import com.arjun.soul_note.ui.components.DiaryEntryCard
import com.arjun.soul_note.ui.components.MoodIllustration
import com.arjun.soul_note.ui.components.LoadingState
import com.arjun.soul_note.ui.components.MessageState
import com.arjun.soul_note.ui.navigation.EditorTarget
import com.arjun.soul_note.ui.theme.Elevation
import com.arjun.soul_note.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import org.koin.core.parameter.parametersOf

/**
 * The full diary: search, filter and the complete list.
 *
 * Filtering lives behind chips rather than a settings screen because the common case is
 * "show me favourites" or "what was I like last month", and both are one tap from here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryListScreen(
    session: SessionState,
    today: LocalDate,
    editorTarget: EditorTarget,
    onOpenEditor: (String?) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberViewModel<DiaryListViewModel>(session.userId, today) {
        parametersOf(session.userId, today)
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(searchQuery) {
        if (searchQuery != state.filter.query) viewModel.setQuery(searchQuery)
    }
    LaunchedEffect(state.filter.query) {
        if (state.filter.query != searchQuery) onSearchQueryChange(state.filter.query)
    }

    Scaffold(
        modifier = modifier,
        // The app shell already owns the system and bottom-navigation insets. Applying them a
        // second time here made the end of the diary list sit above a large empty band.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Transparent so the shell's page background is the page background, rather than a
        // flat fill sitting on top of it and cutting off the chrome's own surface.
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenEditor(null) },
                // The primary pill is the only emphatic object over the list. Keeping its
                // elevation constant on press makes the interaction feel grounded; movement
                // comes from the icon's inset disc rather than a jumpy shadow.
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = Elevation.raised,
                    pressedElevation = Elevation.raised,
                    focusedElevation = Elevation.raised,
                    hoveredElevation = Elevation.raised,
                ),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedIcon(Icons.Default.Add, contentDescription = null)
                    }
                },
                text = {
                    Text(
                        stringResource(Res.string.diary_new_entry),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                modifier = Modifier
                    .height(56.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f),
                        shape = CircleShape,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.gutter),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalArrangement = Arrangement.spacedBy(Spacing.xSmall),
            ) {
                FilterChip(
                    selected = state.filter.favoritesOnly,
                    onClick = viewModel::toggleFavoritesOnly,
                    label = { Text(stringResource(Res.string.diary_favourites_filter)) },
                )
                MoodCatalog.quickPicker.forEach { descriptor ->
                    FilterChip(
                        selected = descriptor.mood in state.filter.moodFilter,
                        onClick = { viewModel.toggleMoodFilter(descriptor.mood) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xSmall),
                            ) {
                                MoodIllustration(descriptor.mood, Modifier.size(24.dp))
                                Text(moodName(descriptor))
                            }
                        },
                    )
                }
                if (state.filter.hasActiveFilters) {
                    TextButton(onClick = viewModel::clearFilters) {
                        Text(stringResource(Res.string.diary_clear_filters))
                    }
                }
            }

            if (state.filter.hasActiveFilters) {
                Text(
                    // Loaded rows against every match, not rows against rows: the previous
                    // "N of N" was only ever true when the whole result set happened to fit.
                    text = stringResource(
                        Res.string.diary_result_count,
                        state.loadedCount,
                        state.totalCount,
                    ) + if (state.filter.activeFilterCount > 0) {
                        " · " + state.filter.activeFilterCount
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xSmall),
                )
            }

            when {
                state.isLoading -> LoadingState(label = stringResource(Res.string.state_loading_entries))

                state.entries.isEmpty() -> MessageState(
                    title = stringResource(
                        if (state.filter.hasActiveFilters) {
                            Res.string.diary_empty_filtered_title
                        } else {
                            Res.string.diary_empty_title
                        },
                    ),
                    message = stringResource(
                        if (state.filter.hasActiveFilters) {
                            Res.string.diary_empty_filtered_body
                        } else {
                            Res.string.diary_empty_body
                        },
                    ),
                    icon = Icons.Default.EditNote,
                    actionLabel = if (state.filter.hasActiveFilters) {
                        stringResource(Res.string.diary_clear_filters)
                    } else {
                        null
                    },
                    onAction = if (state.filter.hasActiveFilters) viewModel::clearFilters else null,
                )

                else -> {
                    val listState = rememberLazyListState()
                    listState.RequestNextPageOnApproach(
                        enabled = state.hasMore,
                        itemCount = state.entries.size,
                        onLoadMore = viewModel::loadMore,
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Vertical only: the horizontal gutter is already applied by the
                        // Column above, so a full PaddingValues(gutter) here would double it
                        // and make these cards narrower than every other card in the app.
                        // The top inset is what separates the first card from the filter
                        // chips, and the bottom one lets the last card scroll clear of the
                        // navigation bar instead of sitting under it.
                        contentPadding = PaddingValues(vertical = Spacing.gutter),
                        // DiaryEntryCard carries its own 12dp of internal padding, which is
                        // what spaces the title from the preview. This is the gap *between*
                        // cards, and it was `Spacing.none`, which is why the list read as one
                        // continuous stack of paper with the shadows merging into each other.
                        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                    ) {
                        items(state.entries, key = { it.id }) { entry ->
                            DiaryEntryCard(
                                entry = entry,
                                onClick = { onOpenEditor(entry.id) },
                                onToggleFavorite = { viewModel.onFavouriteToggled(entry) },
                            )
                        }
                        if (state.hasMore) {
                            item(key = "diary_paging_footer") {
                                PagingFooter()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sort orders offered in the list's overflow menu, in menu order. */
internal val diarySortOrders: List<DiarySortOrder> = DiarySortOrder.entries
