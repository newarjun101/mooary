package com.arjun.soul_note.core.photocache

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * A private per-user directory for the decoded profile photo.
 *
 * Why bytes on disk at all: Coil is handed a *path*, never a network url. The bytes are
 * fetched by the app's own Ktor client, which already holds the refreshing Firebase id token
 * and the 401-refresh retry that `coil-network-ktor3` would not give us. Everything else
 * about the image -- decode, downscale, cache eviction -- is left to Coil.
 *
 * okio rather than `java.io` / Foundation so this is one implementation instead of three: the
 * three that existed first each needed their own interop (`NSData.create` is not a Kotlin
 * function, `FileDialog` is modal to the whole process) and none of them was worth the
 * divergence. okio arrives with Coil.
 */
private val fs = FileSystem.SYSTEM

private val directory: Path =
    (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "soulnote-avatars").also { fs.createDirectories(it) }

private fun fileFor(fileName: String): Path = directory / fileName

/**
 * The file name an account's avatar is cached under. Derived from the account id so two
 * accounts on one device never collide, and free of any caller-supplied text, so a caller
 * cannot use this to reach outside the cache.
 */
fun photoCacheFileName(userId: String): String = "avatar-$userId.img"

fun photoCacheDirectory(): String = directory.toString()

/**
 * Writes [bytes] under [fileName], replacing any existing file.
 *
 * The `write`/`read` block forms rather than `sink()`/`source()` plus `use`: okio's
 * `Closeable` is not `kotlin.io.Closeable` on Kotlin/Native, so `use` does not resolve there
 * and this file has to compile for iOS too.
 */
fun writePhotoCacheFile(fileName: String, bytes: ByteArray) {
    fs.write(fileFor(fileName)) { write(bytes) }
}

/** The bytes previously written under [fileName], or null if there are none. */
fun readPhotoCacheFile(fileName: String): ByteArray? {
    val path = fileFor(fileName)
    return if (fs.exists(path)) fs.read(path) { readByteArray() } else null
}

/** Removes [fileName]. Succeeds when it was not there to begin with. */
fun deletePhotoCacheFile(fileName: String) {
    fs.delete(fileFor(fileName), mustExist = false)
}

/** The full local path of [fileName], for handing to an image loader. */
fun photoCachePath(fileName: String): String = fileFor(fileName).toString()
