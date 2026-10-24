/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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


package net.bible.sharedlogic.platform

import androidx.sqlite.SQLiteConnection
import okio.Path

/**
 * Opens a third-party SQLite module (MyBible, MySword, e-Sword) read-only. The Android implementation also
 * handles read-only WAL modules by opening a snapshot copy, which needs file permissions, a cache directory and
 * file copying that are platform facts.
 */
interface ReadOnlyModuleDbOpener {
    /** Throws [androidx.sqlite.SQLiteException] when the module cannot be opened. */
    fun open(path: Path): SQLiteConnection
}
