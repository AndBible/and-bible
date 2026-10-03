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

/** Outcome of evaluating one scroll increment against the auto-fullscreen threshold. */
enum class FullscreenAction { None, Enter, Exit }

/**
 * Accumulated scroll state carried between [autoFullscreenAction] calls (one call per scroll
 * increment). [accumulated] is the running distance in px - same sign convention as [autoFullscreenAction]'s
 * `deltaY` (down = positive) - since the last direction change or threshold crossing.
 *
 * [lastDirectionUp] mirrors classic `BibleGestureListener`'s `lastDirection` field
 * (`view/activity/page/BibleGestureListener.kt:49`), which stores `distanceY > 0` from the
 * *previous* `onScroll` call. This API's `deltaY` uses the *opposite* sign convention to Android's
 * own `distanceY` (`deltaY = -distanceY`, per the Task 6 bridge - down = positive here, whereas
 * `distanceY > 0` means the touch moved *up*), so classic's flip-bit expressed in `deltaY` terms is
 * `deltaY < 0` (an upward increment) - hence this field's name and the comparison in
 * [autoFullscreenAction] below (`directionUp = deltaY < 0`).
 *
 * The default `false` mirrors classic's `lastDirection = false` initial value verbatim - including
 * its asymmetry: on a genuinely fresh [AutoFullscreenTracking], the first call reporting an
 * *upward* (`deltaY < 0`) increment disagrees with the `false` default and is therefore treated as
 * a direction flip - it is "spent" (contributes nothing to [accumulated], see below) exactly like
 * classic's first up-scroll from a cold `BibleGestureListener`. The first call reporting a
 * *downward or zero* (`deltaY >= 0`) increment agrees with the default and is NOT a flip - it
 * counts immediately. This baseline asymmetry belongs to classic itself (an arbitrary but load-
 * bearing default) and must be reproduced verbatim, not "fixed" into a symmetric one.
 */
data class AutoFullscreenTracking(val accumulated: Float = 0f, val lastDirectionUp: Boolean = false)

/** [action] to take this call, and the updated [tracking] to thread into the next call. */
data class AutoFullscreenResult(val action: FullscreenAction, val tracking: AutoFullscreenTracking)

/**
 * Mirrors classic `BibleGestureListener.onScroll` (view/activity/page/BibleGestureListener.kt:119-150).
 *
 * Accumulates [deltaY] (screen convention **down = positive** - the raw per-call touch-position delta,
 * i.e. classic's `e2.y - scrollEv.y` increment, which is the *negation* of Android GestureDetector's own
 * `distanceY`) across calls via [tracking]. Whenever `deltaY < 0` (an upward increment - matching
 * classic's `distanceY > 0` flip-bit, see [AutoFullscreenTracking.lastDirectionUp]) differs from
 * [AutoFullscreenTracking.lastDirectionUp], the accumulator resets to zero for *this* call (the call
 * that flipped direction never itself reaches a threshold) - exactly mirroring classic's
 * `scrollEv = MotionEvent.obtain(e2)` reassignment happening *before* `dist` is (re)computed on a
 * direction change.
 *
 * Once the accumulator's magnitude reaches [thresholdPx] (56dp-equivalent in classic - `SCROLL_DIP` -
 * caller passes the already-converted px value):
 * - not [isFullScreen] and accumulated < -[thresholdPx] (sustained upward scroll) -> candidate [FullscreenAction.Enter]
 * - [isFullScreen] and accumulated > [thresholdPx] (sustained downward scroll) -> candidate [FullscreenAction.Exit]
 *
 * Either crossing resets the accumulator to zero **unconditionally** - regardless of whether the action
 * below actually fires - matching classic, where the `scrollEv` reset sits directly inside the `if` block,
 * independent of the `autoFullScreen`/`lastFullScreenByDoubleTap` gates that follow it. [isEnabled] mirrors
 * classic's `auto_fullscreen_pref`; [lockedByDoubleTap] mirrors `lastFullScreenByDoubleTap` (set once
 * fullscreen was entered via double-tap, cleared as soon as fullscreen next exits). Both gate *only*
 * whether a crossing actually produces an action - never the accumulator reset - and both apply to Enter
 * and Exit symmetrically, since classic uses the identical `!lastFullScreenByDoubleTap && autoFullScreen`
 * guard in both branches (`:138` and `:144`).
 */
fun autoFullscreenAction(
    deltaY: Float,
    isEnabled: Boolean,
    isFullScreen: Boolean,
    lockedByDoubleTap: Boolean,
    thresholdPx: Float,
    tracking: AutoFullscreenTracking,
): AutoFullscreenResult {
    val directionUp = deltaY < 0
    var accumulated = if (directionUp != tracking.lastDirectionUp) 0f else tracking.accumulated + deltaY

    var action = FullscreenAction.None
    if (!isFullScreen && accumulated < -thresholdPx) {
        if (isEnabled && !lockedByDoubleTap) action = FullscreenAction.Enter
        accumulated = 0f
    } else if (isFullScreen && accumulated > thresholdPx) {
        if (isEnabled && !lockedByDoubleTap) action = FullscreenAction.Exit
        accumulated = 0f
    }
    return AutoFullscreenResult(action, AutoFullscreenTracking(accumulated, directionUp))
}
