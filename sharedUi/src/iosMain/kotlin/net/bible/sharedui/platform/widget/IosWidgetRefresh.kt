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

import net.bible.sharedcore.log.Log

/**
 * The guard of `IosPlatformBootstrap.refreshWidgetSnapshot` (Swift calls it on every `.active`): the writer is built
 * through Koin, which reads labels, Bibles and SWORD, so it must not even be resolved before reading is ready (startup
 * is databases-only until phase 2; memory `ios-startup-is-databases-only-until-phase-2`). A failure never crosses the
 * Swift boundary (the Kotlin function has no `@Throws`).
 */
internal object IosWidgetRefresh {
    private const val TAG = "IosWidgetRefresh"

    /** Runs [action] only when [ready]; true when it ran without throwing. */
    fun runWhenReady(ready: () -> Boolean, action: () -> Unit): Boolean {
        if (!ready()) return false
        return try {
            action()
            true
        } catch (e: Throwable) {
            Log.w(TAG, "widget snapshot refresh failed", e)
            false
        }
    }
}
