/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.room.Room
import net.bible.android.BibleApplication
import net.bible.android.database.OldMonolithicAppDatabase
import net.bible.android.database.DATABASE_VERSION
import net.bible.service.common.CommonUtils
import net.bible.service.db.migrations.oldMonolithicAppDatabaseMigrations
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

const val OLD_MONOLITHIC_DATABASE_NAME = "andBibleDatabase.db"
const val SQLITE3_MIMETYPE = "application/x-sqlite3"
const val TAG = "DbContainer"

class DataBaseNotReady: Exception()

object DatabaseContainer {
    private var oldDbInstance: OldMonolithicAppDatabase? = null

    var ready: Boolean = false

    private fun backupDatabaseIfNeeded() {
        val dbPath = BibleApplication.application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME)
        var dbVersion: Int? = null
        Log.i(TAG, "backupDatabaseIfNeeded")
        try {
            val db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, OPEN_READONLY)
            db.use { d ->
                val cursor = d.rawQuery("PRAGMA user_version", null)
                cursor.use { c ->
                    while (c.moveToNext()) {
                        dbVersion = c.getIntOrNull(0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "Could not backup database. Maybe fresh install.")
        }
        if(dbVersion != null && dbVersion != DATABASE_VERSION) {
            Log.i(TAG, "backupping database of version $dbVersion (current: $DATABASE_VERSION)")
            val backupPath = CommonUtils.dbBackupPath
            val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupPath, "dbBackup-$dbVersion-$timeStamp.db")
            dbPath.copyTo(backupFile, true)
        }
    }

    val oldDb: OldMonolithicAppDatabase
        get () {
            if(!ready && !BibleApplication.application.isRunningTests) throw DataBaseNotReady()

            return oldDbInstance ?: synchronized(this) {
                backupDatabaseIfNeeded()

                Log.i(TAG, "Opening database")
                oldDbInstance ?: Room.databaseBuilder(
                    BibleApplication.application, OldMonolithicAppDatabase::class.java, OLD_MONOLITHIC_DATABASE_NAME
                )
                    .allowMainThreadQueries()
                    .addMigrations(*oldMonolithicAppDatabaseMigrations)
                    .build()
                    .also {
                        oldDbInstance = it
                        Log.i(TAG, "Database opened.")
                    }
            }
        }
    fun reset() {
        synchronized(this) {
            try {
                oldDb.close()
            } catch (e: DataBaseNotReady) {}
            oldDbInstance = null
        }
    }

    fun sync() { // Sync all data so far into database file
        oldDb.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)").use {
                it.moveToFirst()
            }
    }
    fun vacuum() {
        oldDb.documentSearchDao().clear()
        oldDb.openHelper.writableDatabase
            .query("VACUUM;").use {
                it.moveToFirst()
            }
    }
}
