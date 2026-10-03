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
 * One-shot open->closed edge detector for the reading-view drawer.
 *
 * Feed it every observed open/closed value; [observe] returns `true` **only** on an open->closed
 * transition. Seeding from the initial value is the point: a raw `collect {}` over drawer state
 * re-fires on every (re)subscription replay, which would re-run the one-shot side effect
 * (`bibleView.requestFocus()`) on each recomposition-triggered resubscribe. That is the banked
 * lesson from the earlier batches' StateFlow-bridged one-shot side effects.
 */
class DrawerCloseLatch(initiallyOpen: Boolean) {
    private var wasOpen = initiallyOpen

    /** `true` iff this observation is the open->closed transition. */
    fun observe(isOpen: Boolean): Boolean {
        val fired = wasOpen && !isOpen
        wasOpen = isOpen
        return fired
    }
}
