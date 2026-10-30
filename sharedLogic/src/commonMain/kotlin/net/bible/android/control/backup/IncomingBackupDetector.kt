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

package net.bible.android.control.backup

import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.sharedcore.log.Log
import okio.Source
import okio.use
import org.crosswire.common.compress.ZipReader

/**
 * Tells a database backup that was handed to the app from outside ("open with", share, command line) from a module
 * archive or any other file, so only the backup goes to [BackupControl.restoreAppDatabaseFromSourceWithUI].
 *
 * - `.abdb` and `.abdb.zip` names are backups by name alone (no reading).
 * - Any other `.zip` is a backup when one of its first [MAX_ENTRIES_SCANNED] entries is the backup manifest or a
 *   `db/<database file>` entry (the layout [BackupControl] writes and restores).
 * - Everything else, a raw SQLite file without the `.abdb` name included, is not: it may be a MyBible module.
 */
object IncomingBackupDetector {
    private const val TAG = "IncomingBackupDetector"
    private const val MAX_ENTRIES_SCANNED = 8

    /** [name] is the file's display name; [open] opens a fresh stream of its content, called at most once. */
    suspend fun isDatabaseBackup(name: String?, open: suspend () -> Source): Boolean {
        val lower = name?.lowercase().orEmpty()
        if (lower.endsWith(".abdb") || lower.endsWith(".abdb.zip")) return true
        if (!lower.endsWith(".zip")) return false
        return try {
            ZipReader(open()).use { reader ->
                var scanned = 0
                var found = false
                while (!found && scanned < MAX_ENTRIES_SCANNED) {
                    val entry = reader.nextEntry() ?: break
                    scanned++
                    found = entry.name == ANDBIBLE_BACKUP_MANIFEST_FILENAME ||
                        (entry.name.startsWith("db/") && entry.name.removePrefix("db/") in ALL_DB_FILENAMES)
                }
                found
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect $name: ${e.message}")
            false
        }
    }
}
