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

package net.bible.sharedui.nav

import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How a destination that produces a RESULT delivers it, in either of the two ways it can be
 * entered.
 *
 * The nav-graph phase removes `setResult` as a mechanism (phase spec §5.2): inside a graph the
 * parent passes a lambda, the child calls it and pops. That is true for a destination reached only
 * from inside the graph. It is NOT true for one that is also reached from a classic Activity
 * caller -- `ManageLabels` (from `Bookmarks` and from six outside callers) and `MyDocumentPages`
 * (from `MyDocuments` and from `CurrentGeneralBookPage`) are both. Such a destination has two exit
 * shapes, and this class holds the ONE place that decides between them:
 *
 * - **entered from outside** -- it is the host's start destination, so it exits the host WITH the
 *   result, through [exitWithResult], a host-side lambda that packs an `Intent`, `setResult`s and
 *   `finish`es. This is what `ReadingProgress` did by hand before this class existed.
 * - **entered from inside** -- it publishes to [pending] and pops. The parent arm consumes it once
 *   in a `LaunchedEffect`.
 *
 * **Generalises slice 3's `ReadingPlanNavDeps.pendingSelection`**, and for its stated reasons: a
 * plain flow rather than the navigation library's saved-state handle, because it is ordinary
 * `commonMain` Kotlin, needs no `NavHostController` to test, and does not depend on an API surface
 * this migration has not verified for the multiplatform fork. Created by the HOST -- a channel
 * `remember`ed in an arm would be gone before the parent could read it, since the parent's
 * composition is disposed while the child sits on top of it.
 *
 * **A pending result does not survive process death** (design §4.3). The consequence is an
 * unapplied selection, not lost data, and the window is narrow. Making it durable would mean a
 * `SavedStateHandle` and therefore savable payloads -- deliberately not built now, because it would
 * rest on an API surface this migration has not verified for the multiplatform navigation fork.
 *
 * One channel per result type, declared on the deps object of the graph that owns the producing
 * destination. A single erased channel would lose exactly the typing this exists for.
 *
 * Cancellation is a VARIANT of [T], not a second exit path (plan D1): the host adapter picks
 * `RESULT_OK` or `RESULT_CANCELED` from the variant it is handed.
 */
class NavResultChannel<T>(private val exitWithResult: (T) -> Unit) {

    private val _pending = MutableStateFlow<T?>(null)

    /** What a child published before it popped. Collected by the parent arm; cleared by [consume]. */
    val pending: StateFlow<T?> = _pending.asStateFlow()

    /** Delivers [result] the right way for how this destination was entered. */
    fun deliver(navController: NavHostController, result: T) {
        deliverNavResult(
            hasParentEntry = navController.previousBackStackEntry != null,
            result = result,
            publishToParent = { _pending.value = it },
            popBackStack = { navController.popBackStack() },
            exitWithResult = exitWithResult,
        )
    }

    /** Reads the pending result and clears it, so a recomposition cannot apply it twice. */
    fun consume(): T? {
        val value = _pending.value
        _pending.value = null
        return value
    }

    /** Test seam: [deliver] needs a `NavHostController`, and [consume]'s clearing is worth its own test. */
    internal fun publishForTest(value: T) {
        _pending.value = value
    }
}

/**
 * The branch [NavResultChannel.deliver] is made of, as a plain function of booleans and lambdas.
 *
 * Split out for the reason [popOrExitOnFailedPop] documents: this is the part that must be tested,
 * and it cannot be while it is wrapped around a `NavHostController`. `internal` so it is a seam that
 * is tested rather than a private that is not.
 *
 * The failed-pop fall-through is plan D2: with a parent entry the pop cannot fail, but if it ever
 * did, delivering the result outwards loses less than dropping it.
 */
internal fun <T> deliverNavResult(
    hasParentEntry: Boolean,
    result: T,
    publishToParent: (T) -> Unit,
    popBackStack: () -> Boolean,
    exitWithResult: (T) -> Unit,
) {
    if (!hasParentEntry) {
        exitWithResult(result)
        return
    }
    publishToParent(result)
    if (!popBackStack()) exitWithResult(result)
}
