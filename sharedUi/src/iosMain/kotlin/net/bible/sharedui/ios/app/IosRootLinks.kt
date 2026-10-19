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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.control.page.CurrentPage
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ios.reading.IosReadingNavigator

/**
 * How the platform ports built before the UI (the Koin graph: link handling, history, speak, key choosers) reach the
 * shared root once it exists. Android reaches the same things through `CurrentActivityHolder`; iOS has one NavHost, so
 * the app root registers its shell and the reading host registers its navigator here and the ports look them up per
 * call. Every call before registration (or after [reset]) is logged and does nothing; navigation is posted to the main
 * thread because the callers (bridge, speak, link handling) run on other threads.
 *
 * Process-wide mutable state by necessity (the graph is built first); tests set and [reset] it.
 */
@kotlin.native.HiddenFromObjC
object IosRootLinks {
    private const val TAG = "IosRootLinks"

    /** Where navigation runs; tests replace it. */
    internal var mainContext: kotlin.coroutines.CoroutineContext = Dispatchers.Main

    private var shell: IosHostShell? = null
    private var navigator: IosReadingNavigator? = null
    private var strongsSearch: ((String, List<String>) -> Boolean)? = null

    /** The app root registers its shell. */
    fun attachShell(shell: IosHostShell) {
        this.shell = shell
    }

    /** The reading host registers what the ports need from it. */
    fun attachReading(navigator: IosReadingNavigator, strongsSearch: (String, List<String>) -> Boolean) {
        this.navigator = navigator
        this.strongsSearch = strongsSearch
    }

    fun reset() {
        shell = null
        navigator = null
        strongsSearch = null
    }

    private fun onMain(what: String, block: (IosHostShell) -> Unit) {
        val s = shell
        if (s == null) {
            Log.w(TAG, "$what: the app root is not attached yet; ignored")
            return
        }
        s.hostScope.launch(mainContext) { block(s) }
    }

    /** `LinkPlatform.openRoute`: Android starts the nav-host activity on [route]. */
    fun openRoute(route: String) = onMain("openRoute($route)") { it.navigate(route) }

    /** `SpeakPlatform.bringReadingToFront`: pops back to the reading destination, or opens it. */
    fun bringReadingToFront() = onMain("bringReadingToFront") { it.openReading() }

    /** `HistoryPlatform.leaveCurrentScreen`: pops the screen on top. */
    fun leaveCurrentScreen() = onMain("leaveCurrentScreen") { it.goBackInHistory() }

    /** `HistoryPlatform.isOnReadingScreen`: the reading destination is current; true while no route is known yet. */
    fun isOnReadingScreen(): Boolean {
        val route = shell?.currentRoute?.value ?: return true
        return route.substringBefore('?') == NavRoutes.READING
    }

    /** `LinkPlatform.searchStrongsInReadingView`; false (not handled) without a reading host. */
    fun searchStrongsInReadingView(ref: String, documentInitials: List<String>): Boolean =
        strongsSearch?.invoke(ref, documentInitials) ?: false

    /** `KeyChooserLauncher.startKeyChooser`: whether a chooser was started (false without a reading host). */
    fun startKeyChooser(page: CurrentPage): Boolean {
        val nav = navigator
        if (nav == null) {
            Log.w(TAG, "startKeyChooser: no reading host yet; not started")
            return false
        }
        nav.startKeyChooser(page)
        return true
    }
}
