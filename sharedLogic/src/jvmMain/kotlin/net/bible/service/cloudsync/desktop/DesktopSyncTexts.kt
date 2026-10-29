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
package net.bible.service.cloudsync.desktop

import net.bible.sharedcore.log.Log
import org.koin.core.Koin

private const val TAG = "DesktopSyncTexts"
private const val HOLDER_CLASS = "net.bible.sharedui.assembly.IosStringTableHolder"

/**
 * Looks existing string-table keys up in the app's string table. :sharedLogic cannot depend on :sharedUi, where the table and
 * its Koin holder live, so the holder (the `current` string table with its `text(key)`) is reached by reflection. A key that cannot
 * be resolved is returned as it is, which only shows in a broken packaging. No new strings are involved.
 */
fun reflectiveStringLookup(koin: Koin, holderClassName: String = HOLDER_CLASS): (String) -> String = { key ->
    try {
        val holder = checkNotNull(koin.getOrNull<Any>(Class.forName(holderClassName).kotlin)) { "no $holderClassName in Koin" }
        val table = holder.javaClass.getMethod("getCurrent").invoke(holder)
        table.javaClass.getMethod("text", String::class.java).invoke(table, key) as String
    } catch (e: Exception) {
        Log.w(TAG, "string $key unavailable: ${e.message}")
        key
    }
}

/** The display names of the desktop's cloud back ends and the WebDAV URL hint, from existing keys. */
class DesktopSyncTexts(private val lookup: (String) -> String) {
    fun adapterDisplayName(id: String): String = lookup(
        when (id) {
            "NEXT_CLOUD" -> "adapters_next_cloud"
            "WEBDAV" -> "adapters_webdav"
            else -> error("Unknown cloud adapter: $id")
        },
    )

    fun serverUrlHint(): String = lookup("webdav_server_url_hint")
}
