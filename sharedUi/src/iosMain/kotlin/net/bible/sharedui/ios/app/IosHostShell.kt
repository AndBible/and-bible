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

import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.ios.IosPending

/** The three navigation operations [IosHostShell] needs; a seam so the shell is testable without a NavHost. */
@kotlin.native.HiddenFromObjC
interface IosNavigator {
    fun navigate(route: String)
    fun popBackStack(): Boolean
    /** Pops back to [route]; false (and no change) when [route] is not on the back stack. */
    fun popBackStackTo(route: String, inclusive: Boolean): Boolean
}

/** [IosNavigator] over the single iOS [NavHostController]; the controller is read lazily (it exists after the first composition). */
@kotlin.native.HiddenFromObjC
class NavControllerNavigator(private val navController: () -> NavHostController) : IosNavigator {
    override fun navigate(route: String) = navController().navigate(route)

    override fun popBackStack(): Boolean = navController().popBackStack()

    override fun popBackStackTo(route: String, inclusive: Boolean): Boolean =
        navController().popBackStack(route, inclusive)
}

/**
 * [HostShell] for iOS. There is no activity to finish or recreate, so [exitHost] and [recreateHost] are pending.
 * The history route keeps the Android compare-and-clear semantics: only the owner that published the current route
 * can clear it, because navigation-compose runs the entering destination's effects before the leaving one's disposal.
 */
@kotlin.native.HiddenFromObjC
class IosHostShell(
    override val hostScope: CoroutineScope,
    private val navigator: IosNavigator,
) : HostShell {
    // The history-route fields are main-thread only (composition effects), so they take no lock.
    private var historyRouteOwner: Any? = null

    /** The route the current owner published, or null. */
    var historyRoute: String? = null
        private set

    private val route = MutableStateFlow<String?>(null)

    /**
     * The current destination's route pattern, as the app's destination listener reports it ([routeChanged]); lets a
     * caller that opened a result-bearing screen see that it was left.
     */
    val currentRoute: StateFlow<String?> get() = route

    private val version = MutableStateFlow(0)

    /** Counts [routeChanged] calls, so a caller sees that SOME navigation happened even when the route ends where it began. */
    val routeVersion: StateFlow<Int> get() = version

    fun routeChanged(newRoute: String?) {
        route.value = newRoute
        version.value += 1
    }

    override var windowTitle: String = ""
        private set

    override fun exitHost() = IosPending.log("HostShell.exitHost")

    override fun recreateHost() = IosPending.log("HostShell.recreateHost")

    override fun setWindowTitle(title: String) {
        windowTitle = title
    }

    /**
     * Pops back to an existing READING entry when there is one (Android gate (b) does the same with
     * `popBackStack(READING)`), so the Welcome return gate can call this unconditionally without a second READING.
     */
    override fun openReading() {
        // Like Android's CLEAR_TOP | SINGLE_TOP: reuse a reading entry that is already on the stack (Search paths).
        if (navigator.popBackStackTo(NavRoutes.READING, inclusive = false)) return
        // First-run flow: Download sits above Welcome, so pop it first; either may be absent.
        navigator.popBackStackTo(NavRoutes.DOWNLOAD_PATTERN, inclusive = true)
        navigator.popBackStackTo(NavRoutes.WELCOME, inclusive = true)
        navigator.navigate(NavRoutes.READING)
    }

    /**
     * Leaves a reading destination that cannot start: pops back to Welcome when it is on the stack, else navigates to
     * it (a start-route READING has Welcome nowhere beneath it).
     */
    fun showWelcome() {
        if (!navigator.popBackStackTo(NavRoutes.WELCOME, inclusive = false)) navigator.navigate(NavRoutes.WELCOME)
    }

    override fun setHistoryRoute(owner: Any, route: String) {
        historyRouteOwner = owner
        historyRoute = route
    }

    override fun clearHistoryRoute(owner: Any) {
        if (historyRouteOwner !== owner) return
        historyRouteOwner = null
        historyRoute = null
    }

    override fun goBackInHistory(): Boolean = navigator.popBackStack()

    override fun navigate(route: String) = navigator.navigate(route)
}
