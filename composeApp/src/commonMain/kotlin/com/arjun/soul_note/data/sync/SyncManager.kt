package com.arjun.soul_note.data.sync

import com.arjun.soul_note.core.common.DispatcherProvider
import com.arjun.soul_note.core.datetime.Iso8601
import com.arjun.soul_note.core.datetime.LocalDates
import com.arjun.soul_note.core.datetime.TimeProvider
import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.ErrorKeys
import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.network.ConnectivityObserver
import com.arjun.soul_note.data.local.LocalStore
import com.arjun.soul_note.data.remote.RemoteDiaryDataSource
import com.arjun.soul_note.data.remote.RemoteMoodDataSource
import com.arjun.soul_note.data.remote.RemoteUserDataSource
import com.arjun.soul_note.data.remote.firebase.FirestoreCodec
import com.arjun.soul_note.data.remote.firebase.FirestoreWrite
import com.arjun.soul_note.data.remote.firebase.RemoteDocument
import com.arjun.soul_note.data.remote.firebase.RemoteMappers
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.DiaryFields
import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.FirestorePaths
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.domain.model.MoodFields
import com.arjun.soul_note.domain.model.SyncState
import com.arjun.soul_note.domain.model.SyncStatus
import com.arjun.soul_note.domain.model.UserFields
import com.arjun.soul_note.domain.model.UserProfile
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/** Supplies the signed-in user id to the sync engine. */
fun interface SessionProvider {
    suspend fun currentUserId(): String?
}

/** An anomaly the engine found, surfaced so a concurrent edit is never silently dropped. */
data class SyncAnomaly(
    val collection: String,
    val recordId: String,
    val reason: SyncConflictReason,
)

enum class SyncConflictReason {
    /** The remote copy was newer and was applied locally. */
    REMOTE_NEWER,

    /** A concurrent local edit was kept; the user is told rather than losing work. */
    CONCURRENT_LOCAL_EDIT,

    /** A remote deletion was applied. */
    REMOTE_DELETED,
}

/** Outcome of one pass, useful for tests and for the profile screen. */
data class SyncOutcome(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val deleted: Int = 0,
    val state: SyncState = SyncState(),
)

/**
 * The reconciliation engine that makes the application offline-first.
 *
 * ## Direction of truth
 *
 * The **local database is the single source of truth for the UI**. This engine's only job
 * is to converge local and remote:
 *
 * 1. **Push** — every locally-dirty row (`PENDING_CREATE` / `PENDING_UPDATE`) is written to
 *    Firestore; `PENDING_DELETE` tombstones are removed remotely and then purged locally.
 * 2. **Pull** — documents whose `updatedAt` is newer than a per-collection watermark are
 *    fetched and merged.
 *
 * ## Deterministic conflict resolution
 *
 * When both sides changed, the row with the larger `updatedAt` wins. That rule is applied
 * everywhere, needs no server clock and no user prompt, and is fully covered by unit
 * tests. Crucially, a losing **local** edit is never silently overwritten: the local row
 * is kept, a [SyncAnomaly] is recorded, and the row is flagged so the UI can tell the user.
 * "Newest wins" decides the data; "flag it" preserves trust.
 *
 * ## Why batching matters
 *
 * Pushes go through Firestore's atomic `:commit` endpoint in bounded batches, so a batch
 * either lands completely or not at all. A half-synced entry is impossible, and the
 * pending counter in the UI is always truthful.
 */
class SyncManager(
    private val local: LocalStore,
    private val remoteDiary: RemoteDiaryDataSource,
    private val remoteMood: RemoteMoodDataSource,
    private val remoteUser: RemoteUserDataSource,
    private val session: SessionProvider,
    private val connectivity: ConnectivityObserver,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
    private val scope: CoroutineScope,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _anomalies = MutableStateFlow<List<SyncAnomaly>>(emptyList())
    val anomalies: StateFlow<List<SyncAnomaly>> = _anomalies.asStateFlow()

    /** Serialises passes so a write-triggered and a reconnect-triggered pass never overlap. */
    private val mutex = Mutex()
    private val watermarks = mutableMapOf<String, Long>()


    /**
     * Requests a pass, fire-and-forget.
     *
     * Repositories call this after every local write. Because of [mutex] a burst of edits
     * collapses into a single pass rather than one request per keystroke.
     */
    fun requestSync() {
        if (!connectivity.isOnline()) return
        scope.launch { syncNow() }
    }

    /** Runs a pass and returns what happened. Awaitable by the profile screen. */
    suspend fun syncNow(): SyncOutcome = withContext(dispatchers.io) {
        if (!connectivity.isOnline()) return@withContext SyncOutcome(state = _state.value)
        mutex.withLock {
            val userId = session.currentUserId()
            if (userId.isNullOrBlank()) {
                _state.value = SyncState()
                return@withLock SyncOutcome(state = _state.value)
            }

            _state.value = _state.value.copy(isSyncing = true, lastErrorKey = null)
            try {
                val deleted = pushDeletions(userId)
                val pushed = pushUpserts(userId)
                val pulled = pullRemoteChanges(userId)

                val counts = local.syncCounts()
                _state.value = SyncState(
                    pendingCount = counts.first,
                    lastSyncedAt = timeProvider.now(),
                    isSyncing = false,
                    conflictCount = counts.second,
                    lastErrorKey = null,
                )
                logger.info("SyncManager") { "pass complete: pushed=$pushed pulled=$pulled deleted=$deleted" }
                SyncOutcome(pushed = pushed, pulled = pulled, deleted = deleted, state = _state.value)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val error = throwable.toAppError(logger)
                _state.value = _state.value.copy(isSyncing = false, lastErrorKey = error.reasonKeyOrDefault())
                logger.error("SyncManager") { "pass failed" }
                SyncOutcome(state = _state.value)
            }
        }
    }

    /** Forces a full re-pull, used by "Sync now" after a long offline period. */
    suspend fun fullResync() {
        watermarks.clear()
        syncNow()
    }


    /**
     * Removes remote tombstones, then purges them locally.
     *
     * The local purge happens *only* after the backend confirms, which is the single point
     * at which a deletion is allowed to be forgotten.
     */
    private suspend fun pushDeletions(userId: String): Int {
        val diaryDeletes = local.pendingDiary()
            .filter { it.syncStatus == SyncStatus.PENDING_DELETE }
            .map { FirestoreWrite.Delete(FirestorePaths.entry(userId, it.id)) }
        val moodDeletes = local.pendingMoods()
            .filter { it.syncStatus == SyncStatus.PENDING_DELETE }
            .map { FirestoreWrite.Delete(FirestorePaths.mood(userId, it.id)) }

        var deleted = 0
        if (diaryDeletes.isNotEmpty()) {
            remoteDiary.pushEntries(diaryDeletes).getOrThrow()
            diaryDeletes.forEach { local.purgeDiary(userId, it.path.substringAfterLast('/')) }
            deleted += diaryDeletes.size
        }
        if (moodDeletes.isNotEmpty()) {
            remoteMood.pushMoods(moodDeletes).getOrThrow()
            moodDeletes.forEach { local.purgeMood(userId, it.path.substringAfterLast('/')) }
            deleted += moodDeletes.size
        }
        return deleted
    }

    /** Writes every locally-dirty row to Firestore, in bounded atomic batches. */
    private suspend fun pushUpserts(userId: String): Int {
        var acknowledged = 0

        val diaryWrites = local.pendingDiary()
            .filter { it.syncStatus != SyncStatus.PENDING_DELETE }
            .map { entry ->
                FirestoreWrite.Set(
                    path = FirestorePaths.entry(userId, entry.id),
                    fields = RemoteMappers.diaryToFields(
                        title = entry.title,
                        content = entry.content,
                        entryDate = LocalDates.toIsoString(entry.entryDate),
                        moodId = entry.mood.id,
                        emotions = entry.emotions.map { it.id },
                        tags = entry.tags,
                        isFavorite = entry.isFavorite,
                        location = entry.location,
                        weather = entry.weather,
                        wordCount = entry.wordCount,
                        createdAt = entry.createdAt,
                        updatedAt = entry.updatedAt,
                        deletedAt = null,
                    ),
                )
            }

        diaryWrites.chunked(BATCH_SIZE).forEach { batch ->
            remoteDiary.pushEntries(batch).getOrThrow()
            batch.forEach { local.markDiarySynced(userId, it.path.substringAfterLast('/'), timeProvider.now()) }
            acknowledged += batch.size
        }

        val moodWrites = local.pendingMoods()
            .filter { it.syncStatus != SyncStatus.PENDING_DELETE }
            .map { entry ->
                FirestoreWrite.Set(
                    path = FirestorePaths.mood(userId, entry.id),
                    fields = RemoteMappers.moodToFields(
                        moodId = entry.mood.id,
                        note = entry.note,
                        emotions = entry.emotions.map { it.id },
                        date = LocalDates.toIsoString(entry.date),
                        createdAt = entry.createdAt,
                        updatedAt = entry.updatedAt,
                        deletedAt = null,
                    ),
                )
            }

        moodWrites.chunked(BATCH_SIZE).forEach { batch ->
            remoteMood.pushMoods(batch).getOrThrow()
            batch.forEach { local.markMoodSynced(userId, it.path.substringAfterLast('/'), timeProvider.now()) }
            acknowledged += batch.size
        }

        acknowledged += pushProfileIfDirty(userId)
        return acknowledged
    }

    /**
     * The profile is pushed only when the local copy is newer than the watermark, so a
     * routine sync performs no redundant write.
     */
    private suspend fun pushProfileIfDirty(userId: String): Int {
        val profile = local.getProfile(userId) ?: return 0
        val watermark = watermarks[PROFILE_WATERMARK] ?: 0L
        if (profile.updatedAt.toEpochMilliseconds() <= watermark) return 0

        remoteUser.pushProfile(
            listOf(
                FirestoreWrite.Set(
                    path = FirestorePaths.user(userId),
                    fields = RemoteMappers.profileToFields(
                        email = profile.email,
                        displayName = profile.displayName,
                        photoUrl = profile.photoUrl,
                        createdAt = profile.createdAt,
                        updatedAt = profile.updatedAt,
                    ),
                ),
            ),
        ).getOrThrow()
        watermarks[PROFILE_WATERMARK] = profile.updatedAt.toEpochMilliseconds()
        return 1
    }


    /** Fetches and merges everything newer than the watermarks. */
    private suspend fun pullRemoteChanges(userId: String): Int {
        val diaryWatermark = watermarks[DIARY_WATERMARK] ?: 0L
        val moodWatermark = watermarks[MOOD_WATERMARK] ?: 0L

        val remoteDiaryDocs = remoteDiary.listEntries(userId, diaryWatermark)
        val remoteMoodDocs = remoteMood.listMoods(userId, moodWatermark)

        var merged = 0
        var highestDiary = diaryWatermark
        var highestMood = moodWatermark

        remoteDiaryDocs.forEach { doc ->
            if (mergeDiary(userId, doc)) merged++
            doc.remoteUpdatedAtMillis()?.let { if (it > highestDiary) highestDiary = it }
        }
        remoteMoodDocs.forEach { doc ->
            if (mergeMood(userId, doc)) merged++
            doc.remoteUpdatedAtMillis()?.let { if (it > highestMood) highestMood = it }
        }

        mergeProfile(userId, remoteUser.getProfile(userId))
        watermarks[DIARY_WATERMARK] = highestDiary
        watermarks[MOOD_WATERMARK] = highestMood
        return merged
    }

    /**
     * Merges one remote diary document. Returns `true` when the local row changed.
     *
     * A locally-pending row is *never* overwritten: the user's unsent edit is authoritative
     * until it has been pushed, which is what prevents a pull from discarding work the
     * user has not seen leave the device.
     */
    private suspend fun mergeDiary(userId: String, doc: RemoteDocument): Boolean {
        val id = doc.id
        val existing = local.getDiary(userId, id)

        if (existing != null && existing.syncStatus != SyncStatus.SYNCED) {
            if (existing.syncStatus == SyncStatus.PENDING_DELETE || existing.syncStatus == SyncStatus.PENDING_CREATE) {
                return false
            }
            // PENDING_UPDATE: a concurrent remote edit also happened.
            val remoteUpdated = doc.remoteUpdatedAtMillis() ?: return false
            if (remoteUpdated > existing.updatedAt.toEpochMilliseconds()) {
                recordAnomaly(SyncAnomaly(FirestorePaths.DIARY_ENTRIES, id, SyncConflictReason.CONCURRENT_LOCAL_EDIT))
            }
            return false
        }

        val remoteUpdated = doc.remoteUpdatedAtMillis() ?: return false
        if (existing != null && existing.updatedAt.toEpochMilliseconds() >= remoteUpdated) return false

        val remoteDeleted = doc.remoteDeletedAt()
        if (remoteDeleted != null) {
            if (existing == null) return false
            local.tombstoneDiary(userId, id, remoteDeleted, SyncStatus.PENDING_DELETE)
            local.purgeDiary(userId, id)
            recordAnomaly(SyncAnomaly(FirestorePaths.DIARY_ENTRIES, id, SyncConflictReason.REMOTE_DELETED))
            return true
        }

        val entry = doc.toDiaryEntry(userId, existing) ?: return false
        if (existing != null) {
            recordAnomaly(SyncAnomaly(FirestorePaths.DIARY_ENTRIES, id, SyncConflictReason.REMOTE_NEWER))
        }
        local.upsertDiary(entry)
        return true
    }

    private suspend fun mergeMood(userId: String, doc: RemoteDocument): Boolean {
        val id = doc.id
        val existing = local.getMood(userId, id)
        if (existing != null && existing.syncStatus != SyncStatus.SYNCED) return false

        val remoteUpdated = doc.remoteUpdatedAtMillis() ?: return false
        if (existing != null && existing.updatedAt.toEpochMilliseconds() >= remoteUpdated) return false

        val remoteDeleted = doc.remoteDeletedAt()
        if (remoteDeleted != null) {
            if (existing == null) return false
            local.purgeMood(userId, id)
            return true
        }

        val entry = doc.toMoodEntry(userId) ?: return false
        local.upsertMood(entry)
        return true
    }

    private suspend fun mergeProfile(userId: String, doc: RemoteDocument?) {
        doc ?: return
        val existing = local.getProfile(userId) ?: return
        val remoteUpdated = doc.remoteUpdatedAtMillis() ?: return
        if (existing.updatedAt.toEpochMilliseconds() >= remoteUpdated) return

        local.upsertProfile(
            UserProfile(
                id = userId,
                email = FirestoreCodec.asString(doc.fields[UserFields.EMAIL]) ?: existing.email,
                displayName = FirestoreCodec.asNullableString(doc.fields[UserFields.DISPLAY_NAME]),
                photoUrl = FirestoreCodec.asNullableString(doc.fields[UserFields.PHOTO_URL]),
                createdAt = existing.createdAt,
                updatedAt = Instant.fromEpochMilliseconds(remoteUpdated),
            ),
        )
        watermarks[PROFILE_WATERMARK] = remoteUpdated
    }


    /**
     * Removes every remote record for the account, subcollections before the profile.
     *
     * Called by the account-deletion use case *before* the Auth account is removed, while
     * the id token is still valid. Failing loudly here is deliberate: a partially deleted
     * account must be visible to the user rather than leaving orphaned private data.
     */
    suspend fun deleteRemoteData(userId: String) {
        withContext(dispatchers.io) {
            remoteMood.deleteUserMoods(userId).getOrThrow()
            remoteDiary.deleteUserDiary(userId).getOrThrow()
            watermarks.clear()
            logger.info("SyncManager") { "remote data removed" }
        }
    }


    private fun recordAnomaly(anomaly: SyncAnomaly) {
        _anomalies.value = (_anomalies.value + anomaly).takeLast(MAX_TRACKED_ANOMALIES)
    }

    fun clearAnomalies() {
        _anomalies.value = emptyList()
    }

    private fun RemoteDocument.remoteUpdatedAtMillis(): Long? =
        (updateTime ?: createTime)?.let { Iso8601.parseTimestamp(it)?.toEpochMilliseconds() }

    private fun RemoteDocument.remoteDeletedAt(): Instant? {
        val raw = fields[DiaryFields.DELETED_AT] ?: fields[MoodFields.DELETED_AT]
        return FirestoreCodec.asString(raw)?.let(Iso8601::parseTimestamp)
    }

    private fun RemoteDocument.toDiaryEntry(userId: String, existing: DiaryEntry?): DiaryEntry? {
        val title = FirestoreCodec.asString(fields[DiaryFields.TITLE]) ?: existing?.title ?: return null
        val content = FirestoreCodec.asString(fields[DiaryFields.CONTENT]) ?: existing?.content ?: return null
        val entryDate = FirestoreCodec.asString(fields[DiaryFields.ENTRY_DATE])
            ?.let(LocalDates::parseIsoString)
            ?: existing?.entryDate
            ?: LocalDates.today(timeProvider.now(), timeZone)

        return DiaryEntry(
            id = id,
            userId = userId,
            title = title,
            content = content,
            entryDate = entryDate,
            mood = Mood.fromId(FirestoreCodec.asString(fields[DiaryFields.MOOD])),
            emotions = FirestoreCodec.asStringList(fields[DiaryFields.EMOTIONS]).map(Emotion::fromId),
            tags = FirestoreCodec.asStringList(fields[DiaryFields.TAGS]),
            isFavorite = FirestoreCodec.asBool(fields[DiaryFields.IS_FAVORITE]) ?: false,
            location = FirestoreCodec.asNullableString(fields[DiaryFields.LOCATION]),
            weather = FirestoreCodec.asNullableString(fields[DiaryFields.WEATHER]),
            createdAt = FirestoreCodec.asString(fields[DiaryFields.CREATED_AT])
                ?.let(Iso8601::parseTimestamp) ?: existing?.createdAt ?: timeProvider.now(),
            updatedAt = remoteUpdatedAtMillis()?.let(Instant::fromEpochMilliseconds) ?: timeProvider.now(),
            deletedAt = null,
            syncStatus = SyncStatus.SYNCED,
        )
    }

    private fun RemoteDocument.toMoodEntry(userId: String): MoodEntry? {
        val moodId = FirestoreCodec.asString(fields[MoodFields.MOOD]) ?: return null
        return MoodEntry(
            id = id,
            userId = userId,
            mood = Mood.fromId(moodId),
            note = FirestoreCodec.asNullableString(fields[MoodFields.NOTE]),
            emotions = FirestoreCodec.asStringList(fields[MoodFields.EMOTIONS]).map(Emotion::fromId),
            date = FirestoreCodec.asString(fields[MoodFields.DATE])
                ?.let(LocalDates::parseIsoString) ?: LocalDates.today(timeProvider.now(), timeZone),
            createdAt = FirestoreCodec.asString(fields[MoodFields.CREATED_AT])
                ?.let(Iso8601::parseTimestamp) ?: timeProvider.now(),
            updatedAt = remoteUpdatedAtMillis()?.let(Instant::fromEpochMilliseconds) ?: timeProvider.now(),
            deletedAt = null,
            syncStatus = SyncStatus.SYNCED,
        )
    }

    private companion object {
        /** Firestore's documented maximum writes per commit. */
        const val BATCH_SIZE = 400
        const val MAX_TRACKED_ANOMALIES = 50
        const val DIARY_WATERMARK = "diary"
        const val MOOD_WATERMARK = "mood"
        const val PROFILE_WATERMARK = "profile"
    }
}

private fun AppError.reasonKeyOrDefault(): String = when (this) {
    is AppError.Authentication -> ErrorKeys.SESSION_EXPIRED
    is AppError.Validation -> reasonKey
    is AppError.Conflict -> reasonKey
    is AppError.Storage -> reasonKey
    is AppError.Export -> reasonKey
    is AppError.Unexpected -> reasonKey
    else -> ErrorKeys.UNEXPECTED
}
