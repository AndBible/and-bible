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
package net.bible.sharedcore.reading

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Nav-graph slice 7 Task 6 fix round 1: [ReadingViewVisibility] has TWO independent inputs and
 * [ReadingViewVisibility.isVisible] is their OR — the destination's depth counter
 * ([ReadingViewVisibility.enter]/[ReadingViewVisibility.exit]) and the temporary Activity input
 * ([ReadingViewVisibility.setActivityVisible], driven by classic `MainBibleActivity`'s lifecycle
 * while the reading destination's content slot cannot render the reading view).
 *
 * The composition is what this class pins, because the failure mode it guards against is silent in
 * both directions: one input clobbering the other turns the flag off under a reading view that is
 * still on screen (no `KeyHistoryItem`, and `HistoryManager.goBack` finishing the Activity), and
 * nothing else in the tree would fail. The two ends are tested where they are wired:
 * `ReadingHistoryAnchorTest` for the Activity call sites, `ReadingDestinationInGraphTest` for the
 * destination's `DisposableEffect`.
 *
 * The object is a process-wide singleton, so each test resets it at both ends.
 */
class ReadingViewVisibilityTest {

    @BeforeTest
    fun reset() = ReadingViewVisibility.setVisible(false)

    @AfterTest
    fun clear() = ReadingViewVisibility.setVisible(false)

    /**
     * Either input alone is enough, and neither one's exit clears the other's. Mutations that make
     * this RED: fold `setActivityVisible` into the counter (`depth = if (visible) 1 else 0`) — the
     * Activity's `false` then clears the composed destination; or publish `depth > 0` alone — the
     * Activity input stops being an input at all.
     */
    @Test
    fun theTwoInputsCompose() {
        // The destination alone, with the Activity input off.
        ReadingViewVisibility.enter()
        assertTrue(ReadingViewVisibility.isVisible, "a composed destination alone makes it visible")

        // The Activity arriving on top of it changes nothing…
        ReadingViewVisibility.setActivityVisible(true)
        assertTrue(ReadingViewVisibility.isVisible)

        // …and the Activity going away does not clear the destination.
        ReadingViewVisibility.setActivityVisible(false)
        assertTrue(
            ReadingViewVisibility.isVisible,
            "the Activity input going false must not clear a composed destination",
        )

        ReadingViewVisibility.exit()
        assertFalse(ReadingViewVisibility.isVisible, "both inputs off — and only then is it false")

        // The mirror image: the Activity alone, and a destination's exit must not clear IT.
        ReadingViewVisibility.setActivityVisible(true)
        assertTrue(ReadingViewVisibility.isVisible, "the Activity input alone makes it visible")

        ReadingViewVisibility.enter()
        ReadingViewVisibility.exit()
        assertTrue(
            ReadingViewVisibility.isVisible,
            "a destination's balanced enter/exit must not clear the resumed Activity",
        )

        ReadingViewVisibility.setActivityVisible(false)
        assertFalse(ReadingViewVisibility.isVisible)
    }

    /**
     * The destination input is a depth counter (`FLAG_ACTIVITY_MULTIPLE_TASK` can make a second
     * reading instance real), and an unbalanced [ReadingViewVisibility.exit] must not push it
     * negative — a negative depth would hide the caller's bug behind a flag that can no longer be
     * turned on. Mutation: drop the `if (depth > 0)` guard in `exit()`; the last assertion fails.
     */
    @Test
    fun theDepthCounterIsBalancedAndNeverGoesNegative() {
        ReadingViewVisibility.enter()
        ReadingViewVisibility.enter()
        ReadingViewVisibility.exit()
        assertTrue(ReadingViewVisibility.isVisible, "two in, one out — one reading view is still on screen")

        ReadingViewVisibility.exit()
        ReadingViewVisibility.exit() // unbalanced, i.e. a bug in some caller
        assertFalse(ReadingViewVisibility.isVisible)

        ReadingViewVisibility.enter()
        assertTrue(ReadingViewVisibility.isVisible, "one enter must still be enough to turn it on")
    }

    /**
     * `setVisible` is the TEST-only reset and ignores both inputs, `false` clearing the Activity
     * input too. Without that, one test's `MainBibleActivity` leaves the Activity input true and the
     * next test's `setVisible(false)` fixture is a lie — the `@Before` in `ReadingHistoryAnchorTest`
     * is exactly that call. Mutation: drop `activityVisible = false` from `setVisible`.
     */
    @Test
    fun theTestOnlyResetClearsBothInputs() {
        ReadingViewVisibility.setActivityVisible(true)
        ReadingViewVisibility.enter()

        ReadingViewVisibility.setVisible(false)
        assertFalse(ReadingViewVisibility.isVisible, "a reset must clear the Activity input as well")

        // …and `true` still leaves a depth of exactly one, so one exit lands on false.
        ReadingViewVisibility.setVisible(true)
        ReadingViewVisibility.exit()
        assertFalse(ReadingViewVisibility.isVisible)
    }
}
