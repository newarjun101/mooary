package com.arjun.mooary.domain.usecase.reminders

import com.arjun.mooary.core.logging.NoOpLogger
import com.arjun.mooary.core.notifications.NotificationManager
import com.arjun.mooary.core.notifications.ReminderKind
import com.arjun.mooary.core.notifications.ReminderRequest
import com.arjun.mooary.core.notifications.ScheduledReminder
import com.arjun.mooary.domain.repository.AppSettings
import com.arjun.mooary.domain.repository.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

/**
 * The preference used to be written without anything ever calling the scheduler, so these
 * tests exist to pin the two together: a stored `remindersEnabled = true` that arms nothing
 * was a real, shipped state that no amount of compiling would have caught.
 */
/** Any fixed instant: the fake scheduler never consults the clock. */
private val now = Instant.parse("2024-04-01T12:00:00Z")

class SyncReminderScheduleUseCaseTest {

    private fun settings(enabled: Boolean, diaryHour: Int = 21, moodHour: Int = 9) =
        MutableSettingsRepository(AppSettings(remindersEnabled = enabled, dailyDiaryReminderHour = diaryHour, dailyMoodReminderHour = moodHour))

    private fun useCase(
        settings: SettingsRepository,
        manager: NotificationManager,
    ) = SyncReminderScheduleUseCase(
        settingsRepository = settings,
        notificationManager = manager,
        logger = NoOpLogger,
    )

    @Test
    fun `enabling reminders arms every kind`() = runTest {
        val store = settings(enabled = false)
        val manager = FakeNotificationManager()

        val outcome = useCase(store, manager).setEnabled(true)

        assertEquals(ReminderOutcome.ARMED, outcome)
        assertEquals(
            setOf(ReminderKind.DAILY_DIARY, ReminderKind.DAILY_MOOD_CHECK_IN, ReminderKind.WEEKLY_REFLECTION),
            manager.scheduled.map { it.kind }.toSet(),
        )
        assertEquals(3, manager.scheduled.size)
    }

    @Test
    fun `the diary reminder fires at the diary hour and the mood one at the mood hour`() = runTest {
        val store = settings(enabled = false, diaryHour = 7, moodHour = 6)
        val manager = FakeNotificationManager()

        useCase(store, manager).setEnabled(true)

        val diary = manager.scheduled.single { it.kind == ReminderKind.DAILY_DIARY }
        val mood = manager.scheduled.single { it.kind == ReminderKind.DAILY_MOOD_CHECK_IN }
        assertEquals(7, diary.time.hour)
        assertEquals(6, mood.time.hour)
    }

    @Test
    fun `the weekly reflection is weekly and the others are daily`() = runTest {
        val manager = FakeNotificationManager()

        useCase(settings(enabled = false), manager).setEnabled(true)

        val reflection = manager.scheduled.single { it.kind == ReminderKind.WEEKLY_REFLECTION }
        assertEquals(setOf(7), reflection.daysOfWeek, "Sunday")
        assertTrue(manager.scheduled.single { it.kind == ReminderKind.DAILY_DIARY }.daysOfWeek.isEmpty())
    }

    @Test
    fun `disabling clears every alarm`() = runTest {
        val store = settings(enabled = true)
        val manager = FakeNotificationManager()

        val outcome = useCase(store, manager).setEnabled(false)

        assertEquals(ReminderOutcome.DISABLED, outcome)
        assertEquals(1, manager.cancelAllCalls, "expected exactly one clear")
        assertTrue(manager.scheduled.isEmpty())
    }

    @Test
    fun `applying twice does not stack duplicate reminders`() = runTest {
        val manager = FakeNotificationManager()
        val reminders = useCase(settings(enabled = true), manager)

        reminders()
        reminders()

        // Ids are derived from the kind, so the second pass replaces rather than adds.
        assertEquals(3, manager.scheduled.size)
        assertEquals(3, manager.scheduled.map { it.id }.toSet().size)
    }

    @Test
    fun `a refused permission arms nothing and is reported`() = runTest {
        val manager = FakeNotificationManager(permitted = false)

        val outcome = useCase(settings(enabled = false), manager).setEnabled(true)

        assertEquals(ReminderOutcome.PERMISSION_DENIED, outcome)
        assertTrue(manager.scheduled.isEmpty(), "nothing may be armed without permission")
        assertTrue(ReminderOutcome.PERMISSION_DENIED.needsNotice)
    }

    @Test
    fun `a platform without a scheduler is reported rather than silently accepted`() = runTest {
        val store = settings(enabled = false)
        val manager = FakeNotificationManager(unsupported = true)

        val outcome = useCase(store, manager).setEnabled(true)

        assertEquals(ReminderOutcome.UNSUPPORTED, outcome)
        assertTrue(ReminderOutcome.UNSUPPORTED.needsNotice)
    }

    @Test
    fun `a successful outcome is not worth a message`() = runTest {
        assertTrue(!ReminderOutcome.ARMED.needsNotice)
        assertTrue(!ReminderOutcome.DISABLED.needsNotice)
    }

    @Test
    fun `an out of range hour is clamped instead of throwing`() = runTest {
        val manager = FakeNotificationManager()

        useCase(settings(enabled = false, diaryHour = 99), manager).setEnabled(true)

        assertEquals(23, manager.scheduled.single { it.kind == ReminderKind.DAILY_DIARY }.time.hour)
    }

    @Test
    fun `reminder ids are stable per kind`() {
        assertEquals(
            SyncReminderScheduleUseCase.idFor(ReminderKind.DAILY_DIARY),
            SyncReminderScheduleUseCase.idFor(ReminderKind.DAILY_DIARY),
        )
        assertTrue(
            SyncReminderScheduleUseCase.idFor(ReminderKind.DAILY_DIARY) !=
                SyncReminderScheduleUseCase.idFor(ReminderKind.DAILY_MOOD_CHECK_IN),
        )
    }


    private class FakeNotificationManager(
        private val permitted: Boolean = true,
        private val unsupported: Boolean = false,
    ) : NotificationManager {
        val scheduled = mutableListOf<ReminderRequest>()
        var cancelAllCalls = 0

        override suspend fun scheduleReminder(request: ReminderRequest): Result<ScheduledReminder> {
            if (unsupported) {
                return Result.failure(UnsupportedOperationException("no scheduler"))
            }
            // Replaces any existing request with the same id, as both platform schedulers do.
            scheduled.removeAll { it.id == request.id }
            scheduled += request
            return Result.success(ScheduledReminder(request.id, now))
        }

        override suspend fun cancelReminder(id: String) {
            scheduled.removeAll { it.id == id }
        }

        override suspend fun cancelAll() {
            cancelAllCalls++
            scheduled.clear()
        }

        override suspend fun scheduled(): List<ScheduledReminder> = emptyList()

        override suspend fun requestPermission(): Boolean = permitted
    }

    private class MutableSettingsRepository(
        initial: AppSettings,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = state
        override suspend fun current(): AppSettings = state.value
        override suspend fun setThemeMode(mode: com.arjun.mooary.domain.repository.ThemeMode) {
            state.value = state.value.copy(themeMode = mode)
        }

        override suspend fun setLanguage(language: com.arjun.mooary.domain.repository.AppLanguage) {
            state.value = state.value.copy(language = language)
        }

        override suspend fun setRemindersEnabled(enabled: Boolean) {
            state.value = state.value.copy(remindersEnabled = enabled)
        }

        override suspend fun setReminderHours(diaryHour: Int, moodHour: Int) {
            state.value = state.value.copy(
                dailyDiaryReminderHour = diaryHour,
                dailyMoodReminderHour = moodHour,
            )
        }

        override suspend fun markOnboardingSeen() {
            state.value = state.value.copy(hasSeenOnboarding = true)
        }

        override suspend fun clear() {
            state.value = AppSettings()
        }
    }
}
