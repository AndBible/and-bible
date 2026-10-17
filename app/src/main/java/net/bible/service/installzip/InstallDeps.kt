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

package net.bible.service.installzip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.control.backup.BackupControl
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.android.database.BookmarkDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.common.ArchiveUtils.unzip
import net.bible.service.common.safeZipEntryPath
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.bookmarksDbStats
import net.bible.service.sword.epub.EPUB_OPTIMIZER_VERSION
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.epubInitials
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.CoreStrings
import okio.Path.Companion.toOkioPath
import okio.source
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.crosswire.jsword.book.sword.SwordConstants
import org.crosswire.jsword.book.sword.SwordGenBook
import org.koin.mp.KoinPlatform
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** Mirrors classic `InstallZip.installStudyPads`'s unzip + stats step, operating on the
 *  already-acquired local file instead of re-opening the original uri. */
internal suspend fun studyPadStatsFor(localFile: File, backup: BackupControl): Pair<String, File> {
    val unzipFolder = File(backup.internalDbBackupDir.toFile(), "unzip") // L1b-handover(G2 Task 14): installzip works on Path
    unzip(localFile.source(), unzipFolder.toOkioPath())
    val dbFile = File(unzipFolder, "db/${BookmarkDatabase.dbFileName}")
    val stats = bookmarksDbStats(SyncableDatabaseDefinition.BOOKMARKS, dbFile.toOkioPath(), KoinPlatform.getKoin().get<CoreStrings>()) // L1b-handover(G2 Task 14): InstallDeps takes CoreStrings from its caller, then drop this Koin lookup
    return stats to unzipFolder
}


/** Sniffs the same 16-byte header as the URI host, against the acquired local file. */
internal suspend fun determineLocalFileType(file: File): BackupFileType = withContext(Dispatchers.IO) {
    try {
        file.inputStream().use { inputStream ->
            val header = ByteArray(16)
            inputStream.read(header)
            val headerString = String(header)
            if (headerString == "SQLite format 3\u0000")
                BackupFileType.SQLITE3
            else if (headerString.startsWith("PK\u0003\u0004")) {
                BackupFileType.ZIP
            } else
                BackupFileType.UNKNOWN
        }
    } catch (e: Exception) {
        Log.e("InstallDeps", "Unexpected exception when determining file type", e)
        BackupFileType.UNKNOWN
    }
}

/** Preserves the foreground host's terminal toast mapping; nonterminal phases have no toast. */
fun terminalEventMessage(event: InstallPhase, strings: CoreStrings): String = when (event) {
    is InstallPhase.Done -> strings.installZipSuccessful
    is InstallPhase.Cancelled -> strings.installZipCanceled
    is InstallPhase.Error -> if (event.kind == InstallError.INVALID_FILE) strings.invalidModule else strings.errorOccurred
    else -> ""
}

/**
 * Adapts classic `ZipHandler.checkZipFile`'s entry-enumeration + classification to operate on an
 * already-acquired local [File] and return a pure [InstallInspector.SwordZipScan] instead of
 * throwing [net.bible.android.view.activity.installzip.ModulesExists]/[net.bible.android.view.activity.installzip.EpubFile]/
 * [net.bible.android.view.activity.installzip.InvalidModule]. Existing-file paths are plain
 * download-dir-relative paths (classic additionally ran `.canonicalPath` on the relative
 * `File`, which resolves against the JVM's working directory rather than the download dir --
 * not reproduced here, as it only ever fed a display string).
 */
internal fun scanSwordZip(localFile: File): InstallInspector.SwordZipScan {
    val targetDirectory = SwordBookPathJvm.getSwordDownloadDir()
    val existingFiles = mutableListOf<String>()
    val otherFiles = mutableListOf<String>()
    var modsDirFound = false
    var modulesFound = false
    var totalEntries = 0

    ZipInputStream(localFile.inputStream()).use { zin ->
        var entry: ZipEntry? = try {
            zin.nextEntry
        } catch (e: IllegalArgumentException) {
            null
        } ?: return InstallInspector.SwordZipScan(emptyList(), 0, isEpub = false, invalid = true)

        while (entry != null) {
            totalEntries++
            // Zip-slip: an entry that could escape the target directory makes the archive invalid.
            if (safeZipEntryPath(entry.name) == null) {
                return InstallInspector.SwordZipScan(emptyList(), 0, isEpub = false, invalid = true)
            }
            val name = entry.name.replace('\\', '/')
            val targetFile = File(targetDirectory, name)
            if (!entry.isDirectory && targetFile.exists()) {
                existingFiles.add(targetFile.relativeTo(targetDirectory).path)
            }
            when {
                name.startsWith(SwordConstants.DIR_CONF + "/") && name.endsWith(SwordConstants.EXTENSION_CONF) ->
                    modsDirFound = true
                name.startsWith(SwordConstants.DIR_CONF + "/") -> Unit // ignore directory entry
                name.startsWith(SwordConstants.DIR_DATA + "/") -> modulesFound = true
                name.startsWith("epub/") || name.startsWith("mysword/") || name.startsWith("mybible/") || name.startsWith("esword/") -> {
                    modulesFound = true
                    modsDirFound = true
                }
                name == ANDBIBLE_BACKUP_MANIFEST_FILENAME -> Unit
                else -> otherFiles.add(name)
            }
            entry = zin.nextEntry
        }
    }

    if (otherFiles.isNotEmpty()) {
        val isEpub = otherFiles.any { it == "META-INF/container.xml" }
        return InstallInspector.SwordZipScan(existingFiles, totalEntries, isEpub = isEpub, invalid = !isEpub)
    }
    if (!(modsDirFound && modulesFound)) {
        return InstallInspector.SwordZipScan(existingFiles, totalEntries, isEpub = false, invalid = true)
    }
    return InstallInspector.SwordZipScan(existingFiles, totalEntries, isEpub = false, invalid = false)
}

/**
 * Mirrors classic `InstallZip.installEpub`'s optimizer-version + bookmark-count test: an upgrade
 * confirmation is needed only when the epub is already installed, has genuine bookmarks/notes
 * against it, AND its persisted optimizer version is behind the current one.
 */
internal suspend fun checkEpubUpgrade(displayName: String): Boolean {
    val dir = File(SharedConstants.modulesDir, "epub/$displayName")
    if (!dir.exists()) return false
    val initials = epubInitials(displayName)
    val book = Books.installed().getBook(initials)
    val optimizerVersion = ((book as? SwordGenBook)?.backend as? EpubBackend)?.state?.optimizerVersion ?: 1
    val bookmarkCount = if (DatabaseContainer.ready) {
        DatabaseContainer.instance.bookmarkDb.bookmarkDao().genericBookmarkCountFor(initials)
    } else 0
    return bookmarkCount > 0 && optimizerVersion < EPUB_OPTIMIZER_VERSION
}
