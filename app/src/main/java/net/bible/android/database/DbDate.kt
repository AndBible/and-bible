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

import java.util.Date

/**
 * Date type of the move set. On the JVM it is exactly [java.util.Date]; it becomes an `expect class` when the
 * move set goes common. Moved code calls `getTime()` rather than the Kotlin `.time` property so that the common
 * declaration needs only the functions `DbDate()`, `DbDate(millis)` and `getTime()`.
 */
typealias DbDate = Date
