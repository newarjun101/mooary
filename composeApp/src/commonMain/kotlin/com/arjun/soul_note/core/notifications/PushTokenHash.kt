package com.arjun.soul_note.core.notifications

/**
 * A short, stable id derived from a device identity, used as a Firestore document id.
 *
 * A raw device identifier does not belong in a Firestore path: document paths surface in logs,
 * errors and rules traces. The path therefore holds this one-way identifier while the FCM token
 * remains only a field value.
 *
 * FNV-1a over UTF-16 code units, rendered as 16 hex characters. Not a cryptographic hash, and
 * it does not need to be one: this is a document *name* for a collection holding a handful of
 * devices, and the property that matters is that the same device identity always lands on the
 * same document. The one thing a weak hash must not do is collide *with itself*, which would
 * make unrelated devices overwrite each other.
 *
 * Implemented here rather than reached for because common code cannot use `java.security` or
 * `platform.crypto`, and the common hash of [kotlin.String] is 32-bit — fine for a hash map,
 * narrow enough that a birthday collision across a few hundred devices is worth avoiding.
 */
internal fun hashDeviceIdentity(identity: String): String {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
    for (char in identity) {
        hash = hash xor (char.code.toLong() and 0xffff)
        // 0x100000001b3, the FNV prime, as a positive Long.
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
