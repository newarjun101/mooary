package com.arjun.soul_note.domain.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** The file formats the exporter can produce. */
enum class ExportFormat(val key: String, val extension: String, val mimeType: String) {
    JSON("json", "json", "application/json"),
    CSV("csv", "csv", "text/csv"),
}

/** Everything a user owns, serialisable to JSON and flattens cleanly to CSV. */
data class ExportBundle(
    val profile: UserProfile,
    val diaryEntries: List<DiaryEntry>,
    val moodEntries: List<MoodEntry>,
    val exportedAt: Instant,
) {
    val diaryEntryCount: Int get() = diaryEntries.size
    val moodEntryCount: Int get() = moodEntries.size
}

/** The result of a successful export, ready to be shared. */
data class ExportResult(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val format: ExportFormat,
    val entryCount: Int,
)

/** A self-contained account-deletion receipt. */
data class AccountDeletionSummary(
    val diaryEntriesDeleted: Int,
    val moodRecordsDeleted: Int,
    val remoteDeleted: Boolean,
)
