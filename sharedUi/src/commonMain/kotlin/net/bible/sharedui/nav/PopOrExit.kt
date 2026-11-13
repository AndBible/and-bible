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
import androidx.navigation.NavBackStackEntry
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
 * Whether a pop requested on behalf of the destination [entry] may go ahead: only while [entry] is still the
 * current back stack entry (issue #4073).
 *
 * [net.bible.sharedui.components.AbNavHost] fades a popped screen out for 700 ms. `popBackStack()` removes its
 * entry at once, but the screen, its up arrow, save button and dialogs stay drawn and clickable. A second tap there
 * used to pop again on behalf of a destination that was already gone: the entry below, typically the reading start
 * destination, whose pop fails and so exits the host. Keyed on identity, not lifecycle: the new current entry is
 * not RESUMED until its enter fade ends, yet its own taps and programmatic cascades (a parent that pops itself on
 * a child's result) must go through at once.
 *
 * One exception: a host-memoised controller (the bookmark list's, kept across visits) holds callbacks built for
 * the FIRST entry of its destination. Once that entry is DESTROYED (its exit fade is over), no screen of it is
 * drawn any more, so a call naming it comes from a later entry of the same destination reusing the memo: it acts
 * for the current entry when that is the same destination. A popped entry that is still fading out is not yet
 * DESTROYED and stays dropped.
 *
 * [entry] `null` means "no requesting destination known" (a host-level caller outside any arm): never dropped.
 */
internal fun NavHostController.isCurrentEntry(entry: NavBackStackEntry?): Boolean {
    if (entry == null) return true
    val current = currentBackStackEntry ?: return false
    if (current === entry) return true
    return entry.lifecycle.currentState == Lifecycle.State.DESTROYED && current.destination.id == entry.destination.id
}

/**
 * The pop every cluster graph's up/back/save/cancel goes through: pops (or exits the host at the start
 * destination, see [popOrExitOnFailedPop]) on behalf of [from], and does nothing if [from] is no longer the
 * current entry ([isCurrentEntry]).
 */
internal fun NavHostController.popOrExitFrom(from: NavBackStackEntry, exitHost: () -> Unit) {
    if (!isCurrentEntry(from)) return
    popOrExitOnFailedPop(popBackStack(), exitHost)
}
