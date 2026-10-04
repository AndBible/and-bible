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
 *
 * IMPORTANT asymmetry inherited verbatim from classic (see [AutoFullscreenTracking.lastDirectionUp]):
 * from a genuinely fresh [AutoFullscreenTracking] (`lastDirectionUp = false`, matching classic's
 * `lastDirection = false`), the very FIRST call reporting an *upward* increment (`deltaY < 0`) always
 * disagrees with the default and is treated as a direction flip - it is "spent" (contributes 0 to
 * [AutoFullscreenTracking.accumulated]), no matter its magnitude. The first call reporting a
 * *downward* increment (`deltaY >= 0`) agrees with the default and counts immediately, no spend. Several
 * tests below deliberately pick the "down" direction (or prime the direction first) specifically to
 * avoid that free spend when they need to observe genuine magnitude-sensitive accumulation in a single
 * call; this is called out inline wherever it matters.
 */
class AutoFullscreenPolicyTest {
    private val threshold = 56f

    @Test fun belowThresholdAccumulationStaysNone() {
        // A single up-scroll (-40px) from a FRESH tracker is itself the classic baseline's direction
        // flip (lastDirectionUp defaults to false; -40px is upward, i.e. directionUp = true, which
        // disagrees with the default) - so this call is "spent": accumulated resets to 0 regardless of
        // magnitude, and nothing fires. (Genuine magnitude-sensitive sub-threshold accumulation, once
        // primed past this first-call spend, is covered by sustainedUpScrollCrossingThresholdEntersFullscreen's
        // r2 below: accumulated = -30, still None.)
        val result = autoFullscreenAction(
            deltaY = -40f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = AutoFullscreenTracking(),
        )
        assertEquals(FullscreenAction.None, result.action)
        assertEquals(0f, result.tracking.accumulated)
        assertEquals(true, result.tracking.lastDirectionUp)
    }

    @Test fun sustainedUpScrollCrossingThresholdEntersFullscreen() {
        // Traced directly against classic (distanceY = -deltaY per call; direction = distanceY > 0;
        // dist = e2.y - scrollEv.y, recomputed AFTER a flip-triggered anchor reset):
        //   call1 dy=-40: distanceY=40>0 -> direction=true; lastDirection(default)=false -> FLIP
        //                 -> scrollEv reset to e2 -> dist=0 -> None. (accumulated=0, lastDirectionUp=true)
        //   call2 dy=-30: distanceY=30>0 -> direction=true; lastDirection=true -> no flip
        //                 -> dist = 0 + (-30) = -30 -> not < -56 -> None. (accumulated=-30)
        //   call3 dy=-30: distanceY=30>0 -> direction=true; lastDirection=true -> no flip
        //                 -> dist = -30 + (-30) = -60 -> < -56 -> Enter! dist reset to 0 unconditionally.
        // The first call is "spent" by the fresh-tracker flip (see class doc) - it takes THREE calls,
        // not two, for a sustained up-scroll to cross the threshold from a cold tracker.
        var tracking = AutoFullscreenTracking()
        val r1 = autoFullscreenAction(
            deltaY = -40f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r1.action)
        assertEquals(0f, r1.tracking.accumulated) // first-call direction-flip spend, not a partial -40
        assertEquals(true, r1.tracking.lastDirectionUp)
        tracking = r1.tracking

        val r2 = autoFullscreenAction(
            deltaY = -30f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r2.action) // 0 - 30 = -30, not yet past -56
        assertEquals(-30f, r2.tracking.accumulated)
        tracking = r2.tracking

        val r3 = autoFullscreenAction(
            deltaY = -30f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.Enter, r3.action) // -30 - 30 = -60, past -56
        // Crossing the threshold resets the accumulator, regardless of the action firing.
        assertEquals(0f, r3.tracking.accumulated)
    }

    @Test fun sustainedDownScrollCrossingThresholdWhileFullscreenExits() {
        // Traced directly against classic, same method as above, but a DOWN-scroll (dy > 0, i.e.
        // distanceY = -dy < 0, direction=false) AGREES with the fresh tracker's lastDirection=false
        // default - so, unlike the up-scroll case above, there is NO free spend here: every call counts
        // from the very first one.
        //   call1 dy=20: distanceY=-20, direction=false == lastDirection(false) -> no flip
        //                -> dist = 0 + 20 = 20 -> not > 56 -> None. (accumulated=20)
        //   call2 dy=20: direction=false == lastDirection -> no flip -> dist = 20+20 = 40 -> None.
        //   call3 dy=20: direction=false == lastDirection -> no flip -> dist = 40+20 = 60 -> > 56 -> Exit!
        //                dist reset to 0 unconditionally.
        var tracking = AutoFullscreenTracking()

        val r1 = autoFullscreenAction(
            deltaY = 20f, isEnabled = true, isFullScreen = true, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r1.action)
        assertEquals(20f, r1.tracking.accumulated) // first down-call is NOT spent (agrees with the default)
        assertEquals(false, r1.tracking.lastDirectionUp)
        tracking = r1.tracking

        val r2 = autoFullscreenAction(
            deltaY = 20f, isEnabled = true, isFullScreen = true, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r2.action) // 20 + 20 = 40 < 56
        assertEquals(40f, r2.tracking.accumulated)
        tracking = r2.tracking

        val r3 = autoFullscreenAction(
            deltaY = 20f, isEnabled = true, isFullScreen = true, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.Exit, r3.action) // 40 + 20 = 60 > 56
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
        // read as None but for the wrong reason (not proving the reset actually happened). Note r1's
        // own call was itself a flip too (fresh-tracker up-scroll, see class doc), so r1's accumulated
        // was already 0 going into r2 - r2 flips AGAIN here (up -> down), landing on 0 either way.
        assertEquals(0f, r2.tracking.accumulated)
        assertEquals(false, r2.tracking.lastDirectionUp) // deltaY=10 is downward -> directionUp = false
        tracking = r2.tracking

        val r3 = autoFullscreenAction(
            deltaY = 10f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, r3.action) // 0 + 10 = 10, nowhere near threshold post-reset
        assertEquals(10f, r3.tracking.accumulated)
    }

    @Test fun disabledAlwaysNone() {
        // Down-scroll while fullscreen doesn't suffer the fresh-tracker free-spend that an up-scroll
        // would (see class doc: dy >= 0 agrees with the lastDirectionUp = false default) - so a single
        // call genuinely crosses the threshold here, exercising the isEnabled gate for real (not just
        // trivially staying None because nothing accumulated).
        // Per classic (scrollEv is reset unconditionally inside the `if` block, only the state mutation is
        // gated by `autoFullScreen`), the accumulator still resets even though no action fires.
        val result = autoFullscreenAction(
            deltaY = 100f, isEnabled = false, isFullScreen = true, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = AutoFullscreenTracking(),
        )
        assertEquals(FullscreenAction.None, result.action)
        assertEquals(0f, result.tracking.accumulated)
    }

    @Test fun lockedByDoubleTapSuppressesExit() {
        var tracking = AutoFullscreenTracking()
        // Prime with a small down-scroll (agrees with the fresh default, so it is NOT a flip, and
        // establishes a nonzero running accumulated value before the crossing call below - simply
        // demonstrating that accumulation threads across calls, not working around any flip risk: a
        // down-scroll from a fresh tracker was never at risk of being spent in the first place).
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
        //
        // An up-scroll from a FRESH tracker would itself be spent by the direction-flip default (see
        // class doc) and could never actually reach the threshold in one call - that would only prove
        // "nothing accumulated," not "the lock suppressed a real crossing." So this test constructs a
        // tracking value that already has lastDirectionUp = true (as if a prior up-scroll had already
        // established the direction), which is exactly the API contract Task 6 threads between real
        // calls - letting this single call genuinely cross the threshold and be suppressed by the lock.
        val tracking = AutoFullscreenTracking(accumulated = 0f, lastDirectionUp = true)
        val result = autoFullscreenAction(
            deltaY = -100f, isEnabled = true, isFullScreen = false, lockedByDoubleTap = true,
            thresholdPx = threshold, tracking = tracking,
        )
        assertEquals(FullscreenAction.None, result.action)
        assertEquals(0f, result.tracking.accumulated)
    }

    @Test fun exactlyAtThresholdDoesNotFire() {
        // Down-scroll while fullscreen doesn't need priming (see disabledAlwaysNone above) - a single
        // call from a fresh tracker already counts in full, landing exactly on the boundary.
        // Classic uses strict `<` / `>` (not `<=`/`>=`), so landing exactly on the threshold is NOT
        // (yet) a crossing.
        val result = autoFullscreenAction(
            deltaY = threshold, isEnabled = true, isFullScreen = true, lockedByDoubleTap = false,
            thresholdPx = threshold, tracking = AutoFullscreenTracking(),
        )
        assertEquals(FullscreenAction.None, result.action)
        assertEquals(threshold, result.tracking.accumulated)
    }
}
