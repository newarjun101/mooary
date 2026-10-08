package com.arjun.soul_note.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.toAppError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.parameter.ParametersHolder
import org.koin.mp.KoinPlatformTools

/**
 * The state every data-backed screen renders.
 *
 * Modelled explicitly instead of with nullable data plus a separate error field, because
 * "loaded and empty" and "still loading" and "failed" are three genuinely different UI
 * states, and collapsing them is how apps end up flashing "no entries yet" during a load.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Failure(val error: AppError) : UiState<Nothing>

    val dataOrNull: T? get() = (this as? Success)?.data
}

/**
 * Minimal ViewModel for Compose Multiplatform.
 *
 * Compose Multiplatform has no lifecycle-owned ViewModel store on every target and this
 * project has no `androidx.lifecycle` dependency, so screen state lives in a plain object
 * with its own [CoroutineScope]. The trade-off is explicit: state survives recomposition
 * and re-entry within a session, but not a process restart. Anything that must survive a
 * restart is written to the local database instead — which is where drafts and settings
 * already live, so no user-authored text is ever at risk.
 *
 * A [SupervisorJob] keeps one failed screen action from cancelling its siblings, and the
 * exception handler routes unexpected throwables into [transientError] instead of crashing
 * the process.
 */
abstract class BaseViewModel {

    /** Screen lifetime. Cancelled by [onCleared]. */
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob())

    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        report(throwable.toAppError())
    }

    private val _transientError = MutableStateFlow<AppError?>(null)

    /** Last error not yet shown by the UI, for snackbars and inline banners. */
    val transientError: StateFlow<AppError?> = _transientError.asStateFlow()

    /** Launch work tied to the screen's lifetime. */
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(errorHandler, block = block)

    /** Reports a failure the screen should surface. */
    protected fun report(error: AppError) {
        _transientError.value = error
    }

    /** Called by the UI once it has shown [transientError]. */
    fun consumeTransientError() {
        _transientError.value = null
    }

    /** Called when the screen leaves the composition. */
    open fun onCleared() {
        scope.cancel()
    }
}

/**
 * Resolves a screen ViewModel from Koin, keeping it alive across recomposition and
 * releasing it when the screen leaves.
 *
 * [keys] participate in the remember key, so a change (a different user id, a different
 * entry being edited) produces a correctly re-parameterised ViewModel instead of stale
 * state. [parameters] are the Koin parameters the definition expects.
 */
@Composable
inline fun <reified T : BaseViewModel> rememberViewModel(
    vararg keys: Any?,
    noinline parameters: () -> ParametersHolder,
): T {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val instance = remember(keys.toList()) {
        val resolved: T = koin.get(T::class, null, parameters)
        resolved
    }
    DisposableEffect(instance) {
        onDispose { instance.onCleared() }
    }
    return instance
}
