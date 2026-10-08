package com.arjun.soul_note.core.notifications

/**
 * A short, stable id derived from a push registration token, used as a Firestore document id.
 *
 * The token is a credential: it is what lets its holder address this device. Putting it in a
 * document id would also mean it appears in every log line, error message and rules trace that
 * ever mentions the path — the opposite of what the privacy rules ask for — so the id is a
 * hash and the token itself is only ever a field value.
 *
 * FNV-1a over UTF-16 code units, rendered as 16 hex characters. Not a cryptographic hash, and
 * it does not need to be one: this is a document *name* for a collection holding a handful of
 * devices, and the property that matters is that the same token always lands on the same
 * document and a different token almost never does. The one thing a weak hash must not do is
 * collide *with itself*, which re-registering would turn into duplicate devices.
 *
 * Implemented here rather than reached for because common code cannot use `java.security` or
 * `platform.crypto`, and the common hash of [kotlin.String] is 32-bit — fine for a hash map,
 * narrow enough that a birthday collision across a few hundred devices is worth avoiding.
 */
internal fun hashPushToken(token: String): String {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
    for (char in token) {
        hash = hash xor (char.code.toLong() and 0xffff)
        // 0x100000001b3, the FNV prime, as a positive Long.
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
