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
import androidx.sqlite.SQLiteStatement

/** Prepares and runs [sql] to completion with [args] bound to its `?` placeholders (1-based, in order). */
fun SQLiteConnection.exec(sql: String, vararg args: Any?) =
    prepare(sql).use { st -> st.bindAll(args); while (st.step()) {} }

/**
 * Binds [args] positionally. Supports null, Long, Int, Boolean, Double, Float, String and ByteArray;
 * anything else is a programming error and throws.
 */
fun SQLiteStatement.bindAll(args: Array<out Any?>) = args.forEachIndexed { i, a ->
    val n = i + 1
    when (a) {
        null -> bindNull(n)
        is Long -> bindLong(n, a)
        is Int -> bindLong(n, a.toLong())
        is Boolean -> bindBoolean(n, a)
        is Double -> bindDouble(n, a)
        is Float -> bindDouble(n, a.toDouble())
        is String -> bindText(n, a)
        is ByteArray -> bindBlob(n, a)
        else -> error("unsupported bind type ${a::class}")
    }
}

/** Runs [sql] and maps every result row with [row]; the statement is only valid inside the lambda. */
fun <T> SQLiteConnection.queryRows(sql: String, vararg args: Any?, row: (SQLiteStatement) -> T): List<T> =
    prepare(sql).use { st -> st.bindAll(args); buildList { while (st.step()) add(row(st)) } }

/** First column of the first row as a Long, or null when there is no row or the value is SQL NULL. */
fun SQLiteConnection.queryLong(sql: String, vararg args: Any?): Long? =
    queryRows(sql, *args) { if (it.isNull(0)) null else it.getLong(0) }.firstOrNull()
