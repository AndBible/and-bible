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

import okio.use
import kotlinx.coroutines.IO
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.backup.BackupControl
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.android.database.BookmarkDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.common.ArchiveUtils.unzip
import net.bible.service.common.createTempFile
import net.bible.service.common.safeZipEntryPath
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.bookmarksDbStats
import net.bible.service.sword.epub.EPUB_OPTIMIZER_VERSION
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.epubInitials
import net.bible.service.sword.mybible.SqliteVerseBackendState
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.AppDirectories
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import org.crosswire.common.compress.ZipReader
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.book.sword.SwordConstants
import org.crosswire.jsword.book.sword.SwordGenBook
import org.koin.mp.KoinPlatform

/** Mirrors classic `InstallZip.installStudyPads`'s unzip + stats step, operating on the
 *  already-acquired local file instead of re-opening the original uri. */
internal suspend fun studyPadStatsFor(localFile: Path, backup: BackupControl, strings: CoreStrings): Pair<String, Path> {
    val unzipFolder = backup.internalDbBackupDir / "unzip"
    unzip(localFile, unzipFolder)
    val dbFile = unzipFolder / "db/${BookmarkDatabase.dbFileName}"
    val stats = bookmarksDbStats(SyncableDatabaseDefinition.BOOKMARKS, dbFile, strings)
    return stats to unzipFolder
}


/** Sniffs the same 16-byte header as the URI host, against the acquired local file. */
internal suspend fun determineLocalFileType(file: Path): BackupFileType = withContext(Dispatchers.IO) {
    try {
        FileSystem.SYSTEM.source(file).buffer().use { inputStream ->
            val header = ByteArray(16)
            inputStream.read(header)
            val headerString = header.decodeToString()
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

/** True for a `*.SQLite3` entry directly at the archive root (no directory part). Backslashes must already be normalised. */
internal fun isRootSqliteEntry(name: String): Boolean =
    !name.contains('/') && name.endsWith(".sqlite3", ignoreCase = true)

/** Where an archive entry is extracted, relative to the module dir: root `*.SQLite3` modules go to `mybible/`. */
internal fun moduleArchiveTargetName(name: String): String =
    if (isRootSqliteEntry(name)) "mybible/$name" else name

/** True when [file] is a SQLite database with the MyBible layout: an `info` table plus verses, commentaries or dictionary. */
private fun isMyBibleDatabase(file: Path): Boolean {
    val state = SqliteVerseBackendState(file)
    return try {
        val tables = state.rows("select name from sqlite_master where type = 'table'") { it.getText(0) }.toSet()
        "info" in tables && ("verses" in tables || "commentaries" in tables || "dictionary" in tables)
    } catch (e: Exception) {
        false
    } finally {
        state.close()
    }
}

/** Copies the current entry of [zin] to a temp file and checks it for the MyBible schema. */
private fun currentEntryIsMyBible(zin: ZipReader): Boolean {
    val tmp = createTempFile(FileSystem.SYSTEM_TEMPORARY_DIRECTORY, "mybible-scan", ".sqlite3")
    return try {
        FileSystem.SYSTEM.sink(tmp).buffer().use { it.writeAll(zin.entry) }
        isMyBibleDatabase(tmp)
    } finally {
        FileSystem.SYSTEM.delete(tmp, mustExist = false)
    }
}

/**
 * Adapts classic `ZipHandler.checkZipFile`'s entry-enumeration + classification to operate on an
 * already-acquired local [Path] and return a pure [InstallInspector.SwordZipScan] instead of
 * throwing [net.bible.android.view.activity.installzip.ModulesExists]/[net.bible.android.view.activity.installzip.EpubFile]/
 * [net.bible.android.view.activity.installzip.InvalidModule]. Existing-file paths are plain
 * download-dir-relative paths (classic additionally ran `.canonicalPath` on the relative
 * `File`, which resolves against the JVM's working directory rather than the download dir --
 * not reproduced here, as it only ever fed a display string).
 */
internal fun scanSwordZip(localFile: Path): InstallInspector.SwordZipScan {
    val targetDirectory = SwordBookPath.getSwordDownloadDir()
    val existingFiles = mutableListOf<String>()
    val otherFiles = mutableListOf<String>()
    var modsDirFound = false
    var modulesFound = false
    var totalEntries = 0

    ZipReader(FileSystem.SYSTEM.source(localFile)).use { zin ->
        var entry: ZipReader.Entry? = try {
            zin.nextEntry()
        } catch (e: IOException) {
            null
        } ?: return InstallInspector.SwordZipScan(emptyList(), 0, isEpub = false, invalid = true)

        while (entry != null) {
            totalEntries++
            // Zip-slip: an entry that could escape the target directory makes the archive invalid.
            if (safeZipEntryPath(entry.name) == null) {
                return InstallInspector.SwordZipScan(emptyList(), 0, isEpub = false, invalid = true)
            }
            val name = entry.name.replace('\\', '/')
            val targetFile = targetDirectory / moduleArchiveTargetName(name)
            // A name the file system rejects (for example a NUL char) cannot exist there: File.exists() said false.
            val exists = !entry.isDirectory && try {
                FileSystem.SYSTEM.exists(targetFile)
            } catch (e: IllegalArgumentException) {
                return InstallInspector.SwordZipScan(emptyList(), 0, isEpub = false, invalid = true)
            }
            if (exists) {
                existingFiles.add(targetFile.relativeTo(targetDirectory).toString())
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
                // A MyBible module (plus auxiliary SQLite files) at the archive root; installed under mybible/.
                isRootSqliteEntry(name) && currentEntryIsMyBible(zin) -> {
                    modulesFound = true
                    modsDirFound = true
                }
                else -> otherFiles.add(name)
            }
            entry = try {
                zin.nextEntry()
            } catch (e: IOException) {
                return InstallInspector.SwordZipScan(emptyList(), 0, isEpub = false, invalid = true)
            }
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
    val dir = KoinPlatform.getKoin().get<AppDirectories>().modulesDir / "epub/$displayName"
    if (!FileSystem.SYSTEM.exists(dir)) return false
    val initials = epubInitials(displayName)
    val book = Books.installed().getBook(initials)
    val optimizerVersion = ((book as? SwordGenBook)?.backend as? EpubBackend)?.state?.optimizerVersion ?: 1
    val bookmarkCount = if (DatabaseContainer.ready) {
        DatabaseContainer.instance.bookmarkDb.bookmarkDao().genericBookmarkCountFor(initials)
    } else 0
    return bookmarkCount > 0 && optimizerVersion < EPUB_OPTIMIZER_VERSION
}
