package net.bible.sharedui.host.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.search.ReadingSearchRequest
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.host.app.HostRootLinks
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostRootSearchHandoffTest {
    private class Navigator : HostNavigator {
        val routes = mutableListOf<String>()
        override fun navigate(route: String) { routes += route }
        override fun popBackStack() = false
        override fun popBackStackTo(route: String, inclusive: Boolean) = false
    }

    private val navigator = Navigator()
    private val shell = HostNavShell(CoroutineScope(EmptyCoroutineContext), navigator)
    private val received = mutableListOf<ReadingSearchRequest>()

    @BeforeTest fun setup() {
        HostRootLinks.reset()
        HostRootLinks.mainContext = Dispatchers.Unconfined
    }

    @AfterTest fun cleanup() {
        HostRootLinks.reset()
        HostRootLinks.mainContext = Dispatchers.Main
    }

    @Test fun requestBeforeShellAndHostIsRetained() {
        val request = ReadingSearchRequest(query = "early")
        HostRootLinks.openRoute(NavRoutes.readingSearch(request))
        shell.routeChanged(NavRoutes.SETTINGS)
        HostRootLinks.attachShell(shell)
        assertEquals(NavRoutes.READING, navigator.routes.last())
        shell.routeChanged(NavRoutes.READING)
        HostRootLinks.attachSearch { received += it; true }
        HostRootLinks.deliverPendingSearch()
        assertEquals(listOf(request), received)
    }

    @Test fun hiddenHostReceivesOnlyNewestAfterReadingBecomesCurrent() {
        HostRootLinks.attachShell(shell)
        HostRootLinks.attachSearch { received += it; true }
        shell.routeChanged(NavRoutes.SETTINGS)
        HostRootLinks.openRoute(NavRoutes.readingSearch(ReadingSearchRequest(query = "old")))
        val latest = ReadingSearchRequest(query = "new")
        HostRootLinks.openRoute(NavRoutes.readingSearch(latest))
        assertTrue(received.isEmpty())
        shell.routeChanged(NavRoutes.READING)
        HostRootLinks.deliverPendingSearch()
        HostRootLinks.deliverPendingSearch()
        assertEquals(listOf(latest), received)
    }
}
