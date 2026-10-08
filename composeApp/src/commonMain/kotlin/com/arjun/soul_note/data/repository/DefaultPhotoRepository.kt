package com.arjun.soul_note.data.repository

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.AppException
import com.arjun.soul_note.core.error.suspendRunCatchingApp
import com.arjun.soul_note.core.photocache.deletePhotoCacheFile
import com.arjun.soul_note.core.photocache.photoCachePath
import com.arjun.soul_note.core.photocache.photoCacheFileName
import com.arjun.soul_note.core.photocache.readPhotoCacheFile
import com.arjun.soul_note.core.photocache.writePhotoCacheFile
import com.arjun.soul_note.data.remote.firebase.FirebaseStorageClient
import com.arjun.soul_note.domain.repository.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Storage on one side, the on-device cache on the other.
 *
 * The cache is written *before* the upload is reported as successful, so a viewer that reads
 * the returned path never waits on the network. Bytes are capped before they leave the device
 * rather than after, because a rejected 6 MB upload costs a round trip and the rules ceiling
 * is not a substitute for not sending it.
 */
class DefaultPhotoRepository(
    private val storage: FirebaseStorageClient?,
    private val io: CoroutineDispatcher,
    private val maxBytes: Int = MAX_UPLOAD_BYTES,
) : PhotoRepository {

    override suspend fun cachedAvatarPath(userId: String): String? = withContext(io) {
        readPhotoCacheFile(photoCacheFileName(userId))?.let { photoCachePath(photoCacheFileName(userId)) }
    }

    override suspend fun refreshAvatar(userId: String): Result<String?> = try {
        val storage = storage ?: return Result.success(cachedAvatarPath(userId))
        Result.success(download(userId, storage))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (app: AppException) {
        Result.failure(app)
    } catch (throwable: Throwable) {
        Result.failure(AppException(AppError.Unexpected("error_storage")))
    }

    override suspend fun uploadAvatar(userId: String, bytes: ByteArray): Result<String> {
        if (bytes.isEmpty()) {
            return Result.failure(AppException(AppError.Validation(null, "error_photo_empty")))
        }
        if (bytes.size > maxBytes) {
            return Result.failure(AppException(AppError.Validation(null, "error_photo_too_large")))
        }
        val storage = storage ?: return suspendRunCatchingApp {
            withContext(io) { writePhotoCacheFile(photoCacheFileName(userId), bytes) }
            photoCachePath(photoCacheFileName(userId))
        }
        return suspendRunCatchingApp {
            withContext(io) { writePhotoCacheFile(photoCacheFileName(userId), bytes) }
            // A failed upload leaves the new bytes in the cache, which would show a photo that
            // no other device has. Rolling back to the previous file keeps the UI honest.
            try {
                storage.uploadAvatar(userId, bytes)
            } catch (throwable: Throwable) {
                withContext(io) { deletePhotoCacheFile(photoCacheFileName(userId)) }
                throw throwable
            }
            photoCachePath(photoCacheFileName(userId))
        }
    }

    override suspend fun removeAvatar(userId: String): Result<Unit> = try {
        storage?.deleteAvatar(userId)
        withContext(io) { deletePhotoCacheFile(photoCacheFileName(userId)) }
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (app: AppException) {
        Result.failure(app)
    } catch (throwable: Throwable) {
        Result.failure(AppException(AppError.Unexpected("error_storage")))
    }

    /** Returns the local path, or null when the account simply has no photo yet. */
    private suspend fun download(userId: String, storage: FirebaseStorageClient): String? {
        cachedAvatarPath(userId)?.let { return it }
        val bytes = storage.downloadAvatar(userId) ?: return null
        withContext(io) { writePhotoCacheFile(photoCacheFileName(userId), bytes) }
        return photoCachePath(photoCacheFileName(userId))
    }

    private companion object {
        /** Matches the ceiling in storage.rules, so a rejection is a client-side error. */
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
    }
}
