package com.arjun.soul_note.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull

/** Whether the device currently believes it can reach the internet. */
data class NetworkStatus(
    val isOnline: Boolean,
    val isExpensive: Boolean = false,
    val isValidated: Boolean = false,
)

/**
 * Platform-provided connectivity signal.
 *
 * The app is offline-first, so this is used **only** to decide when to attempt a sync
 * push. It never gates reads or writes — those always resolve from the local database.
 */
interface ConnectivityObserver {
    val status: StateFlow<NetworkStatus>

    fun isOnline(): Boolean
}

/** Connectivity observer that always reports "online", used by tests and previews. */
class AlwaysOnlineObserver(
    initial: NetworkStatus = NetworkStatus(isOnline = true, isValidated = true),
) : ConnectivityObserver {
    private val _status = kotlinx.coroutines.flow.MutableStateFlow(initial)
    override val status: StateFlow<NetworkStatus> = _status
    override fun isOnline(): Boolean = _status.value.isOnline

    fun set(value: NetworkStatus) {
        _status.value = value
    }
}

/** Emits the moment the app becomes online again; the sync manager's trigger. */
val ConnectivityObserver.onlineSignal: Flow<Unit>
    get() = status.mapNotNull { if (it.isOnline) Unit else null }
