package com.arjun.soul_note.data.remote.firebase

import com.arjun.soul_note.core.datetime.Iso8601
import com.arjun.soul_note.core.error.ErrorKeys
import com.arjun.soul_note.core.error.suspendRunCatchingApp
import com.arjun.soul_note.data.remote.RemoteAuthDataSource
import com.arjun.soul_note.data.remote.RemoteDiaryDataSource
import com.arjun.soul_note.data.remote.RemoteIdentity
import com.arjun.soul_note.data.remote.RemoteMoodDataSource
import com.arjun.soul_note.data.remote.RemoteUserDataSource
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.SecureTokenStore
import com.arjun.soul_note.domain.model.DiaryFields
import com.arjun.soul_note.domain.model.FirestorePaths
import com.arjun.soul_note.domain.model.MoodFields
import com.arjun.soul_note.domain.model.UserFields
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Firestore-backed diary data source.
 *
 * Pushes are *batched* through `:commit` so a sync pass is atomic per batch, and pulls
 * are incremental: only documents whose `updatedAt` is newer than the caller's
 * watermark are fetched, so a routine sync costs one small query rather than a full
 * journal download.
 */
/**
 * The `updatedAt > watermark` filter that makes an incremental sync cheap.
 *
 * Two details have to stay together, and both were once wrong in a way no compiler could see:
 * the value has to be a Firestore **timestamp** rather than the millisecond count as a string
 * (Firestore never compares across types, so a string filter matches nothing and the query
 * looks like it works), and it has to be spelled in the same ISO form the write path uses.
 * [FirestoreFilter.ValueType] is not a default worth relying on here, which is why the
 * timestamp type is stated explicitly on every call.
 */
internal fun updatedAfterFilter(field: String, updatedAfterMillis: Long): FirestoreFilter =
    FirestoreFilter(
        field = field,
        operator = FirestoreFilter.Operator.GREATER_THAN,
        value = Iso8601.formatTimestamp(Instant.fromEpochMilliseconds(updatedAfterMillis)),
        valueType = FirestoreFilter.ValueType.TIMESTAMP,
    )

class FirebaseDiaryDataSource(
    private val client: FirestoreClient,
    private val logger: AppLogger,
) : RemoteDiaryDataSource {

    override suspend fun listEntries(userId: String, updatedAfterMillis: Long?): List<RemoteDocument> {
        val filters = buildList {
            if (updatedAfterMillis != null) {
                add(updatedAfterFilter(DiaryFields.UPDATED_AT, updatedAfterMillis))
            }
        }
        return client.query(
            FirestoreQuery(
                collectionPath = FirestorePaths.diaryEntries(userId),
                filters = filters,
                ordersBy = listOf(FirestoreOrder(DiaryFields.UPDATED_AT, FirestoreOrder.Direction.ASCENDING)),
                limit = PAGE_SIZE,
            ),
        )
    }

    override suspend fun getEntry(userId: String, entryId: String): RemoteDocument? =
        client.getDocument(FirestorePaths.entry(userId, entryId))

    override suspend fun pushEntries(writes: List<FirestoreWrite>): Result<Unit> =
        suspendRunCatchingApp {
            client.commit(writes)
            logger.debug("FirebaseDiaryDataSource") { "pushed ${writes.size} writes" }
        }

    override suspend fun deleteUserDiary(userId: String): Result<Unit> = suspendRunCatchingApp {
        client.deleteCollection(FirestorePaths.diaryEntries(userId))
    }

    private companion object {
        const val PAGE_SIZE = 400
    }
}

class FirebaseMoodDataSource(
    private val client: FirestoreClient,
    private val logger: AppLogger,
) : RemoteMoodDataSource {

    override suspend fun listMoods(userId: String, updatedAfterMillis: Long?): List<RemoteDocument> {
        val filters = buildList {
            if (updatedAfterMillis != null) {
                add(updatedAfterFilter(MoodFields.UPDATED_AT, updatedAfterMillis))
            }
        }
        return client.query(
            FirestoreQuery(
                collectionPath = FirestorePaths.moods(userId),
                filters = filters,
                ordersBy = listOf(FirestoreOrder(MoodFields.UPDATED_AT, FirestoreOrder.Direction.ASCENDING)),
                limit = PAGE_SIZE,
            ),
        )
    }

    override suspend fun getMood(userId: String, moodId: String): RemoteDocument? =
        client.getDocument(FirestorePaths.mood(userId, moodId))

    override suspend fun pushMoods(writes: List<FirestoreWrite>): Result<Unit> =
        suspendRunCatchingApp {
            client.commit(writes)
            logger.debug("FirebaseMoodDataSource") { "pushed ${writes.size} writes" }
        }

    override suspend fun deleteUserMoods(userId: String): Result<Unit> = suspendRunCatchingApp {
        client.deleteCollection(FirestorePaths.moods(userId))
    }

    private companion object {
        const val PAGE_SIZE = 400
    }
}

class FirebaseUserDataSource(
    private val client: FirestoreClient,
) : RemoteUserDataSource {

    override suspend fun getProfile(userId: String): RemoteDocument? =
        client.getDocument(FirestorePaths.user(userId))

    override suspend fun pushProfile(writes: List<FirestoreWrite>): Result<Unit> =
        suspendRunCatchingApp { client.commit(writes) }

    override suspend fun deleteProfile(userId: String): Result<Unit> = suspendRunCatchingApp {
        client.commit(listOf(FirestoreWrite.Delete(FirestorePaths.user(userId))))
    }
}

/**
 * Identity Toolkit backed auth data source.
 *
 * The session is held in memory for the request path and mirrored to a
 * [SecureTokenStore] so a cold start restores the user without a login screen.
 * [refreshMutex] serialises refreshes: several ViewModels can discover an expired token
 * at once, and only one refresh should hit the network.
 */
class FirebaseAuthDataSource(
    private val client: FirebaseAuthClient,
    private val tokenStore: SecureTokenStore,
    private val logger: AppLogger,
) : RemoteAuthDataSource {

    private val _current = MutableStateFlow<AuthSession?>(null)
    private val refreshMutex = Mutex()

    val session: StateFlow<AuthSession?> = _current.asStateFlow()

    override suspend fun signIn(email: String, password: String): Result<RemoteIdentity> =
        suspendRunCatchingApp {
            val session = client.signInWithPassword(email, password)
            persist(session)
            session.toIdentity()
        }

    override suspend fun signUp(email: String, password: String): Result<RemoteIdentity> =
        suspendRunCatchingApp {
            val session = client.signUp(email, password)
            persist(session)
            session.toIdentity()
        }

    override suspend fun signOut(): Result<Unit> = suspendRunCatchingApp {
        _current.value = null
        tokenStore.clear()
    }

    override suspend fun resetPassword(email: String): Result<Unit> = suspendRunCatchingApp {
        client.sendPasswordResetEmail(email)
    }

    override suspend fun deleteAccount(): Result<Unit> = suspendRunCatchingApp {
        // `accounts:delete` takes the id token as its only credential, so the stale session
        // snapshot is not good enough: a session left open past the token's life gets a 401
        // and the user is left with an account they cannot remove. [idToken] is the same
        // refreshing accessor the Firestore client is given, so this asks for exactly the
        // token the rest of the app is already using successfully.
        val token = idToken() ?: return@suspendRunCatchingApp
        client.deleteAccount(token)
        _current.value = null
        tokenStore.clear()
    }

    override suspend fun restoreSession(): Result<RemoteIdentity?> = suspendRunCatchingApp {
        val stored = tokenStore.read()
        if (stored.isNullOrBlank()) {
            // Firestore never accepts an unauthenticated write. Anonymous Auth gives the first
            // launch a rules-bound uid so its device registration can be stored before the
            // person decides whether to create an account.
            val anonymous = client.signInAnonymously()
            persist(anonymous)
            return@suspendRunCatchingApp anonymous.toIdentity()
        }

        val cached = AuthSessionCodec.decode(stored)
        val restored = if (cached.isExpired(nowEpochSeconds())) {
            client.refresh(cached.refreshToken)
        } else {
            runCatching { client.lookup(cached.idToken) }.getOrElse { client.refresh(cached.refreshToken) }
        }
        persist(restored)
        restored.toIdentity()
    }

    override suspend fun refreshSession(): Result<RemoteIdentity> = suspendRunCatchingApp {
        refreshMutex.withLock {
            val latest = _current.value ?: error("no session")
            val refreshed = if (latest.isExpired(nowEpochSeconds())) {
                client.refresh(latest.refreshToken).also { persist(it) }
            } else {
                latest
            }
            refreshed
        }.toIdentity()
    }

    override suspend fun sendEmailVerification(): Result<Unit> = suspendRunCatchingApp {
        val session = _current.value ?: return@suspendRunCatchingApp
        client.sendEmailVerification(session.idToken)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        suspendRunCatchingApp {
            val session = _current.value ?: return@suspendRunCatchingApp
            val email = session.email.ifBlank { error("unknown email") }
            // Identity Toolkit requires a fresh sign-in before it will change a password,
            // which also re-validates the current one.
            val reverified = client.signInForReauth(email, currentPassword)
            client.changePassword(reverified.idToken, newPassword)
            persist(reverified)
        }

    override suspend fun currentUserId(): String? = _current.value?.userId

    /**
     * The bearer token the Firestore client attaches to every request.
     *
     * Identity Toolkit ID tokens are short-lived, so a cached one can already be dead by the
     * time a sync runs. Handing the client a stale token turns every request into a 401 that
     * reads like a permissions problem, so refresh in place when expired and let
     * [refreshMutex] collapse the stampede when several screens ask at once.
     *
     * Returns null rather than throwing when there is no session or the refresh fails: an
     * unauthenticated request is a legitimate state (signed out, offline) and the caller
     * already handles a missing token.
     */
    override suspend fun idToken(): String? = refreshMutex.withLock {
        val latest = _current.value ?: return@withLock null
        if (!latest.isExpired(nowEpochSeconds())) return@withLock latest.idToken
        suspendRunCatchingApp { client.refresh(latest.refreshToken) }
            .onSuccess { persist(it) }
            .getOrNull()
            ?.idToken
    }

    private suspend fun persist(session: AuthSession) {
        _current.value = session
        tokenStore.write(AuthSessionCodec.encode(session))
    }

    private fun AuthSession.toIdentity(): RemoteIdentity = RemoteIdentity(
        userId = userId,
        email = email,
        emailVerified = emailVerified,
        displayName = displayName,
        photoUrl = photoUrl,
        createdAtEpochMillis = 0L,
    )

    private fun nowEpochSeconds(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
}

/**
 * Session serialisation.
 *
 * The token is a credential, so it is stored opaquely in the platform keystore rather
 * than in the journal database. This codec only shapes the string; the encryption is the
 * platform store's job.
 */
internal object AuthSessionCodec {

    fun encode(session: AuthSession): String = listOf(
        session.idToken,
        session.refreshToken,
        session.userId,
        session.expiresAtEpochSeconds.toString(),
        session.email,
        session.emailVerified.toString(),
        session.displayName.orEmpty(),
        session.photoUrl.orEmpty(),
    ).joinToString(SEPARATOR)

    fun decode(value: String): AuthSession {
        val parts = value.split(SEPARATOR)
        require(parts.size >= 8) { "malformed session" }
        return AuthSession(
            idToken = parts[0],
            refreshToken = parts[1],
            userId = parts[2],
            expiresAtEpochSeconds = parts[3].toLongOrNull() ?: 0L,
            localId = parts[2],
            email = parts[4],
            emailVerified = parts[5].toBoolean(),
            displayName = parts[6].takeIf(String::isNotBlank),
            photoUrl = parts[7].takeIf(String::isNotBlank),
        )
    }

    private const val SEPARATOR = "\u001F"
}

/** Domain ↔ Firestore document translation for diary, mood and profile records. */
object RemoteMappers {


    fun diaryToFields(
        title: String,
        content: String,
        entryDate: String,
        moodId: String,
        emotions: List<String>,
        tags: List<String>,
        isFavorite: Boolean,
        location: String?,
        weather: String?,
        wordCount: Int,
        createdAt: Instant,
        updatedAt: Instant,
        deletedAt: Instant?,
    ): Map<String, JsonElement> = buildMap {
        put(DiaryFields.TITLE, FirestoreCodec.string(title))
        put(DiaryFields.CONTENT, FirestoreCodec.string(content))
        put(DiaryFields.ENTRY_DATE, FirestoreCodec.string(entryDate))
        put(DiaryFields.MOOD, FirestoreCodec.string(moodId))
        put(DiaryFields.EMOTIONS, FirestoreCodec.stringArray(emotions))
        put(DiaryFields.TAGS, FirestoreCodec.stringArray(tags))
        put(DiaryFields.IS_FAVORITE, FirestoreCodec.bool(isFavorite))
        put(DiaryFields.LOCATION, FirestoreCodec.nullableString(location))
        put(DiaryFields.WEATHER, FirestoreCodec.nullableString(weather))
        put(DiaryFields.WORD_COUNT, FirestoreCodec.long(wordCount.toLong()))
        put(DiaryFields.CREATED_AT, FirestoreCodec.timestamp(Iso8601.formatTimestamp(createdAt)))
        put(DiaryFields.UPDATED_AT, FirestoreCodec.timestamp(Iso8601.formatTimestamp(updatedAt)))
        put(DiaryFields.DELETED_AT, FirestoreCodec.nullableTimestamp(deletedAt?.let(Iso8601::formatTimestamp)))
    }


    fun moodToFields(
        moodId: String,
        note: String?,
        emotions: List<String>,
        date: String,
        createdAt: Instant,
        updatedAt: Instant,
        deletedAt: Instant?,
    ): Map<String, JsonElement> = buildMap {
        put(MoodFields.MOOD, FirestoreCodec.string(moodId))
        put(MoodFields.NOTE, FirestoreCodec.nullableString(note))
        put(MoodFields.EMOTIONS, FirestoreCodec.stringArray(emotions))
        put(MoodFields.DATE, FirestoreCodec.string(date))
        put(MoodFields.CREATED_AT, FirestoreCodec.timestamp(Iso8601.formatTimestamp(createdAt)))
        put(MoodFields.UPDATED_AT, FirestoreCodec.timestamp(Iso8601.formatTimestamp(updatedAt)))
        put(MoodFields.DELETED_AT, FirestoreCodec.nullableTimestamp(deletedAt?.let(Iso8601::formatTimestamp)))
    }


    fun profileToFields(
        email: String,
        displayName: String?,
        photoUrl: String?,
        createdAt: Instant,
        updatedAt: Instant,
    ): Map<String, JsonElement> = buildMap {
        put(UserFields.EMAIL, FirestoreCodec.string(email))
        put(UserFields.DISPLAY_NAME, FirestoreCodec.nullableString(displayName))
        put(UserFields.PHOTO_URL, FirestoreCodec.nullableString(photoUrl))
        put(UserFields.CREATED_AT, FirestoreCodec.timestamp(Iso8601.formatTimestamp(createdAt)))
        put(UserFields.UPDATED_AT, FirestoreCodec.timestamp(Iso8601.formatTimestamp(updatedAt)))
    }

    fun primitive(value: String): JsonPrimitive = JsonPrimitive(value)
}
