package com.arjun.soul_note.domain.repository

/**
 * The profile photo, and the only place its bytes travel.
 *
 * Storage is an enhancement, never a prerequisite: a failure here leaves the account with the
 * photo it already had, and a signed-out user is never asked to upload anything. Nothing in
 * this interface blocks a local read.
 */
interface PhotoRepository {

    /**
     * The local file the avatar for [userId] is cached at, or null when the account has no
     * photo cached. Never triggers a download — see [refreshAvatar] for that.
     */
    suspend fun cachedAvatarPath(userId: String): String?

    /**
     * Downloads the avatar if it is not already cached, and returns its local path, or null
     * when the account has no photo.
     *
     * A `404` is an absent photo and returns null. Any other failure is a [Result] failure
     * and the caller is expected to keep showing the initials.
     */
    suspend fun refreshAvatar(userId: String): Result<String?>

    /**
     * Uploads [bytes] as [userId]'s avatar and returns the path it can be rendered from.
     *
     * The returned value is the local cache path, not a remote url: a Storage object url
     * carries a bearer token that must not be stored in the profile document, and the
     * emulator refuses a second read with a stale one.
     */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray): Result<String>

    /** Removes the avatar from Storage and from the cache. Absent is success. */
    suspend fun removeAvatar(userId: String): Result<Unit>
}
