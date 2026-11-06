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
package net.bible.sharedui.host.reading

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEventInput
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.HostBackDispatcher
import net.bible.sharedui.components.AbNavHost
import net.bible.sharedui.reading.nav.ReadingNavDeps
import net.bible.sharedui.reading.nav.readingNavGraph
import net.bible.sharedui.theme.LocalDisableAnimations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #4003 review: on iOS the scene's back input (`UIKitNavigationEventInput`) turns its edge-swipe back recognizer on
 * whenever the dispatcher has an enabled handler. The reading destination must therefore enable its back handler only
 * while the host's back chain has something to close; an always-enabled handler would let the recognizer take left-edge
 * drags (chapter swipe, selection) from the Bible text on the plain reading view.
 */
@OptIn(ExperimentalTestApi::class)
class HostReadingBackEnablementTest {
    /** Records what the platform input (UIKit's edge-swipe recognizer on iOS) is told. */
    private class ProbeInput : NavigationEventInput() {
        var hasEnabled = false
            private set

        override fun onHasEnabledHandlersChanged(enabled: Boolean) {
            hasEnabled = enabled
        }
    }

    @Test
    fun readingBackHandlerIsEnabledOnlyWhileTheChainHasALayer() = runComposeUiTest {
        var drawer by mutableStateOf(false)
        var find by mutableStateOf(false)
        val log = mutableListOf<String>()
        val route = ReadingBackRoute()
        val probe = ProbeInput()
        val deps = ReadingNavDeps(
            host = Any(), windowTitle = "Reading", onBack = route::onBack,
            backEnabled = { route.hasOpenLayer() },
            content = { Text("Bible text") },
            onKey = { false }, onScreenTurnedOn = {}, onScreenTurnedOff = {}, setWindowTitle = {},
            results = emptyList(),
        )
        setContent {
            val dispatcher = HostBackDispatcher.currentDispatcher()
            DisposableEffect(dispatcher) {
                dispatcher?.addInput(probe)
                onDispose { dispatcher?.removeInput(probe) }
            }
            CompositionLocalProvider(LocalDisableAnimations provides true) {
                val nav = rememberNavController()
                AbNavHost(nav, NavRoutes.READING) { readingNavGraph(nav, deps) }
            }
        }
        waitForIdle()
        assertFalse(probe.hasEnabled, "no host yet: nothing to close, the edge-swipe back must stay off")

        route.chain = ReadingBackChain(
            isDrawerOpen = { drawer }, closeDrawer = { drawer = false; log += "drawer" },
            isSearchSheetVisible = { false }, closeSearchSheet = {},
            isSearchBarOpen = { find }, closeSearchBar = { find = false; log += "find" },
            observeOpen = { drawer || find },
        )
        waitForIdle()
        assertFalse(probe.hasEnabled, "plain reading: the edge-swipe back must stay off")

        find = true
        waitForIdle()
        assertTrue(probe.hasEnabled, "Find is open: back (Esc, edge swipe) must reach the chain")
        assertTrue(HostBackDispatcher.back())
        waitForIdle()
        assertEquals(listOf("find"), log)
        assertFalse(probe.hasEnabled, "Find closed: back off again")

        drawer = true
        waitForIdle()
        assertTrue(probe.hasEnabled, "the drawer is open: back must reach the chain")
        assertTrue(HostBackDispatcher.back())
        waitForIdle()
        assertEquals(listOf("find", "drawer"), log)
        // Nothing open: Esc is unhandled (the plain reading no-op), not passed to an empty chain.
        assertFalse(HostBackDispatcher.back())
        assertEquals(listOf("find", "drawer"), log)
    }
}
