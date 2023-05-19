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
import android.util.Log
import androidx.room.Room
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.control.backup.BackupControl
import net.bible.android.database.BOOKMARK_DATABASE_VERSION
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.OldMonolithicAppDatabase
import net.bible.android.database.READING_PLAN_DATABASE_VERSION
import net.bible.android.database.REPO_DATABASE_VERSION
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SETTINGS_DATABASE_VERSION
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.TemporaryDatabase
import net.bible.android.database.WORKSPACE_DATABASE_VERSION
import net.bible.android.database.WorkspaceDatabase
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabasePatching.createTriggers
import net.bible.service.db.DatabasePatching.dropTriggers
import net.bible.service.db.migrations.DatabaseSplitMigrations
import net.bible.service.db.migrations.oldMonolithicAppDatabaseMigrations
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

const val OLD_MONOLITHIC_DATABASE_NAME = "andBibleDatabase.db"

const val TAG = "DbContainer"

val ALL_DB_FILENAMES = arrayOf(
    BookmarkDatabase.dbFileName,
    ReadingPlanDatabase.dbFileName,
    WorkspaceDatabase.dbFileName,
    RepoDatabase.dbFileName,
    SettingsDatabase.dbFileName
)

class DataBaseNotReady: Exception()



class DatabaseContainer {
    init {
        backupDatabaseIfNeeded()
        migrateOldDatabaseIfNeeded()
    }

    private fun getOldDatabase(): OldMonolithicAppDatabase =
        Room.databaseBuilder(
            application, OldMonolithicAppDatabase::class.java, OLD_MONOLITHIC_DATABASE_NAME
        )
            .allowMainThreadQueries()
            .addMigrations(*oldMonolithicAppDatabaseMigrations)
            .build()

    private fun migrateOldDatabaseIfNeeded() {
        val oldDbFile = application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME)
        if(oldDbFile.exists()) {
            getOldDatabase().openHelper.writableDatabase.use {
                val migrations = DatabaseSplitMigrations(it)
                migrations.migrateAll()
            }
            oldDbFile.delete()
        }
    }
    fun getBookmarkDb(filename: String = BookmarkDatabase.dbFileName) = Room.databaseBuilder(
        application, BookmarkDatabase::class.java, filename
    )
        .allowMainThreadQueries()
        .addMigrations()
        .build()

    val bookmarkDb: BookmarkDatabase = getBookmarkDb()

    fun getReadingPlanDb(filename: String = ReadingPlanDatabase.dbFileName) =
        Room.databaseBuilder(
            application, ReadingPlanDatabase::class.java, filename
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    val readingPlanDb: ReadingPlanDatabase = getReadingPlanDb()

    fun getWorkspaceDb(filename: String = WorkspaceDatabase.dbFileName) =
        Room.databaseBuilder(
            application, WorkspaceDatabase::class.java, filename
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    val workspaceDb: WorkspaceDatabase = getWorkspaceDb()

    val temporaryDb: TemporaryDatabase =
        Room.databaseBuilder(
            application, TemporaryDatabase::class.java, TemporaryDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    val repoDb: RepoDatabase =
        Room.databaseBuilder(
            application, RepoDatabase::class.java, RepoDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    val settingsDb: SettingsDatabase =
        Room.databaseBuilder(
            application, SettingsDatabase::class.java, SettingsDatabase.dbFileName
        )
            .allowMainThreadQueries()
            .addMigrations()
            .build()

    private fun backupDatabaseIfNeeded() {
        if(application.isRunningTests) return
        val oldDb = application.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME)
        if(oldDb.exists()) {
            backupOldDatabase(oldDb)
        } else {
            backupNewDatabaseIfNeeded()
        }
    }

    private fun backupOldDatabase(oldDb: File) {
        val dbVersion =
            SQLiteDatabase.openDatabase(oldDb.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        Log.i(TAG, "backupping old database of version $dbVersion)")
        val backupPath = CommonUtils.dbBackupPath
        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupPath, "dbBackup-$dbVersion-$timeStamp.db")
        oldDb.copyTo(backupFile, true)
    }

    private fun backupNewDatabaseIfNeeded() {
        Log.i(TAG, "backupDatabaseIfNeeded")
        val versions = ALL_DB_FILENAMES.map {
            val file = application.getDatabasePath(it)
            if(file.exists()) {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            } else {
                0
            }
        }

        val maxVersions = ALL_DB_FILENAMES.map { maxDatabaseVersion(it) }
        val needBackup = maxVersions != versions

        if(needBackup) {
            val backupZipFile = BackupControl.makeDatabaseBackupFile()
            val versionString = versions.joinToString("-")
            Log.i(TAG, "backupping database of version $versionString (current: ${maxVersions.joinToString("-") })")
            val backupPath = CommonUtils.dbBackupPath
            val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupPath, "dbBackup-$versionString-$timeStamp.abdb")
            backupZipFile.copyTo(backupFile, true)
            backupZipFile.delete()
        }
    }

    init {
        dropTriggers(this)
        createTriggers(this)
    }

    private val backedUpDatabases = arrayOf(bookmarkDb, readingPlanDb, workspaceDb, repoDb, settingsDb)
    private val allDatabases = arrayOf(*backedUpDatabases, temporaryDb)

    internal fun sync() = allDatabases.forEach {
        it.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)").use { c -> c.moveToFirst() }
    }

    internal fun vacuum() {
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
            if(!ready && !application.isRunningTests) throw DataBaseNotReady()
            return _instance ?: synchronized(this) {
                _instance ?: try { DatabaseContainer() } catch (e: Exception) {
                    Log.e(TAG, "Can't open database", e)
                    throw e
                }
                    .also {
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
                } catch (e: DataBaseNotReady) {
                    Log.i(TAG, "Can't close, database not ready")
                }
                _instance = null
            }
        }

        fun maxDatabaseVersion(filename: String): Int = when(filename) {
            BookmarkDatabase.dbFileName -> BOOKMARK_DATABASE_VERSION
            ReadingPlanDatabase.dbFileName -> READING_PLAN_DATABASE_VERSION
            WorkspaceDatabase.dbFileName -> WORKSPACE_DATABASE_VERSION
            RepoDatabase.dbFileName -> REPO_DATABASE_VERSION
            SettingsDatabase.dbFileName -> SETTINGS_DATABASE_VERSION
            else -> throw IllegalStateException("Unknown database file: $filename")
        }
    }
}
