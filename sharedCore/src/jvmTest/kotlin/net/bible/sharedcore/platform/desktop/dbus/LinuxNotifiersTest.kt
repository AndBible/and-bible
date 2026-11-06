package net.bible.sharedcore.platform.desktop.dbus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedcore.log.Log
import org.junit.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LinuxNotifiersTest {
    private fun noBus(os: String) = object : DesktopDbus(os) {
        override fun connect() = error("no bus in this test")
    }

    @Test
    fun factoryPicksPortalUnderFlatpakFreedesktopOtherwiseNoOpOnWindows() {
        val scope = CoroutineScope(SupervisorJob())
        val created = mutableListOf<Any>()
        Log.sinkOverride = { _, _, _, _ -> }
        try {
            fun make(os: String, env: Map<String, String>, info: Boolean = false) =
                linuxNativeNotifier(noBus(os), { env[it] }, scope, { info }).also { created.add(it) }

            assertTrue(make("Linux", mapOf("FLATPAK_ID" to "org.andbible.AndBible")) is PortalNotifier)
            assertTrue(make("Linux", emptyMap(), info = true) is PortalNotifier)
            assertTrue(make("Linux", emptyMap()) is FreedesktopNotifier)
            assertTrue(make("Linux", mapOf("FLATPAK_ID" to "")) is FreedesktopNotifier)
            assertSame(NoOpNativeNotifier, make("Windows 11", emptyMap()))
            assertSame(NoOpNativeNotifier, make("Mac OS X", mapOf("FLATPAK_ID" to "x")))
        } finally {
            created.filterIsInstance<AutoCloseable>().forEach { it.close() }
            scope.cancel()
            Log.sinkOverride = null
        }
    }
}
