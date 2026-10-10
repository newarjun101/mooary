package com.arjun.mooary.di

import com.arjun.mooary.ui.calendar.CalendarViewModel
import com.arjun.mooary.ui.diary.DiaryEditorViewModel
import com.arjun.mooary.ui.diary.DiaryListViewModel
import com.arjun.mooary.ui.dashboard.DashboardViewModel
import com.arjun.mooary.ui.mood.MoodViewModel
import com.arjun.mooary.ui.profile.ProfileViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Screen ViewModels.
 *
 * All are `factory` on purpose: each one is created for a concrete (user, date) pair by
 * [com.arjun.mooary.ui.base.rememberViewModel], which disposes it when the screen leaves.
 * Caching them in Koin would leak a scope per navigation and keep stale text alive, so the
 * lifetime is owned by the composition instead.
 *
 * The order of the Koin parameters must match the constructor order of each ViewModel —
 * they are positional, because the screen decides the values at the call site.
 */
val viewModelModule: Module = module {

    factory { (userId: String, today: kotlinx.datetime.LocalDate) ->
        DiaryListViewModel(
            observePage = get(),
            observeTags = get(),
            toggleFavorite = get(),
            userId = userId,
        )
    }

    factory { (userId: String, today: kotlinx.datetime.LocalDate, entryId: String?) ->
        DiaryEditorViewModel(
            observeEntry = get(),
            createEntry = get(),
            updateEntry = get(),
            deleteEntry = get(),
            drafts = get(),
            userId = userId,
            today = today,
            entryIdToEdit = entryId,
        )
    }

    factory { (userId: String, today: kotlinx.datetime.LocalDate) ->
        MoodViewModel(
            observeHistory = get(),
            recordMood = get(),
            updateMood = get(),
            deleteMood = get(),
            userId = userId,
            today = today,
        )
    }

    factory { (userId: String, today: kotlinx.datetime.LocalDate, greetingName: String?) ->
        DashboardViewModel(
            observeEntries = get(),
            observeAllMoods = get(),
            observeFilteredMoods = get(),
            observeFilteredEntries = get(),
            calculateDashboard = get(),
            calculateOverview = get(),
            toggleFavorite = get(),
            userId = userId,
            today = today,
            greetingName = greetingName,
        )
    }

    factory { (userId: String, today: kotlinx.datetime.LocalDate) ->
        CalendarViewModel(
            observeEntries = get(),
            observeSummaries = get(),
            userId = userId,
            today = today,
        )
    }

    factory { (userId: String) ->
        ProfileViewModel(
            settingsRepository = get(),
            exportData = get(),
            syncReminderSchedule = get(),
            userId = userId,
        )
    }
}

/** Everything the app needs, in one list. */
val diaryModules: List<Module> = listOf(appModule, viewModelModule)
