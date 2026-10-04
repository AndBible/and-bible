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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 3 fix round 1, Finding 1: a bare `popBackStack()` binding for up-navigation is a dead
 * button whenever the destination is the graph's only (start) entry, because `popBackStack()`
 * then returns `false` and does nothing. [popOrExitOnFailedPop] is the branch that fixes it —
 * tested directly, as a plain boolean-in function, because the real callers (each cluster graph's
 * `private NavHostController.popOrExit`) need an Android `Context` to construct and `:sharedUi` has
 * no Robolectric-style runner to provide one.
 *
 * Was `AiNavGraphPopOrExitTest` in `net.bible.sharedui.ai.nav`, covering one of FOUR byte-identical
 * per-cluster copies of the function while the other three (reading plan, search, settings) each
 * carried a kdoc admitting it had no test. Whole-branch review M1 hoisted the function into
 * `net.bible.sharedui.nav` and moved this test with it, so the single implementation every cluster
 * now calls is the one under test — rather than growing three more mirror tests.
 */
class PopOrExitTest {

    @Test
    fun exitsTheHostWhenThereWasNothingToPop() {
        var exited = false
        popOrExitOnFailedPop(popped = false, exitHost = { exited = true })
        assertTrue(exited, "popBackStack() returning false must fall through to exitHost")
    }

    @Test
    fun doesNotExitTheHostWhenAPopSucceeded() {
        var exited = false
        popOrExitOnFailedPop(popped = true, exitHost = { exited = true })
        assertFalse(exited, "a successful pop must not also exit the host")
    }
}
