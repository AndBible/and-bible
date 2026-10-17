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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ScrollCoordinator] — the state machine that prevents snap-back
 * when scroll-settle updates the selection.
 *
 * The bug: user scrolls to item N → snap settles → scroll-settle callback fires
 * onSelected(N) → state layer updates selectedIndex → the re-center animation triggers
 * and scrolls back onto item N, fighting the position the user just landed on.
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
    fun `programmaticScroll is true between begin and end`() {
        val coordinator = ScrollCoordinator()
        coordinator.beginProgrammaticScroll()
        assertTrue(
            "programmaticScroll should be true while a programmatic scroll is in flight",
            coordinator.programmaticScroll,
        )
        coordinator.endProgrammaticScroll()
        assertFalse(
            "programmaticScroll should be false once the scroll has ended",
            coordinator.programmaticScroll,
        )
    }

    @Test
    fun `overlapping programmatic scrolls keep flag true until all complete`() {
        // Models a re-center animation being retargeted before the previous one has
        // finished: the first animation's end must not clear the flag while the second
        // is still running, or the settle callback would fire mid-animation and commit
        // whichever item happened to be passing the centre.
        val coordinator = ScrollCoordinator()
        coordinator.beginProgrammaticScroll()
        coordinator.beginProgrammaticScroll()
        coordinator.endProgrammaticScroll()
        assertEquals(
            "second scroll still in flight, flag must remain true",
            true,
            coordinator.programmaticScroll,
        )
        coordinator.endProgrammaticScroll()
        assertFalse(
            "flag should be cleared once every overlapping scroll has ended",
            coordinator.programmaticScroll,
        )
    }

    @Test
    fun `unbalanced end does not drive the counter negative`() {
        // A cancelled animation can end a scroll that was never begun (or end it twice).
        // If that drove the counter below zero, the next genuine programmatic scroll
        // would fail to raise the flag and its settle would be misread as user input.
        val coordinator = ScrollCoordinator()
        coordinator.endProgrammaticScroll()
        coordinator.endProgrammaticScroll()
        coordinator.beginProgrammaticScroll()
        assertTrue(
            "flag must still rise after unbalanced end calls",
            coordinator.programmaticScroll,
        )
        coordinator.endProgrammaticScroll()
        assertFalse(coordinator.programmaticScroll)
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
    fun `external selection change should trigger re-center`() {
        val coordinator = ScrollCoordinator()

        // No markScrollSettled() — this is an external change (tap, boundary crossing)
        assertTrue(coordinator.shouldRecenter())

        // Bracketing the resulting animation suppresses its own scroll-settle callback
        coordinator.beginProgrammaticScroll()
        assertTrue(coordinator.programmaticScroll)
        coordinator.endProgrammaticScroll()
    }

    /**
     * A settle whose re-centre never arrives — the widget closed in between — must not
     * leave the suppression armed for the next time it opens, or the first re-centre of
     * the new session is silently swallowed and the strip sits where the last one left it.
     */
    @Test
    fun `reset clears a settle whose re-center never arrived`() {
        val coordinator = ScrollCoordinator()

        coordinator.markScrollSettled()
        coordinator.reset()

        assertTrue(
            "a re-center in the next session must not be swallowed by the previous one",
            coordinator.shouldRecenter(),
        )
    }

    @Test
    fun `reset clears a programmatic scroll that was never ended`() {
        val coordinator = ScrollCoordinator()

        coordinator.beginProgrammaticScroll()
        coordinator.beginProgrammaticScroll()
        coordinator.reset()

        assertFalse(
            "an abandoned programmatic scroll must not suppress settles forever",
            coordinator.programmaticScroll,
        )
    }
}
