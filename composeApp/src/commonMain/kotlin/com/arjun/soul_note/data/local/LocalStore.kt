package com.arjun.soul_note.data.local

import app.cash.sqldelight.coroutines.asFlow
import com.arjun.soul_note.core.datetime.LocalDates
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.data.local.db.DiaryDatabase
import com.arjun.soul_note.data.local.db.Diary_entries
import com.arjun.soul_note.data.local.db.Drafts
import com.arjun.soul_note.data.local.db.Mood_entries
import com.arjun.soul_note.data.local.db.User_profiles
import com.arjun.soul_note.domain.model.DayMoodSummary
import com.arjun.soul_note.domain.model.DiaryDraft
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.domain.model.SyncStatus
import com.arjun.soul_note.domain.model.UserProfile
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Row ↔ model translation.
 *
 * Isolated here so the SQL schema and the domain model evolve independently, and so a
 * malformed row (a mood id from a newer client, a corrupt emotion blob) degrades to a
 * sensible default instead of throwing inside a Flow.
 */
internal object Mappers {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun toDomain(row: Diary_entries): DiaryEntry = DiaryEntry(
        id = row.id,
        userId = row.user_id,
        title = row.title,
        content = row.content,
        entryDate = row.entry_date.toLocalDateOr(row.created_at),
        mood = Mood.fromId(row.mood_id),
        emotions = decodeEmotions(row.emotions_json),
        tags = decodeTags(row.tags),
        isFavorite = row.is_favorite == 1L,
        location = row.location,
        weather = row.weather,
        createdAt = row.created_at.asInstant(),
        updatedAt = row.updated_at.asInstant(),
        deletedAt = row.deleted_at?.asInstant(),
        syncStatus = decodeSyncStatus(row.sync_status),
    )

    fun toDomain(row: Mood_entries): MoodEntry = MoodEntry(
        id = row.id,
        userId = row.user_id,
        mood = Mood.fromId(row.mood_id),
        note = row.note,
        emotions = decodeEmotions(row.emotions_json),
        date = row.date.toLocalDateOr(row.created_at),
        createdAt = row.created_at.asInstant(),
        updatedAt = row.updated_at.asInstant(),
        deletedAt = row.deleted_at?.asInstant(),
        syncStatus = decodeSyncStatus(row.sync_status),
    )

    fun toDomain(row: User_profiles): UserProfile = UserProfile(
        id = row.id,
        email = row.email,
        displayName = row.display_name,
        photoUrl = row.photo_url,
        createdAt = row.created_at.asInstant(),
        updatedAt = row.updated_at.asInstant(),
    )

    fun toDomain(row: Drafts): DiaryDraft = DiaryDraft(
        userId = row.user_id,
        entryId = row.entry_id,
        title = row.title,
        content = row.content,
        entryDate = row.entry_date.toLocalDateOr(row.updated_at),
        mood = Mood.fromId(row.mood_id),
        emotions = decodeEmotions(row.emotions_json),
        tags = decodeTags(row.tags),
        updatedAt = row.updated_at.asInstant(),
    )

    fun decodeEmotions(raw: String): List<Emotion> = runCatching {
        val array = json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
        array.mapNotNull { element ->
            val primitive = element as? JsonPrimitive ?: return@mapNotNull null
            Emotion.fromId(primitive.content)
        }
    }.getOrElse { emptyList() }

    fun encodeEmotions(emotions: List<Emotion>): String =
        JsonArray(emotions.map { JsonPrimitive(it.id) }).toString()

    fun decodeTags(raw: String): List<String> =
        if (raw.isEmpty()) emptyList() else raw.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }

    fun encodeTags(tags: List<String>): String = tags.joinToString(",")

    fun decodeSyncStatus(raw: String): SyncStatus =
        runCatching { SyncStatus.valueOf(raw) }.getOrDefault(SyncStatus.SYNCED)

    fun Long.asInstant(): Instant = Instant.fromEpochMilliseconds(this)

    private fun String.toLocalDateOr(fallbackEpochMillis: Long): LocalDate =
        LocalDates.parseIsoString(this)
            ?: LocalDates.today(Instant.fromEpochMilliseconds(fallbackEpochMillis), TimeZone.UTC)
}

/**
 * Every read and write the repositories perform against the local cache.
 *
 * Writes always land here first, so the UI is never blocked on the network and the app is
 * fully functional with no connectivity at all.
 */
class LocalStore(
    private val database: DiaryDatabase,
    private val io: CoroutineDispatcher,
    private val logger: AppLogger,
) {
    private val diary get() = database.diaryQueries
    private val moods get() = database.moodQueries
    private val users get() = database.userQueries
    private val settings get() = database.settingsQueries


    fun observeVisibleDiary(userId: String): Flow<List<DiaryEntry>> =
        diary.selectVisible(userId).asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeDiaryPage(userId: String, limit: Int, offset: Int): Flow<List<DiaryEntry>> =
        diary.selectVisiblePage(userId, limit.toLong(), offset.toLong())
            .asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeRecentDiary(userId: String, limit: Int): Flow<List<DiaryEntry>> =
        diary.selectRecent(userId, limit.toLong()).asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeFavorites(userId: String): Flow<List<DiaryEntry>> =
        diary.selectFavorites(userId).asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeDiaryForDate(userId: String, date: LocalDate): Flow<List<DiaryEntry>> =
        diary.selectForDate(userId, LocalDates.toIsoString(date))
            .asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeDiaryInRange(userId: String, from: LocalDate, to: LocalDate): Flow<List<DiaryEntry>> =
        diary.selectInRange(userId, LocalDates.toIsoString(from), LocalDates.toIsoString(to))
            .asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeDiaryById(userId: String, entryId: String): Flow<DiaryEntry?> =
        diary.selectById(entryId, userId).asFlow().map { q -> q.executeAsList().firstOrNull()?.let(Mappers::toDomain) }

    fun observeDiaryCount(userId: String): Flow<Int> =
        diary.countVisible(userId).asFlow().map { q -> q.executeAsOne().toInt() }

    fun observeFavoriteCount(userId: String): Flow<Int> =
        diary.countFavorites(userId).asFlow().map { q -> q.executeAsOne().toInt() }

    fun observeDiaryCountInRange(userId: String, from: LocalDate, to: LocalDate): Flow<Int> =
        diary.countInRange(userId, LocalDates.toIsoString(from), LocalDates.toIsoString(to))
            .asFlow().map { q -> q.executeAsOne().toInt() }

    /** Per-day entry counts for the calendar grid, in one query instead of 30+. */
    fun observeDiaryCountsByDate(userId: String, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        diary.countsByDate(userId, LocalDates.toIsoString(from), LocalDates.toIsoString(to))
            .asFlow()
            .map { q -> q.executeAsList().mapNotNull { r -> LocalDates.parseIsoString(r.entry_date)?.let { d -> d to r.entry_count.toInt() } }.toMap() }

    fun observeAllTags(userId: String): Flow<List<String>> =
        diary.distinctTags(userId).asFlow().map { q ->
            q.executeAsList().flatMap(Mappers::decodeTags).distinct().sorted()
        }


    suspend fun upsertDiary(entry: DiaryEntry) = onIo {
        diary.insertOrReplace(
            id = entry.id,
            user_id = entry.userId,
            title = entry.title,
            content = entry.content,
            entry_date = LocalDates.toIsoString(entry.entryDate),
            mood_id = entry.mood.id,
            mood_score = entry.mood.score.toLong(),
            emotions_json = Mappers.encodeEmotions(entry.emotions),
            tags = Mappers.encodeTags(entry.tags),
            is_favorite = if (entry.isFavorite) 1L else 0L,
            location = entry.location,
            weather = entry.weather,
            word_count = entry.wordCount.toLong(),
            created_at = entry.createdAt.toEpochMilliseconds(),
            updated_at = entry.updatedAt.toEpochMilliseconds(),
            deleted_at = entry.deletedAt?.toEpochMilliseconds(),
            sync_status = entry.syncStatus.name,
        )
    }

    /**
     * Tombstones a row instead of deleting it, so an offline delete can still be pushed
     * later. It disappears from every user-facing query because each filters on
     * `deleted_at IS NULL AND sync_status != 'PENDING_DELETE'`.
     */
    suspend fun tombstoneDiary(userId: String, entryId: String, deletedAt: Instant, syncStatus: SyncStatus) =
        onIo {
            val existing = diary.selectById(entryId, userId).executeAsOneOrNull() ?: return@onIo
            val stamp = deletedAt.toEpochMilliseconds()
            diary.update(
                id = entryId,
                userId = userId,
                title = existing.title,
                content = existing.content,
                entryDate = existing.entry_date,
                moodId = existing.mood_id,
                moodScore = existing.mood_score,
                emotionsJson = existing.emotions_json,
                tags = existing.tags,
                isFavorite = existing.is_favorite,
                location = existing.location,
                weather = existing.weather,
                wordCount = existing.word_count,
                updatedAt = stamp,
                deletedAt = stamp,
                syncStatus = syncStatus.name,
            )
        }

    /** Physically removes a row once the backend has acknowledged the delete. */
    suspend fun purgeDiary(userId: String, entryId: String) = onIo {
        diary.delete(entryId, userId)
    }

    suspend fun setDiaryFavorite(
        userId: String,
        entryId: String,
        isFavorite: Boolean,
        updatedAt: Instant,
        syncStatus: SyncStatus,
    ) = onIo {
        val existing = diary.selectById(entryId, userId).executeAsOneOrNull() ?: return@onIo
        diary.update(
            id = entryId,
            userId = userId,
            title = existing.title,
            content = existing.content,
            entryDate = existing.entry_date,
            moodId = existing.mood_id,
            moodScore = existing.mood_score,
            emotionsJson = existing.emotions_json,
            tags = existing.tags,
            isFavorite = if (isFavorite) 1L else 0L,
            location = existing.location,
            weather = existing.weather,
            wordCount = existing.word_count,
            updatedAt = updatedAt.toEpochMilliseconds(),
            deletedAt = existing.deleted_at,
            syncStatus = syncStatus.name,
        )
    }

    suspend fun searchDiary(userId: String, query: String, limit: Int): List<DiaryEntry> = onIo {
        diary.search(userId, query, limit.toLong()).executeAsList().map(Mappers::toDomain)
    }

    suspend fun getDiary(userId: String, entryId: String): DiaryEntry? = onIo {
        diary.selectById(entryId, userId).executeAsOneOrNull()?.let(Mappers::toDomain)
    }


    fun observeVisibleMoods(userId: String): Flow<List<MoodEntry>> =
        moods.selectVisible(userId).asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeMoodPage(userId: String, limit: Int, offset: Int): Flow<List<MoodEntry>> =
        moods.selectVisiblePage(userId, limit.toLong(), offset.toLong())
            .asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeRecentMoods(userId: String, limit: Int): Flow<List<MoodEntry>> =
        moods.selectRecent(userId, limit.toLong()).asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeMoodsForDate(userId: String, date: LocalDate): Flow<List<MoodEntry>> =
        moods.selectForDate(userId, LocalDates.toIsoString(date))
            .asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeMoodsInRange(userId: String, from: LocalDate, to: LocalDate): Flow<List<MoodEntry>> =
        moods.selectInRange(userId, LocalDates.toIsoString(from), LocalDates.toIsoString(to))
            .asFlow().map { q -> q.executeAsList().map(Mappers::toDomain) }

    fun observeMoodById(userId: String, moodId: String): Flow<MoodEntry?> =
        moods.selectById(moodId, userId).asFlow().map { q -> q.executeAsList().firstOrNull()?.let(Mappers::toDomain) }

    fun observeMoodCount(userId: String): Flow<Int> =
        moods.countVisible(userId).asFlow().map { q -> q.executeAsOne().toInt() }

    fun observeMoodCountInRange(userId: String, from: LocalDate, to: LocalDate): Flow<Int> =
        moods.countInRange(userId, LocalDates.toIsoString(from), LocalDates.toIsoString(to))
            .asFlow().map { q -> q.executeAsOne().toInt() }

    /**
     * Per-day roll-up for the calendar, built from the same rows the analytics use, so
     * the calendar and the statistics can never disagree about what a day looked like.
     */
    fun observeDaySummaries(userId: String): Flow<List<DayMoodSummary>> =
        observeMoodsInRange(userId, MIN_DATE, MAX_DATE).map { entries ->
            entries.groupBy { it.date }
                .map { (date, dayEntries) -> DayMoodSummary(date = date, entries = dayEntries) }
                .sortedByDescending { it.date }
        }

    suspend fun getMoodsInRange(userId: String, from: LocalDate, to: LocalDate): List<MoodEntry> = onIo {
        moods.selectInRange(userId, LocalDates.toIsoString(from), LocalDates.toIsoString(to))
            .executeAsList().map(Mappers::toDomain)
    }


    suspend fun upsertMood(entry: MoodEntry) = onIo {
        moods.insertOrReplace(
            id = entry.id,
            user_id = entry.userId,
            mood_id = entry.mood.id,
            mood_score = entry.mood.score.toLong(),
            note = entry.note,
            emotions_json = Mappers.encodeEmotions(entry.emotions),
            date = LocalDates.toIsoString(entry.date),
            created_at = entry.createdAt.toEpochMilliseconds(),
            updated_at = entry.updatedAt.toEpochMilliseconds(),
            deleted_at = entry.deletedAt?.toEpochMilliseconds(),
            sync_status = entry.syncStatus.name,
        )
    }

    suspend fun tombstoneMood(userId: String, moodId: String, deletedAt: Instant, syncStatus: SyncStatus) =
        onIo {
            val existing = moods.selectById(moodId, userId).executeAsOneOrNull() ?: return@onIo
            val stamp = deletedAt.toEpochMilliseconds()
            moods.update(
                id = moodId,
                userId = userId,
                moodId = existing.mood_id,
                moodScore = existing.mood_score,
                note = existing.note,
                emotionsJson = existing.emotions_json,
                date = existing.date,
                updatedAt = stamp,
                deletedAt = stamp,
                syncStatus = syncStatus.name,
            )
        }

    suspend fun purgeMood(userId: String, moodId: String) = onIo {
        moods.delete(moodId, userId)
    }

    suspend fun getMood(userId: String, moodId: String): MoodEntry? = onIo {
        moods.selectById(moodId, userId).executeAsOneOrNull()?.let(Mappers::toDomain)
    }


    fun observeProfile(userId: String): Flow<UserProfile?> =
        users.selectById(userId).asFlow().map { q -> q.executeAsList().firstOrNull()?.let(Mappers::toDomain) }

    suspend fun getProfile(userId: String): UserProfile? = onIo {
        users.selectById(userId).executeAsOneOrNull()?.let(Mappers::toDomain)
    }

    suspend fun upsertProfile(profile: UserProfile) = onIo {
        users.insert(
            id = profile.id,
            email = profile.email,
            display_name = profile.displayName,
            photo_url = profile.photoUrl,
            created_at = profile.createdAt.toEpochMilliseconds(),
            updated_at = profile.updatedAt.toEpochMilliseconds(),
        )
    }

    suspend fun deleteProfile(userId: String) = onIo {
        users.deleteById(userId)
    }


    fun observeDraft(userId: String): Flow<DiaryDraft?> =
        users.selectDraft(userId).asFlow().map { q -> q.executeAsList().firstOrNull()?.let(Mappers::toDomain) }

    suspend fun getDraft(userId: String): DiaryDraft? = onIo {
        users.selectDraft(userId).executeAsOneOrNull()?.let(Mappers::toDomain)
    }

    suspend fun saveDraft(draft: DiaryDraft) = onIo {
        users.upsertDraft(
            userId = draft.userId,
            entryId = draft.entryId,
            title = draft.title,
            content = draft.content,
            entryDate = LocalDates.toIsoString(draft.entryDate),
            moodId = draft.mood.id,
            emotionsJson = Mappers.encodeEmotions(draft.emotions),
            tags = Mappers.encodeTags(draft.tags),
            updatedAt = draft.updatedAt.toEpochMilliseconds(),
        )
    }

    suspend fun clearDraft(userId: String) = onIo {
        users.deleteDraft(userId)
    }


    fun observeSetting(key: String): Flow<String?> =
        settings.select(key).asFlow().map { q -> q.executeAsOneOrNull() }

    suspend fun readSetting(key: String): String? = onIo {
        settings.select(key).executeAsOneOrNull()
    }

    suspend fun writeSetting(key: String, value: String) = onIo {
        settings.upsert(key, value)
    }

    suspend fun clearSettings() = onIo {
        settings.selectAll().executeAsList().forEach { settings.delete(it.key) }
    }


    suspend fun pendingDiary(): List<DiaryEntry> = onIo {
        diary.selectPending().executeAsList().map(Mappers::toDomain)
    }

    suspend fun pendingMoods(): List<MoodEntry> = onIo {
        moods.selectPending().executeAsList().map(Mappers::toDomain)
    }

    suspend fun diaryChangedSince(userId: String, sinceEpochMillis: Long): List<DiaryEntry> = onIo {
        diary.selectSince(userId, sinceEpochMillis).executeAsList().map(Mappers::toDomain)
    }

    suspend fun moodsChangedSince(userId: String, sinceEpochMillis: Long): List<MoodEntry> = onIo {
        moods.selectSince(userId, sinceEpochMillis).executeAsList().map(Mappers::toDomain)
    }

    /** (pending diary + mood rows, conflicts, whether the profile is still unsynced). */
    suspend fun syncCounts(): Triple<Int, Int, Int> = onIo {
        val pending = diary.countPending().executeAsOne().toInt() + moods.countPending().executeAsOne().toInt()
        val conflicts = diary.countConflicts().executeAsOne().toInt() + moods.countConflicts().executeAsOne().toInt()
        Triple(pending, conflicts, 0)
    }

    /**
     * Marks a row reconciled. A tombstone is physically removed here, which is the only
     * point at which a delete is forgotten — the backend has already confirmed it.
     */
    suspend fun markDiarySynced(userId: String, entryId: String, updatedAt: Instant) = onIo {
        val existing = diary.selectById(entryId, userId).executeAsOneOrNull() ?: return@onIo
        if (existing.deleted_at != null) {
            diary.delete(entryId, userId)
            return@onIo
        }
        diary.update(
            id = entryId,
            userId = userId,
            title = existing.title,
            content = existing.content,
            entryDate = existing.entry_date,
            moodId = existing.mood_id,
            moodScore = existing.mood_score,
            emotionsJson = existing.emotions_json,
            tags = existing.tags,
            isFavorite = existing.is_favorite,
            location = existing.location,
            weather = existing.weather,
            wordCount = existing.word_count,
            updatedAt = updatedAt.toEpochMilliseconds(),
            deletedAt = null,
            syncStatus = SyncStatus.SYNCED.name,
        )
    }

    suspend fun markMoodSynced(userId: String, moodId: String, updatedAt: Instant) = onIo {
        val existing = moods.selectById(moodId, userId).executeAsOneOrNull() ?: return@onIo
        if (existing.deleted_at != null) {
            moods.delete(moodId, userId)
            return@onIo
        }
        moods.update(
            id = moodId,
            userId = userId,
            moodId = existing.mood_id,
            moodScore = existing.mood_score,
            note = existing.note,
            emotionsJson = existing.emotions_json,
            date = existing.date,
            updatedAt = updatedAt.toEpochMilliseconds(),
            deletedAt = null,
            syncStatus = SyncStatus.SYNCED.name,
        )
    }

    /**
     * Records that a concurrent edit was detected. The content is left untouched: the
     * deterministic `updatedAt` comparison has already chosen a winner, and the flag
     * exists purely so the user is told rather than silently losing work.
     */
    suspend fun markDiaryConflict(userId: String, entryId: String) = onIo {
        logger.info("LocalStore") { "diary conflict recorded" }
        val existing = diary.selectById(entryId, userId).executeAsOneOrNull() ?: return@onIo
        diary.update(
            id = entryId,
            userId = userId,
            title = existing.title,
            content = existing.content,
            entryDate = existing.entry_date,
            moodId = existing.mood_id,
            moodScore = existing.mood_score,
            emotionsJson = existing.emotions_json,
            tags = existing.tags,
            isFavorite = existing.is_favorite,
            location = existing.location,
            weather = existing.weather,
            wordCount = existing.word_count,
            updatedAt = existing.updated_at,
            deletedAt = existing.deleted_at,
            syncStatus = SyncStatus.CONFLICT.name,
        )
    }

    suspend fun markMoodConflict(userId: String, moodId: String) = onIo {
        val existing = moods.selectById(moodId, userId).executeAsOneOrNull() ?: return@onIo
        moods.update(
            id = moodId,
            userId = userId,
            moodId = existing.mood_id,
            moodScore = existing.mood_score,
            note = existing.note,
            emotionsJson = existing.emotions_json,
            date = existing.date,
            updatedAt = existing.updated_at,
            deletedAt = existing.deleted_at,
            syncStatus = SyncStatus.CONFLICT.name,
        )
    }


    /**
     * Removes every local trace of the account.
     *
     * The database file itself is intentionally kept: racing SQLite teardown against a
     * running query is unsafe, and rows are partitioned by `user_id`, so simply deleting
     * them is both sufficient and safer.
     */
    suspend fun purgeUser(userId: String) = onIo {
        diary.deleteAllForUser(userId)
        moods.deleteAllForUser(userId)
        users.deleteById(userId)
        users.deleteDraft(userId)
        logger.info("LocalStore") { "purged local data" }
    }

    private suspend inline fun <T> onIo(crossinline block: () -> T): T =
        withContext(io) { block() }

    private companion object {
        val MIN_DATE: LocalDate = LocalDate(1970, 1, 1)
        val MAX_DATE: LocalDate = LocalDate(2999, 12, 31)
    }
}
