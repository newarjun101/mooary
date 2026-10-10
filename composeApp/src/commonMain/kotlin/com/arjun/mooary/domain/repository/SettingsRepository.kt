package com.arjun.mooary.domain.repository

import com.arjun.mooary.domain.model.DiaryDraft
import kotlinx.coroutines.flow.Flow

/** User preferences persisted on-device so the app opens in the chosen state offline. */
enum class ThemeMode { LIGHT, DARK }

enum class AppLanguage { ENGLISH, BURMESE }

/** Non-content preferences. Deliberately contains no journal data. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val remindersEnabled: Boolean = false,
    val dailyDiaryReminderHour: Int = 21,
    val dailyMoodReminderHour: Int = 9,
    val hasSeenOnboarding: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setReminderHours(diaryHour: Int, moodHour: Int)
    suspend fun markOnboardingSeen()
    /** Wipes preferences (not journal data) on account deletion. */
    suspend fun clear()
}

/** Autosaved writing state, keyed by user so switching accounts never leaks drafts. */
interface DraftRepository {
    fun observeDraft(userId: String): Flow<DiaryDraft?>
    suspend fun getDraft(userId: String): DiaryDraft?
    suspend fun saveDraft(draft: DiaryDraft)
    suspend fun clearDraft(userId: String)
}

