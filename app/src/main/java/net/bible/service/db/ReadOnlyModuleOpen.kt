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
import net.bible.sharedcore.log.Log
import java.io.File
import java.security.MessageDigest

private const val TAG = "ReadOnlyModuleOpen"

/**
 * Opens a third-party SQLite module (MyBible, MySword, e-Sword) read-only.
 *
 * A module in WAL mode needs a `<file>-shm` next to it even for readers. When the module directory is not
 * writable (for example `0555`) and no `-shm` exists, SQLite fails with error 14 on the open or on the first
 * query, and the module would silently disappear. In exactly that case (the open or a probe query fails with
 * [SQLiteException], `<file>-wal` exists, `<file>-shm` does not and the directory is not writable) the
 * database and its WAL are copied to `snapshotRoot/<sha1 of the absolute path>/` and the copy is opened
 * instead. Copying both files keeps rows that were committed only to the WAL; an `immutable=1` open of the
 * original would drop them. The source directory is never written to.
 *
 * The snapshot is reused while the sizes and modification times of both source files equal those of the
 * snapshot files (the copies carry the source's modification time). Any other failure is rethrown.
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
        return openAndProbe(snapshot(file, wal, snapshotRoot))
    }
}

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

private fun sameStamp(a: File, b: File) = b.isFile && a.length() == b.length() && a.lastModified() == b.lastModified()

private fun snapshot(file: File, wal: File, snapshotRoot: File): File {
    val key = MessageDigest.getInstance("SHA-1").digest(file.absolutePath.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val dir = File(snapshotRoot, key)
    val copy = File(dir, file.name)
    val copyWal = File(dir, wal.name)
    if (sameStamp(file, copy) && sameStamp(wal, copyWal)) return copy
    dir.deleteRecursively()
    check(dir.mkdirs() || dir.isDirectory) { "Cannot create snapshot directory $dir" }
    // WAL first would be unsafe if the source changed in between; both are read-only modules, so order is moot.
    file.copyTo(copy, overwrite = true).setLastModified(file.lastModified())
    wal.copyTo(copyWal, overwrite = true).setLastModified(wal.lastModified())
    return copy
}
