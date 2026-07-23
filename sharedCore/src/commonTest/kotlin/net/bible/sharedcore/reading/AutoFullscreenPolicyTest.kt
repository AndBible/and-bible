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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mirrors classic `BibleGestureListener.onScroll` (view/activity/page/BibleGestureListener.kt:119-150).
 * [deltaY] convention throughout: down = positive (the raw per-call touch-position delta, i.e. classic's
 * `e2.y - scrollEv.y` increment - the NEGATION of Android GestureDetector's own `distanceY`).
 */
class AutoFullscreenPolicyTest {
    private val threshold = 56f

    @Test fun belowThresholdAccumulationStaysNone() {
        // Single small up-scroll (-40px), further from threshold (56px) than it needs to be to prove
        // nothing fires yet.
        val result = autoFullscreenAction(
            deltaY = -40f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = AutoFullscreenTracking(),
        )
        assertEquals(FullscreenAction.None, result.action)
        assertEquals(-40f, result.tracking.accumulated)
        assertEquals(false, result.tracking.lastDirectionDown)
    }

    @Test fun sustainedUpScrollCrossingThresholdEntersFullscreen() {
        // Two consecutive up-scrolls (same direction, no flip) accumulate past -threshold.
        var tracking = AutoFullscreenTracking()
        val r1 = autoFullscreenAction(
            deltaY = -40f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r1.action)
        tracking = r1.tracking

        val r2 = autoFullscreenAction(
            deltaY = -30f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.Enter, r2.action)
        // Crossing the threshold resets the accumulator, regardless of the action firing.
        assertEquals(0f, r2.tracking.accumulated)
    }

    @Test fun sustainedDownScrollCrossingThresholdWhileFullscreenExits() {
        // A fresh AutoFullscreenTracking() defaults lastDirectionDown = false (classic's `lastDirection`
        // starts false too), so the very first down-scroll call flips direction and is "spent" with no
        // threshold check - exactly as classic's scrollEv-reset-before-recomputing-dist does. Subsequent
        // same-direction calls then accumulate normally.
        var tracking = AutoFullscreenTracking()

        val r1 = autoFullscreenAction(
            deltaY = 30f, isEnabled = true, isFullScreen = true, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r1.action)
        assertEquals(0f, r1.tracking.accumulated) // first-call direction-flip spend, not -30/+30 partial
        tracking = r1.tracking

        val r2 = autoFullscreenAction(
            deltaY = 30f, isEnabled = true, isFullScreen = true, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r2.action) // 30 < 56
        tracking = r2.tracking

        val r3 = autoFullscreenAction(
            deltaY = 30f, isEnabled = true, isFullScreen = true, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.Exit, r3.action) // 60 > 56
        assertEquals(0f, r3.tracking.accumulated)
    }

    @Test fun directionFlipResetsTheAccumulator() {
        // A small up-scroll (below threshold) followed by a small down-scroll: the flip must zero the
        // accumulator outright (not just apply the new delta on top of the old one), so a subsequent
        // same-size down-scroll still doesn't reach the threshold.
        var tracking = AutoFullscreenTracking()
        val r1 = autoFullscreenAction(
            deltaY = -40f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r1.action)
        tracking = r1.tracking

        val r2 = autoFullscreenAction(
            deltaY = 10f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r2.action)
        // Must be exactly 0 (flip discards this call's delta) - NOT -40 + 10 = -30, which would also
        // read as None but for the wrong reason (not proving the reset actually happened).
        assertEquals(0f, r2.tracking.accumulated)
        assertEquals(true, r2.tracking.lastDirectionDown)
        tracking = r2.tracking

        val r3 = autoFullscreenAction(
            deltaY = 10f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r3.action) // 0 + 10 = 10, nowhere near threshold post-reset
        assertEquals(10f, r3.tracking.accumulated)
    }

    @Test fun disabledAlwaysNone() {
        // Geometry clearly crosses the threshold in one call, but isEnabled = false suppresses the action.
        // Per classic (scrollEv is reset unconditionally inside the `if` block, only the state mutation is
        // gated by `autoFullScreen`), the accumulator still resets even though no action fires.
        val result = autoFullscreenAction(
            deltaY = -100f, isEnabled = false, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = AutoFullscreenTracking(),
        )
        assertEquals(FullscreenAction.None, result.action)
        assertEquals(0f, result.tracking.accumulated)
    }

    @Test fun lockedByDoubleTapSuppressesExit() {
        var tracking = AutoFullscreenTracking()
        // Prime the direction so the down-scroll below isn't eaten by the fresh-tracking direction flip.
        val prime = autoFullscreenAction(
            deltaY = 10f, isEnabled = true, isFullScreen = true, lockedByDoubleTap = true,
            thresholdPx = threshold, tracking = tracking,
        )
        tracking = prime.tracking

        val result = autoFullscreenAction(
            deltaY = 100f, isEnabled = true, isFullScreen = true, lockedByDoubleTap = true,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, result.action)
        // Still resets, matching classic (the reset is unconditional inside the `if` block).
        assertEquals(0f, result.tracking.accumulated)
    }

    @Test fun lockedByDoubleTapSuppressesEnterToo() {
        // Classic applies the identical `!lastFullScreenByDoubleTap && autoFullScreen` guard in BOTH
        // branches (BibleGestureListener.kt:138 and :144), so the pure port gates Enter and Exit
        // symmetrically even though this combination (locked while not fullscreen) can't occur in
        // practice - lastFullScreenByDoubleTap is always cleared the moment fullscreen exits.
        val result = autoFullscreenAction(
            deltaY = -100f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = true,
            thresholdPx = threshold, tracking = AutoFullscreenTracking(),
        )
        assertEquals(FullscreenAction.None, result.action)
        assertEquals(0f, result.tracking.accumulated)
    }

    @Test fun exactlyAtThresholdDoesNotFire() {
        // Classic uses strict `<` / `>` (not `<=`/`>=`), so landing exactly on the threshold is NOT
        // (yet) a crossing.
        val result = autoFullscreenAction(
            deltaY = -threshold, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = AutoFullscreenTracking(),
        )
        assertEquals(FullscreenAction.None, result.action)
        assertEquals(-threshold, result.tracking.accumulated)
    }
}
