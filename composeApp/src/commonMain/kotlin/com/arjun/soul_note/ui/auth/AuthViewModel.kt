package com.arjun.soul_note.ui.auth

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.core.validation.Field
import com.arjun.soul_note.core.validation.ValidationErrors
import com.arjun.soul_note.domain.model.User
import com.arjun.soul_note.domain.usecase.auth.AuthValidationException
import com.arjun.soul_note.domain.usecase.auth.LoginUseCase
import com.arjun.soul_note.domain.usecase.auth.RegisterUseCase
import com.arjun.soul_note.domain.usecase.auth.SendPasswordResetUseCase
import com.arjun.soul_note.ui.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which credential form is showing. */
enum class AuthMode { SIGN_IN, REGISTER }

/** Everything the auth screen renders. */
data class AuthState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val isResetSent: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: AppError? = null,
    val signedInUser: User? = null,
) {
    val isRegister: Boolean get() = mode == AuthMode.REGISTER
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank() &&
            (!isRegister || confirmPassword.isNotBlank())
}

/**
 * Backing state for sign-in, registration and password reset.
 *
 * Validation failures are surfaced per field rather than as one message, because a form
 * that only says "invalid input" makes the user guess. The domain returns typed
 * [ValidationErrors] for exactly this reason.
 */
class AuthViewModel(
    private val login: LoginUseCase,
    private val register: RegisterUseCase,
    private val resetPassword: SendPasswordResetUseCase,
) : BaseViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun onEmailChanged(value: String) = _state.update { it.copy(email = value.trim(), fieldErrors = it.fieldErrors.clear(EMAIL)) }

    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value, fieldErrors = it.fieldErrors.clear(PASSWORD)) }

    fun onConfirmPasswordChanged(value: String) = _state.update { it.copy(confirmPassword = value, fieldErrors = it.fieldErrors.clear(CONFIRM)) }

    fun switchMode(mode: AuthMode) = _state.update {
        it.copy(mode = mode, fieldErrors = emptyMap(), error = null, isResetSent = false)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, fieldErrors = emptyMap(), error = null) }

        launch {
            val result = if (current.isRegister) {
                register(current.email, current.password, current.confirmPassword)
            } else {
                login(current.email, current.password)
            }
            result
                .onSuccess { user ->
                    _state.update { it.copy(isSubmitting = false, signedInUser = user) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(isSubmitting = false) }
                    applyFailure(throwable)
                }
        }
    }

    fun sendReset() {
        val email = _state.value.email
        if (email.isBlank() || _state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true) }
        launch {
            resetPassword(email)
                .onSuccess { _state.update { state -> state.copy(isSubmitting = false, isResetSent = true) } }
                .onFailure { throwable ->
                    _state.update { it.copy(isSubmitting = false) }
                    applyFailure(throwable)
                }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun applyFailure(throwable: Throwable) {
        if (throwable is AuthValidationException) {
            _state.update { it.copy(fieldErrors = throwable.errors.toFieldMap()) }
        } else {
            _state.update { it.copy(error = throwable.toAppError()) }
        }
    }

    private companion object {
        val EMAIL: String = Field.EMAIL.key
        val PASSWORD: String = Field.PASSWORD.key
        val CONFIRM: String = Field.CONFIRM_PASSWORD.key
    }
}

private fun Map<String, String>.clear(key: String): Map<String, String> = this - key

private fun ValidationErrors.toFieldMap(): Map<String, String> =
    errors.associate { it.field.key to it.reasonKey }
