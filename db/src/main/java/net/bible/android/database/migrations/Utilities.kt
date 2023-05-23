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

import android.database.sqlite.SQLiteDatabase
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

fun createTriggersForTable(db: SupportSQLiteDatabase, tableName: String, idField1: String = "id", idField2: String? = null) = db.run {
    fun where(prefix: String): String =
        if(idField2 == null) {
            "entityId1 = $prefix.$idField1"
        } else {
            "entityId1 = $prefix.$idField1 AND entityId2 = $prefix.$idField2"
        }
    fun insert(prefix: String): String =
        if(idField2 == null) {
            "$prefix.$idField1,''"
        } else {
            "$prefix.$idField1,$prefix.$idField2"
        }

    execSQL(
        "CREATE TRIGGER IF NOT EXISTS ${tableName}_inserts AFTER INSERT ON $tableName BEGIN " +
            "DELETE FROM Log WHERE ${where("NEW")} AND tableName = '$tableName';" +
            "INSERT INTO Log VALUES ('$tableName', ${insert("NEW")}, 'INSERT', STRFTIME('%s')); " +
            "END;")
    execSQL(
        "CREATE TRIGGER IF NOT EXISTS ${tableName}_updates AFTER UPDATE ON $tableName BEGIN " +
            "DELETE FROM Log WHERE ${where("OLD")} AND tableName = '$tableName';" +
            "INSERT INTO Log VALUES ('$tableName', ${insert("OLD")}, 'UPDATE', STRFTIME('%s')); " +
            "END;")
    execSQL(
        "CREATE TRIGGER IF NOT EXISTS ${tableName}_deletes AFTER DELETE ON $tableName BEGIN " +
            "DELETE FROM Log WHERE ${where("OLD")} AND tableName = '$tableName';" +
            "INSERT INTO Log VALUES ('$tableName', ${insert("OLD")}, 'DELETE', STRFTIME('%s')); " +
            "END;")
}
