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
import kotlinx.coroutines.Dispatchers
import net.bible.sharedcore.nav.NavRoutes
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.host.app.HostRootLinks

/** The ports that reach the shared root through [HostRootLinks]: speak, history and link handling. */
class IosRootLinksTest {
    private class Nav : HostNavigator {
        val calls = mutableListOf<String>()
        val stack = mutableSetOf<String>()
        override fun navigate(route: String) { calls += "navigate($route)" }
        override fun popBackStack(): Boolean { calls += "pop"; return true }
        override fun popBackStackTo(route: String, inclusive: Boolean): Boolean {
            calls += "popTo($route,$inclusive)"
            return stack.remove(route)
        }
    }

    private val nav = Nav()
    private val shell = HostNavShell(CoroutineScope(EmptyCoroutineContext), nav)

    @BeforeTest fun attach() {
        HostRootLinks.mainContext = Dispatchers.Unconfined
        HostRootLinks.attachShell(shell)
    }

    @AfterTest fun detach() {
        HostRootLinks.reset()
        HostRootLinks.mainContext = Dispatchers.Main
    }

    @Test fun bringReadingToFrontPopsToReading() {
        nav.stack += NavRoutes.READING
        HostRootLinks.bringReadingToFront()
        assertEquals(listOf("popTo(${NavRoutes.READING},false)"), nav.calls)
    }

    @Test fun bringReadingToFrontOpensReadingWhenItIsNotOnTheStack() {
        HostRootLinks.bringReadingToFront()
        assertEquals("navigate(${NavRoutes.READING})", nav.calls.last())
    }

    @Test fun openRouteNavigatesTheSingleNavHost() {
        HostRootLinks.openRoute("bookmarks")
        assertEquals(listOf("navigate(bookmarks)"), nav.calls)
    }

    @Test fun leavingTheCurrentScreenPopsIt() {
        HostRootLinks.leaveCurrentScreen()
        assertEquals(listOf("pop"), nav.calls)
    }

    @Test fun readingScreenFollowsTheCurrentRouteAndIsTrueBeforeAnyRoute() {
        assertTrue(HostRootLinks.isOnReadingScreen(), "no route yet")
        shell.routeChanged("bookmarks")
        assertFalse(HostRootLinks.isOnReadingScreen())
        shell.routeChanged(NavRoutes.READING)
        assertTrue(HostRootLinks.isOnReadingScreen())
        shell.routeChanged("${NavRoutes.READING}?x=1")
        assertTrue(HostRootLinks.isOnReadingScreen(), "arguments do not matter")
    }

    @Test fun hiddenHostWaitsForReadingAndOnlyNewestRequestIsDeliveredOnce() {
        val received = mutableListOf<net.bible.sharedcore.search.ReadingSearchRequest>()
        HostRootLinks.attachSearch { received += it; true }
        shell.routeChanged("settings/app")
        val first = net.bible.sharedcore.search.ReadingSearchRequest(query = "first")
        val latest = net.bible.sharedcore.search.ReadingSearchRequest(query = "latest")
        HostRootLinks.openRoute(NavRoutes.readingSearch(first))
        HostRootLinks.openRoute(NavRoutes.readingSearch(latest))
        assertTrue(received.isEmpty())
        shell.routeChanged(NavRoutes.READING)
        HostRootLinks.deliverPendingSearch()
        HostRootLinks.deliverPendingSearch()
        assertEquals(listOf(latest), received)
    }

    @Test fun readingWithoutHostWaitsUntilHostAttaches() {
        shell.routeChanged(NavRoutes.READING)
        val request = net.bible.sharedcore.search.ReadingSearchRequest(query = "waiting")
        HostRootLinks.openRoute(NavRoutes.readingSearch(request))
        val received = mutableListOf<net.bible.sharedcore.search.ReadingSearchRequest>()
        HostRootLinks.attachSearch { received += it; true }
        assertEquals(listOf(request), received)
    }

    @Test fun portsDoNothingBeforeTheRootIsAttached() {
        HostRootLinks.reset()
        HostRootLinks.openRoute("bookmarks")
        HostRootLinks.bringReadingToFront()
        HostRootLinks.leaveCurrentScreen()
        assertEquals(emptyList(), nav.calls)
        assertFalse(HostRootLinks.searchStrongsInReadingView("H1", emptyList()), "no reading host: not handled")
    }
}
