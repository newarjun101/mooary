package com.arjun.soul_note.di

import com.arjun.soul_note.ui.auth.AuthViewModel
import com.arjun.soul_note.ui.calendar.CalendarViewModel
import com.arjun.soul_note.ui.diary.DiaryEditorViewModel
import com.arjun.soul_note.ui.diary.DiaryListViewModel
import com.arjun.soul_note.ui.dashboard.DashboardViewModel
import com.arjun.soul_note.ui.mood.MoodViewModel
import com.arjun.soul_note.ui.profile.ProfileViewModel
import com.arjun.soul_note.ui.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Screen ViewModels.
 *
 * All are `factory` on purpose: each one is created for a concrete (user, date) pair by
 * [com.arjun.soul_note.ui.base.rememberViewModel], which disposes it when the screen leaves.
 * Caching them in Koin would leak a scope per navigation and keep stale text alive, so the
 * lifetime is owned by the composition instead.
 *
 * The order of the Koin parameters must match the constructor order of each ViewModel —
 * they are positional, because the screen decides the values at the call site.
 */
val viewModelModule: Module = module {

    factory {
        AuthViewModel(login = get(), register = get(), resetPassword = get())
    }

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

    factory { (userId: String, user: com.arjun.soul_note.domain.model.User?) ->
        ProfileViewModel(
            observeProfile = get(),
            settingsRepository = get(),
            syncManager = get(),
            exportData = get(),
            restoreSession = get(),
            syncReminderSchedule = get(),
            userId = userId,
            user = user,
        )
    }

    factory { (userId: String, email: String?) ->
        SettingsViewModel(
            observeProfile = get(),
            userRepository = get(),
            changePassword = get(),
            logoutUseCase = get(),
            deleteAccountUseCase = get(),
            photoRepository = get(),
            settingsRepository = get(),
            userId = userId,
            email = email,
        )
    }
}

/** Everything the app needs, in one list. */
val diaryModules: List<Module> = listOf(appModule, viewModelModule)
