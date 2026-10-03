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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * [NavSessionMemo]'s four rules, each of which a destination's unsaved working set depends on --
 * the extracted form of what `NavHostComposeActivity.manageLabelsSession` did by hand, and what the
 * text-display-settings destination now uses to survive a child being pushed on top of it.
 */
class NavSessionMemoTest {

    private class Session(val id: Int)

    @Test
    fun theSameKeyGetsTheSameValueAndTheFactoryRunsOnce() {
        val memo = NavSessionMemo<String, Session>()
        var built = 0
        val first = memo.getOrPut("a") { Session(++built) }
        val second = memo.getOrPut("a") { Session(++built) }
        assertSame(first, second)
        assertEquals(1, built, "a memoised visit must not be rebuilt -- rebuilding IS the data loss")
    }

    /**
     * A route with different arguments is different WORK -- a detached edit of workspace A is not an
     * edit of workspace B -- so an unkeyed memo would hand the second entry the first one's state.
     */
    @Test
    fun aDifferentKeyStartsAFreshValue() {
        val memo = NavSessionMemo<String, Session>()
        var built = 0
        val a = memo.getOrPut("a") { Session(++built) }
        val b = memo.getOrPut("b") { Session(++built) }
        assertNotSame(a, b)
        assertEquals(2, built)
    }

    /** [NavSessionMemo.drop] is what makes a FINISHED visit unresumable: the next entry on the same
     *  key must start over rather than reopen an edit that has already been delivered. */
    @Test
    fun droppingForgetsTheValueSoTheNextEntryIsAFreshVisit() {
        val memo = NavSessionMemo<String, Session>()
        var built = 0
        val first = memo.getOrPut("a") { Session(++built) }
        memo.drop()
        assertNull(memo.current)
        val second = memo.getOrPut("a") { Session(++built) }
        assertNotSame(first, second)
        assertEquals(2, built)
    }

    /**
     * The factory's value is stored only after it RETURNS. A memo written before the factory ran
     * would keep a half-built value for the host's whole lifetime, and every later entry would be
     * handed that instead of retrying -- the defect fix round 1 also corrected in the selector's own
     * host-side memo.
     */
    @Test
    fun aThrowingFactoryLeavesNothingMemoised() {
        val memo = NavSessionMemo<String, Session>()
        assertFailsWith<IllegalStateException> { memo.getOrPut("a") { error("boom") } }
        assertNull(memo.current, "a failed build must not be memoised")
        val recovered = memo.getOrPut("a") { Session(1) }
        assertSame(recovered, memo.current)
    }
}
