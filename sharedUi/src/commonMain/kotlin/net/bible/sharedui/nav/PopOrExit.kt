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

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController

/**
 * Whether an up-navigation attempt that just tried to pop the back stack should fall through to
 * exiting the host outright, given [popped] (`navController.popBackStack()`'s result).
 *
 * **Why it is split out from the `NavHostController.popOrExit` extensions that call it**, rather
 * than folded into them: as a plain boolean-in function this branch is unit-testable without a real
 * `NavHostController`, which requires an Android `Context` to construct and has no lightweight fake,
 * while `:sharedUi` has no Robolectric-style test runner — only plain JUnit via `kotlin("test")`.
 * `internal` rather than `private` for exactly that reason: a visible seam that is tested beats a
 * private one that is not. [net.bible.sharedui.nav.PopOrExitTest] is that test.
 *
 * **Why it lives HERE rather than once per cluster graph** (whole-branch review M1). Slices 1/3/5/6
 * each grew their own byte-identical copy, each arguing self-containment and each honestly recording
 * that its own copy had no test — so four copies shared one test. That was defensible at one copy and
 * thin at four, with six more slices queued behind it. One `internal` helper in a cluster-neutral
 * package is the smaller cost: `internal` keeps it out of `:sharedUi`'s public surface (which was
 * the original objection to sharing), and the four cluster graphs keep their OWN `private
 * NavHostController.popOrExit` extension, whose kdoc is where the cluster-specific "which branch is
 * live here" argument belongs and stays.
 */
internal fun popOrExitOnFailedPop(popped: Boolean, exitHost: () -> Unit) {
    if (!popped) exitHost()
}

/**
 * The pop every up/back arrow of a cluster graph goes through (issue #4073).
 *
 * The library fade of [net.bible.sharedui.components.AbNavHost] keeps the popped screen (and its up
 * arrow) on screen for a while after `popBackStack()` has already removed its entry. A second tap in
 * that window used to pop again: the entry below, typically the reading start destination, whose pop
 * fails and so exits the host. Now a tap is ignored while the previous pop is still settling, i.e. while
 * the entry it landed on is not yet RESUMED ([popInFlight]).
 */
internal fun NavHostController.popOrExitGuarded(exitHost: () -> Unit) {
    if (popInFlight()) return
    val popped = popBackStack()
    if (popped) rememberPopLanding()
    popOrExitOnFailedPop(popped, exitHost)
}

/**
 * Whether a pop through [popOrExitGuarded] / [NavResultChannel.deliver] has landed on the current entry
 * and that entry has not finished its transition (not RESUMED) yet. Only the entry a pop landed on is
 * judged, so a screen that was entered by navigating and pops itself right away (redirect screens) is
 * not affected.
 */
internal fun NavHostController.popInFlight(): Boolean {
    val current = currentBackStackEntry ?: return false
    if (current.savedStateHandle.get<Boolean>(POP_LANDED_KEY) != true) return false
    if (current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        current.savedStateHandle.remove<Boolean>(POP_LANDED_KEY)
        return false
    }
    return true
}

/** Marks the entry a pop just landed on; the mark lives and dies with the entry, so no global state. */
internal fun NavHostController.rememberPopLanding() {
    currentBackStackEntry?.savedStateHandle?.set(POP_LANDED_KEY, true)
}

private const val POP_LANDED_KEY = "net.bible.sharedui.nav.popLanded"
