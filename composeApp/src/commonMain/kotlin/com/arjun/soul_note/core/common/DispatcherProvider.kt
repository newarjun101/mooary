package com.arjun.soul_note.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatcher injection point.
 *
 * Nothing in the codebase is allowed to reference [Dispatchers] directly: tests swap in
 * [TestDispatchers] and every coroutine is provably cancellable and virtual-time aware.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

/** Production dispatchers, backed by the platform defaults. */
expect object PlatformDispatcherProvider : DispatcherProvider

/** Fully deterministic dispatchers for unit tests. */
class TestDispatcherProvider(
    private val test: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = test
    override val io: CoroutineDispatcher get() = test
    override val default: CoroutineDispatcher get() = test
}
