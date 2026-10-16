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

package net.bible.android.database

/**
 * Date type of the database layer, stored as epoch milliseconds. On the JVM and Android it is exactly
 * `java.util.Date`; on iOS it is a millisecond-backed class with value equality. Callers use the functions
 * `DbDate()`, `DbDate(millis)` and `getTime()` only.
 */
expect class DbDate : Comparable<DbDate> {
    /** The current time. */
    constructor()
    constructor(millis: Long)

    fun getTime(): Long
}
