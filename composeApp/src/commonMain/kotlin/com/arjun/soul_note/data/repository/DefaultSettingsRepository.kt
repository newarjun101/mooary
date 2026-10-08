package com.arjun.soul_note.data.repository

import com.arjun.soul_note.data.local.LocalStore
import com.arjun.soul_note.domain.model.DiaryDraft
import com.arjun.soul_note.domain.model.SyncState
import com.arjun.soul_note.domain.repository.AppLanguage
import com.arjun.soul_note.domain.repository.AppSettings
import com.arjun.soul_note.domain.repository.DraftRepository
import com.arjun.soul_note.domain.repository.SettingsRepository
import com.arjun.soul_note.domain.repository.SyncStatusRepository
import com.arjun.soul_note.domain.repository.ThemeMode
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Device-local preferences.
 *
 * Persisted in the local database, so the app opens in the chosen state with no network
 * at all. The table holds no journal content: only theme, reminder configuration and
 * onboarding state.
 */
class DefaultSettingsRepository(
    private val local: LocalStore,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = combine(
        local.observeSetting(KEY_THEME).map { stored ->
            ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
        },
        local.observeSetting(KEY_LANGUAGE).map { stored ->
            try {
                AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.ENGLISH
            } catch (_: Throwable) {
                AppLanguage.ENGLISH
            }
        },
        local.observeSetting(KEY_REMINDERS_ENABLED).map { it == "true" },
        local.observeSetting(KEY_REMINDER_HOURS).map(::parseHours),
        local.observeSetting(KEY_ONBOARDING_SEEN).map { it == "true" },
    ) { theme, language, remindersEnabled, hours, onboardingSeen ->
        AppSettings(
            themeMode = theme,
            language = language,
            remindersEnabled = remindersEnabled,
            dailyDiaryReminderHour = hours.first,
            dailyMoodReminderHour = hours.second,
            hasSeenOnboarding = onboardingSeen,
        )
    }.distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setThemeMode(mode: ThemeMode) {
        local.writeSetting(KEY_THEME, mode.name)
    }

    override suspend fun setLanguage(language: AppLanguage) {
        local.writeSetting(KEY_LANGUAGE, language.name)
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        local.writeSetting(KEY_REMINDERS_ENABLED, enabled.toString())
    }

    override suspend fun setReminderHours(diaryHour: Int, moodHour: Int) {
        local.writeSetting(KEY_REMINDER_HOURS, "$diaryHour:$moodHour")
    }

    override suspend fun markOnboardingSeen() {
        local.writeSetting(KEY_ONBOARDING_SEEN, "true")
    }

    override suspend fun clear() {
        local.clearSettings()
    }

    private fun parseHours(value: String?): Pair<Int, Int> {
        val parts = value?.split(':')
        val diary = parts?.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: DEFAULT_DIARY_HOUR
        val mood = parts?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: DEFAULT_MOOD_HOUR
        return diary to mood
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        const val KEY_REMINDER_HOURS = "reminder_hours"
        const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        const val DEFAULT_DIARY_HOUR = 21
        const val DEFAULT_MOOD_HOUR = 9
    }
}

/**
 * Autosaved writing state, keyed by user.
 *
 * Every keystroke is debounced by the ViewModel before it reaches here, so this is a
 * cheap primary-key upsert rather than a background job — a killed process loses at most
 * the last debounce window.
 */
class DefaultDraftRepository(
    private val local: LocalStore,
) : DraftRepository {

    override fun observeDraft(userId: String): Flow<DiaryDraft?> =
        local.observeDraft(userId).distinctUntilChanged()

    override suspend fun getDraft(userId: String): DiaryDraft? = local.getDraft(userId)

    override suspend fun saveDraft(draft: DiaryDraft) {
        local.saveDraft(draft)
    }

    override suspend fun clearDraft(userId: String) {
        local.clearDraft(userId)
    }
}

/**
 * Read-only projection of the sync engine, used by the profile screen's sync status card.
 */
class DefaultSyncStatusRepository(
    private val local: LocalStore,
    syncStateSource: Flow<SyncState>,
) : SyncStatusRepository {

    override val syncState: Flow<SyncState> = combine(
        syncStateSource,
        local.observeSetting(KEY_LAST_SYNC).map { it?.let(::parseInstant) },
    ) { state, lastSyncAt ->
        state.copy(lastSyncedAt = state.lastSyncedAt ?: lastSyncAt)
    }.distinctUntilChanged()

    override suspend fun current(): SyncState = syncState.first()

    private companion object {
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}

private fun parseInstant(value: String): Instant? =
    com.arjun.soul_note.core.datetime.Iso8601.parseTimestamp(value)
