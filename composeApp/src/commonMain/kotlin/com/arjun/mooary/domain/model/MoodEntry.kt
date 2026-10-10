package com.arjun.mooary.domain.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * A mood check-in, tracked independently of the diary.
 *
 * A user may record several moods on the same day (mood can genuinely change within a
 * day), so [date] is not a uniqueness constraint — only the calendar grid and daily
 * summaries collapse multiple records.
 */
data class MoodEntry(
    val id: String,
    val userId: String,
    val mood: Mood,
    val note: String?,
    val emotions: List<Emotion>,
    val date: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
) {
    fun isVisible(): Boolean = deletedAt == null && syncStatus != SyncStatus.PENDING_DELETE
}

/** One day collapsed from many [MoodEntry] records, for the calendar and home summary. */
data class DayMoodSummary(
    val date: LocalDate,
    val entries: List<MoodEntry>,
) {
    val count: Int get() = entries.size

    /** Mean mood score for the day, or `null` when nothing was recorded. */
    val averageScore: Double?
        get() = entries.takeIf { it.isNotEmpty() }?.map { it.mood.score }?.average()

    /**
     * The mood used to represent the day. Later records win, because a user's most
     * recent check-in is the one they mean when they look back.
     */
    val dominantMood: Mood?
        get() = entries.maxByOrNull { it.createdAt }?.mood

    val emotions: List<Emotion>
        get() = entries.flatMap { it.emotions }.distinctBy { it.id }
}

/** A half-written entry persisted on-device, so a crash or a background kill loses nothing. */
data class DiaryDraft(
    val userId: String,
    val entryId: String?,
    val title: String,
    val content: String,
    val entryDate: LocalDate,
    val mood: Mood,
    val emotions: List<Emotion>,
    val tags: List<String>,
    val updatedAt: Instant,
) {
    val isEmpty: Boolean
        get() = title.isBlank() && content.isBlank() && emotions.isEmpty() && tags.isEmpty()
}
