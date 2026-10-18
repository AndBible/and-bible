package net.bible.sharedui.ios.app

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import net.bible.sharedcore.nav.NavRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class RecordingNavigator : IosNavigator {
    val calls = mutableListOf<String>()
    var popResult = true
    override fun navigate(route: String) { calls += "navigate($route)" }
    override fun popBackStack(): Boolean { calls += "pop"; return popResult }
    override fun popUpTo(route: String, inclusive: Boolean) { calls += "popUpTo($route,$inclusive)" }
}

class IosHostShellTest {
    private val nav = RecordingNavigator()
    private val shell = IosHostShell(CoroutineScope(EmptyCoroutineContext), nav)

    @Test
    fun openReadingPopsWelcome() {
        shell.openReading()
        assertEquals(listOf("popUpTo(${NavRoutes.WELCOME},true)", "navigate(${NavRoutes.READING})"), nav.calls)
    }

    @Test
    fun goBackInHistoryPopsOnce() {
        assertEquals(true, shell.goBackInHistory())
        nav.popResult = false
        assertEquals(false, shell.goBackInHistory())
        assertEquals(listOf("pop", "pop"), nav.calls)
    }

    @Test
    fun historyRouteIsPerOwner() {
        val a = Any()
        val b = Any()
        shell.setHistoryRoute(a, "x")
        shell.setHistoryRoute(b, "y")
        shell.clearHistoryRoute(a)
        assertEquals("y", shell.historyRoute)
        shell.clearHistoryRoute(b)
        assertNull(shell.historyRoute)
    }

    @Test
    fun exitAndRecreateArePendingNotThrowing() {
        shell.exitHost()
        shell.recreateHost()
        shell.setWindowTitle("t")
        assertEquals("t", shell.windowTitle)
    }
}
