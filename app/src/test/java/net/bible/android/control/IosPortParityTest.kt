package net.bible.android.control

import net.bible.sharedui.assembly.IOS_BOUND_PORT_TYPES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.koinApplication

/**
 * Port parity between Android and iOS: every port type [buildPlatformBindings] binds is either bound by
 * `IosPlatformBindings` or `buildIosSyncBindings` (declared in [IOS_BOUND_PORT_TYPES]), or a documented Android-only class. The other way round, iOS binds no port that
 * Android does not. This covers what the JVM discovery in `IosPortContractTest` cannot see (ports a definition looks
 * up lazily), so a port added to the Android bindings without an iOS counterpart fails here.
 *
 * Registering definitions does not run their factories, so no Android context is needed.
 */
@OptIn(KoinInternalApi::class)
class IosPortParityTest {
    /** Android-only: classes of the Android UI/bridge layer, not ports the shared definitions use. */
    private val androidOnly = setOf(
        "net.bible.android.common.resource.ResourceProvider",
        "net.bible.android.common.resource.AndroidResourceProvider",
        "net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton",
        "net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton",
        // concrete helpers behind a bound port (the ports are SyncScheduler, LlmHttpTransport / DownloadHttp)
        "net.bible.android.platform.AndroidSyncScheduler",
        "net.bible.android.platform.AndroidHttpClients",
    )

    private fun bindingKeys(): Set<String> {
        val app = koinApplication { allowOverride(false); modules(buildPlatformBindings()) }
        try {
            return app.koin.instanceRegistry.instances.keys.map { it.substringBefore(':') }.toSet()
        } finally {
            app.close()
        }
    }

    private val iosPorts: Set<String> get() = IOS_BOUND_PORT_TYPES.map { it.qualifiedName!! }.toSet()

    @Test
    fun everyAndroidPortHasAnIosBinding() {
        val missing = bindingKeys() - androidOnly - iosPorts
        assertEquals("Android binds ports that iOS does not bind (add them to IosPlatformBindings and IOS_BOUND_PORT_TYPES)",
            emptySet<String>(), missing)
    }

    @Test
    fun iosBindsNoPortThatAndroidDoesNot() {
        val extra = iosPorts - bindingKeys()
        assertEquals("iOS declares ports that Android does not bind", emptySet<String>(), extra)
    }

    @Test
    fun theCheckSeesRealBindings() {
        val keys = bindingKeys()
        assertTrue("only ${keys.size} Android bindings found: $keys", keys.size > 40)
        assertTrue(keys.contains("net.bible.sharedcore.platform.AppSettings"))
    }
}
