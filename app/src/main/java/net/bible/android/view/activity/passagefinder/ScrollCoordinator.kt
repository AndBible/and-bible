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

import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordinates scroll state between user-initiated scrolling and programmatic re-centering
 * to prevent feedback loops where scroll-settle → selection update → re-center → misalignment.
 *
 * When a strip's snap settles on an item, the scroll-settle callback notifies the state
 * layer of the new selection. Without coordination, that state change would immediately
 * trigger a re-center animation back onto the very item the user just landed on — at best
 * redundant work, at worst a visible twitch if the two disagree by a fraction of a pixel.
 * This class breaks that loop.
 */
class ScrollCoordinator {
    // Counter rather than a boolean so overlapping programmatic scrolls (a re-centre
    // retargeted while the previous one is still running) don't let an earlier exit clear
    // the flag while a later scroll is still in flight.
    private val programmaticScrollCount = AtomicInteger(0)

    /** True while at least one programmatic scroll is in progress (suppresses scroll-settle callbacks). */
    val programmaticScroll: Boolean
        get() = programmaticScrollCount.get() > 0

    /** True when the last selection change was triggered by scroll-settle (skip re-center). */
    private var scrollTriggeredSelection: Boolean = false

    /**
     * Called by the scroll-settle callback before notifying the state layer of a new
     * selection. Marks the upcoming selection change as scroll-triggered so the re-centre
     * that follows it knows to skip.
     */
    fun markScrollSettled() {
        scrollTriggeredSelection = true
    }

    /**
     * Called from the re-centre path when the selection changes.
     * Returns true if the re-centre should proceed (external change),
     * false if it should be skipped (scroll-triggered change).
     */
    fun shouldRecenter(): Boolean {
        if (scrollTriggeredSelection) {
            scrollTriggeredSelection = false
            return false
        }
        return true
    }

    /**
     * Marks the start of a programmatic scroll, keeping [programmaticScroll] true — and
     * so scroll-settle callbacks suppressed — until the matching [endProgrammaticScroll].
     *
     * A programmatic re-center animates across many frames, so its start and end are
     * necessarily separate events rather than a scoped block. Pair every call with an
     * [endProgrammaticScroll], including on the paths where the animation is cut short
     * by the user grabbing the strip again.
     */
    fun beginProgrammaticScroll() {
        programmaticScrollCount.incrementAndGet()
    }

    /**
     * Ends one programmatic scroll started by [beginProgrammaticScroll].
     *
     * Floors at zero so an unbalanced extra call cannot drive the counter negative and
     * leave the flag stuck off. Written as a compare-and-set loop rather than
     * `updateAndGet`, which needs API 24.
     */
    fun endProgrammaticScroll() {
        while (true) {
            val current = programmaticScrollCount.get()
            if (current <= 0) return
            if (programmaticScrollCount.compareAndSet(current, current - 1)) return
        }
    }

    /**
     * Clears all coordination state, for reuse across an open/close cycle.
     *
     * [markScrollSettled] is normally paired with the [shouldRecenter] of the re-centre it
     * suppresses, but the widget can close in between — a settle in one frame and a back
     * press before the state change is delivered. The flag would then survive into the
     * next session and swallow its first re-centre, leaving the strip parked somewhere the
     * state does not agree with.
     */
    fun reset() {
        scrollTriggeredSelection = false
        programmaticScrollCount.set(0)
    }
}
