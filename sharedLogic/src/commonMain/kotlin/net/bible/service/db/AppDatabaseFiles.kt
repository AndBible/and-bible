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

import androidx.annotation.VisibleForTesting
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import net.bible.sharedcore.log.Log
import okio.IOException
import kotlin.concurrent.Volatile

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
@VisibleForTesting
fun <T : RoomDatabase> RoomDatabase.Builder<T>.productionConfig(): RoomDatabase.Builder<T> =
    setDriver(sqliteDriverFactory())
        .setQueryCoroutineContext(Dispatchers.IO)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)

/**
 * Builds an app database with [productionConfig] and [migrations]. [name] is a file name in the app's database
 * directory or an absolute path.
 *
 * For a regenerable cache in the app's database directory (anything not in [USER_DATA_DB_FILENAMES]), a file that is
 * not a database or is corrupt is first moved aside by [recoverIfCorrupt], so Room creates it empty instead of every
 * access throwing (the old framework/requery error handler deleted such a file; this keeps the bytes).
 * User-data databases are never recovered here (#4112): [DatabaseContainer.create] reports a damaged one as
 * [CorruptDatabasesException] and only the user's explicit reset ([DatabaseContainer.resetCorruptDatabases]) moves it
 * aside. Absolute paths (imported files, sync downloads and patches, tests' temporary files) are never recovered either:
 * moving a user-chosen import file aside would silently import nothing, so there the SQLite error propagates to the caller.
 */
inline fun <reified T : RoomDatabase> buildAppDatabase(name: String, vararg migrations: Migration): T {
    val absolute = name.toPath().isAbsolute
    val path = if (absolute) name else DatabaseContainer.host.databasePath(name)
    if (!absolute && name !in USER_DATA_DB_FILENAMES) recoverIfCorrupt(path)
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

@VisibleForTesting
fun isCorruptionError(e: Throwable): Boolean = sqlitePrimaryResultCode(e).let { it == SQLITE_CORRUPT || it == SQLITE_NOTADB }

/**
 * Checks, before Room opens it, that [path] (when it exists) has a readable schema (`sqlite_master`). A first
 * SQLITE_NOTADB or SQLITE_CORRUPT is only a suspicion: the connection is closed and a fresh connection repeats
 * the read. Only if both attempts report corruption are the file and its `-journal`/`-wal`/`-shm`/`.lck` siblings
 * moved to `<name>.corrupt-<yyyyMMdd-HHmmss>[-n]` in the same directory and the new name returned.
 * A successful read or any other error (busy/locked, read-only file system, I/O) on either attempt leaves the file
 * alone and returns null; Room then reports any remaining error itself. At most two checks per call, no loop.
 *
 * Limits: only corruption visible when the schema is read is caught here. Corruption in a data page found later
 * (by a query) still throws at that query. The check's own open handles a `-journal`/`-wal` beside the file as
 * SQLite always does (an invalid one is discarded, a hot journal rolled back) before the corruption shows, so those
 * may not survive into the moved-aside copy; `.lck` (and anything SQLite leaves alone) is moved with it.
 */
@VisibleForTesting
fun recoverIfCorrupt(path: String): String? = recoverIfCorrupt(path) { openSqlite(it) }

internal fun recoverIfCorrupt(path: String, openConnection: (String) -> SQLiteConnection): String? {
    val error = confirmedCorruption(path, openConnection) ?: return null
    return moveCorruptAside(path, error)
}

/**
 * The corruption error of [path] when two schema reads on fresh connections both report SQLITE_CORRUPT/NOTADB, else
 * null (missing file, healthy file, or any other error such as busy/locked or I/O). Never changes the file itself
 * (SQLite's own open may still roll back or discard a `-journal`/`-wal`, see [recoverIfCorrupt]).
 */
internal fun confirmedCorruption(path: String, openConnection: (String) -> SQLiteConnection = { openSqlite(it) }): SQLiteException? {
    if (FileSystem.SYSTEM.metadataOrNull(path.toPath())?.isRegularFile != true) return null
    fun corruptionError(): SQLiteException? = try {
        openConnection(path).use { it.queryLong("SELECT count(*) FROM sqlite_master") }
        null
    } catch (e: SQLiteException) {
        e.takeIf(::isCorruptionError)
    }
    val firstError = corruptionError() ?: return null
    Log.w(TAG, "Possible corruption in ${path.toPath().name}; confirming with a fresh connection", firstError)
    return corruptionError()
}

/**
 * Whether [path] is damaged: [confirmedCorruption] (the schema read), or else `PRAGMA quick_check` reports a problem or
 * fails with SQLITE_CORRUPT/NOTADB (#4112: damage in a table page, found by a later query in `create()`). A missing
 * file, a healthy one, or any other error (busy, I/O) is not damage.
 */
internal fun isDamagedDatabase(path: String): Boolean {
    if (FileSystem.SYSTEM.metadataOrNull(path.toPath())?.isRegularFile != true) return false
    if (confirmedCorruption(path) != null) return true
    return try {
        quickCheckProblems(path).isNotEmpty()
    } catch (e: SQLiteException) {
        isCorruptionError(e)
    }
}

/** The rows of `PRAGMA quick_check` other than `ok` (empty for a healthy file). */
private fun quickCheckProblems(path: String): List<String> = openSqlite(path).use { c ->
    c.prepare("PRAGMA quick_check").use { st -> buildList { while (st.step()) add(st.getText(0)) } }.filter { it != "ok" }
}

/** A database file that must not be used: [requireIntactDatabase] found [detail] in [path]. */
class DamagedDatabaseFileException(val path: String, val detail: String, cause: Throwable? = null) :
    Exception("Damaged database file ${path.toPath().name}: $detail", cause)

/**
 * Throws [DamagedDatabaseFileException] unless [path] is a database whose schema reads and whose `PRAGMA quick_check`
 * is `ok` (#4112: a downloaded sync file is checked before it replaces the local database, so damage in the download
 * never costs the local data). Other errors (I/O, busy) propagate as they are.
 */
fun requireIntactDatabase(path: String) {
    val problems = try {
        quickCheckProblems(path)
    } catch (e: SQLiteException) {
        if (isCorruptionError(e)) throw DamagedDatabaseFileException(path, e.message.orEmpty(), e)
        throw e
    }
    if (problems.isNotEmpty()) throw DamagedDatabaseFileException(path, problems.take(3).joinToString("; "))
}

/** Moves [path] and its siblings to `<name>.corrupt-<timestamp>[-n]`; the new name, or null if the move failed. */
internal fun moveCorruptAside(path: String, error: Throwable? = null): String? {
    val fs = FileSystem.SYSTEM
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
 * `PRAGMA user_version` of [path], 0 when it does not exist. The startup backup check reads every version before Room
 * opens anything; [DatabaseContainer.create] has already reported any damaged user-data file by then (#4112), so a
 * read error here propagates instead of moving the file aside.
 */
@VisibleForTesting
fun userVersionOrZero(path: String): Int =
    if (FileSystem.SYSTEM.metadataOrNull(path.toPath())?.isRegularFile == true) readUserVersion(path) else 0

/** [android.content.Context.deleteDatabase], plus Room 3's `<name>.lck` lock file, which it does not know about. */
fun deleteAppDatabase(name: String): Boolean {
    val host = DatabaseContainer.host
    val deleted = host.deleteDatabase(name)
    try { FileSystem.SYSTEM.delete((host.databasePath(name) + ".lck").toPath(), mustExist = false) } catch (_: IOException) { }
    return deleted
}

/**
 * The databases holding the user's own data (#4112): a damaged one is reported ([CorruptDatabasesException]) and only
 * moved aside when the user chooses to reset it. Every other app database is a regenerable cache and is recovered
 * silently by [buildAppDatabase].
 */
val USER_DATA_DB_FILENAMES: Set<String> by lazy { (ALL_DB_FILENAMES.toList() + OLD_MONOLITHIC_DATABASE_NAME).toSet() }

/** One damaged database file found at startup: its file name in the database directory and its full path. */
data class CorruptDatabase(val fileName: String, val path: String)

/**
 * Opening the databases found damaged user-data files (#4112). Nothing has been moved or emptied: the host shows the
 * error with "send report", "Backup & Restore" and "reset" ([DatabaseContainer.resetCorruptDatabases]) choices.
 */
class CorruptDatabasesException(val databases: List<CorruptDatabase>, cause: Throwable? = null) :
    Exception("Damaged database file(s): " + databases.joinToString { it.fileName }, cause)

/** The [CorruptDatabasesException] in this throwable's cause chain (hosts wrap startup failures), or null. */
fun Throwable.corruptDatabasesOrNull(): CorruptDatabasesException? =
    generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }.filterIsInstance<CorruptDatabasesException>().firstOrNull()

/**
 * The user's answer when damaged user-data databases were found (#4112): [RESET] the damaged data, [RETRY] the open
 * (after Backup & Restore, where a backup may have been restored), or [CLOSE] the app. Sending a report is handled by
 * the asking host.
 */
enum class CorruptDatabaseChoice { RESET, RETRY, CLOSE }

/** The user's reset could not move [database] aside (#4112); nothing was emptied. The host tells the user. */
class CorruptDatabaseResetException(val database: CorruptDatabase, cause: Throwable?) :
    Exception("Could not move the damaged database ${database.fileName} aside", cause)

/**
 * Runs [open] and, while it fails with damaged user-data databases, asks the user ([ask]): [CorruptDatabaseChoice.RESET]
 * moves the damaged files aside ([reset], keeping them) and retries; [CorruptDatabaseChoice.RETRY] (the user came back
 * from Backup & Restore) retries as it is, so a restored backup opens normally; [CorruptDatabaseChoice.CLOSE] returns
 * null with nothing changed. A failed [reset] goes to [resetFailed] (the host shows it) and the user is asked again.
 * Every retry looks at the files again ([DatabaseContainer.forgetCorruption]). Any other failure propagates.
 * Android's `StartupActivity` uses it; desktop and iOS show `CorruptDatabaseRecovery`.
 */
suspend fun <T> openAskingOnCorruption(
    open: suspend () -> T,
    ask: suspend (CorruptDatabasesException) -> CorruptDatabaseChoice,
    reset: suspend (List<CorruptDatabase>) -> Unit = { DatabaseContainer.resetCorruptDatabases(it) },
    resetFailed: suspend (Throwable) -> Unit = {},
): T? {
    while (true) {
        val corrupt = try {
            return open()
        } catch (e: Exception) {
            e.corruptDatabasesOrNull() ?: throw e
        }
        when (ask(corrupt)) {
            CorruptDatabaseChoice.RESET -> try {
                reset(corrupt.databases)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Resetting damaged databases failed", e)
                resetFailed(e)
            }
            CorruptDatabaseChoice.RETRY -> Unit
            CorruptDatabaseChoice.CLOSE -> return null
        }
        DatabaseContainer.forgetCorruption()
    }
}

/**
 * Where damaged user data found after the startup's own question goes (#4112): a headless entry, a restored screen or
 * the Welcome flow opening the databases. Each host registers [route] to show its recovery (Android: the startup
 * screen's question; desktop and iOS: the startup failure screen). [show] is false when no route is registered.
 */
object DamagedDataRecovery {
    @Volatile var route: ((CorruptDatabasesException) -> Unit)? = null

    fun show(error: CorruptDatabasesException): Boolean {
        val r = route ?: return false
        r(error)
        return true
    }

    /**
     * Runs [block]; damaged user data it meets goes to the registered [route] and [fallback] is returned instead. With
     * no route registered (no host to show it) the error propagates.
     */
    suspend fun <T> orFallback(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        val damaged = e.corruptDatabasesOrNull() ?: throw e
        if (!show(damaged)) throw e
        fallback
    }
}
