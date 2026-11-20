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

package net.bible.sharedui.platform.widget

/**
 * Holds widget actions until the app is Ready (databases and window repository loaded), then runs them in arrival
 * order; later actions run at once. Koin existing is not enough: a headless intent leaves Koin up with no UI.
 * Not thread-safe; the caller uses one thread (Main).
 */
class WidgetUrlQueue(private val run: (WidgetUrlAction) -> Unit) {
    private val pending = ArrayDeque<WidgetUrlAction>()
    private var ready = false

    /** Runs [action] now when Ready, else keeps it for [markReady]. */
    fun submit(action: WidgetUrlAction) {
        if (ready) run(action) else pending.addLast(action)
    }

    /** Startup is Ready: runs what is waiting and everything after at once. */
    fun markReady() {
        ready = true
        while (pending.isNotEmpty()) run(pending.removeFirst())
    }
}
