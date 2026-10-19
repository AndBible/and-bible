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

package net.bible.sharedlogic.settings

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.android.database.json
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppSettings

/** Stored form of the recently used text display settings; the same JSON as Android's `LastTypesSerializer`. */
@Serializable
internal data class LastDisplayTypes(val types: MutableList<Types>)

/**
 * The text display settings the user changed last (at most [LIMIT], newest first), persisted in the app settings under
 * `lastDisplaySettings` exactly as Android's `CommonUtils.displaySettingChanged` / `lastDisplaySettingsSorted` do, so
 * the overflow and pane menus can offer them as shortcuts.
 */
class RecentDisplaySettings(private val settings: AppSettings) {
    private val last: List<Types>
        get() {
            val stored = settings.getString(KEY, null) ?: return emptyList()
            return try {
                json.decodeFromString(LastDisplayTypes.serializer(), stored).types
            } catch (e: SerializationException) {
                Log.e(TAG, "Could not deserialize $stored")
                emptyList()
            }
        }

    /** The recent types in name order (the menus' rendered order). */
    val sorted: List<Types> get() = last.sortedBy { it.name }

    /** Records [type] as the most recently used one. */
    fun changed(type: Types) {
        val types = last.toMutableList()
        types.remove(type)
        while (types.size >= LIMIT) types.removeAt(types.size - 1)
        types.add(0, type)
        settings.setString(KEY, json.encodeToString(LastDisplayTypes.serializer(), LastDisplayTypes(types)))
    }

    private companion object {
        const val KEY = "lastDisplaySettings"
        const val LIMIT = 5
        const val TAG = "RecentDisplaySettings"
    }
}
