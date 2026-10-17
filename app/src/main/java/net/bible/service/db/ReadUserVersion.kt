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

package net.bible.service.db

/**
 * `PRAGMA user_version` of the SQLite file [path]. Opened read-write like the `openDatabase(OPEN_READWRITE)`
 * that this replaced, so a hot journal left by a crash is rolled back first; unlike it, a missing file is created
 * (every caller checks the file exists first).
 */
fun readUserVersion(path: String): Int = openSqlite(path).use { it.queryLong("PRAGMA user_version")!!.toInt() }
