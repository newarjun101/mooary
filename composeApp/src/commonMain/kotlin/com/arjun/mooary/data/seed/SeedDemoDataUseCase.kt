package com.arjun.mooary.data.seed

import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.core.datetime.TimeProvider
import com.arjun.mooary.core.datetime.TimeZoneProvider
import com.arjun.mooary.data.local.LocalStore
import com.arjun.mooary.domain.model.DiaryEntry
import com.arjun.mooary.domain.model.MoodEntry
import com.arjun.mooary.domain.model.SyncStatus
import kotlinx.coroutines.flow.first

/**
 * Writes demo content for local design review.
 *
 * Three deliberate guards, because seeding writes real rows into the user's diary:
 *
 *  * **Debug builds only.** The caller checks `platformEnvironment().isDebugBuild`; a
 *    release build must never invoke this. Release binaries contain no demo rows.
 *  * **Empty diaries only.** If the user has any visible entry at all, seeding is skipped
 *    entirely, so it can never mix fabricated entries into a real journal.
 *  * **Stable ids.** The ids are fixed, so an extra launch is a no-op rather than 34
 *    duplicates.
 *
 * Rows are stored as [SyncStatus.SYNCED] rather than going through
 * `DiaryRepository.createEntry`, which would mark every row `PENDING_CREATE` and make the
 * sync manager try to push placeholder content to a backend that has no such entries.
 *
 * The "empty only" guard used to read "nothing but seed rows", which looks equivalent and is
 * not. Re-running upserted the demo rows by their stable ids, and `upsertDiary`/`upsertMood`
 * are `INSERT OR REPLACE`, so every relaunch silently rewrote them: an edited check-in
 * reverted to its demo text and a deleted one came back. Nothing errored, and the write
 * landed in the same session the edit was made in, so it read as the edit not saving rather
 * than as seeding. Demo rows are only ever written into an empty diary now.
 */
class SeedDemoDataUseCase(
    private val local: LocalStore,
    private val timeProvider: TimeProvider,
    private val timeZoneProvider: TimeZoneProvider,
) {

    /**
     * Seeds [userId] when the diary has no rows at all.
     *
     * @return how many rows were written, or 0 when seeding was skipped.
     */
    suspend operator fun invoke(userId: String): Int {
        val existing = local.observeVisibleDiary(userId).first()
        if (existing.isNotEmpty()) return 0

        val timeZone = timeZoneProvider.timeZone()
        val today = LocalDates.today(timeProvider.now(), timeZone)

        val entries: List<DiaryEntry> =
            FakeDiaryData.diaryEntries(userId, today, timeZone)
        val moods: List<MoodEntry> =
            FakeDiaryData.moodEntries(userId, today, timeZone)

        entries.forEach { local.upsertDiary(it) }
        moods.forEach { local.upsertMood(it) }

        return entries.size + moods.size
    }
}
