package com.arjun.mooary.di

import com.arjun.mooary.core.common.DispatcherProvider
import com.arjun.mooary.core.common.PlatformDispatcherProvider
import com.arjun.mooary.core.datetime.SystemTimeProvider
import com.arjun.mooary.core.datetime.TimeProvider
import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.core.logging.ConsoleLogger
import com.arjun.mooary.core.logging.NoOpLogger
import com.arjun.mooary.core.notifications.DeviceRegistration
import com.arjun.mooary.core.notifications.FirebaseDeviceRegistration
import com.arjun.mooary.core.notifications.NotificationManager
import com.arjun.mooary.core.notifications.UnsupportedDeviceRegistration
import com.arjun.mooary.core.platform.FileExporter
import com.arjun.mooary.core.platform.PlatformEnvironment
import com.arjun.mooary.core.platform.createFileExporter
import com.arjun.mooary.core.platform.createNotificationManager
import com.arjun.mooary.core.platform.loadFirebaseConfig
import com.arjun.mooary.core.platform.platformEnvironment
import com.arjun.mooary.data.local.LocalStore
import com.arjun.mooary.data.local.createDatabaseDriver
import com.arjun.mooary.data.local.db.DiaryDatabase
import com.arjun.mooary.data.remote.HttpClientFactory
import com.arjun.mooary.data.repository.DefaultDiaryRepository
import com.arjun.mooary.data.repository.DefaultDraftRepository
import com.arjun.mooary.data.repository.DefaultMoodRepository
import com.arjun.mooary.data.repository.DefaultSettingsRepository
import com.arjun.mooary.data.repository.DefaultUserRepository
import com.arjun.mooary.data.seed.SeedDemoDataUseCase
import com.arjun.mooary.domain.repository.DiaryRepository
import com.arjun.mooary.domain.repository.DraftRepository
import com.arjun.mooary.domain.repository.MoodRepository
import com.arjun.mooary.domain.repository.SettingsRepository
import com.arjun.mooary.domain.repository.UserRepository
import com.arjun.mooary.domain.usecase.diary.CreateDiaryEntryUseCase
import com.arjun.mooary.domain.usecase.diary.DeleteDiaryEntryUseCase
import com.arjun.mooary.domain.usecase.diary.ObserveAllTagsUseCase
import com.arjun.mooary.domain.usecase.diary.ObserveDiaryEntriesUseCase
import com.arjun.mooary.domain.usecase.diary.ObserveDiaryEntryUseCase
import com.arjun.mooary.domain.usecase.diary.ObserveDiaryPageUseCase
import com.arjun.mooary.domain.usecase.diary.ObserveFavoritesUseCase
import com.arjun.mooary.domain.usecase.diary.ObserveFilteredDiaryEntriesUseCase
import com.arjun.mooary.domain.usecase.diary.ObserveRecentDiaryEntriesUseCase
import com.arjun.mooary.domain.usecase.diary.ToggleFavoriteEntryUseCase
import com.arjun.mooary.domain.usecase.diary.UpdateDiaryEntryUseCase
import com.arjun.mooary.domain.usecase.export.ExportUserDataUseCase
import com.arjun.mooary.domain.usecase.mood.DeleteMoodUseCase
import com.arjun.mooary.domain.usecase.mood.GetMoodHistoryUseCase
import com.arjun.mooary.domain.usecase.mood.ObserveDayMoodSummariesUseCase
import com.arjun.mooary.domain.usecase.mood.ObserveFilteredMoodsUseCase
import com.arjun.mooary.domain.usecase.mood.ObserveMoodsForDateUseCase
import com.arjun.mooary.domain.usecase.mood.ObserveRecentMoodsUseCase
import com.arjun.mooary.domain.usecase.mood.RecordMoodUseCase
import com.arjun.mooary.domain.usecase.mood.UpdateMoodUseCase
import com.arjun.mooary.domain.usecase.reminders.SyncReminderScheduleUseCase
import com.arjun.mooary.domain.usecase.stats.CalculateDashboardStatisticsUseCase
import com.arjun.mooary.domain.usecase.stats.CalculateMoodStatisticsUseCase
import com.arjun.mooary.domain.usecase.stats.CalculateOverviewStatisticsUseCase
import com.arjun.mooary.domain.usecase.stats.CalculateStreakUseCase
import com.arjun.mooary.ui.base.SessionHolder
import com.arjun.mooary.ui.navigation.EditorTarget
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.uuid.Uuid

/** Dependency graph for the local-first application. */
val appModule: Module = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<AppLogger> { if (platformEnvironment().isDebugBuild) ConsoleLogger(true) else NoOpLogger }
    single { platformEnvironment() }
    single<DispatcherProvider> { PlatformDispatcherProvider }
    single<TimeProvider> { SystemTimeProvider() }
    single { EditorTarget() }
    single { DiaryDatabase(createDatabaseDriver()) }
    single { LocalStore(database = get(), io = PlatformDispatcherProvider.io, logger = get()) }
    single<HttpClient> { HttpClientFactory.create() }
    single { newId }

    single<DiaryRepository> { DefaultDiaryRepository(local = get(), timeProvider = get(), dispatchers = get()) }
    single<MoodRepository> { DefaultMoodRepository(local = get(), timeProvider = get(), dispatchers = get()) }
    single<UserRepository> { DefaultUserRepository(local = get(), timeProvider = get(), logger = get()) }
    single<SettingsRepository> { DefaultSettingsRepository(local = get()) }
    single<DraftRepository> { DefaultDraftRepository(local = get()) }
    single<FileExporter> { createFileExporter(logger = get()) }
    single<NotificationManager> { createNotificationManager(logger = get()) }
    single<DeviceRegistration> {
        loadFirebaseConfig()?.let { FirebaseDeviceRegistration(http = get(), config = it, logger = get()) }
            ?: UnsupportedDeviceRegistration
    }
    single { SessionHolder() }

    factory { SyncReminderScheduleUseCase(settingsRepository = get(), notificationManager = get(), logger = get()) }
    factory { CreateDiaryEntryUseCase(repository = get(), now = { get<TimeProvider>().now() }, newId = { get<() -> String>()() }) }
    factory { UpdateDiaryEntryUseCase(repository = get(), now = { get<TimeProvider>().now() }) }
    factory { DeleteDiaryEntryUseCase(repository = get()) }
    factory { ObserveDiaryEntriesUseCase(repository = get()) }
    factory { ObserveFilteredDiaryEntriesUseCase(repository = get()) }
    factory { ObserveDiaryEntryUseCase(repository = get()) }
    factory { ObserveRecentDiaryEntriesUseCase(repository = get()) }
    factory { ObserveDiaryPageUseCase(repository = get()) }
    factory { ObserveFavoritesUseCase(repository = get()) }
    factory { ObserveAllTagsUseCase(repository = get()) }
    factory { ToggleFavoriteEntryUseCase(repository = get()) }
    factory { RecordMoodUseCase(repository = get(), now = { get<TimeProvider>().now() }, newId = { get<() -> String>()() }) }
    factory { UpdateMoodUseCase(repository = get(), now = { get<TimeProvider>().now() }) }
    factory { DeleteMoodUseCase(repository = get()) }
    factory { ObserveRecentMoodsUseCase(repository = get()) }
    factory { ObserveFilteredMoodsUseCase(repository = get()) }
    factory { GetMoodHistoryUseCase(repository = get()) }
    factory { ObserveMoodsForDateUseCase(repository = get()) }
    factory { ObserveDayMoodSummariesUseCase(repository = get()) }
    factory { CalculateStreakUseCase() }
    factory { CalculateMoodStatisticsUseCase() }
    factory { CalculateDashboardStatisticsUseCase(calculateMoodStatistics = get()) }
    factory { CalculateOverviewStatisticsUseCase(calculateStreak = get()) }
    factory { ExportUserDataUseCase(userRepository = get(), diaryRepository = get(), moodRepository = get(), exporter = get(), now = { get<TimeProvider>().now() }) }
    factory { SeedDemoDataUseCase(local = get(), timeProvider = get(), timeZoneProvider = get<PlatformEnvironment>().timeZone) }
}

private val newId: () -> String = { Uuid.random().toString() }
