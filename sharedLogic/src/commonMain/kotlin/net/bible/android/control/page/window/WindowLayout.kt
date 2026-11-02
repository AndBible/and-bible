/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.control.page.window

import net.bible.android.database.WorkspaceEntities

class WindowLayout(entity: WorkspaceEntities.WindowLayout?) {
    fun restoreFrom(entity: WorkspaceEntities.WindowLayout) {
        this.weight = entity.weight
        this.state = WindowState.fixedValueOf(entity.state)
    }

    var state =
        if(entity != null) WindowState.fixedValueOf(entity.state) else WindowState.VISIBLE

    var weight = entity?.weight ?: 1.0f


    enum class WindowState {
        VISIBLE,
        MINIMISED,
        CLOSED;
        companion object {
            /**
             * The state stored in `Window.window_layout_state`. Besides the names above it accepts the legacy Android
             * "SPLIT" and the states of the old Swift iOS app (`split`, `minimized`, `closed`, `maximized`; also in its
             * "Android format" backups), in any case. An unknown value opens the window VISIBLE instead of throwing:
             * a throw here fails the whole window repository load, so reading never opens.
             */
            fun fixedValueOf(state: String) =
                when (state.uppercase()) {
                    "VISIBLE", "SPLIT", "MAXIMIZED", "MAXIMISED" -> VISIBLE
                    "MINIMISED", "MINIMIZED" -> MINIMISED
                    "CLOSED" -> CLOSED
                    else -> VISIBLE
                }
        }
    }
}
