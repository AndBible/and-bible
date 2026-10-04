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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers two regressions in the Task 6 `AutoFullscreenPolicy` port, both against classic
 * `BibleGestureListener.onScroll` (`b33072833`):
 * - [shouldReanchor] restores classic's two `scrollEv` re-anchor triggers (gesture-boundary,
 *   ~1s rate-limit - `BibleGestureListener.kt:121,124`), dropped by the original port (fire
 *   too eagerly).
 * - [reanchoredTracking] fixes fix-pass-1's own regression at the re-anchor point: a full
 *   `AutoFullscreenTracking()` reset there hardcoded `lastDirectionUp = false`, clobbering
 *   classic's persistent `lastDirection` (`:49`), which the two eventTime-driven triggers never
 *   reset in classic (they only re-anchor the POSITION anchor `scrollEv`).
 */
class GestureReanchorTest {

    @Test fun freshUninitializedAlwaysReanchors() {
        // Mirrors classic's `!::scrollEv.isInitialized` - the very first onScroll call ever
        // must reset regardless of the timestamps (which are meaningless pre-initialization).
        assertTrue(shouldReanchor(initialized = false, gestureStartTime = 0L, currentEventTime = 0L, lastAnchorTime = 0L))
        assertTrue(shouldReanchor(initialized = false, gestureStartTime = 5000L, currentEventTime = 5000L, lastAnchorTime = 12345L))
    }

    @Test fun newGestureLaterStartTimeReanchors() {
        // Mirrors classic's `e1.eventTime > scrollEv.eventTime`: a new physical touch-down's e1
        // carries a later eventTime than the anchor left over from the PRIOR gesture (or from a
        // mid-gesture flip/threshold reset) - must reanchor even though well within the 1s window.
        assertTrue(shouldReanchor(initialized = true, gestureStartTime = 2000L, currentEventTime = 2010L, lastAnchorTime = 1000L))
    }

    @Test fun overOneSecondGapReanchors() {
        // Mirrors classic's `e2.eventTime - scrollEv.eventTime > 1000`: same gesture (gestureStartTime
        // <= lastAnchorTime, so trigger (i) alone would NOT fire) but the current event lands more
        // than 1000ms after the last anchor.
        assertTrue(shouldReanchor(initialized = true, gestureStartTime = 1000L, currentEventTime = 2001L, lastAnchorTime = 1000L))
    }

    @Test fun exactlyOneSecondGapDoesNotReanchor() {
        // Classic uses strict `>`, not `>=` - landing exactly on 1000ms must NOT (yet) reanchor.
        assertFalse(shouldReanchor(initialized = true, gestureStartTime = 1000L, currentEventTime = 2000L, lastAnchorTime = 1000L))
    }

    @Test fun sameGestureWithinOneSecondDoesNotReanchor() {
        // The common "normal" case within a single continuous drag: gestureStartTime already
        // anchored (not later than lastAnchorTime) and well under the 1s window - no reset.
        assertFalse(shouldReanchor(initialized = true, gestureStartTime = 1000L, currentEventTime = 1500L, lastAnchorTime = 1000L))
    }

    @Test fun gestureStartTimeEqualToAnchorDoesNotAloneTrigger() {
        // Boundary of trigger (i): classic uses strict `>`, so e1.eventTime == scrollEv.eventTime
        // (e.g. the anchor was just advanced to this same gesture's start by a mid-gesture reset)
        // must not itself force a reanchor.
        assertEquals(false, shouldReanchor(initialized = true, gestureStartTime = 1000L, currentEventTime = 1050L, lastAnchorTime = 1000L))
    }

    // --- reanchoredTracking: pins fix-pass-2's residual-bug fix (a full AutoFullscreenTracking()
    // reset at the re-anchor point hardcoded lastDirectionUp = false, clobbering carried-over
    // direction - see reanchoredTracking's own KDoc for the full classic-parity trace). ---

    @Test fun reanchoredTrackingZeroesAccumulatorOnly() {
        // Mirrors classic re-anchoring the `scrollEv` POSITION anchor while leaving `lastDirection`
        // (`:49` at `b33072833`) completely untouched - only accumulated resets, lastDirectionUp
        // (the up case) survives the re-anchor unchanged.
        val reanchored = reanchoredTracking(AutoFullscreenTracking(accumulated = 42f, lastDirectionUp = true))
        assertEquals(AutoFullscreenTracking(accumulated = 0f, lastDirectionUp = true), reanchored)
    }

    @Test fun reanchoredTrackingPreservesDownDirectionToo() {
        // Same as above with the opposite (down) direction, to pin that preservation is
        // unconditional, not accidentally tied to the `true` boolean value.
        val reanchored = reanchoredTracking(AutoFullscreenTracking(accumulated = -30f, lastDirectionUp = false))
        assertEquals(AutoFullscreenTracking(accumulated = 0f, lastDirectionUp = false), reanchored)
    }

    @Test fun reanchoredTrackingIsNotAFullReset() {
        // Regression pin: a plain `AutoFullscreenTracking()` reset (fix pass 1's bug) would produce
        // lastDirectionUp = false regardless of the carried-over value - this must NOT equal that
        // full-reset shape when the carried direction was up.
        val reanchored = reanchoredTracking(AutoFullscreenTracking(accumulated = 42f, lastDirectionUp = true))
        assertFalse(reanchored == AutoFullscreenTracking())
    }
}
