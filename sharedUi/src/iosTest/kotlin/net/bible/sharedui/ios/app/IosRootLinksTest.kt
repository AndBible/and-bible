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
import net.bible.sharedui.host.app.IosHostShell
import net.bible.sharedui.host.app.IosNavigator
import net.bible.sharedui.host.app.IosRootLinks

/** The ports that reach the shared root through [IosRootLinks]: speak, history and link handling. */
class IosRootLinksTest {
    private class Nav : IosNavigator {
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
    private val shell = IosHostShell(CoroutineScope(EmptyCoroutineContext), nav)

    @BeforeTest fun attach() {
        IosRootLinks.mainContext = Dispatchers.Unconfined
        IosRootLinks.attachShell(shell)
    }

    @AfterTest fun detach() {
        IosRootLinks.reset()
        IosRootLinks.mainContext = Dispatchers.Main
    }

    @Test fun bringReadingToFrontPopsToReading() {
        nav.stack += NavRoutes.READING
        IosRootLinks.bringReadingToFront()
        assertEquals(listOf("popTo(${NavRoutes.READING},false)"), nav.calls)
    }

    @Test fun bringReadingToFrontOpensReadingWhenItIsNotOnTheStack() {
        IosRootLinks.bringReadingToFront()
        assertEquals("navigate(${NavRoutes.READING})", nav.calls.last())
    }

    @Test fun openRouteNavigatesTheSingleNavHost() {
        IosRootLinks.openRoute("bookmarks")
        assertEquals(listOf("navigate(bookmarks)"), nav.calls)
    }

    @Test fun leavingTheCurrentScreenPopsIt() {
        IosRootLinks.leaveCurrentScreen()
        assertEquals(listOf("pop"), nav.calls)
    }

    @Test fun readingScreenFollowsTheCurrentRouteAndIsTrueBeforeAnyRoute() {
        assertTrue(IosRootLinks.isOnReadingScreen(), "no route yet")
        shell.routeChanged("bookmarks")
        assertFalse(IosRootLinks.isOnReadingScreen())
        shell.routeChanged(NavRoutes.READING)
        assertTrue(IosRootLinks.isOnReadingScreen())
        shell.routeChanged("${NavRoutes.READING}?x=1")
        assertTrue(IosRootLinks.isOnReadingScreen(), "arguments do not matter")
    }

    @Test fun portsDoNothingBeforeTheRootIsAttached() {
        IosRootLinks.reset()
        IosRootLinks.openRoute("bookmarks")
        IosRootLinks.bringReadingToFront()
        IosRootLinks.leaveCurrentScreen()
        assertEquals(emptyList(), nav.calls)
        assertFalse(IosRootLinks.searchStrongsInReadingView("H1", emptyList()), "no reading host: not handled")
    }
}
