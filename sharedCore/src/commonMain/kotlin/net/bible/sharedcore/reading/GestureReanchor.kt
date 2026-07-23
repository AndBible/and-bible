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

/**
 * The [AutoFullscreenTracking] to install at a [shouldReanchor]-triggered re-anchor point.
 *
 * Zeroes only [AutoFullscreenTracking.accumulated] and PRESERVES [AutoFullscreenTracking.lastDirectionUp]
 * from [current] - this mirrors classic re-anchoring `scrollEv` (the Y-position anchor,
 * `MotionEvent.obtain(e2)` at `BibleGestureListener.kt:121,124` at `b33072833`) while leaving
 * `lastDirection` (`:49`) completely untouched: classic's two eventTime-driven re-anchor triggers
 * (`shouldReanchor`'s two conditions) reset the POSITION anchor only. `lastDirection` is genuine
 * persistent cross-gesture state in classic - it changes ONLY on an actually observed direction
 * flip (`:132`), never on a gesture boundary or the ~1s idle gap.
 *
 * A full `AutoFullscreenTracking()` reset here (as the first fix pass did) hardcodes
 * `lastDirectionUp = false`, silently discarding whatever direction was carried over - which
 * diverges from classic in the `(prev = up, new = down)` case: classic sees a flip on the first
 * downward delta of the new gesture/window (discards that delta, `scrollEv = e2` with no `dist`
 * yet contributing) and requires a full fresh 56dp downward accumulation before exiting
 * fullscreen; the full-reset port instead sees `directionUp(new) = false == lastDirectionUp(reset) = false`
 * - agreement, not a flip - so that first downward delta counts immediately, making the port
 * cross the exit threshold with LESS accumulated downward movement than classic. This is exactly
 * the app's core interaction pattern (an upward swipe enters fullscreen, the finger lifts, a
 * downward swipe exits it), so the divergence is reachable and strictly MORE eager than classic in
 * that direction - a behaviour-preserving-contract violation the full reset introduced.
 */
fun reanchoredTracking(current: AutoFullscreenTracking): AutoFullscreenTracking =
    current.copy(accumulated = 0f)
