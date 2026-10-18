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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.reading

import net.bible.sharedui.reading.session.SessionEventRelayPlatform

/**
 * iOS [SessionEventRelayPlatform]. There is no tilt scrolling or per-view notification flag on iOS, and the pane
 * sits inside the host layout (no offsets to refresh), so those members do nothing; [updateConfig] re-sends
 * `set_config` through the window's loader.
 */
@kotlin.native.HiddenFromObjC
class IosSessionEventRelayPlatform(private val updateConfigNow: () -> Unit) : SessionEventRelayPlatform {
    /** Set by a layout configuration change; the next load-time check re-reads the windows (nothing reads it on iOS yet). */
    var checkWindows: Boolean = false
        private set

    override fun onActiveWindowChanged(isThisWindowActive: Boolean) = Unit

    override fun updateOffsets(immediate: Boolean) = Unit

    override fun markWindowsCheckNeeded() { checkWindows = true }

    override fun updateConfig() = updateConfigNow()
}
