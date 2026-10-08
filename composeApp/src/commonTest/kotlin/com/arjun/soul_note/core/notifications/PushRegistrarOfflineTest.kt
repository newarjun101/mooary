package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.core.logging.NoOpLogger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The registrar must be constructible on a build that has no Firestore client.
 *
 * `createPushRegistrar` takes a nullable client because "no transport" and "no Firebase
 * configuration" are independent, and iOS has the first while a local-only build has the
 * second. When it took a non-null client the Koin registration resolved one eagerly, the
 * `FirestoreClient` definition refused to build without a config, and the app died during the
 * first composition -- on the one platform that could never have used the client anyway.
 *
 * The assertion is deliberately transport-agnostic: with no client the answer is unsupported
 * whether or not `createPushTokenSource()` happens to return something, so this holds on a
 * default build, an Android build with messaging switched on, and an iOS build alike.
 */
class PushRegistrarOfflineTest {

    @Test
    fun `a build with no Firestore client reports push as unsupported`() {
        val registrar = createPushRegistrar(firestore = null, logger = NoOpLogger)

        assertFalse(registrar.isSupported)
        assertIs<UnsupportedPushRegistrar>(registrar)
    }

    @Test
    fun `registering without a Firestore client fails as unsupported instead of throwing`() = runTest {
        val registrar = createPushRegistrar(firestore = null, logger = NoOpLogger)

        // The contract is a typed failure, not an exception out of the DI graph: a caller that
        // asks to register gets a Result it can log, instead of a crash at composition.
        val result = registrar.register(userId = "user-1")

        assertTrue(result.isFailure)
        assertIs<UnsupportedOperationException>(result.exceptionOrNull())
    }
}