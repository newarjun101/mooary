package com.arjun.soul_note.ui.settings

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.core.validation.Field
import com.arjun.soul_note.core.validation.Limits
import com.arjun.soul_note.core.validation.ValidationErrors
import com.arjun.soul_note.core.validation.ValidationResult
import com.arjun.soul_note.core.validation.Validators
import com.arjun.soul_note.domain.repository.AppLanguage
import com.arjun.soul_note.domain.repository.PhotoRepository
import com.arjun.soul_note.domain.repository.SettingsRepository
import com.arjun.soul_note.domain.repository.UserRepository
import com.arjun.soul_note.domain.usecase.auth.AuthValidationException
import com.arjun.soul_note.domain.usecase.auth.ChangePasswordUseCase
import com.arjun.soul_note.domain.usecase.auth.DeleteAccountUseCase
import com.arjun.soul_note.domain.usecase.auth.LogoutUseCase
import com.arjun.soul_note.ui.base.BaseViewModel
import com.arjun.soul_note.core.platform.applyAppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A confirmation the screen renders.
 *
 * The view model names *what* happened and the screen owns the words, so nothing here can
 * drift out of the localisation resources.
 */
enum class SettingsNotice {
    NAME_SAVED,
    PASSWORD_CHANGED,
    PHOTO_REMOVED,
    PHOTO_UPDATED,
}

/** Reason keys the screen turns into localised text. Kept as keys, never as English. */
object SettingsFieldKeys {
    const val NAME = "field_display_name"
    const val CURRENT_PASSWORD = "field_current_password"
    const val NEW_PASSWORD = "field_new_password"
    const val CONFIRM_PASSWORD = "field_confirm_password"
    const val PASSWORD_MISMATCH = "error_password_mismatch"
    const val PASSWORD_UNCHANGED = "error_password_unchanged"
}

data class SettingsState(
    val email: String? = null,
    val displayName: String = "",
    val photoUrl: String? = null,
    /** Local cache path of the avatar; what Coil is actually pointed at. */
    val photoPath: String? = null,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val isLocalOnly: Boolean = true,
    val isSavingName: Boolean = false,
    val isChangingPassword: Boolean = false,
    val isChangingPhoto: Boolean = false,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val notice: SettingsNotice? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: AppError? = null,
    /**
     * Set once the account and its data are actually gone. The screen navigates on this
     * rather than on the tap, because navigating on the tap pops the route, which clears
     * this view model and cancels the very coroutine doing the deletion.
     */
    val deleted: Boolean = false,
) {
    /** Account-only actions are hidden for the on-device diary, which has no account. */
    val canManageAccount: Boolean get() = !isLocalOnly

    val canSaveName: Boolean get() = !isSavingName && displayName.isNotBlank()

    val canSubmitPassword: Boolean
        get() = !isChangingPassword && currentPassword.isNotBlank() &&
            newPassword.isNotBlank() && confirmPassword.isNotBlank()

    fun errorFor(key: String): String? = fieldErrors[key]
}

/**
 * Backing state for the settings screen: profile edits, the password, and the two
 * destructive account actions.
 *
 * Deletion and sign-out live here rather than on the profile screen because this is where a
 * user goes deliberately to change how their account is set up; both are confirmed before
 * they run, and the use cases are the only things that can trigger them.
 */
class SettingsViewModel(
    observeProfile: UserRepository,
    private val userRepository: UserRepository,
    private val changePassword: ChangePasswordUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val userId: String,
    email: String?,
) : BaseViewModel() {

    private val local = MutableStateFlow(LocalSettingsState())

    init { loadPhoto() }

    val state: StateFlow<SettingsState> = combine(
        observeProfile.observeProfile(userId),
        settingsRepository.settings,
        local,
    ) { profile, settings, localState ->
        SettingsState(
            email = email,
            // Only adopt the stored name while the field is untouched, otherwise a sync
            // landing mid-edit would overwrite what the user is typing with the old value.
            displayName = localState.displayName ?: profile?.displayName.orEmpty(),
            photoUrl = profile?.photoUrl,
            language = settings.language,
            isLocalOnly = email.isNullOrBlank(),
            isSavingName = localState.isSavingName,
            isChangingPassword = localState.isChangingPassword,
            isChangingPhoto = localState.isChangingPhoto,
            photoPath = localState.photoPath,
            currentPassword = localState.currentPassword,
            newPassword = localState.newPassword,
            confirmPassword = localState.confirmPassword,
            notice = localState.notice,
            fieldErrors = localState.fieldErrors,
            error = localState.error,
            deleted = localState.deleted,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsState(email = email))

    private data class LocalSettingsState(
        /** Null means "not edited yet", which is what lets the stored name win. */
        val displayName: String? = null,
        val isSavingName: Boolean = false,
        val isChangingPassword: Boolean = false,
        val isChangingPhoto: Boolean = false,
        val photoPath: String? = null,
        val currentPassword: String = "",
        val newPassword: String = "",
        val confirmPassword: String = "",
        val notice: SettingsNotice? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
        val error: AppError? = null,
        val deleted: Boolean = false,
    )

    fun onDisplayNameChanged(value: String) {
        local.update { current ->
            // The length rule is applied as they type rather than on submit, so the field
            // marks itself while the name is still being edited instead of refusing it later.
            val tooLong = value.trim().length > Limits.DISPLAY_NAME_MAX_LENGTH
            current.copy(
                displayName = value,
                fieldErrors = if (tooLong) {
                    current.fieldErrors + (SettingsFieldKeys.NAME to TITLE_TOO_LONG_KEY)
                } else {
                    current.fieldErrors.clear(SettingsFieldKeys.NAME)
                },
                notice = null,
            )
        }
    }

    fun saveDisplayName() {
        if (local.value.isSavingName) return
        val name = local.value.displayName?.trim().orEmpty()
        val validation = Validators.displayName(name)
        if (validation is ValidationResult.Invalid) {
            local.update { it.copy(fieldErrors = validation.errors.toFieldMap()) }
            return
        }
        local.update {
            it.copy(
                isSavingName = true,
                fieldErrors = emptyMap(),
                error = null,
                notice = null,
            )
        }
        launch {
            userRepository.updateDisplayName(userId, name.ifBlank { null })
                .onSuccess {
                    local.update {
                        it.copy(isSavingName = false, notice = SettingsNotice.NAME_SAVED)
                    }
                }
                .onFailure { report(it) }
        }
    }

    fun setLanguage(language: AppLanguage) {
        launch {
            settingsRepository.setLanguage(language)
            applyAppLanguage(language)
        }
    }

    fun onCurrentPasswordChanged(value: String) = local.update {
        it.copy(
            currentPassword = value,
            fieldErrors = it.fieldErrors.clear(SettingsFieldKeys.CURRENT_PASSWORD),
        )
    }

    fun onNewPasswordChanged(value: String) = local.update {
        it.copy(
            newPassword = value,
            fieldErrors = it.fieldErrors.clear(SettingsFieldKeys.NEW_PASSWORD),
        )
    }

    fun onConfirmPasswordChanged(value: String) = local.update {
        it.copy(
            confirmPassword = value,
            fieldErrors = it.fieldErrors.clear(SettingsFieldKeys.CONFIRM_PASSWORD),
        )
    }

    /**
     * Changes the password, refusing the two cases the server would only reject late: a
     * confirmation that does not match, and a new password identical to the current one.
     * Both are checked here so the user gets a per-field message instead of a banner after
     * a round trip, and the second one because Identity Toolkit accepts a re-submitted
     * password, leaving the user thinking they had changed it when they had not.
     */
    fun submitPasswordChange() {
        if (local.value.isChangingPassword) return
        val current = local.value.currentPassword
        val candidate = local.value.newPassword
        val confirm = local.value.confirmPassword

        val problems = buildMap {
            if (candidate != confirm) put(SettingsFieldKeys.CONFIRM_PASSWORD, SettingsFieldKeys.PASSWORD_MISMATCH)
            if (candidate == current && current.isNotEmpty()) {
                put(SettingsFieldKeys.NEW_PASSWORD, SettingsFieldKeys.PASSWORD_UNCHANGED)
            }
        }
        if (problems.isNotEmpty()) {
            local.update { it.copy(fieldErrors = problems) }
            return
        }

        val validation = Validators.password(candidate)
        if (validation is ValidationResult.Invalid) {
            local.update { it.copy(fieldErrors = validation.errors.toPasswordFieldMap()) }
            return
        }

        local.update {
            it.copy(isChangingPassword = true, fieldErrors = emptyMap(), error = null, notice = null)
        }
        launch {
            changePassword(current, candidate)
                .onSuccess {
                    // The three inputs are cleared so the new password cannot be read off the
                    // screen, and so a second submit cannot silently reuse the old one.
                    local.update {
                        it.copy(
                            isChangingPassword = false,
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            notice = SettingsNotice.PASSWORD_CHANGED,
                        )
                    }
                }
                .onFailure { report(it) }
        }
    }

    /**
     * Uploads bytes the picker produced. The cache is written before the network call, so the
     * avatar is on screen the moment this returns even if Storage later refuses it.
     */
    fun uploadPhoto(bytes: ByteArray) {
        if (local.value.isChangingPhoto) return
        local.update { it.copy(isChangingPhoto = true, error = null, notice = null) }
        launch {
            photoRepository.uploadAvatar(userId, bytes)
                .onSuccess { path ->
                    // The avatar is shown before the profile row is touched. A failed profile
                    // write must not hide a photo that is already uploaded and cached, so the
                    // write is not part of the success path this branch reports on.
                    local.update {
                        it.copy(isChangingPhoto = false, photoPath = path, notice = SettingsNotice.PHOTO_UPDATED)
                    }
                    userRepository.updatePhotoUrl(userId, path)
                }
                .onFailure { throwable ->
                    local.update { it.copy(isChangingPhoto = false) }
                    report(throwable)
                }
        }
    }

    /**
     * Removes the photo from Storage *and* from the local cache, in that order.
     *
     * The cache goes last so a failed remote delete leaves the photo showing rather than
     * blanking the avatar for a user whose bytes are still perfectly valid in the bucket.
     */
    /**
     * Fills in the avatar on open. A cache hit needs no network at all, so a returning user
     * sees their photo with no spinner; a miss goes to Storage and still leaves the initials
     * up underneath. Never surfaces an error -- an unreachable bucket must not greet someone
     * with a failure banner just for opening Settings.
     *
     * Runs from [init] and suspends on both repository calls, so a user who picks a photo
     * before it returns would have that photo overwritten by this load: the miss branch writes
     * `null` over the freshly uploaded path and the avatar goes blank again until the next
     * launch. The value is therefore only adopted if it is still the one this load started
     * from, so a later write always wins over an earlier read.
     */
    private fun loadPhoto() {
        val before = local.value.photoPath
        fun adopt(path: String?) {
            local.update { state -> if (state.photoPath == before) state.copy(photoPath = path) else state }
        }
        launch {
            val cached = photoRepository.cachedAvatarPath(userId)
            if (cached != null) {
                adopt(cached)
                return@launch
            }
            photoRepository.refreshAvatar(userId).onSuccess { path -> adopt(path) }
        }
    }

    fun removePhoto() {
        if (local.value.isChangingPhoto) return
        local.update { it.copy(isChangingPhoto = true, error = null) }
        launch {
            photoRepository.removeAvatar(userId)
                .onSuccess {
                    userRepository.updatePhotoUrl(userId, null)
                    local.update {
                        it.copy(
                            isChangingPhoto = false,
                            photoPath = null,
                            notice = SettingsNotice.PHOTO_REMOVED,
                        )
                    }
                }
                .onFailure { throwable ->
                    local.update { it.copy(isChangingPhoto = false) }
                    report(throwable)
                }
        }
    }

    fun signOut() {
        launch {
            logoutUseCase().onFailure { report(it) }
        }
    }

    fun deleteAccount() {
        launch {
            deleteAccountUseCase()
                .onSuccess { local.update { it.copy(deleted = true) } }
                .onFailure { report(it) }
        }
    }

    fun consumeNotice() {
        local.update { it.copy(notice = null) }
    }

    fun dismissError() {
        local.update { it.copy(error = null) }
    }

    private fun report(throwable: Throwable) {
        if (throwable is AuthValidationException) {
            local.update {
                it.copy(isChangingPassword = false, fieldErrors = throwable.errors.toPasswordFieldMap())
            }
        } else {
            local.update { it.copy(isSavingName = false, isChangingPassword = false, error = throwable.toAppError()) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TITLE_TOO_LONG_KEY = "error_title_too_long"
    }
}

private fun ValidationErrors.toFieldMap(): Map<String, String> =
    errors.associate { it.field.key to it.reasonKey }

/**
 * Attributes a shared password failure to the *new* password field.
 *
 * The validator speaks in terms of `Field.PASSWORD`, which is the sign-up form's key. Here
 * the only password the user is being asked for is the replacement, so a "too weak" or
 * "too short" verdict has to land on that field. Mapped to the raw key instead, it would
 * match no field on this screen and the message would be silently dropped.
 */
private fun ValidationErrors.toPasswordFieldMap(): Map<String, String> =
    errors.associate { error ->
        val key = if (error.field == Field.PASSWORD) {
            SettingsFieldKeys.NEW_PASSWORD
        } else {
            error.field.key
        }
        key to error.reasonKey
    }

private fun Map<String, String>.clear(key: String): Map<String, String> = this - key
