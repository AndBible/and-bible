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

import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.database.migrations.joinColumnNames

/**
 * `getColumnNames` / `getColumnNamesJoined` for the callers still on `SupportSQLiteDatabase`
 * (cloud sync patch handling, StudyPad export, DB import). The migrations' own versions take an
 * `SQLiteConnection` since D1 Task 13. TEMPORARY: D1 Task 15 converts those callers and deletes this file.
 */
fun getColumnNames(db: SupportSQLiteDatabase, tableName: String, schema: String? = null): List<String> {
    val schemaString = schema?.let { "$it." } ?: ""
    db.query("PRAGMA ${schemaString}table_info($tableName)").use { cursor ->
        val columnNameIdx = cursor.getColumnIndex("name")
        val columnNames = mutableListOf<String>()
        while (cursor.moveToNext()) columnNames.add(cursor.getString(columnNameIdx))
        return columnNames
    }
}

fun getColumnNamesJoined(db: SupportSQLiteDatabase, tableName: String, schema: String? = null): String =
    joinColumnNames(getColumnNames(db, tableName, schema))
