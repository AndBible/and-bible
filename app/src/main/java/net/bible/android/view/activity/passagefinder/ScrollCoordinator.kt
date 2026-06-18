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
 * When a LazyRow's snap fling settles on an item, the scroll-settle callback notifies the
 * parent of the new selection. Without coordination, the parent's state change triggers a
 * LaunchedEffect that calls scrollToItem — which aligns to viewport start, not center,
 * displacing the already-centered item. This class breaks that feedback loop.
 */
class ScrollCoordinator {
    // Counter rather than a boolean so overlapping programmatic scrolls (e.g. a new
    // LaunchedEffect starting before the previous one's `finally` runs) don't let an
    // earlier exit clear the flag while a later scroll is still in flight.
    private val programmaticScrollCount = AtomicInteger(0)

    /** True while at least one programmatic scroll is in progress (suppresses scroll-settle callbacks). */
    val programmaticScroll: Boolean
        get() = programmaticScrollCount.get() > 0

    /** True when the last selection change was triggered by scroll-settle (skip re-center). */
    private var scrollTriggeredSelection: Boolean = false

    /**
     * Called by the scroll-settle callback before notifying the parent of a new selection.
     * Marks the upcoming selection change as scroll-triggered so the re-center
     * LaunchedEffect knows to skip.
     */
    fun markScrollSettled() {
        scrollTriggeredSelection = true
    }

    /**
     * Called by the re-center LaunchedEffect when selection changes.
     * Returns true if the re-center should proceed (external change),
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
     * Wraps a programmatic scroll operation, keeping [programmaticScroll] true for the
     * duration so scroll-settle callbacks are suppressed. Re-entrant: nested or overlapping
     * calls each bump the counter, and the flag only clears when every active call has
     * returned (or been cancelled).
     */
    suspend fun <T> withProgrammaticScroll(block: suspend () -> T): T {
        programmaticScrollCount.incrementAndGet()
        try {
            return block()
        } finally {
            programmaticScrollCount.decrementAndGet()
        }
    }
}
