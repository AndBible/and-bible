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

/**
 * Whether the caller should reset [AutoFullscreenTracking] to a fresh accumulator BEFORE
 * delegating to [autoFullscreenAction], mirroring the two gesture-boundary re-anchor triggers
 * classic `BibleGestureListener.onScroll` applies to its `scrollEv` anchor
 * (`view/activity/page/BibleGestureListener.kt:121,124` at `b33072833`, prior to the Task 6
 * `AutoFullscreenPolicy` port) - dropped by that port and restored as caller-side bookkeeping here,
 * since eventTime bookkeeping is Android-`MotionEvent`-specific plumbing the pure, multiplatform
 * policy was never meant to absorb.
 *
 * Trigger (i) - **new gesture**: classic `!::scrollEv.isInitialized || e1.eventTime > scrollEv.eventTime`.
 * A new physical touch-down's `e1` always carries a later `eventTime` than whatever the anchor was
 * last set to, so this fires once per finger-lift/re-touch - without it, two separate short
 * same-direction swipes (each individually under threshold) stack their accumulation across the
 * finger-lift and can spuriously cross it.
 *
 * Trigger (ii) - **~1s rate-limit**: classic `e2.eventTime - scrollEv.eventTime > 1000`. Re-anchors
 * mid-gesture whenever more than a second elapsed since the last anchor, capping accumulation to a
 * rolling <1s window - without it, a slow multi-second reading-scroll accumulates unbounded and can
 * cross the threshold where classic never would.
 *
 * @param initialized Whether an anchor has EVER been recorded (mirrors classic `::scrollEv.isInitialized`).
 *   `false` unconditionally forces a reset - matching classic's very first `onScroll` call ever.
 * @param gestureStartTime This call's `e1.eventTime` (the down-event that started the CURRENT gesture).
 * @param currentEventTime This call's `e2.eventTime` (the current move event).
 * @param lastAnchorTime The remembered anchor eventTime from the previous reset (caller's own
 *   bookkeeping - see [autoFullscreenAction]'s KDoc for the mid-gesture re-anchor points, i.e.
 *   direction-flip/threshold-cross, that also advance this value between calls to this function).
 */
fun shouldReanchor(
    initialized: Boolean,
    gestureStartTime: Long,
    currentEventTime: Long,
    lastAnchorTime: Long,
): Boolean {
    if (!initialized) return true
    if (gestureStartTime > lastAnchorTime) return true
    if (currentEventTime - lastAnchorTime > 1000) return true
    return false
}
