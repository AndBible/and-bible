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

import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.files.BackupFileSniffer
import net.bible.sharedcore.platform.files.BackupFileType
import okio.Source
import okio.use
import org.crosswire.common.compress.ZipReader

/**
 * Tells a database backup that was handed to the app from outside ("open with", share, command line) from everything
 * else, so only a backup goes to [BackupControl.restoreAppDatabaseFromSourceWithUI]. StudyPad exports and module backups
 * are zips of the same family (a StudyPad export is even named `<pad>.abdb.zip`), so a name never decides:
 *
 * - A `.zip` is a backup when its manifest ([AndBibleBackupManifest.fromZip]) says [BackupType.DB_BACKUP]; a StudyPad
 *   export or a module backup is not (InstallZip handles them).
 * - A `.zip` without a manifest (releases before 822) is a backup when one of its first [MAX_ENTRIES_SCANNED] entries is
 *   a `db/<database file>`.
 * - A bare `.abdb` (the old monolithic backup, restorable but no longer written) is a backup when it has the SQLite header.
 * - Everything else, a raw SQLite file with another name included, is not: it may be a MyBible module.
 */
object IncomingBackupDetector {
    private const val TAG = "IncomingBackupDetector"
    private const val MAX_ENTRIES_SCANNED = 8
    private val LEGACY_SUFFIX = DATABASE_BACKUP_SUFFIX.removeSuffix(".zip")

    /** [name] is the file's display name; [open] opens a fresh stream of its content, and may be called more than once. */
    suspend fun isDatabaseBackup(name: String?, open: suspend () -> Source): Boolean {
        val lower = name?.lowercase().orEmpty()
        return try {
            when {
                lower.endsWith(".zip") -> {
                    val manifest = AndBibleBackupManifest.fromZip(open())
                    if (manifest != null) manifest.backupType == BackupType.DB_BACKUP else containsDatabaseEntry(open())
                }
                lower.endsWith(LEGACY_SUFFIX) -> {
                    val source = open()
                    BackupFileSniffer.sniff { source } == BackupFileType.SQLITE3
                }
                else -> false
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect $name: ${e.message}")
            false
        }
    }

    private fun containsDatabaseEntry(source: Source): Boolean = ZipReader(source).use { reader ->
        var scanned = 0
        var found = false
        while (!found && scanned < MAX_ENTRIES_SCANNED) {
            val entry = reader.nextEntry() ?: break
            scanned++
            found = entry.name.startsWith("db/") && entry.name.removePrefix("db/") in ALL_DB_FILENAMES
        }
        found
    }
}
