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

/** iOS counterpart of `java.util.Date`: epoch milliseconds with value equality and natural ordering. */
actual class DbDate actual constructor(private val millis: Long) : Comparable<DbDate> {
    actual constructor() : this(currentTimeMillis())

    actual fun getTime(): Long = millis

    override fun compareTo(other: DbDate): Int = millis.compareTo(other.millis)
    override fun equals(other: Any?): Boolean = other is DbDate && other.millis == millis
    override fun hashCode(): Int = millis.hashCode()
    override fun toString(): String = "DbDate($millis)"
}
