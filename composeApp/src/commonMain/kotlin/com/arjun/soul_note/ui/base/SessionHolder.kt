package com.arjun.soul_note.ui.base

import com.arjun.soul_note.domain.model.User
import com.arjun.soul_note.domain.repository.AuthRepository
import com.arjun.soul_note.domain.usecase.auth.RestoreSessionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Who the app is currently acting as. */
data class SessionState(
    val user: User? = null,
    val userId: String = SessionHolder.LOCAL_USER_ID,
    val isRestoring: Boolean = true,
) {
    val isSignedIn: Boolean get() = user != null

    /** True when the diary is the on-device, account-free one. */
    val isLocalOnly: Boolean get() = user == null
}

/**
 * The identity every screen reads from.
 *
 * A build without Firebase configuration has no authenticated user, but it must still be
 * usable — that is the whole point of the offline-first design. So this holder falls back
 * to a fixed [LOCAL_USER_ID] while signed out, giving a single on-device diary that needs
 * no account. Once a user signs in, the real id takes over and their data is kept separate
 * from the local one.
 */
class SessionHolder(
    authRepository: AuthRepository,
    private val restoreSession: RestoreSessionUseCase,
    scope: CoroutineScope,
) {
    private val restoring = MutableStateFlow(true)

    val state: StateFlow<SessionState> = combine(
        authRepository.observeAuthState(),
        restoring,
    ) { user, isRestoring ->
        SessionState(
            user = user,
            userId = user?.id ?: LOCAL_USER_ID,
            isRestoring = isRestoring,
        )
    }.stateIn(scope, SharingStarted.Eagerly, SessionState())

    init {
        // Restoring is fire-and-forget: the UI shows the local diary immediately and
        // upgrades to the restored account when (and if) a session comes back.
        scope.launch {
            runCatching { restoreSession() }
            restoring.value = false
        }
    }

    companion object {
        /**
         * Fixed id for the offline, signed-out diary. Deliberately not a UUID: it must be
         * identical on every launch so the local records are found again.
         */
        const val LOCAL_USER_ID = "local_user"
    }
}
