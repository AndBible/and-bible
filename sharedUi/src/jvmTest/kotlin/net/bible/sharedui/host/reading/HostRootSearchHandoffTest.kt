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
        HostRootLinks.setActive(true)
        HostRootLinks.setReadingNavigator {
            if (shell.currentRoute.value == NavRoutes.WELCOME) false else { shell.openReading(); true }
        }
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

    @Test fun attachmentUsesExistingLifecycleSnapshot() {
        for (alreadyActive in listOf(false, true)) {
            HostRootLinks.reset()
            HostRootLinks.mainContext = Dispatchers.Unconfined
            val lifecycle = net.bible.sharedui.desktop.assembly.DesktopHostLifecycle()
            if (alreadyActive) lifecycle.onWindowActivated()
            val attachedShell = HostNavShell(CoroutineScope(EmptyCoroutineContext), Navigator())
            attachedShell.routeChanged(NavRoutes.READING)
            val requests = mutableListOf<ReadingSearchRequest>()
            HostRootLinks.attachShell(attachedShell)
            HostRootLinks.attachSearch { requests += it; true }
            val stop = lifecycle.observe(listOf(net.bible.sharedui.assembly.HostLifecycleEvent.Active,
                net.bible.sharedui.assembly.HostLifecycleEvent.ResignActive)) {
                HostRootLinks.setActive(it == net.bible.sharedui.assembly.HostLifecycleEvent.Active)
            }
            try {
                HostRootLinks.setActive(lifecycle.isActive())
                val request = ReadingSearchRequest(query = "snapshot")
                HostRootLinks.openRoute(NavRoutes.readingSearch(request))
                assertEquals(if (alreadyActive) listOf(request) else emptyList(), requests)
                lifecycle.onWindowActivated()
                assertEquals(listOf(request), requests)
            } finally { stop() }
        }
    }

    @Test fun inactiveHostRetainsNewestUntilActivationAndDeliversOnce() {
        HostRootLinks.attachShell(shell)
        HostRootLinks.attachSearch { received += it; true }
        shell.routeChanged(NavRoutes.READING)
        HostRootLinks.setActive(false)
        HostRootLinks.openRoute(NavRoutes.readingSearch(ReadingSearchRequest(query = "old")))
        val newest = ReadingSearchRequest(query = "new")
        HostRootLinks.openRoute(NavRoutes.readingSearch(newest))
        assertTrue(received.isEmpty(), "inactive host must not run a search")
        HostRootLinks.setActive(true)
        HostRootLinks.setActive(true)
        assertEquals(listOf(newest), received)
    }

    @Test fun pendingRequestWaitsForRealWelcomeGateAndUnlock() {
        for (acceptUnlock in listOf(false, true)) {
            HostRootLinks.reset()
            HostRootLinks.mainContext = Dispatchers.Unconfined
            HostRootLinks.setActive(true)
            val nav = Navigator()
            val testShell = HostNavShell(CoroutineScope(Dispatchers.Unconfined), nav)
            val delivered = mutableListOf<ReadingSearchRequest>()
            var usable = false
            var ensured = 0
            var route: String? = NavRoutes.WELCOME
            testShell.routeChanged(route)
            HostRootLinks.attachShell(testShell)
            HostRootLinks.attachSearch { delivered += it; true }
            val gate = net.bible.sharedui.host.app.HostWelcomeReturnGate(
                scope = CoroutineScope(Dispatchers.Unconfined),
                usableBible = { usable },
                unlockIfNeeded = { usable = acceptUnlock },
                ensureReading = { check(usable); ensured++ },
                currentRoute = { route },
                openReading = { route = NavRoutes.READING; testShell.routeChanged(route); HostRootLinks.onDestinationChanged() },
                refreshWelcome = {},
            )
            val request = ReadingSearchRequest(query = "gated")
            HostRootLinks.openRoute(NavRoutes.readingSearch(request))
            assertTrue(nav.routes.isEmpty(), "a request cannot bypass Welcome")
            assertEquals(0, ensured)
            route = NavRoutes.DOWNLOAD_PATTERN
            testShell.routeChanged(route)
            gate.onDestination(route, welcomeOnStack = true)
            route = NavRoutes.WELCOME
            testShell.routeChanged(route)
            gate.onDestination(route, welcomeOnStack = true)
            assertEquals(if (acceptUnlock) 1 else 0, ensured)
            assertEquals(if (acceptUnlock) listOf(request) else emptyList(), delivered)
        }
    }

    @Test fun requestOnWelcomeMustNotBypassUsableBibleGate() {
        shell.routeChanged(NavRoutes.WELCOME)
        HostRootLinks.attachShell(shell)
        HostRootLinks.openRoute(NavRoutes.readingSearch(ReadingSearchRequest(query = "pending")))
        assertTrue(navigator.routes.isEmpty(), "Welcome must remain until its usable-Bible gate succeeds")
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
