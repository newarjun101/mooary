package com.arjun.soul_note.ui.calendar

import com.arjun.soul_note.core.datetime.LocalDates
import com.arjun.soul_note.domain.model.CalendarDay
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.DayMoodSummary
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.domain.usecase.diary.ObserveDiaryEntriesUseCase
import com.arjun.soul_note.domain.usecase.mood.ObserveDayMoodSummariesUseCase
import com.arjun.soul_note.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** The month grid and the selected day. */
data class CalendarState(
    val month: LocalDate,
    val days: List<CalendarDay> = emptyList(),
    val selectedDate: LocalDate? = null,
    val entriesForDay: List<DiaryEntry> = emptyList(),
    val moodsForDay: List<MoodEntry> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Backing state for the calendar.
 *
 * The grid is built once from the month's day range rather than from the raw entry list,
 * so leading and trailing days from the neighbouring months are present as empty cells and
 * the month always has a stable shape instead of reflowing as entries are added.
 */
class CalendarViewModel(
    observeEntries: ObserveDiaryEntriesUseCase,
    observeSummaries: ObserveDayMoodSummariesUseCase,
    private val userId: String,
    private val today: LocalDate,
) : BaseViewModel() {

    private val month = MutableStateFlow(LocalDates.monthStart(today))
    private val selected = MutableStateFlow<LocalDate?>(today)

    val state: StateFlow<CalendarState> = combine(
        observeEntries(userId),
        observeSummaries(userId),
        month,
        selected,
    ) { entries, summaries, currentMonth, selectedDate ->
        val start = LocalDates.monthStart(currentMonth)
        val end = LocalDates.monthEnd(currentMonth)
        val byDate = entries.filter { it.isVisible() }.groupBy { it.entryDate }
        val summariesByDate = summaries.associateBy { it.date }

        CalendarState(
            month = currentMonth,
            days = LocalDates.monthDates(currentMonth).map { date ->
                CalendarDay(
                    date = date,
                    isInMonth = true,
                    diaryEntryCount = byDate[date]?.size ?: 0,
                    moodSummary = summariesByDate[date]?.takeIf { it.count > 0 },
                    isToday = date == today,
                    isFuture = date > today,
                )
            },
            selectedDate = selectedDate,
            entriesForDay = selectedDate?.let { byDate[it] }.orEmpty(),
            moodsForDay = selectedDate?.let { summariesByDate[it]?.entries }.orEmpty(),
            isLoading = false,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CalendarState(month = today))

    fun selectDate(date: LocalDate) {
        selected.value = date
    }

    fun showPreviousMonth() {
        month.value = LocalDates.monthStart(month.value.minusMonths(1))
    }

    fun showNextMonth() {
        val next = LocalDates.monthStart(month.value.plusMonths(1))
        // Never page past the current month: there is nothing there to write about yet.
        if (next <= today) month.value = next
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun LocalDate.minusMonths(count: Int): LocalDate =
    LocalDates.monthStart(this).plus(DatePeriod(months = -count))

private fun LocalDate.plusMonths(count: Int): LocalDate =
    LocalDates.monthStart(this).plus(DatePeriod(months = count))
