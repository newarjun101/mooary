package com.arjun.soul_note.ui.dashboard

import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.domain.model.DashboardStatistics
import com.arjun.soul_note.domain.model.DiaryDateFilter
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.DiaryFilter
import com.arjun.soul_note.domain.model.MoodFilter
import com.arjun.soul_note.domain.model.OverviewStatistics
import com.arjun.soul_note.domain.model.TimeRange
import com.arjun.soul_note.domain.usecase.diary.ObserveDiaryEntriesUseCase
import com.arjun.soul_note.domain.usecase.diary.ObserveFilteredDiaryEntriesUseCase
import com.arjun.soul_note.domain.usecase.mood.GetMoodHistoryUseCase
import com.arjun.soul_note.domain.usecase.mood.ObserveFilteredMoodsUseCase
import com.arjun.soul_note.domain.usecase.stats.CalculateDashboardStatisticsUseCase
import com.arjun.soul_note.domain.usecase.stats.CalculateOverviewStatisticsUseCase
import com.arjun.soul_note.ui.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class DashboardState(
    val range: TimeRange = TimeRange.Last30Days,
    val greetingName: String? = null,
    val statistics: DashboardStatistics? = null,
    val overview: OverviewStatistics? = null,
    val recentEntries: List<DiaryEntry> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Backing state for the dashboard.
 *
 * Moods are loaded per selected range rather than as a fixed-size recent window: a
 * hard "latest 400 rows" cap silently truncates the yearly view the moment a user journals
 * more than once a day, which would show a full year of bars with only the recent weeks
 * filled in and no way for the user to tell. Entries stay fully loaded because the streak
 * and favourite totals are lifetime figures, not windowed ones.
 */
class DashboardViewModel(
    observeEntries: ObserveDiaryEntriesUseCase,
    observeAllMoods: GetMoodHistoryUseCase,
    observeFilteredMoods: ObserveFilteredMoodsUseCase,
    private val calculateDashboard: CalculateDashboardStatisticsUseCase,
    private val calculateOverview: CalculateOverviewStatisticsUseCase,
    observeFilteredEntries: ObserveFilteredDiaryEntriesUseCase,
    private val userId: String,
    private val today: LocalDate,
    greetingName: String?,
) : BaseViewModel() {

    private val name = MutableStateFlow(greetingName)
    private val range = MutableStateFlow(TimeRange.Last30Days)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val window = range.flatMapLatest { selected ->
        val bounds = DiaryDateFilter.CustomRange(selected.windowStart(today), today)
        combine(
            observeFilteredMoods(userId, MoodFilter(dateFilter = bounds)),
            observeFilteredEntries(userId, DiaryFilter(dateFilter = bounds)),
        ) { moods, entries -> moods to entries }
    }

    val state: StateFlow<DashboardState> = combine(
        window,
        observeEntries(userId),
        observeAllMoods(userId),
        range,
        name,
    ) { windowed, allEntries, allMoods, selected, greeting ->
        val (moods, windowedEntries) = windowed
        val visibleEntries = allEntries.filter { it.isVisible() }
        val visibleMoods = moods.filter { it.isVisible() }
        val statistics = calculateDashboard(
            entries = windowedEntries,
            moods = visibleMoods,
            today = today,
            range = selected,
        )
        DashboardState(
            range = selected,
            greetingName = greeting,
            statistics = statistics,
            // Lifetime figures, so they are computed from the unfiltered history: feeding
            // the windowed moods in here made the streak tile shrink from 23 days to 8
            // when the 7-day chip was selected, which is nonsense for a streak.
            overview = calculateOverview(allEntries, allMoods, today, statistics.mood),
            recentEntries = visibleEntries.sortedByDescending { it.entryDate }.take(RECENT_LIMIT),
            isLoading = false,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DashboardState())

    fun onRangeSelected(selected: TimeRange) {
        range.value = selected
    }

    /** Pull-to-refresh: asks the engine for a pass, then waits for the state to settle. */
    fun onRefreshRequested(requestSync: () -> Unit) {
        launch {
            runCatching { requestSync() }.onFailure { report(it.toAppError()) }
        }
    }

    private companion object {
        const val RECENT_LIMIT = 5
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
