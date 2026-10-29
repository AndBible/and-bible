/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.ios.app

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import net.bible.sharedcore.nav.NavRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.bible.sharedui.host.app.IosHostShell
import net.bible.sharedui.host.app.IosNavigator
import net.bible.sharedui.host.app.iosExitWithResult

private class RecordingNavigator : IosNavigator {
    val calls = mutableListOf<String>()
    var popResult = true
    /** Routes currently "on the stack" for [popBackStackTo]. */
    val stack = mutableSetOf<String>()
    override fun navigate(route: String) { calls += "navigate($route)" }
    override fun popBackStack(): Boolean { calls += "pop"; return popResult }
    override fun popBackStackTo(route: String, inclusive: Boolean): Boolean {
        calls += "popTo($route,$inclusive)"
        return stack.remove(route)
    }
}

class IosHostShellTest {
    private val nav = RecordingNavigator()
    private val shell = IosHostShell(CoroutineScope(EmptyCoroutineContext), nav)

    private val readingReuse = "popTo(${NavRoutes.READING},false)"
    private val popDownload = "popTo(${NavRoutes.DOWNLOAD_PATTERN},true)"
    private val popWelcome = "popTo(${NavRoutes.WELCOME},true)"

    @Test
    fun recreationRefreshesLanguageWithoutReplacingNavigationOrHistory() {
        var refreshes = 0
        val liveShell = IosHostShell(CoroutineScope(EmptyCoroutineContext), nav) { refreshes++ }
        val owner = Any()
        liveShell.setHistoryRoute(owner, "history")
        nav.stack += NavRoutes.READING
        liveShell.recreateHost()
        assertEquals(1, refreshes)
        assertEquals("history", liveShell.historyRoute)
        assertEquals(setOf(NavRoutes.READING), nav.stack)
        assertEquals(emptyList(), nav.calls)
    }

    @Test
    fun openReadingFromWelcomePopsWelcome() {
        nav.stack += NavRoutes.WELCOME
        shell.openReading()
        assertEquals(listOf(readingReuse, popDownload, popWelcome, "navigate(${NavRoutes.READING})"), nav.calls)
        assertEquals(setOf<String>(), nav.stack)
    }

    @Test
    fun openReadingFromDownloadPopsDownloadAndWelcome() {
        nav.stack += listOf(NavRoutes.WELCOME, NavRoutes.DOWNLOAD_PATTERN)
        shell.openReading()
        assertEquals(listOf(readingReuse, popDownload, popWelcome, "navigate(${NavRoutes.READING})"), nav.calls)
        assertEquals(setOf<String>(), nav.stack)
    }

    @Test
    fun openReadingReusesReadingAlreadyOnTheStack() {
        nav.stack += NavRoutes.READING
        shell.openReading()
        assertEquals(listOf(readingReuse), nav.calls)
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
    fun recoveryBackupExitReturnsWithoutNavigatingToReading() {
        var returned = 0
        val recoveryShell = IosHostShell(CoroutineScope(EmptyCoroutineContext), nav, onExit = { returned++ })
        recoveryShell.exitHost()
        assertEquals(1, returned)
        assertEquals(emptyList(), nav.calls)
    }

    @Test
    fun recreateAndTitleDoNotNavigate() {
        shell.recreateHost()
        shell.setWindowTitle("t")
        assertEquals("t", shell.windowTitle)
        assertEquals(emptyList(), nav.calls)
    }

    @Test
    fun exitHostWithoutHandlerReturnsToReading() {
        nav.stack += NavRoutes.READING
        shell.exitHost()
        assertEquals(listOf(readingReuse), nav.calls)
        nav.calls.clear()
        shell.exitHost() // no reading entry any more: the first-run path opens a fresh one
        assertEquals(listOf(readingReuse, popDownload, popWelcome, "navigate(${NavRoutes.READING})"), nav.calls)
    }

    @Test
    fun resultChannelFallbackPops() {
        var opened = 0
        iosExitWithResult<String>(nav) { opened++ }("answer")
        assertEquals(listOf("pop"), nav.calls)
        assertEquals(0, opened)
    }

    @Test
    fun resultChannelFallbackOpensReadingWhenThereIsNothingToPop() {
        nav.popResult = false
        var opened = 0
        iosExitWithResult<String>(nav) { opened++; shell.openReading() }("answer")
        assertEquals(listOf("pop", readingReuse, popDownload, popWelcome, "navigate(${NavRoutes.READING})"), nav.calls)
        assertEquals(1, opened)
    }
}
