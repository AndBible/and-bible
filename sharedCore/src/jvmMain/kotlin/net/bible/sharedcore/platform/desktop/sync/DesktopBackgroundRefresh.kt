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
package net.bible.sharedcore.platform.desktop.sync

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.sync.BackgroundRefresh

/** Desktop has no OS refresh scheduler; the sync driver runs its own periodic passes. Everything here only logs. */
class DesktopBackgroundRefresh : BackgroundRefresh {
    override fun register(handler: suspend () -> Boolean) { Log.d(TAG, "register ignored: the desktop sync driver schedules itself") }
    override fun schedule(earliestSeconds: Long) { Log.d(TAG, "schedule($earliestSeconds) ignored") }
    override fun cancel() { Log.d(TAG, "cancel ignored") }

    private companion object { const val TAG = "DesktopBackgroundRefresh" }
}
