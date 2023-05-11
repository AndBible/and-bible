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
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.OldMonolithicAppDatabase
import net.bible.android.database.OLD_DATABASE_VERSION
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.TemporaryDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.service.common.CommonUtils
import net.bible.service.db.migrations.DatabaseSplitMigrations
import net.bible.service.db.migrations.oldMonolithicAppDatabaseMigrations
import net.bible.service.history.HistoryManager.Companion.MAX_HISTORY
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

const val OLD_MONOLITHIC_DATABASE_NAME = "andBibleDatabase.db"
const val SQLITE3_MIMETYPE = "application/x-sqlite3"
const val TAG = "DbContainer"

class DataBaseNotReady: Exception()

class DatabaseContainer {
    init {
        migrateOldDatabaseIfNeeded()
        //backupDatabaseIfNeeded()
    }

    private fun getOldDatabase(): OldMonolithicAppDatabase =
        Room.databaseBuilder(
            BibleApplication.application, OldMonolithicAppDatabase::class.java, OLD_MONOLITHIC_DATABASE_NAME
        )
            .allowMainThreadQueries()
            .addMigrations(*oldMonolithicAppDatabaseMigrations)
            .build()

    private fun migrateOldDatabaseIfNeeded() {
        val oldDbFile = BibleApplication.application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME)
        if(oldDbFile.exists()) {
            getOldDatabase().openHelper.writableDatabase.use {
                val migrations = DatabaseSplitMigrations(it)
                migrations.migrateAll()
            }
            oldDbFile.delete()
        }
    }


    val bookmarkDb: BookmarkDatabase =
        Room.databaseBuilder(
            BibleApplication.application, BookmarkDatabase::class.java, BookmarkDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()


    val readingPlanDb: ReadingPlanDatabase =
        Room.databaseBuilder(
            BibleApplication.application, ReadingPlanDatabase::class.java, ReadingPlanDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    val workspaceDb: WorkspaceDatabase =
        Room.databaseBuilder(
            BibleApplication.application, WorkspaceDatabase::class.java, WorkspaceDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    val temporaryDb: TemporaryDatabase =
        Room.databaseBuilder(
            BibleApplication.application, TemporaryDatabase::class.java, TemporaryDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    val repoDb: RepoDatabase =
        Room.databaseBuilder(
            BibleApplication.application, RepoDatabase::class.java, RepoDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    val settingsDb: SettingsDatabase =
        Room.databaseBuilder(
            BibleApplication.application, SettingsDatabase::class.java, SettingsDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()


    private fun backupDatabaseIfNeeded() {
        // TODO: fix this!
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
        if(dbVersion != null && dbVersion != OLD_DATABASE_VERSION) {
            Log.i(TAG, "backupping database of version $dbVersion (current: $OLD_DATABASE_VERSION)")
            val backupPath = CommonUtils.dbBackupPath
            val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupPath, "dbBackup-$dbVersion-$timeStamp.db")
            dbPath.copyTo(backupFile, true)
        }
    }

    private val backedUpDatabases = arrayOf(bookmarkDb, readingPlanDb, workspaceDb, repoDb, settingsDb)
    private val allDatabases = arrayOf(*backedUpDatabases, temporaryDb)

    internal fun sync() = allDatabases.forEach {
        it.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)").use { c -> c.moveToFirst() }
    }

    internal fun vacuum() {
        for (it in workspaceDb.workspaceDao().allWindows()) {
            workspaceDb.workspaceDao().pruneHistory(it.id, MAX_HISTORY)
        }
        backedUpDatabases.forEach {
            it.openHelper.writableDatabase
                .query("VACUUM;").use { c -> c.moveToFirst() }
        }
    }

    internal fun closeAll() = allDatabases.forEach { it.close()}

    companion object {
        var ready: Boolean = false
        private var _instance: DatabaseContainer? = null
        val instance: DatabaseContainer get() {
            if(!ready && !BibleApplication.application.isRunningTests) throw DataBaseNotReady()
            return _instance ?: synchronized(this) {
                _instance ?: DatabaseContainer().also {
                    _instance = it
                }
            }
        }

        fun sync() = instance.sync()
        fun vacuum() = instance.vacuum()
        fun reset() {
            synchronized(this) {
                try {
                    instance.closeAll()
                } catch (e: DataBaseNotReady) {}
                _instance = null
            }
        }
    }
}
