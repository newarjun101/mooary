package com.arjun.mooary.ui.base

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Who the app is currently acting as. */
data class SessionState(
    val userId: String = SessionHolder.LOCAL_USER_ID,
) {
    /** The journal is deliberately stored only on this device. */
    val isLocalOnly: Boolean get() = true
}

/**
 * The identity every screen reads from.
 *
 * The app deliberately has one on-device journal and no email-account session.
 */
class SessionHolder {
    val state: StateFlow<SessionState> = MutableStateFlow(SessionState())

    companion object {
        /**
         * Fixed id for the offline, signed-out diary. Deliberately not a UUID: it must be
         * identical on every launch so the local records are found again.
         */
        const val LOCAL_USER_ID = "local_user"
    }
}
