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
package net.bible.sharedlogic.platform.ios

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import okio.Path

/**
 * iOS [ReadOnlyModuleDbOpener]: the first half of Android's `openReadOnlyModuleDb` (read-only open with the bundled
 * driver plus a probe query, because the WAL/shm failure can surface on the first statement). The snapshot
 * fallback is deliberately not ported: a read-only WAL module without `-shm` fails with SQLite error 14 here as
 * on Android before the fallback, and is not "fixed" in this task.
 */
class IosReadOnlyModuleDbOpener : ReadOnlyModuleDbOpener {
    override fun open(path: Path): SQLiteConnection {
        val db = openSqlite(path.toString(), readOnly = true)
        try {
            db.queryRows("SELECT count(*) FROM sqlite_master") { it.getLong(0) }
        } catch (e: SQLiteException) {
            runCatching { db.close() }
            throw e
        }
        return db
    }
}
