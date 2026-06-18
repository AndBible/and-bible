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

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Centralized haptic feedback controller for the PassageFinder widget.
 *
 * Uses [View.performHapticFeedback] which requires no VIBRATE permission and
 * automatically respects the system haptic feedback accessibility setting
 * (HAPT-05). All ticks are throttled to a minimum interval of [throttleMs]
 * to prevent unpleasant buzzing during fast flings.
 */
class HapticController(private val view: View) {

    private var lastTickTime = 0L
    private val throttleMs = 60L

    /** Standard tick for crossing a book boundary in the BookStrip. */
    fun onBookBoundary() {
        throttledHaptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    /**
     * Lighter tick for crossing a chapter boundary.
     * Uses TEXT_HANDLE_MOVE on API 27+ for a subtler feel; falls back to CLOCK_TICK
     * on older devices where TEXT_HANDLE_MOVE is not supported.
     */
    fun onChapterBoundary() {
        throttledHaptic(textHandleMoveOrFallback())
    }

    /** Lighter tick for crossing a verse boundary (same constant as chapter). */
    fun onVerseBoundary() {
        throttledHaptic(textHandleMoveOrFallback())
    }

    private fun textHandleMoveOrFallback(): Int =
        if (Build.VERSION.SDK_INT >= 27) {
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }

    private fun throttledHaptic(constant: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTickTime >= throttleMs) {
            view.performHapticFeedback(constant)
            lastTickTime = now
        }
    }
}
