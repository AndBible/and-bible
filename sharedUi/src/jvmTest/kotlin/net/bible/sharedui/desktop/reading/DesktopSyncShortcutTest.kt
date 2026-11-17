package net.bible.sharedui.desktop.reading

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.CloudSyncPort
import net.bible.service.cloudsync.SyncNow
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.host.app.HostRootLinks
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercise the real desktop action and SyncNow, stopping only at the cloud/network boundary. */
class DesktopSyncShortcutTest {
    private class Cloud : CloudSyncPort {
        override val signedIn = true
        override val runningChanged = EventSource<Boolean>()
        var starts = 0
        override suspend fun signIn(): Boolean = error("Already signed in")
        override fun start() = CompletableDeferred(true).also { starts++ }
        override suspend fun waitUntilFinished() = Unit
    }

    @Test fun configuredSyncUsesTheMenuSyncNowAction() = syncScenario(configured = true)

    @Test fun unconfiguredSyncOpensSyncSettingsInsteadOfStarting() = syncScenario(configured = false)

    private fun syncScenario(configured: Boolean) = runTest {
        val cloud = Cloud()
        var saves = 0
        val routes = mutableListOf<String>()
        val sync = SyncNow(cloud, EventSource(), { configured }, { saves++ }, { true }, { false }, {}, this)
        val previousContext = HostRootLinks.mainContext
        val shell = HostNavShell(this, object : HostNavigator {
            override fun navigate(route: String) { routes += route }
            override fun popBackStack() = false
            override fun popBackStackTo(route: String, inclusive: Boolean) = false
        })
        shell.routeChanged(NavRoutes.READING)
        HostRootLinks.mainContext = Dispatchers.Unconfined
        HostRootLinks.attachShell(shell)
        startKoin { modules(module { single { sync } }) }
        try {
            val shortcuts = DesktopShortcuts(DesktopShortcutActionsImpl())
            assertTrue(shortcuts.onChord(Chord("s", ctrl = true), null))
            runCurrent()
            assertEquals(if (configured) 1 else 0, saves)
            assertEquals(if (configured) 1 else 0, cloud.starts)
            assertEquals(if (configured) emptyList() else listOf(NavRoutes.SYNC_SETTINGS), routes)

            // The AI prompt editor is a separate destination; its keys must not start synchronization.
            shell.routeChanged(NavRoutes.PROMPT_EDIT_PATTERN)
            assertFalse(shortcuts.onChord(Chord("s", ctrl = true), null))
            runCurrent()
            assertEquals(if (configured) 1 else 0, cloud.starts)
        } finally {
            sync.close()
            stopKoin()
            HostRootLinks.reset()
            HostRootLinks.mainContext = previousContext
        }
    }
}
