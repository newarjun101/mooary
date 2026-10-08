package com.arjun.soul_note.ui.settings

import com.arjun.soul_note.core.analytics.AnalyticsEventCall
import com.arjun.soul_note.core.analytics.AnalyticsSink
import com.arjun.soul_note.core.analytics.AnalyticsTracker
import com.arjun.soul_note.domain.model.User
import com.arjun.soul_note.domain.model.UserProfile
import com.arjun.soul_note.domain.repository.AuthRepository
import com.arjun.soul_note.domain.repository.PhotoRepository
import com.arjun.soul_note.domain.repository.UserRepository
import com.arjun.soul_note.domain.usecase.auth.ChangePasswordUseCase
import com.arjun.soul_note.domain.usecase.auth.DeleteAccountUseCase
import com.arjun.soul_note.domain.usecase.auth.LogoutUseCase
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The settings screen is the one place a user goes to change their account, so the two ways
 * it can quietly go wrong are worth pinning: it must not report success for a password it
 * did not change, and it must not leave a failure where the user cannot see it.
 *
 * The display-name field has one non-obvious rule of its own, tested below: a sync landing
 * mid-edit must not overwrite what the user is typing.
 */
class SettingsViewModelTest {

    private val now = Instant.parse("2024-04-01T12:00:00Z")

    private val userId = "user-1"

    private class FakeUserRepository(
        var stored: UserProfile? = null,
    ) : UserRepository {
        var savedName: String? = null
        var saveCount = 0
        var failWith: Throwable? = null
        val profile = MutableStateFlow<UserProfile?>(null)

        init {
            profile.value = stored
        }

        override fun observeProfile(userId: String): Flow<UserProfile?> = profile

        override suspend fun getProfile(userId: String): Result<UserProfile?> =
            Result.success(profile.value)

        override suspend fun upsertProfile(profile: UserProfile): Result<Unit> {
            this.profile.value = profile
            return Result.success(Unit)
        }

        override suspend fun updateDisplayName(userId: String, displayName: String?): Result<Unit> {
            failWith?.let { return Result.failure(it) }
            savedName = displayName
            saveCount++
            return Result.success(Unit)
        }

        override suspend fun updatePhotoUrl(userId: String, photoUrl: String?): Result<Unit> {
            failWith?.let { return Result.failure(it) }
            profile.value = profile.value?.copy(photoUrl = photoUrl)
            return Result.success(Unit)
        }

        override suspend fun deleteAllUserData(userId: String): Result<Unit> = Result.success(Unit)

        override suspend fun purgeLocalData(userId: String) = Unit
    }

    /** Only the four calls this screen makes are implemented; the rest are unreachable. */
    private class FakeAuthRepository(
        private val userId: String,
        var failWith: Throwable? = null,
    ) : AuthRepository {
        var changedTo: String? = null
        var signedOut = false
        var deleted = false

        override suspend fun signOut(): Result<Unit> {
            failWith?.let { return Result.failure(it) }
            signedOut = true
            return Result.success(Unit)
        }

        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
            failWith?.let { return Result.failure(it) }
            changedTo = newPassword
            return Result.success(Unit)
        }

        override suspend fun deleteAccount(): Result<Unit> {
            failWith?.let { return Result.failure(it) }
            deleted = true
            return Result.success(Unit)
        }

        override fun currentUserId(): String? = userId

        override suspend fun signIn(email: String, password: String): Result<User> = unsupported()
        override suspend fun signUp(email: String, password: String): Result<User> = unsupported()
        override suspend fun resetPassword(email: String): Result<Unit> = unsupported()
        override suspend fun sendEmailVerification(): Result<Unit> = unsupported()
        override suspend fun refreshUser(): Result<User> = unsupported()
        override fun observeAuthState(): Flow<User?> = MutableStateFlow(null)
        override suspend fun restoreSession(): User? = null

        private fun <T> unsupported(): Result<T> =
            Result.failure(UnsupportedOperationException("not used by the settings screen"))
    }

    private fun analytics() = AnalyticsTracker(
        object : AnalyticsSink {
            override fun send(call: AnalyticsEventCall) = Unit
        },
    )

    /**
     * Builds the view model with a live collector attached.
     *
     * [SettingsViewModel.state] is a `WhileSubscribed` flow, so with nothing collecting it
     * the combination never runs and every `state.value` read returns the initial value --
     * which would make these tests pass or fail for reasons unrelated to the behaviour. The
     * unconfined dispatcher lets the combination run eagerly on each update, so a value
     * read straight after an action reflects that action.
     */
    private class FakeSettingsRepository(
        initial: com.arjun.soul_note.domain.repository.AppSettings =
            com.arjun.soul_note.domain.repository.AppSettings(),
    ) : com.arjun.soul_note.domain.repository.SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: Flow<com.arjun.soul_note.domain.repository.AppSettings> = state
        override suspend fun current(): com.arjun.soul_note.domain.repository.AppSettings = state.value
        override suspend fun setThemeMode(mode: com.arjun.soul_note.domain.repository.ThemeMode) {
            state.value = state.value.copy(themeMode = mode)
        }
        override suspend fun setLanguage(language: com.arjun.soul_note.domain.repository.AppLanguage) {
            state.value = state.value.copy(language = language)
        }
        override suspend fun setRemindersEnabled(enabled: Boolean) {}
        override suspend fun setReminderHours(diaryHour: Int, moodHour: Int) {}
        override suspend fun markOnboardingSeen() {}
        override suspend fun clear() {}
    }

    private fun TestScope.viewModel(
        repository: FakeUserRepository = FakeUserRepository(profile("Ada")),
        auth: FakeAuthRepository = FakeAuthRepository(userId),
        email: String? = "ada@example.com",
        photos: FakePhotoRepository = FakePhotoRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    ): SettingsViewModel {
        val model = SettingsViewModel(
            observeProfile = repository,
            userRepository = repository,
            changePassword = ChangePasswordUseCase(auth),
            logoutUseCase = LogoutUseCase(auth, repository, analytics()),
            deleteAccountUseCase = DeleteAccountUseCase(auth, repository, analytics()),
            photoRepository = photos,
            settingsRepository = settingsRepo,
            userId = userId,
            email = email,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { }
        }
        return model
    }

    /**
     * Records what Settings asked for rather than touching Storage, so the tests can assert
     * the ordering that matters: the cache survives a failed remote delete.
     */
    private class FakePhotoRepository(
        private val cached: String? = null,
        private val uploadResult: Result<String> = Result.success("/cache/ada.png"),
        private val removeResult: Result<Unit> = Result.success(Unit),
        private val gateOnRefresh: Boolean = false,
    ) : PhotoRepository {
        /** Held until [releaseRefresh], so a test can decide when the load comes back. */
        private val refreshGate = CompletableDeferred<Unit>()
        var removedFor: String? = null
        var uploadedFor: String? = null
        var uploadedBytes: ByteArray? = null

        override suspend fun cachedAvatarPath(userId: String): String? = cached

        override suspend fun refreshAvatar(userId: String): Result<String?> {
            if (gateOnRefresh) refreshGate.await()
            return Result.success(cached)
        }

        suspend fun releaseRefresh() {
            refreshGate.complete(Unit)
        }

        override suspend fun uploadAvatar(userId: String, bytes: ByteArray): Result<String> {
            uploadedFor = userId
            uploadedBytes = bytes
            return uploadResult
        }

        override suspend fun removeAvatar(userId: String): Result<Unit> {
            removedFor = userId
            return removeResult
        }
    }

    private fun profile(name: String?) = UserProfile(
        id = userId,
        email = "ada@example.com",
        displayName = name,
        photoUrl = null,
        createdAt = now,
        updatedAt = now,
    )

    /**
     * Waits for the state to satisfy [predicate].
     *
     * `BaseViewModel` runs its scope on the default dispatcher rather than the test
     * scheduler, so the `combine` behind [SettingsViewModel.state] completes on another
     * thread. Reading `state.value` straight after an action therefore races the
     * combination, and these tests would be asserting on a stale value. Awaiting the
     * specific state each test cares about is what makes them deterministic.
     */
    private suspend fun await(
        model: SettingsViewModel,
        predicate: (SettingsState) -> Boolean,
    ): SettingsState = model.state.first(predicate)

    @Test
    fun `the stored name is shown until the user edits it`() = runTest {
        val model = viewModel()

        assertEquals("Ada", await(model) { it.displayName == "Ada" }.displayName)
    }

    @Test
    fun `a sync landing mid-edit does not overwrite what is being typed`() = runTest {
        val repository = FakeUserRepository(profile("Ada"))
        val model = viewModel(repository = repository)

        model.onDisplayNameChanged("Adalovelace")
        // A pull lands while the field is dirty. It carries a photo, so waiting for that is
        // a signal the combination really did re-run against the new profile -- otherwise
        // this would pass on a stale value and prove nothing.
        repository.profile.value = profile("Ada").copy(photoUrl = "/cache/ada.png")

        await(model) { it.photoUrl != null }
        assertEquals("Adalovelace", model.state.value.displayName)
    }

    @Test
    fun `saving the name stores the trimmed value`() = runTest {
        val repository = FakeUserRepository(profile("Ada"))
        val model = viewModel(repository = repository)

        model.onDisplayNameChanged("  Ada Lovelace  ")
        model.saveDisplayName()

        await(model) { it.notice == SettingsNotice.NAME_SAVED }
        assertEquals("Ada Lovelace", repository.savedName)
    }

    @Test
    fun `a blank name is stored as no name at all`() = runTest {
        val repository = FakeUserRepository(profile("Ada"))
        val model = viewModel(repository = repository)

        model.onDisplayNameChanged("   ")
        model.saveDisplayName()

        await(model) { it.notice == SettingsNotice.NAME_SAVED }
        assertNull(repository.savedName)
    }

    @Test
    fun `a failed name save is reported rather than acknowledged`() = runTest {
        val repository = FakeUserRepository(profile("Ada")).apply {
            failWith = IllegalStateException("offline")
        }
        val model = viewModel(repository = repository)

        model.onDisplayNameChanged("Ada")
        model.saveDisplayName()

        val state = await(model) { it.error != null }
        assertNull(state.notice, "a failed save must not report success")
    }

    @Test
    fun `a name longer than the limit is refused before it is sent`() = runTest {
        val repository = FakeUserRepository(profile("Ada"))
        val model = viewModel(repository = repository)

        model.onDisplayNameChanged("x".repeat(81))
        assertEquals(
            "error_title_too_long",
            await(model) { it.errorFor(SettingsFieldKeys.NAME) != null }
                .errorFor(SettingsFieldKeys.NAME),
        )

        model.saveDisplayName()
        assertEquals(0, repository.saveCount, "an over-long name must never be stored")
    }

    @Test
    fun `a confirmation that does not match is refused`() = runTest {
        val auth = FakeAuthRepository(userId)
        val model = viewModel(auth = auth)

        model.onCurrentPasswordChanged("old-password")
        model.onNewPasswordChanged("new-password")
        model.onConfirmPasswordChanged("new-password-typo")
        model.submitPasswordChange()

        assertEquals(
            SettingsFieldKeys.PASSWORD_MISMATCH,
            await(model) { it.errorFor(SettingsFieldKeys.CONFIRM_PASSWORD) != null }
                .errorFor(SettingsFieldKeys.CONFIRM_PASSWORD),
        )
        assertNull(auth.changedTo)
    }

    @Test
    fun `a new password identical to the old one is refused`() = runTest {
        val auth = FakeAuthRepository(userId)
        val model = viewModel(auth = auth)

        model.onCurrentPasswordChanged("same-password")
        model.onNewPasswordChanged("same-password")
        model.onConfirmPasswordChanged("same-password")
        model.submitPasswordChange()

        assertEquals(
            SettingsFieldKeys.PASSWORD_UNCHANGED,
            await(model) { it.errorFor(SettingsFieldKeys.NEW_PASSWORD) != null }
                .errorFor(SettingsFieldKeys.NEW_PASSWORD),
        )
        assertNull(auth.changedTo)
    }

    @Test
    fun `a too short password is refused by the shared validator`() = runTest {
        val auth = FakeAuthRepository(userId)
        val model = viewModel(auth = auth)

        model.onCurrentPasswordChanged("old-password")
        model.onNewPasswordChanged("ab")
        model.onConfirmPasswordChanged("ab")
        model.submitPasswordChange()

        await(model) { it.errorFor(SettingsFieldKeys.NEW_PASSWORD) != null }
        assertNull(auth.changedTo)
    }

    @Test
    fun `changing the password clears the inputs so the new one is not left on screen`() = runTest {
        val auth = FakeAuthRepository(userId)
        val model = viewModel(auth = auth)

        model.onCurrentPasswordChanged("old-password")
        model.onNewPasswordChanged("brand-new-password")
        model.onConfirmPasswordChanged("brand-new-password")
        model.submitPasswordChange()

        val state = await(model) { it.notice == SettingsNotice.PASSWORD_CHANGED }
        assertEquals("brand-new-password", auth.changedTo)
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
    }

    @Test
    fun `a rejected password change is reported and the inputs are kept for another try`() = runTest {
        val auth = FakeAuthRepository(userId).apply {
            failWith = IllegalStateException("wrong password")
        }
        val model = viewModel(auth = auth)

        model.onCurrentPasswordChanged("wrong-password")
        model.onNewPasswordChanged("brand-new-password")
        model.onConfirmPasswordChanged("brand-new-password")
        model.submitPasswordChange()

        val state = await(model) { it.error != null }
        assertNull(state.notice, "a rejected change must not report success")
        assertEquals(
            "brand-new-password",
            state.newPassword,
            "the user should not have to retype it",
        )
    }

    @Test
    fun `account actions are hidden for the on-device diary`() = runTest {
        val model = viewModel(email = null)

        assertFalse(await(model) { !it.canManageAccount }.canManageAccount)
    }

    @Test
    fun `account actions are offered once there is an account`() = runTest {
        val model = viewModel()

        assertTrue(await(model) { it.canManageAccount }.canManageAccount)
    }

    @Test
    fun `the screen is only told the account is gone after it actually is`() = runTest {
        val auth = FakeAuthRepository(userId)
        val model = viewModel(auth = auth)

        model.deleteAccount()

        assertTrue(await(model) { it.deleted }.deleted)
    }

    @Test
    fun `a failed deletion does not report the account as gone`() = runTest {
        val auth = FakeAuthRepository(userId).apply {
            failWith = IllegalStateException("no")
        }
        val model = viewModel(auth = auth)

        model.deleteAccount()

        val state = await(model) { it.error != null }
        assertFalse(
            state.deleted,
            "navigating on a failed delete would strand the account behind a signed-in screen",
        )
    }

    @Test
    fun `removing the photo clears it and says so`() = runTest {
        val repository = FakeUserRepository(profile("Ada")).apply {
            profile.value = profile("Ada").copy(photoUrl = "/cache/ada.png")
        }
        val model = viewModel(repository = repository)

        model.removePhoto()

        await(model) { it.notice == SettingsNotice.PHOTO_REMOVED }
        assertNull(repository.profile.value?.photoUrl)
    }

    @Test
    fun `initials use at most two letters`() {
        assertEquals("AL", "Ada Lovelace".initials())
        assertEquals("A", "Ada".initials())
        assertEquals("?", "".initials())
    }

    @Test
    fun `uploading a photo points the avatar at the cached file`() = runTest {
        val photos = FakePhotoRepository()
        val model = viewModel(photos = photos)
        model.uploadPhoto(byteArrayOf(1, 2, 3))

        await(model) { it.photoPath == "/cache/ada.png" }
        assertEquals(userId, photos.uploadedFor)
        assertContentEquals(byteArrayOf(1, 2, 3), photos.uploadedBytes)
        assertEquals("/cache/ada.png", model.state.value.photoPath)
    }

    @Test
    fun `a photo picked before the load returns is not overwritten by it`() = runTest {
        // The load launched from init suspends on Storage, and a user who picks a photo while
        // it is in flight used to have that photo replaced by the load's answer -- which for a
        // cache miss is null, so the avatar went blank again until the next launch.
        val photos = FakePhotoRepository(gateOnRefresh = true)
        val model = viewModel(photos = photos)
        model.uploadPhoto(byteArrayOf(1, 2, 3))
        await(model) { it.photoPath == "/cache/ada.png" }

        photos.releaseRefresh()
        // The load resumes on the test scheduler, so the state has to be settled before it is
        // read; without this the assertion passes whether or not the load clobbered anything.
        advanceUntilIdle()

        assertEquals("/cache/ada.png", model.state.value.photoPath)
    }

    @Test
    fun `a photo already in the cache shows without a download`() = runTest {
        val model = viewModel(photos = FakePhotoRepository(cached = "/cache/old.png"))

        assertEquals("/cache/old.png", await(model) { it.photoPath != null }.photoPath)
    }

    @Test
    fun `removing a photo deletes the bytes in Storage`() = runTest {
        val photos = FakePhotoRepository()
        val model = viewModel(photos = photos)
        model.removePhoto()

        val settled = await(model) { it.notice == SettingsNotice.PHOTO_REMOVED }
        assertEquals(userId, photos.removedFor)
        assertNull(settled.photoPath)
    }

    @Test
    fun `a failed delete keeps the photo on screen`() = runTest {
        // Storage being unreachable is not a reason to blank an avatar the user still has.
        val photos = FakePhotoRepository(
            cached = "/cache/old.png",
            removeResult = Result.failure(RuntimeException("storage down")),
        )
        val model = viewModel(photos = photos)
        model.removePhoto()

        val settled = await(model) { it.error != null }
        assertEquals("/cache/old.png", settled.photoPath)
        assertFalse(settled.isChangingPhoto)
    }

    @Test
    fun `a failed upload stops the spinner and reports the error`() = runTest {
        val photos = FakePhotoRepository(uploadResult = Result.failure(RuntimeException("too big")))
        val model = viewModel(photos = photos)
        model.uploadPhoto(byteArrayOf(1))

        val settled = await(model) { it.error != null }
        assertFalse(settled.isChangingPhoto)
        assertNotNull(settled.error)
    }
}
