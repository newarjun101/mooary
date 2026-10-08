package com.arjun.soul_note.ui.profile

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.domain.model.ExportFormat
import com.arjun.soul_note.domain.model.SyncState
import com.arjun.soul_note.domain.model.User
import com.arjun.soul_note.domain.model.UserProfile
import com.arjun.soul_note.domain.repository.SettingsRepository
import com.arjun.soul_note.domain.repository.AppLanguage
import com.arjun.soul_note.domain.repository.ThemeMode
import com.arjun.soul_note.domain.repository.UserRepository
import com.arjun.soul_note.domain.usecase.auth.RestoreSessionUseCase
import com.arjun.soul_note.domain.usecase.reminders.ReminderOutcome
import com.arjun.soul_note.domain.usecase.reminders.SyncReminderScheduleUseCase
import com.arjun.soul_note.domain.usecase.reminders.needsNotice
import com.arjun.soul_note.domain.usecase.export.ExportUserDataUseCase
import com.arjun.soul_note.data.sync.SyncManager
import com.arjun.soul_note.ui.base.BaseViewModel
import com.arjun.soul_note.core.platform.applyAppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Profile, sync status, settings and the destructive actions. */
data class ProfileState(
    val user: User? = null,
    val profile: UserProfile? = null,
    val sync: SyncState = SyncState(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val remindersEnabled: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    /** Set when the platform refused the reminder schedule, so the UI can say so. */
    val reminderNotice: ReminderOutcome? = null,
    val error: AppError? = null,
)

/**
 * Backing state for the profile screen.
 *
 * Sign-out and account deletion are not here: they moved to the settings screen, which is
 * where a user goes deliberately to change how their account is set up.
 */
class ProfileViewModel(
    observeProfile: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val exportData: ExportUserDataUseCase,
    private val restoreSession: RestoreSessionUseCase,
    private val syncReminderSchedule: SyncReminderScheduleUseCase,
    private val userId: String,
    user: User?,
) : BaseViewModel() {

    private val local = MutableStateFlow(LocalProfileState())

    val state: StateFlow<ProfileState> = combine(
        observeProfile.observeProfile(userId),
        settingsRepository.settings,
        syncManager.state,
        local,
    ) { profile, settings, sync, localState ->
        ProfileState(
            user = user,
            profile = profile,
            sync = sync,
            themeMode = settings.themeMode,
            language = settings.language,
            remindersEnabled = settings.remindersEnabled,
            isExporting = localState.isExporting,
            exportMessage = localState.exportMessage,
            reminderNotice = localState.reminderNotice,
            error = localState.error,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProfileState(user = user))

    private data class LocalProfileState(
        val isExporting: Boolean = false,
        val exportMessage: String? = null,
        val reminderNotice: ReminderOutcome? = null,
        val error: AppError? = null,
    )

    fun setThemeMode(mode: ThemeMode) {
        launch {
            runCatching { settingsRepository.setThemeMode(mode) }
                .onFailure { report(it.toAppError()) }
        }
    }

    /** Changes the shared locale in place, without recreating the current activity. */
    fun setLanguage(language: AppLanguage) {
        launch {
            settingsRepository.setLanguage(language)
            applyAppLanguage(language)
        }
    }

    /**
     * Persists the preference and then actually arms or clears the platform reminders.
     *
     * Writing the setting alone would leave the switch reporting a state the scheduler knows
     * nothing about, which is how a user ends up with reminders "on" and no reminders.
     */
    fun setRemindersEnabled(enabled: Boolean) {
        launch {
            runCatching { syncReminderSchedule.setEnabled(enabled) }
                .onSuccess { outcome ->
                    local.update { it.copy(reminderNotice = outcome.takeIf { it.needsNotice }) }
                }
                .onFailure { report(it.toAppError()) }
        }
    }

    /**
     * Records a refusal that happened before the use case was ever called.
     *
     * The permission prompt is answered by the platform, not by the scheduler, so a declined
     * prompt never reaches [SyncReminderScheduleUseCase]. Without this the switch would snap
     * back with no explanation, and the user would have no reason to try again.
     */
    fun reportReminderRefused(outcome: ReminderOutcome) {
        local.update { it.copy(reminderNotice = outcome.takeIf { it.needsNotice }) }
    }

    /** Re-arms reminders from the stored preference; called on start-up. */
    fun restoreReminderSchedule() {
        launch {
            runCatching { syncReminderSchedule() }
                .onFailure { report(it.toAppError()) }
        }
    }

    fun export(format: ExportFormat) {
        if (local.value.isExporting) return
        local.update { it.copy(isExporting = true, exportMessage = null) }
        launch {
            exportData(userId, format)
                .onSuccess { result ->
                    local.update { it.copy(isExporting = false, exportMessage = result.fileName) }
                }
                .onFailure { throwable ->
                    local.update { it.copy(isExporting = false, error = throwable.toAppError()) }
                }
        }
    }

    fun syncNow() {
        launch {
            runCatching { syncManager.syncNow() }
                .onFailure { report(it.toAppError()) }
        }
    }

    fun restore() {
        launch {
            runCatching { restoreSession() }
                .onFailure { report(it.toAppError()) }
        }
    }

    fun consumeExportMessage() {
        local.update { it.copy(exportMessage = null) }
    }

    fun dismissError() {
        local.update { it.copy(error = null) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
