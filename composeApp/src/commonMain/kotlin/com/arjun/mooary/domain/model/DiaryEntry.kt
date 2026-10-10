package com.arjun.mooary.domain.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * A diary entry, owned by exactly one user.
 *
 * @property entryDate the calendar day the entry is *about*, in the user's local
 *   timezone. This is deliberately separate from [createdAt]: a user may write about
 *   yesterday at 09:00 today, and both facts matter (entry date drives the calendar and
 *   streaks, creation time drives chronological history).
 */
data class DiaryEntry(
    val id: String,
    val userId: String,
    val title: String,
    val content: String,
    val entryDate: LocalDate,
    val mood: Mood,
    val emotions: List<Emotion>,
    val tags: List<String>,
    val isFavorite: Boolean,
    val location: String? = null,
    val weather: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
) {
    /** Derived, never stored authoritatively, so it can never drift from [content]. */
    val wordCount: Int get() = content.countWords()

    /** First line, truncated for list previews. */
    fun preview(maxLength: Int = 140): String {
        val singleLine = content.replace('\n', ' ').replace('\r', ' ').trim()
        return if (singleLine.length <= maxLength) {
            singleLine
        } else {
            singleLine.take(maxLength).trimEnd() + "…"
        }
    }

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        if (title.lowercase().contains(needle)) return true
        if (content.lowercase().contains(needle)) return true
        if (tags.any { it.lowercase().contains(needle) }) return true
        if (emotions.any { it.name.lowercase().contains(needle) || it.id.contains(needle) }) return true
        return false
    }

    fun isVisible(): Boolean = deletedAt == null && syncStatus != SyncStatus.PENDING_DELETE
}

/**
 * Word counting that behaves the same on every platform.
 *
 * Deliberately simple and locale-independent: splitting on whitespace keeps it stable
 * across platforms, which matters because the value is mirrored to Firestore and shown
 * in the UI.
 */
fun String.countWords(): Int {
    var count = 0
    var inWord = false
    for (character in this) {
        if (character.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}
