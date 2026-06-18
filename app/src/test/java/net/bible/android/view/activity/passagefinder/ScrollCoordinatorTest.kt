/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.view.activity.passagefinder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ScrollCoordinator] — the state machine that prevents snap-back
 * when scroll-settle updates the selection.
 *
 * The bug: user scrolls to item N → snap settles → scroll-settle callback fires
 * onSelected(N) → parent updates selectedIndex → re-center LaunchedEffect triggers
 * scrollToItem(N) → aligns to viewport START, not center → visible snap-back.
 *
 * ScrollCoordinator breaks this loop by tracking whether the selection change
 * came from a scroll-settle (skip re-center) or an external source (re-center).
 */
class ScrollCoordinatorTest {

    @Test
    fun `shouldRecenter returns true by default for external selection changes`() {
        val coordinator = ScrollCoordinator()
        assertTrue(coordinator.shouldRecenter())
    }

    @Test
    fun `shouldRecenter returns false after markScrollSettled`() {
        val coordinator = ScrollCoordinator()
        coordinator.markScrollSettled()
        assertFalse(
            "Re-center should be skipped when selection was triggered by scroll settle",
            coordinator.shouldRecenter(),
        )
    }

    @Test
    fun `shouldRecenter resets to true after consuming the scroll-triggered flag`() {
        val coordinator = ScrollCoordinator()
        coordinator.markScrollSettled()
        coordinator.shouldRecenter() // consumes the flag
        assertTrue(
            "After consuming the flag, next selection change should re-center",
            coordinator.shouldRecenter(),
        )
    }

    @Test
    fun `markScrollSettled is idempotent before consumption`() {
        val coordinator = ScrollCoordinator()
        coordinator.markScrollSettled()
        coordinator.markScrollSettled()
        assertFalse(coordinator.shouldRecenter())
        // Second consumption should return true (flag cleared after first)
        assertTrue(coordinator.shouldRecenter())
    }

    @Test
    fun `programmaticScroll is false by default`() {
        val coordinator = ScrollCoordinator()
        assertFalse(coordinator.programmaticScroll)
    }

    @Test
    fun `withProgrammaticScroll sets flag during execution`() = runTest {
        val coordinator = ScrollCoordinator()
        coordinator.withProgrammaticScroll {
            assertTrue(
                "programmaticScroll should be true inside withProgrammaticScroll",
                coordinator.programmaticScroll,
            )
        }
        assertFalse(
            "programmaticScroll should be false after withProgrammaticScroll",
            coordinator.programmaticScroll,
        )
    }

    @Test
    fun `withProgrammaticScroll resets flag even on exception`() = runTest {
        val coordinator = ScrollCoordinator()
        try {
            coordinator.withProgrammaticScroll {
                throw RuntimeException("simulated error")
            }
        } catch (_: RuntimeException) {
            // expected
        }
        assertFalse(
            "programmaticScroll should be reset even after exception",
            coordinator.programmaticScroll,
        )
    }

    @Test
    fun `overlapping withProgrammaticScroll calls keep flag true until all complete`() = runTest {
        // Models the scenario where a re-center LaunchedEffect restarts before the
        // previous one's `finally` has run: the inner block's exit must not clear the
        // flag while the outer block is still in flight.
        val coordinator = ScrollCoordinator()
        coordinator.withProgrammaticScroll {
            assertEquals(true, coordinator.programmaticScroll)
            coordinator.withProgrammaticScroll {
                assertEquals(true, coordinator.programmaticScroll)
            }
            assertEquals(
                "outer scroll still in flight, flag must remain true",
                true,
                coordinator.programmaticScroll,
            )
        }
        assertFalse(
            "flag should be cleared once every overlapping scroll has returned",
            coordinator.programmaticScroll,
        )
    }

    @Test
    fun `full feedback loop scenario - scroll settle should not trigger re-center`() {
        val coordinator = ScrollCoordinator()

        // Step 1: User scrolls, snap settles on item
        // Step 2: Scroll-settle callback fires
        assertFalse("Should not be in programmatic scroll", coordinator.programmaticScroll)

        // Step 3: Before notifying parent, mark as scroll-triggered
        coordinator.markScrollSettled()

        // Step 4: Parent updates selection → re-center LaunchedEffect checks
        assertFalse(
            "Re-center should be SKIPPED because selection came from scroll settle",
            coordinator.shouldRecenter(),
        )

        // Step 5: Next external selection change (e.g., tap on book) should re-center
        assertTrue(
            "Re-center should PROCEED for external selection changes",
            coordinator.shouldRecenter(),
        )
    }

    @Test
    fun `external selection change should trigger re-center`() = runTest {
        val coordinator = ScrollCoordinator()

        // No markScrollSettled() — this is an external change (tap, boundary crossing)
        assertTrue(coordinator.shouldRecenter())

        // The programmatic scroll wrapping ensures scroll-settle is suppressed
        coordinator.withProgrammaticScroll {
            assertTrue(coordinator.programmaticScroll)
        }
    }
}
