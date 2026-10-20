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
package net.bible.sharedui.reading.toolbar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceCyclerTest {
    private var current = "b"
    private val switched = mutableListOf<String>()
    private fun cycler(ids: List<String>, gate: CompletableDeferred<Unit>? = null) = WorkspaceCycler(
        workspaceIds = { ids },
        currentId = { current },
        switchTo = { id -> gate?.await(); switched += id; current = id },
    )

    @Test fun movesForwardAndBackWrappingAtTheEnds() = runTest {
        val c = cycler(listOf("a", "b", "c"))
        c.cycle(true); assertEquals("c", current)
        c.cycle(true); assertEquals("a", current)
        c.cycle(false); assertEquals("c", current)
    }

    @Test fun fewerThanTwoWorkspacesDoNothing() = runTest {
        cycler(listOf("b")).cycle(true)
        cycler(emptyList()).cycle(false)
        assertEquals(emptyList(), switched)
    }

    /** A second fling while the first switch is still loading must continue from where the first lands. */
    @Test fun aDoubleFlingDoesNotLandOnTheSameWorkspaceTwice() = runTest {
        val gate = CompletableDeferred<Unit>()
        val c = cycler(listOf("a", "b", "c", "d"), gate)
        val first = async { c.cycle(true) }
        val second = async { c.cycle(true) }
        testScheduler.runCurrent()
        gate.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf("c", "d"), switched)
    }
}
