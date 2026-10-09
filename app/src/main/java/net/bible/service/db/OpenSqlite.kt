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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import java.io.File

/**
 * Driver for every non-Room open and (Task 16-17) every Room builder. The unit tests use the bundled
 * driver too (decided in D1 Task 1), so they never replace this.
 */
var sqliteDriverFactory: () -> SQLiteDriver = { BundledSQLiteDriver() }

/**
 * Opens a non-Room SQLite file (third-party modules, legacy files) with the bundled SQLite. Read-write opens
 * create the file when missing. Read-only opens are exercised only on the emulator (Task 18).
 *
 * Until D1 Task 17 two SQLite libraries share the process: never point this at a file a Room database currently has
 * open (closing a bundled connection drops requery's POSIX locks on that file).
 */
fun openSqlite(path: String, readOnly: Boolean = false): SQLiteConnection {
    val driver = sqliteDriverFactory()
    return if (driver is BundledSQLiteDriver) {
        driver.open(path, if (readOnly) SQLITE_OPEN_READONLY else SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
    } else {
        driver.open(path)
    }
}

/**
 * `PRAGMA user_version` of the SQLite file [file]. Opened read-write like requery's `openDatabase(OPEN_READWRITE)`
 * that this replaces, so a hot journal left by a crash is rolled back first; unlike it, a missing file is created
 * (every caller checks the file exists first).
 *
 * Until D1 Task 17 two SQLite libraries share the process: never point this at a file a Room database currently has
 * open (closing a bundled connection drops requery's POSIX locks on that file).
 */
fun readUserVersion(file: File): Int = openSqlite(file.path).use { it.queryLong("PRAGMA user_version")!!.toInt() }
