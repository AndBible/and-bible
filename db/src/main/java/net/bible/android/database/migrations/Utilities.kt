/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database.migrations

import android.util.Log
import androidx.room.migration.Migration as RoomMigration
import androidx.sqlite.db.SupportSQLiteDatabase

abstract class Migration(startVersion: Int, endVersion: Int): RoomMigration(startVersion, endVersion) {
    abstract fun doMigrate(db: SupportSQLiteDatabase)

    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Migrating from version $startVersion to $endVersion")
        doMigrate(db)
    }
}

fun makeMigration(versionRange: IntRange, migration: (db: SupportSQLiteDatabase) -> Unit): Migration =
    object: Migration(versionRange.first, versionRange.last) {
        override fun doMigrate(db: SupportSQLiteDatabase) {
            migration.invoke(db)
        }
    }
