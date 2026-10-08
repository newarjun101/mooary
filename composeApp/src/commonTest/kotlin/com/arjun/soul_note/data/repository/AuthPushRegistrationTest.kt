package com.arjun.soul_note.data.repository

import com.arjun.soul_note.core.notifications.PushRegistrar
import com.arjun.soul_note.core.notifications.RecordingLogger
import com.arjun.soul_note.data.remote.RemoteAuthDataSource
import com.arjun.soul_note.data.remote.RemoteIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Tests for the push-registration hook on session changes.
 *
 * The behaviour worth pinning is the negative space: signing in must succeed whether or not
 * push works. A login screen that can be blocked by a messaging SDK is worse than no push at
 * all, and on a build with the flag off — which is the default — the SDK is not there to block
 * anything in the first place.
 */
class AuthPushRegistrationTest {

    private val identity = RemoteIdentity(
        userId = "user-1",
        email = "reader@example.invalid",
        emailVerified = true,
        displayName = null,
        photoUrl = null,
        createdAtEpochMillis = 0L,
    )

    private fun remote() = object : RemoteAuthDataSource {
        override suspend fun signIn(email: String, password: String) = Result.success(identity)
        override suspend fun signUp(email: String, password: String) = Result.success(identity)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun resetPassword(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession() = Result.success(identity)
        override suspend fun currentUserId(): String? = "user-1"
        override suspend fun idToken(): String? = "id-token"
        override suspend fun refreshSession() = Result.success(identity)
        override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)
        override suspend fun changePassword(current: String, next: String) = Result.success(Unit)
    }

    private fun registrar(
        supported: Boolean = true,
        result: Result<Unit> = Result.success(Unit),
        calls: MutableList<String>,
    ) = object : PushRegistrar {
        override val isSupported = supported
        override suspend fun register(userId: String): Result<Unit> {
            calls += userId
            return result
        }
    }

    private fun repository(
        push: PushRegistrar?,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = DefaultAuthRepository(
        remote = remote(),
        push = push,
        logger = RecordingLogger(),
        scope = scope,
    )

    @Test
    fun `signing in registers the device`() = runTest {
        val calls = mutableListOf<String>()
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))

        repository(registrar(calls = calls), scope).signIn("a@b.invalid", "pw").getOrThrow()

        assertEquals(listOf("user-1"), calls)
    }

    @Test
    fun `a restored session registers too`() = runTest {
        val calls = mutableListOf<String>()
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))

        // The returning-user case: no password is typed, so this is the only chance to register.
        repository(registrar(calls = calls), scope).restoreSession()

        assertEquals(listOf("user-1"), calls)
    }

    @Test
    fun `signing in succeeds when there is no transport`() = runTest {
        val calls = mutableListOf<String>()
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val push = registrar(supported = false, calls = calls)

        val result = repository(push, scope).signIn("a@b.invalid", "pw")

        assertTrue(result.isSuccess, "an unsupported platform must not fail sign-in")
        assertTrue(calls.isEmpty(), "an unsupported registrar should not be called at all")
    }

    @Test
    fun `signing in succeeds when push registration fails`() = runTest {
        val calls = mutableListOf<String>()
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val push = registrar(result = Result.failure(IllegalStateException()), calls = calls)

        val result = repository(push, scope).signIn("a@b.invalid", "pw")

        assertTrue(result.isSuccess, "push must not be able to fail a sign-in")
        assertEquals("user-1", result.getOrThrow().id)
        assertEquals(listOf("user-1"), calls, "it should still have been attempted")
    }

    @Test
    fun `a build with no registrar at all still signs in`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))

        val result = repository(push = null, scope = scope).signIn("a@b.invalid", "pw")

        assertTrue(result.isSuccess, "the default build has no push module and must not care")
        assertEquals("user-1", result.getOrThrow().id)
    }
}
