package com.arjun.soul_note.di

import com.arjun.soul_note.core.analytics.AnalyticsSink
import com.arjun.soul_note.core.analytics.AnalyticsTracker
import com.arjun.soul_note.core.analytics.InMemoryAnalyticsSink
import com.arjun.soul_note.core.common.DispatcherProvider
import com.arjun.soul_note.core.common.PlatformDispatcherProvider
import com.arjun.soul_note.core.datetime.SystemTimeProvider
import com.arjun.soul_note.core.datetime.TimeProvider
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.logging.ConsoleLogger
import com.arjun.soul_note.core.logging.NoOpLogger
import com.arjun.soul_note.core.network.AlwaysOnlineObserver
import com.arjun.soul_note.core.network.ConnectivityObserver
import com.arjun.soul_note.core.notifications.NotificationManager
import com.arjun.soul_note.core.notifications.PushRegistrar
import com.arjun.soul_note.core.notifications.createPushRegistrar
import com.arjun.soul_note.core.platform.FileExporter
import com.arjun.soul_note.core.platform.FirebaseConfig
import com.arjun.soul_note.core.platform.SecureTokenStore
import com.arjun.soul_note.core.platform.createFileExporter
import com.arjun.soul_note.core.platform.createNotificationManager
import com.arjun.soul_note.core.platform.createSecureTokenStore
import com.arjun.soul_note.core.platform.loadFirebaseConfig
import com.arjun.soul_note.core.platform.PlatformEnvironment
import com.arjun.soul_note.core.platform.platformEnvironment
import com.arjun.soul_note.data.local.LocalStore
import com.arjun.soul_note.data.local.createDatabaseDriver
import com.arjun.soul_note.data.local.db.DiaryDatabase
import com.arjun.soul_note.data.remote.HttpClientFactory
import com.arjun.soul_note.data.remote.OfflineRemoteDataSources
import com.arjun.soul_note.data.remote.RemoteAuthDataSource
import com.arjun.soul_note.data.remote.RemoteDiaryDataSource
import com.arjun.soul_note.data.remote.RemoteMoodDataSource
import com.arjun.soul_note.data.remote.RemoteUserDataSource
import com.arjun.soul_note.data.remote.firebase.FirebaseAuthClient
import com.arjun.soul_note.data.remote.firebase.FirebaseAuthDataSource
import com.arjun.soul_note.data.remote.firebase.FirebaseDiaryDataSource
import com.arjun.soul_note.data.remote.firebase.FirebaseMoodDataSource
import com.arjun.soul_note.data.remote.firebase.FirebaseUserDataSource
import com.arjun.soul_note.data.remote.firebase.FirestoreClient
import com.arjun.soul_note.data.remote.firebase.FirebaseStorageClient
import com.arjun.soul_note.data.repository.DefaultAuthRepository
import com.arjun.soul_note.data.repository.DefaultDiaryRepository
import com.arjun.soul_note.data.repository.DefaultDraftRepository
import com.arjun.soul_note.data.repository.DefaultMoodRepository
import com.arjun.soul_note.data.repository.DefaultSettingsRepository
import com.arjun.soul_note.data.repository.DefaultSyncStatusRepository
import com.arjun.soul_note.data.repository.DefaultPhotoRepository
import com.arjun.soul_note.data.repository.DefaultUserRepository
import com.arjun.soul_note.data.sync.SessionProvider
import com.arjun.soul_note.data.sync.SyncManager
import com.arjun.soul_note.ui.base.SessionHolder
import com.arjun.soul_note.domain.repository.AuthRepository
import com.arjun.soul_note.domain.repository.DiaryRepository
import com.arjun.soul_note.domain.repository.DraftRepository
import com.arjun.soul_note.domain.repository.MoodRepository
import com.arjun.soul_note.domain.repository.SettingsRepository
import com.arjun.soul_note.domain.repository.SyncStatusRepository
import com.arjun.soul_note.domain.repository.PhotoRepository
import com.arjun.soul_note.domain.repository.UserRepository
import com.arjun.soul_note.data.seed.SeedDemoDataUseCase
import com.arjun.soul_note.domain.usecase.auth.ChangePasswordUseCase
import com.arjun.soul_note.domain.usecase.auth.DeleteAccountUseCase
import com.arjun.soul_note.domain.usecase.auth.LoginUseCase
import com.arjun.soul_note.domain.usecase.auth.LogoutUseCase
import com.arjun.soul_note.domain.usecase.auth.ObserveAuthStateUseCase
import com.arjun.soul_note.domain.usecase.auth.RegisterUseCase
import com.arjun.soul_note.domain.usecase.auth.RestoreSessionUseCase
import com.arjun.soul_note.domain.usecase.auth.SendPasswordResetUseCase
import com.arjun.soul_note.domain.usecase.diary.CreateDiaryEntryUseCase
import com.arjun.soul_note.domain.usecase.diary.DeleteDiaryEntryUseCase
import com.arjun.soul_note.domain.usecase.diary.ObserveAllTagsUseCase
import com.arjun.soul_note.domain.usecase.diary.ObserveDiaryEntriesUseCase
import com.arjun.soul_note.domain.usecase.diary.ObserveFilteredDiaryEntriesUseCase
import com.arjun.soul_note.domain.usecase.diary.ObserveDiaryPageUseCase
import com.arjun.soul_note.domain.usecase.diary.ObserveDiaryEntryUseCase
import com.arjun.soul_note.domain.usecase.diary.ObserveFavoritesUseCase
import com.arjun.soul_note.domain.usecase.diary.ObserveRecentDiaryEntriesUseCase
import com.arjun.soul_note.domain.usecase.diary.ToggleFavoriteEntryUseCase
import com.arjun.soul_note.domain.usecase.diary.UpdateDiaryEntryUseCase
import com.arjun.soul_note.domain.usecase.export.ExportUserDataUseCase
import com.arjun.soul_note.domain.usecase.reminders.SyncReminderScheduleUseCase
import com.arjun.soul_note.domain.usecase.mood.DeleteMoodUseCase
import com.arjun.soul_note.domain.usecase.mood.ObserveDayMoodSummariesUseCase
import com.arjun.soul_note.domain.usecase.mood.ObserveMoodsForDateUseCase
import com.arjun.soul_note.domain.usecase.mood.GetMoodHistoryUseCase
import com.arjun.soul_note.domain.usecase.mood.ObserveFilteredMoodsUseCase
import com.arjun.soul_note.domain.usecase.mood.ObserveRecentMoodsUseCase
import com.arjun.soul_note.domain.usecase.mood.RecordMoodUseCase
import com.arjun.soul_note.domain.usecase.mood.UpdateMoodUseCase
import com.arjun.soul_note.domain.usecase.stats.CalculateDashboardStatisticsUseCase
import com.arjun.soul_note.domain.usecase.stats.CalculateMoodStatisticsUseCase
import com.arjun.soul_note.domain.usecase.stats.CalculateOverviewStatisticsUseCase
import com.arjun.soul_note.domain.usecase.stats.CalculateStreakUseCase
import com.arjun.soul_note.ui.navigation.EditorTarget
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.uuid.Uuid

/**
 * Firebase configuration, or an explicit record that there is none.
 *
 * Wrapped in a non-null type so Koin never has to store a `null` definition: the
 * app-supporting decision "is this build local-only?" is then a plain property read
 * instead of a `singleOrNull` that may or may not resolve.
 */
data class RemoteConfig(val config: FirebaseConfig?) {
    val isConfigured: Boolean get() = config?.isValid == true
}

/**
 * Everything the app is built from.
 *
 * Construction order is handled here rather than at the call sites:
 *
 *  * The sync engine needs the current user id, which the auth stack owns, so
 *    [SyncManager] is created after the auth bindings and reads it through a
 *    [SessionProvider] — wiring it eagerly would be a construction cycle.
 *  * The repositories accept an optional `SyncManager`, so they are declared after it.
 *
 * Everything is a singleton: the SQLDelight database, the Ktor client and the local-only
 * fallback set all own real resources that must exist exactly once per process.
 */
val appModule: Module = module {

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<AppLogger> {
        if (platformEnvironment().isDebugBuild) ConsoleLogger(enabled = true) else NoOpLogger
    }

    single { platformEnvironment() }
    single<DispatcherProvider> { PlatformDispatcherProvider }
    single<TimeProvider> { SystemTimeProvider() }
    single<ConnectivityObserver> { AlwaysOnlineObserver() }
    single<AnalyticsSink> { InMemoryAnalyticsSink() }
    single { AnalyticsTracker(get()) }
    single { RemoteConfig(loadFirebaseConfig()) }
    single { EditorTarget() }
    single<OfflineRemoteDataSources> { OfflineRemoteDataSources(get()) }


    single { DiaryDatabase(createDatabaseDriver()) }
    single { LocalStore(database = get(), io = PlatformDispatcherProvider.io, logger = get()) }


    single<HttpClient> { HttpClientFactory.create() }

    // Declared here but only built if a configured branch asks for it: a local-only build
    // resolves the offline sources instead and never opens a connection. One instance is
    // shared by all three data sources so the Ktor engine and the JSON codec exist once.
    single<FirestoreClient> {
        val config = get<RemoteConfig>().config
            ?: error("FirestoreClient resolved in a build with no Firebase configuration")
        FirestoreClient(
            http = get(),
            config = config,
            // The Firestore REST API authenticates with the caller's *ID token*, not their
            // uid. Passing the uid here produced `Authorization: Bearer <uid>`, which
            // Identity Toolkit rejects with 401 and which surfaces to the user as a
            // PERMISSION_DENIED that looks exactly like a security-rules fault.
            tokenProvider = { get<RemoteAuthDataSource>().idToken() },
            logger = get(),
        )
    }
    // Storage authenticates the same way Firestore does -- an ID token as a bearer, which the
    // object API does accept. Only resolved when configured, mirroring PushRegistrar.
    single<FirebaseStorageClient> {
        val config = get<RemoteConfig>().config
            ?: error("FirebaseStorageClient resolved in a build with no Firebase configuration")
        FirebaseStorageClient(
            http = get(),
            config = config,
            tokenProvider = { get<RemoteAuthDataSource>().idToken() },
            logger = get(),
        )
    }

    single<PhotoRepository> {
        val remoteConfig = get<RemoteConfig>()
        DefaultPhotoRepository(
            storage = if (remoteConfig.isConfigured) get() else null,
            io = get<DispatcherProvider>().io,
        )
    }

    single { newId }

    single<SecureTokenStore> { createSecureTokenStore(get()) }

    single<RemoteAuthDataSource> {
        val config = get<RemoteConfig>().config
        if (config == null) {
            get<OfflineRemoteDataSources>().auth
        } else {
            FirebaseAuthDataSource(
                client = FirebaseAuthClient(get(), config, get()),
                tokenStore = get(),
                logger = get(),
            )
        }
    }

    single<RemoteDiaryDataSource> {
        val config = get<RemoteConfig>().config
        if (config == null) {
            get<OfflineRemoteDataSources>().diary
        } else {
            FirebaseDiaryDataSource(client = get(), logger = get())
        }
    }

    single<RemoteMoodDataSource> {
        val config = get<RemoteConfig>().config
        if (config == null) {
            get<OfflineRemoteDataSources>().mood
        } else {
            FirebaseMoodDataSource(client = get(), logger = get())
        }
    }

    single<RemoteUserDataSource> {
        val config = get<RemoteConfig>().config
        if (config == null) {
            get<OfflineRemoteDataSources>().user
        } else {
            FirebaseUserDataSource(client = get())
        }
    }


    single<SessionProvider> {
        val remote: RemoteAuthDataSource = get()
        SessionProvider { remote.currentUserId() }
    }

    single {
        SyncManager(
            local = get(),
            remoteDiary = get(),
            remoteMood = get(),
            remoteUser = get(),
            session = get(),
            connectivity = get(),
            timeProvider = get(),
            dispatchers = get(),
            logger = get(),
            scope = get(),
        )
    }


    single<AuthRepository> {
        DefaultAuthRepository(
            remote = get(),
            push = get(),
            logger = get(),
            scope = get(),
        )
    }

    single<DiaryRepository> {
        DefaultDiaryRepository(
            local = get(),
            remote = get(),
            timeProvider = get(),
            dispatchers = get(),
            connectivity = get(),
            logger = get(),
            syncManager = get(),
        )
    }

    single<MoodRepository> {
        DefaultMoodRepository(
            local = get(),
            remote = get(),
            timeProvider = get(),
            dispatchers = get(),
            connectivity = get(),
            logger = get(),
            syncManager = get(),
        )
    }

    single<UserRepository> {
        DefaultUserRepository(
            local = get(),
            remote = get(),
            timeProvider = get(),
            logger = get(),
            syncManager = get(),
        )
    }

    single<SettingsRepository> { DefaultSettingsRepository(local = get()) }
    single<DraftRepository> { DefaultDraftRepository(local = get()) }
    single<SyncStatusRepository> {
        val syncManager: SyncManager = get()
        DefaultSyncStatusRepository(local = get(), syncStateSource = syncManager.state)
    }


    single<FileExporter> { createFileExporter(logger = get()) }
    single<NotificationManager> { createNotificationManager(logger = get()) }
    // Resolves to an unsupported registrar wherever there is no messaging SDK, or where the
    // SDK is present but not usable. Registered rather than resolved at the call site because a
    // missing bean here fails only when someone first signs in.
    single<PushRegistrar> {
        createPushRegistrar(
            // Resolved only when the build is configured. An unconditional `get()` here
            // built a FirestoreClient in a local-only build, whose definition throws, so the
            // registrar could not be created at all even though the local-only answer is the
            // unsupported one it would have selected anyway.
            firestore = if (get<RemoteConfig>().isConfigured) get() else null,
            logger = get(),
        )
    }


    single { SessionHolder(authRepository = get(), restoreSession = get(), scope = get()) }


    factory { LoginUseCase(authRepository = get(), userRepository = get(), analytics = get(), now = { get<TimeProvider>().now() }) }
    factory { RegisterUseCase(authRepository = get(), userRepository = get(), analytics = get(), now = { get<TimeProvider>().now() }) }
    factory { LogoutUseCase(authRepository = get(), userRepository = get(), analytics = get()) }
    factory {
        SyncReminderScheduleUseCase(
            settingsRepository = get(),
            notificationManager = get(),
            logger = get(),
        )
    }
    factory { SendPasswordResetUseCase(authRepository = get(), analytics = get()) }
    factory { ObserveAuthStateUseCase(authRepository = get()) }
    factory { RestoreSessionUseCase(authRepository = get()) }
    factory { DeleteAccountUseCase(authRepository = get(), userRepository = get(), analytics = get()) }
    factory { ChangePasswordUseCase(authRepository = get()) }

    factory {
        CreateDiaryEntryUseCase(
            repository = get(),
            analytics = get(),
            now = { get<TimeProvider>().now() },
            newId = { get<() -> String>()() },
        )
    }
    factory {
        UpdateDiaryEntryUseCase(
            repository = get(),
            analytics = get(),
            now = { get<TimeProvider>().now() },
        )
    }
    factory { DeleteDiaryEntryUseCase(repository = get(), analytics = get()) }
    factory { ObserveDiaryEntriesUseCase(repository = get()) }
    factory { ObserveFilteredDiaryEntriesUseCase(repository = get()) }
    factory { ObserveDiaryEntryUseCase(repository = get()) }
    factory { ObserveRecentDiaryEntriesUseCase(repository = get()) }
    factory { ObserveDiaryPageUseCase(repository = get()) }
    factory { ObserveFavoritesUseCase(repository = get()) }
    factory { ObserveAllTagsUseCase(repository = get()) }
    factory { ToggleFavoriteEntryUseCase(repository = get(), analytics = get()) }

    factory {
        RecordMoodUseCase(
            repository = get(),
            analytics = get(),
            now = { get<TimeProvider>().now() },
            newId = { get<() -> String>()() },
        )
    }
    factory {
        UpdateMoodUseCase(
            repository = get(),
            analytics = get(),
            now = { get<TimeProvider>().now() },
        )
    }
    factory { DeleteMoodUseCase(repository = get(), analytics = get()) }
    factory { ObserveRecentMoodsUseCase(repository = get()) }
    factory { ObserveFilteredMoodsUseCase(repository = get()) }
    factory { GetMoodHistoryUseCase(repository = get()) }
    factory { ObserveMoodsForDateUseCase(repository = get()) }
    factory { ObserveDayMoodSummariesUseCase(repository = get()) }

    factory { CalculateStreakUseCase() }
    factory { CalculateMoodStatisticsUseCase() }
    factory { CalculateDashboardStatisticsUseCase(calculateMoodStatistics = get()) }
    factory { CalculateOverviewStatisticsUseCase(calculateStreak = get()) }
    factory {
        ExportUserDataUseCase(
            userRepository = get(),
            diaryRepository = get(),
            moodRepository = get(),
            exporter = get(),
            analytics = get(),
            now = { get<TimeProvider>().now() },
        )
    }

    factory {
        SeedDemoDataUseCase(
            local = get(),
            timeProvider = get(),
            timeZoneProvider = get<PlatformEnvironment>().timeZone,
        )
    }
}

/** Shared so the id factory has one definition rather than one per use case. */
private val newId: () -> String = { Uuid.random().toString() }
