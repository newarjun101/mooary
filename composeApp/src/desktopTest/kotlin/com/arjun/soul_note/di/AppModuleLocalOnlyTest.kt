package com.arjun.soul_note.di

import com.arjun.soul_note.core.notifications.PushRegistrar
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * A local-only build has to be able to construct the graph.
 *
 * This is the regression test for the defect that stopped the iOS app from starting. The
 * `FirestoreClient` definition throws when there is no configuration -- deliberately, so a
 * build that cannot reach the network never opens a connection -- and the `PushRegistrar`
 * registration resolved it unconditionally to pass into the registrar factory. The first
 * composition therefore died inside Koin before a single frame was drawn.
 *
 * `get` is the whole point of the test. Koin's structural `verify()` would pass: the
 * `FirestoreClient` bean *is* registered, so the dependency graph is well formed and only
 * running the factory reveals that it throws.
 *
 * The config is overridden rather than merely absent. `loadFirebaseConfig()` reads
 * `~/.diary-app/firebase.properties` and the environment, so on a developer machine that has
 * them the graph is configured and the bug hides -- which is exactly how it reached a build
 * where the offline branch is the only branch. Pinning `RemoteConfig(null)` makes this a
 * local-only build on every machine, which is the configuration that has to keep working.
 */
class AppModuleLocalOnlyTest {

    private fun localOnlyKoin() = koinApplication {
        allowOverride(true)
        modules(
            appModule,
            module { single { RemoteConfig(config = null) } },
        )
    }.koin

    @Test
    fun `push registrar resolves in a build with no Firebase configuration`() {
        assertNotNull(localOnlyKoin().get<PushRegistrar>())
    }

    @Test
    fun `a local-only build reports push as unsupported`() {
        assertFalse(localOnlyKoin().get<PushRegistrar>().isSupported)
    }
}