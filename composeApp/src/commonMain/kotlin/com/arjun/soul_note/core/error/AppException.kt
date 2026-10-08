package com.arjun.soul_note.core.error

/**
 * Carries an already-classified [AppError] through a `Result` chain, so a repository can
 * signal a domain-level failure without re-throwing SDK exceptions.
 */
class AppException(val error: AppError) : Exception(error.toString())

/** Throws [AppException] when the receiver failed. */
fun <T> Result<T>.getOrThrowAppError(): T = getOrElse { throw AppException(it.toAppError()) }

/** Runs [block], converting any throwable into a failed [Result]. */
inline fun <T> runCatchingApp(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: kotlinx.coroutines.CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(AppException(throwable.toAppError()))
}

/** Suspending variant of [runCatchingApp] that never swallows coroutine cancellation. */
suspend inline fun <T> suspendRunCatchingApp(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: kotlinx.coroutines.CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(AppException(throwable.toAppError()))
}
