package com.arjun.soul_note.domain.model

/**
 * Lifecycle of a locally-owned record with respect to the backend.
 *
 * The local database is the single source of truth for the UI; these states describe
 * only what still has to be reconciled with Firestore.
 */
enum class SyncStatus {
    /** Local and remote agree. */
    SYNCED,

    /** Written locally, never sent. */
    PENDING_CREATE,

    /** Changed locally after the last successful push. */
    PENDING_UPDATE,

    /** Tombstoned locally, delete not yet acknowledged by the backend. */
    PENDING_DELETE,

    /**
     * Local and remote both changed since the last sync and neither was newer at the
     * time of detection. The deterministic [updatedAt] comparison has already been
     * applied by the sync manager; the flag is kept so the UI can tell the user that a
     * concurrent edit happened rather than silently dropping their work.
     */
    CONFLICT,
    ;

    val isPending: Boolean
        get() = this != SYNCED

    /** A pending tombstone must never be shown to the user. */
    val isVisibleToUser: Boolean
        get() = this != PENDING_DELETE
}

/** The authenticated principal, as far as the domain layer is concerned. */
data class User(
    val id: String,
    val email: String,
    val isEmailVerified: Boolean,
    val createdAtMillis: Long,
)

/** The private profile document at `users/{userId}`. */
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: kotlin.time.Instant,
    val updatedAt: kotlin.time.Instant,
) {
    /** Best available human name for the dashboard greeting. */
    fun greetingName(): String? = displayName
        ?.takeIf { it.isNotBlank() }
        ?: email.substringBefore('@').takeIf { it.isNotBlank() }
}
