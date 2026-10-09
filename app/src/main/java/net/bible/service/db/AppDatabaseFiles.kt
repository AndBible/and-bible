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

import android.util.Log
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.Dispatchers
import net.bible.android.BibleApplication.Companion.application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AppDatabaseFiles"

/** SQLite primary result codes that mean "this file is not a usable database". */
private const val SQLITE_CORRUPT = 11
private const val SQLITE_NOTADB = 26

/** Files SQLite and Room 3 keep beside a database (Room 3's `.lck` is its open/migration lock file). */
private val SIBLING_SUFFIXES = listOf("-journal", "-wal", "-shm", ".lck")

/**
 * The configuration every app database is built with: the bundled SQLite driver ([sqliteDriverFactory]),
 * queries on [Dispatchers.IO] and TRUNCATE journaling (which makes Room 3's pool a single connection).
 * Migrations are added per database by the caller.
 */
internal fun <T : RoomDatabase> RoomDatabase.Builder<T>.productionConfig(): RoomDatabase.Builder<T> =
    setDriver(sqliteDriverFactory())
        .setQueryCoroutineContext(Dispatchers.IO)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)

/**
 * Builds an app database with [productionConfig] and [migrations]. [name] is a file name in the app's database
 * directory or an absolute path.
 *
 * For a file in the app's database directory, a file that is not a database or is corrupt is first moved aside by
 * [recoverIfCorrupt], so Room creates it empty instead of every access throwing (the old framework/requery error
 * handler deleted such a file; this keeps the bytes). Absolute paths (imported files, sync downloads and patches,
 * tests' temporary files) are never recovered: moving a user-chosen import file aside would silently import nothing,
 * so there the SQLite error propagates to the caller.
 */
internal inline fun <reified T : RoomDatabase> buildAppDatabase(name: String, vararg migrations: Migration): T {
    if (!File(name).isAbsolute) recoverIfCorrupt(application.getDatabasePath(name))
    return Room.databaseBuilder<T>(application, name)
        .productionConfig()
        .addMigrations(*migrations)
        .build()
}

/**
 * The SQLite primary result code of [e], or null when it carries none. The bundled driver (`androidx.sqlite`)
 * has no code property on Android (its `SQLiteException` is `android.database.SQLException`); it puts the
 * (extended) code in a fixed message prefix, "Error code: N, message: ...", which is parsed here.
 */
internal fun sqlitePrimaryResultCode(e: Throwable): Int? =
    Regex("^Error code: (\\d+)").find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.and(0xFF)

internal fun isCorruptionError(e: Throwable): Boolean = sqlitePrimaryResultCode(e).let { it == SQLITE_CORRUPT || it == SQLITE_NOTADB }

/**
 * Checks, before Room opens it, that [file] (when it exists) is a readable database: one raw open that reads the
 * schema (`sqlite_master`). On SQLITE_NOTADB or SQLITE_CORRUPT the file and its `-journal`/`-wal`/`-shm`/`.lck`
 * siblings are moved to `<name>.corrupt-<yyyyMMdd-HHmmss>[-n]` in the same directory and the new name is returned.
 * Any other error (busy/locked, read-only file system, I/O) leaves the file alone and returns null; Room then
 * reports that error itself. One check per call, no retry: it cannot loop.
 *
 * Limits: only corruption visible when the schema is read is caught here. Corruption in a data page found later
 * (by a query) still throws at that query. The check's own open handles a `-journal`/`-wal` beside the file as
 * SQLite always does (an invalid one is discarded, a hot journal rolled back) before the corruption shows, so those
 * may not survive into the moved-aside copy; `.lck` (and anything SQLite leaves alone) is moved with it.
 */
internal fun recoverIfCorrupt(file: File): File? {
    if (!file.isFile) return null
    val error = try {
        openSqlite(file.path).use { it.queryLong("SELECT count(*) FROM sqlite_master") }
        null
    } catch (e: SQLiteException) {
        e
    }
    if (error == null || !isCorruptionError(error)) return null
    val target = corruptTarget(file)
    if (!file.renameTo(target)) {
        Log.e(TAG, "Corrupt database ${file.path} could not be moved aside", error)
        return null
    }
    for (suffix in SIBLING_SUFFIXES) {
        val sibling = File(file.path + suffix)
        if (sibling.exists() && !sibling.renameTo(File(target.path + suffix))) sibling.delete()
    }
    Log.e(TAG, "Corrupt database ${file.name} moved aside to ${target.name}; it is recreated empty", error)
    return target
}

private fun corruptTarget(file: File): File {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    var target = File(file.parentFile, "${file.name}.corrupt-$stamp")
    var n = 1
    while (target.exists()) target = File(file.parentFile, "${file.name}.corrupt-$stamp-${n++}")
    return target
}

/**
 * `PRAGMA user_version` of [file], 0 when it does not exist. A corrupt file is moved aside first ([recoverIfCorrupt],
 * then it counts as missing): the startup backup check reads every version before Room opens anything, and must not
 * crash on a file that the builders would recover anyway.
 */
internal fun userVersionRecoveringCorruption(file: File): Int =
    if (file.isFile && recoverIfCorrupt(file) == null) readUserVersion(file) else 0

/** [android.content.Context.deleteDatabase], plus Room 3's `<name>.lck` lock file, which it does not know about. */
internal fun deleteAppDatabase(name: String): Boolean {
    val deleted = application.deleteDatabase(name)
    File(application.getDatabasePath(name).path + ".lck").delete()
    return deleted
}
