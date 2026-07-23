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
 * Covers the regression the Task 6 `AutoFullscreenPolicy` port introduced: classic
 * `BibleGestureListener.onScroll`'s two `scrollEv` re-anchor triggers (gesture-boundary,
 * ~1s rate-limit - `BibleGestureListener.kt:121,124` at `b33072833`) were dropped, letting
 * fullscreen fire more eagerly than classic. [shouldReanchor] is the caller-side decision
 * restoring them; these tests pin its four cases directly against classic's two `if`s.
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
}
