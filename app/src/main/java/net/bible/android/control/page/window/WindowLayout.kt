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
        val entityWeight = entity.weight
        this.weight = when {
            entityWeight <= 0f || !entityWeight.isFinite() -> {
                android.util.Log.w("WindowLayout", "Invalid weight in restoreFrom: $entityWeight, using default 1.0f")
                1.0f
            }
            else -> entityWeight
        }
        this.state = WindowState.fixedValueOf(entity.state)
    }

    var state =
        if(entity != null) WindowState.fixedValueOf(entity.state) else WindowState.VISIBLE

    var weight = run {
        val entityWeight = entity?.weight
        when {
            entityWeight == null -> 1.0f
            entityWeight <= 0f || !entityWeight.isFinite() -> {
                // Log warning about invalid weight and use default
                android.util.Log.w("WindowLayout", "Invalid weight value: $entityWeight, using default 1.0f")
                1.0f
            }
            else -> entityWeight
        }
    }


    enum class WindowState {
        VISIBLE,
        MINIMISED,
        CLOSED;
        companion object {
            fun fixedValueOf(state: String) =
                when(state) {
                    "SPLIT" -> VISIBLE
                    else -> valueOf(state)
                }
        }
    }
}
