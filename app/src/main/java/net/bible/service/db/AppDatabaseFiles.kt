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

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import net.bible.sharedcore.log.Log
import okio.IOException

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
    val absolute = name.toPath().isAbsolute
    val path = if (absolute) name else DatabaseContainer.host.databasePath(name)
    if (!absolute) recoverIfCorrupt(path)
    path.toPath().parent?.let { FileSystem.SYSTEM.createDirectories(it) }
    return Room.databaseBuilder<T>(name = path)
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
 * Checks, before Room opens it, that [path] (when it exists) is a readable database: one raw open that reads the
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
internal fun recoverIfCorrupt(path: String): String? {
    val fs = FileSystem.SYSTEM
    if (fs.metadataOrNull(path.toPath())?.isRegularFile != true) return null
    val error = try {
        openSqlite(path).use { it.queryLong("SELECT count(*) FROM sqlite_master") }
        null
    } catch (e: SQLiteException) {
        e
    }
    if (error == null || !isCorruptionError(error)) return null
    val target = corruptTarget(path)
    if (!renamed(path, target)) {
        Log.e(TAG, "Corrupt database $path could not be moved aside", error)
        return null
    }
    for (suffix in SIBLING_SUFFIXES) {
        val sibling = path + suffix
        if (fs.exists(sibling.toPath()) && !renamed(sibling, target + suffix)) {
            try { fs.delete(sibling.toPath(), mustExist = false) } catch (_: IOException) { }
        }
    }
    Log.e(TAG, "Corrupt database ${path.toPath().name} moved aside to ${target.toPath().name}; it is recreated empty", error)
    return target
}

/** [FileSystem.atomicMove] with its [IOException] reported as the `false` that `File.renameTo` gave. */
private fun renamed(from: String, to: String): Boolean =
    try { FileSystem.SYSTEM.atomicMove(from.toPath(), to.toPath()); true } catch (_: IOException) { false }

private fun corruptTarget(path: String): String {
    val file = path.toPath()
    val stamp = DatabaseContainer.host.formatTimestamp(kotlin.time.Clock.System.now().toEpochMilliseconds(), "yyyyMMdd-HHmmss", deviceLocale = false)
    fun candidate(suffix: String): Path = file.parent?.resolve("${file.name}.corrupt-$suffix") ?: "${file.name}.corrupt-$suffix".toPath()
    var target = candidate(stamp)
    var n = 1
    while (FileSystem.SYSTEM.exists(target)) target = candidate("$stamp-${n++}")
    return target.toString()
}

/**
 * `PRAGMA user_version` of [path], 0 when it does not exist. A corrupt file is moved aside first ([recoverIfCorrupt],
 * then it counts as missing): the startup backup check reads every version before Room opens anything, and must not
 * crash on a file that the builders would recover anyway.
 */
internal fun userVersionRecoveringCorruption(path: String): Int =
    if (FileSystem.SYSTEM.metadataOrNull(path.toPath())?.isRegularFile == true && recoverIfCorrupt(path) == null) readUserVersion(path) else 0

/** [android.content.Context.deleteDatabase], plus Room 3's `<name>.lck` lock file, which it does not know about. */
internal fun deleteAppDatabase(name: String): Boolean {
    val host = DatabaseContainer.host
    val deleted = host.deleteDatabase(name)
    try { FileSystem.SYSTEM.delete((host.databasePath(name) + ".lck").toPath(), mustExist = false) } catch (_: IOException) { }
    return deleted
}
