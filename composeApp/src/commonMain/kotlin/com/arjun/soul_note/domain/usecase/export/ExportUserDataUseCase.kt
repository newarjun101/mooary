package com.arjun.soul_note.domain.usecase.export

import com.arjun.soul_note.core.analytics.AnalyticsEvent
import com.arjun.soul_note.core.analytics.AnalyticsParams
import com.arjun.soul_note.core.analytics.AnalyticsTracker
import com.arjun.soul_note.core.datetime.LocalDates
import com.arjun.soul_note.core.error.getOrThrowAppError
import com.arjun.soul_note.core.error.suspendRunCatchingApp
import com.arjun.soul_note.core.platform.ExportedFile
import com.arjun.soul_note.core.platform.FileExporter
import com.arjun.soul_note.core.validation.Limits
import com.arjun.soul_note.domain.model.ExportBundle
import com.arjun.soul_note.domain.model.ExportFormat
import com.arjun.soul_note.domain.model.ExportResult
import com.arjun.soul_note.domain.model.UserProfile
import com.arjun.soul_note.domain.repository.DiaryRepository
import com.arjun.soul_note.domain.repository.MoodRepository
import com.arjun.soul_note.domain.repository.UserRepository
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Renders a user's data to JSON or CSV and hands it to the OS share sheet.
 *
 * The file is written to app-local storage and never uploaded anywhere — there is no
 * network call in this path by construction, and [FileExporter] is the only writer.
 */
class ExportUserDataUseCase(
    private val userRepository: UserRepository,
    private val diaryRepository: DiaryRepository,
    private val moodRepository: MoodRepository,
    private val exporter: FileExporter,
    private val analytics: AnalyticsTracker,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        userId: String,
        format: ExportFormat,
    ): Result<ExportResult> = suspendRunCatchingApp {
        analytics.track(AnalyticsEvent.EXPORT_STARTED, mapOf(AnalyticsParams.FORMAT to format.key))

        val profile = userRepository.getProfile(userId).getOrThrowAppError()
            ?: UserProfile(
                id = userId,
                email = "",
                displayName = null,
                photoUrl = null,
                createdAt = now(),
                updatedAt = now(),
            )

        val diaryEntries = diaryRepository.getAllForExport(userId).getOrThrowAppError()
            .filter { it.isVisible() }
            .take(Limits.MAX_EXPORT_ENTRIES)
        val moodEntries = moodRepository.getAllForExport(userId).getOrThrowAppError()
            .filter { it.isVisible() }
            .take(Limits.MAX_EXPORT_ENTRIES)

        val bundle = ExportBundle(
            profile = profile,
            diaryEntries = diaryEntries,
            moodEntries = moodEntries,
            exportedAt = now(),
        )

        val content = when (format) {
            ExportFormat.JSON -> DataSerializer.toJson(bundle)
            ExportFormat.CSV -> DataSerializer.toCsv(bundle)
        }

        // File names are stamped in UTC so an export is reproducible regardless of zone.
        val stamp = LocalDates.toIsoString(LocalDates.today(now(), TimeZone.UTC))
        val fileName = "diary-export-$stamp.${format.extension}"
        val file: ExportedFile = exporter.writeText(fileName, content, format.mimeType)

        analytics.track(
            AnalyticsEvent.EXPORT_COMPLETED,
            mapOf(
                AnalyticsParams.FORMAT to format.key,
                AnalyticsParams.ENTRY_COUNT to (diaryEntries.size + moodEntries.size).toString(),
            ),
        )

        ExportResult(
            fileName = file.fileName,
            absolutePath = file.absolutePath,
            sizeBytes = file.sizeBytes,
            format = format,
            entryCount = diaryEntries.size + moodEntries.size,
        )
    }

    /** Writes the file and immediately opens the platform share sheet. */
    suspend fun exportAndShare(userId: String, format: ExportFormat): Result<ExportResult> {
        val result = invoke(userId, format)
        val export = result.getOrNull() ?: return result
        exporter.share(
            ExportedFile(
                fileName = export.fileName,
                absolutePath = export.absolutePath,
                sizeBytes = export.sizeBytes,
                mimeType = format.mimeType,
            ),
            subject = "Diary export",
        )
        return result
    }
}
