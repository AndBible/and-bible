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
package net.bible.sharedui.desktop.reading

import net.bible.sharedui.host.reading.IosReadingGestures
import net.bible.sharedui.host.reading.IosReadingWindows
import kotlin.math.abs

/**
 * Maps the page's wheel and click messages onto the hoisted [IosReadingGestures] (full screen on scroll, pane touched,
 * swipe mode), the desktop's stand-in for the finger gestures of iOS. Chromium scrolls the page itself; this only
 * mirrors the movement for the gesture rules.
 *
 * A wheel gesture is a run of wheel frames with gaps of at most [GESTURE_GAP_MILLIS]. Vertical movement goes to
 * [IosReadingGestures.onScroll] as a Float, never rounded, so fractional wheel deltas add up. Horizontal movement is
 * summed per gesture; it becomes at most one swipe per gesture, and only when it is clearly horizontal.
 *
 * Swing thread only.
 *
 * @param gestures the gestures of a window, null when no reading host is bound
 */
class DesktopReadingGestures(
    private val gestures: (windowId: String) -> IosReadingGestures? = { IosReadingWindows.gesturesFor(it) },
) {
    private class Gesture(var lastMillis: Long, var sumX: Double = 0.0, var sumY: Double = 0.0, var swiped: Boolean = false)

    private val state = mutableMapOf<String, Gesture>()

    /** A coalesced wheel frame of [windowId]: pixel deltas [dx], [dy] (positive = content moves towards its end/right) at [timeMillis]. */
    fun onWheel(windowId: String, dx: Double, dy: Double, timeMillis: Long) {
        val g = gestures(windowId) ?: return
        var s = state[windowId]
        if (s == null || timeMillis - s.lastMillis > GESTURE_GAP_MILLIS || timeMillis < s.lastMillis) {
            s = Gesture(timeMillis)
            state[windowId] = s
            g.onScrollGestureBegan(timeMillis)
        }
        s.lastMillis = timeMillis
        s.sumX += dx
        s.sumY += dy
        if (dy != 0.0) g.onScroll(-dy.toFloat(), timeMillis)
        if (!s.swiped && abs(s.sumX) >= SWIPE_MIN_PX && abs(s.sumX) > 2 * abs(s.sumY)) {
            s.swiped = true
            g.onSwipe(windowId, towardsNext = s.sumX > 0)
        }
    }

    /** A click in the page: a touch for the gestures (restarts the window buttons timer). The click itself is the page's. */
    fun onClick(windowId: String) {
        gestures(windowId)?.onTouched()
    }

    /** Forgets the window's gesture state. */
    fun forget(windowId: String) {
        state.remove(windowId)
    }

    companion object {
        const val GESTURE_GAP_MILLIS = 250L
        const val SWIPE_MIN_PX = 150.0
    }
}
