package net.bible.sharedui.ios.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.sharedcore.nav.NavRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unconfined scope: a launched check runs eagerly up to its first real suspension and resumes inline, deterministically. */
class IosWelcomeReturnGateTest {
    private class Fixture(var usable: Boolean = true, var ensureGate: CompletableDeferred<Unit>? = null) {
        var route: String? = NavRoutes.WELCOME
        var opened = 0
        var refreshed = 0
        var ensured = 0
        val gate = IosWelcomeReturnGate(
            scope = CoroutineScope(Dispatchers.Unconfined),
            usableBible = { usable },
            ensureReading = { ensured++; ensureGate?.await() },
            currentRoute = { route },
            openReading = { opened++; route = NavRoutes.READING },
            refreshWelcome = { refreshed++ },
        )
        /** A flow leaves Welcome (Download above it) and later pops back. */
        fun leave() { route = "download"; gate.onDestination(route, welcomeOnStack = true) }
        fun back() { route = NavRoutes.WELCOME; gate.onDestination(route, welcomeOnStack = true) }
    }

    @Test
    fun returnWithUsableBibleOpensReadingOnce() {
        val f = Fixture()
        f.leave(); f.back()
        assertEquals(1, f.opened); assertEquals(1, f.ensured); assertEquals(0, f.refreshed)
    }

    @Test
    fun returnWithoutUsableBibleStaysAndRefreshes() {
        val f = Fixture(usable = false)
        f.leave(); f.back()
        assertEquals(0, f.opened); assertEquals(0, f.ensured); assertEquals(1, f.refreshed)
    }

    @Test
    fun firstArrivalAtWelcomeDoesNothing() {
        val f = Fixture()
        f.gate.onDestination(NavRoutes.WELCOME, welcomeOnStack = true)
        assertFalse(f.gate.awaitingReturn)
        assertEquals(0, f.opened + f.refreshed + f.ensured)
    }

    @Test
    fun overlappingReturnsPushOnlyOneReading() {
        val f = Fixture(ensureGate = CompletableDeferred())
        f.leave(); f.back();   // first check suspended in ensure
        f.leave(); f.back();   // second return while the first is still running
        f.ensureGate!!.complete(Unit)
        assertEquals(1, f.opened, "the second check finds READING current and does not navigate")
    }

    @Test
    fun leavingToAnotherDestinationDuringEnsureCancelsTheOpen() {
        val f = Fixture(ensureGate = CompletableDeferred())
        f.leave(); f.back()
        f.route = "download"   // user opened Download again while ensure ran
        f.ensureGate!!.complete(Unit)
        assertEquals(0, f.opened)
        assertTrue(f.gate.awaitingReturn.not())
    }
}
