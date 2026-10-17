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
import androidx.sqlite.SQLiteException
import net.bible.android.BibleApplication
import net.bible.sharedcore.log.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val TAG = "ReadOnlyModuleOpen"

/** Serializes snapshot builds so two threads opening the same module never copy into the same paths at once. */
private val snapshotLock = Any()

/**
 * Opens a third-party SQLite module (MyBible, MySword, e-Sword) read-only.
 *
 * A module in WAL mode needs a `<file>-shm` next to it even for readers. When the module directory is not
 * writable (for example `0555`) and no `-shm` exists, SQLite fails with error 14 on the open or on the first
 * query, and the module would silently disappear. Any [SQLiteException] from the open or a probe query, while
 * `<file>-wal` exists, `<file>-shm` does not and the directory is not writable, triggers a snapshot attempt:
 * the database and its WAL are copied to `snapshotRoot/<sha1 of the absolute path>/` and the copy is opened
 * instead. Copying both files keeps rows that were committed only to the WAL; an `immutable=1` open of the
 * original would drop them. The source directory is never written to.
 *
 * The snapshot is reused while the sizes and modification times of both source files equal those of the
 * snapshot files (the copies carry the source's modification time). If the snapshot cannot be built or
 * opened (disk full, source changed while copying, ...) the original [SQLiteException] is rethrown with the
 * snapshot failure attached as suppressed, so callers that only catch [SQLiteException] keep working.
 * Any other failure is rethrown unchanged.
 */
fun openReadOnlyModuleDb(file: File, snapshotRoot: File): SQLiteConnection {
    try {
        return openAndProbe(file)
    } catch (e: SQLiteException) {
        val wal = File(file.path + "-wal")
        val shm = File(file.path + "-shm")
        val dir = file.absoluteFile.parentFile
        if (!wal.isFile || shm.exists() || dir == null || dir.canWrite()) throw e
        Log.w(TAG, "Read-only WAL module without -shm, opening a snapshot of $file", e)
        try {
            return openAndProbe(snapshot(file, wal, snapshotRoot))
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            e.addSuppressed(interrupted)
            throw e
        } catch (snapshotFailure: Exception) {
            e.addSuppressed(snapshotFailure)
            throw e
        }
    }
}

/** [openReadOnlyModuleDb] with the app's snapshot cache (`<cacheDir>/readonly-wal`). */
fun openReadOnlyModuleDb(file: File): SQLiteConnection =
    openReadOnlyModuleDb(file, File(BibleApplication.application.cacheDir, "readonly-wal"))

/** Opens [file] read-only and runs a query, because the WAL/shm failure can surface on the first statement. */
private fun openAndProbe(file: File): SQLiteConnection {
    val db = openSqlite(file.path, readOnly = true)
    try {
        db.queryRows("SELECT count(*) FROM sqlite_master") { it.getLong(0) }
    } catch (e: SQLiteException) {
        runCatching { db.close() }
        throw e
    }
    return db
}

private class Stamp(val size: Long, val mtime: Long) {
    constructor(f: File) : this(f.length(), f.lastModified())
    fun matches(f: File) = f.isFile && f.length() == size && f.lastModified() == mtime
}

private fun snapshot(file: File, wal: File, snapshotRoot: File): File = synchronized(snapshotLock) {
    val key = MessageDigest.getInstance("SHA-1").digest(file.absolutePath.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val dir = File(snapshotRoot, key)
    val copy = File(dir, file.name)
    val copyWal = File(dir, wal.name)
    // Stamps are captured before copying: a source that changes mid-copy no longer matches and is rejected below.
    val mainStamp = Stamp(file)
    val walStamp = Stamp(wal)
    if (mainStamp.matches(copy) && walStamp.matches(copyWal)) return copy
    dir.deleteRecursively()
    if (!dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create snapshot directory $dir")
    file.copyTo(copy, overwrite = true).setLastModified(mainStamp.mtime)
    wal.copyTo(copyWal, overwrite = true).setLastModified(walStamp.mtime)
    if (!mainStamp.matches(file) || !walStamp.matches(wal) || !mainStamp.matches(copy) || !walStamp.matches(copyWal)) {
        dir.deleteRecursively()
        throw IOException("$file changed while it was being copied")
    }
    copy
}
