package com.arjun.mooary.domain.model

/**
 * Legacy local-row state retained only to read databases produced by prior releases.
 * Local repositories always write [SYNCED]; no backend reconciliation exists.
 */
enum class SyncStatus { SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, CONFLICT }

/** A name stored only on this device for a more personal journal. */
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
