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
 * Driver for every non-Room open and every Room builder ([buildAppDatabase]). The unit tests use the bundled
 * driver too, so nothing replaces this.
 */
val sqliteDriverFactory: () -> SQLiteDriver = { BundledSQLiteDriver() }

/**
 * Opens a non-Room SQLite file (third-party modules, legacy files) with the bundled SQLite. Read-write opens
 * create the file when missing. Read-only opens are exercised only on the emulator, not by the unit tests.
 * Room uses the same bundled library, so POSIX locks on a file are shared with Room's connections.
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
 * `PRAGMA user_version` of the SQLite file [file]. Opened read-write like the `openDatabase(OPEN_READWRITE)`
 * that this replaced, so a hot journal left by a crash is rolled back first; unlike it, a missing file is created
 * (every caller checks the file exists first).
 */
fun readUserVersion(file: File): Int = openSqlite(file.path).use { it.queryLong("PRAGMA user_version")!!.toInt() }
