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

import net.bible.android.SharedConstants
import net.bible.android.control.backup.BackupControl
import net.bible.android.database.BookmarkDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils.unzipInputStream
import net.bible.service.db.importDatabaseFile
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import net.bible.service.sword.csvprompt.addManuallyInstalledCsvPromptBooks
import net.bible.service.sword.epub.addManuallyInstalledEpubBooks
import net.bible.service.sword.epub.deleteEpubModule
import net.bible.service.sword.esword.addESwordBook
import net.bible.service.sword.mybible.addMyBibleBook
import net.bible.service.sword.mysword.addMySwordBook
import net.bible.service.sword.ttf.addManuallyInstalledTtfBooks
import java.io.File
import java.io.FileOutputStream

/** Thrown by the committer when a file is not a valid module (e.g. bad SQLite header). */
class InvalidInstallFile(val filename: String) : Exception()

/**
 * Headless "commit" step of the InstallZip pipeline: writes/registers an already-acquired,
 * already-classified local file as its target module type. Every `commit*` here is called by the
 * (upcoming, Task A3) runner state machine once any user decision (overwrite / StudyPad import /
 * epub upgrade) has already been resolved -- this interface performs no decisioning of its own.
 *
 * Implementations MUST delegate the actual extraction/registration to the existing engine
 * (`BackupControl` / `net.bible.service.sword.*`) rather than reimplementing it -- see
 * [AndroidInstallCommitter] for the mapping. Tests for the runner exercise this interface via a
 * fake; [AndroidInstallCommitter] itself is verified by a thin adapter test.
 */
interface InstallCommitter {
    /** SWORD zip. Reopen the local file per call (engine reads once). */
    suspend fun commitSwordZip(localFile: File, totalEntries: Int, onProgress: (Int) -> Unit)

    /** @return true on success, false if the epub was rejected by the discovery scanner
     *  (`addManuallyInstalledEpubBooks()` genuinely returns Boolean). */
    suspend fun commitEpub(localFile: File, displayName: String, deleteExisting: Boolean): Boolean

    /** Copies + registers a MyBible/MySword/eSword sqlite book. Throws [InvalidInstallFile] if the
     *  file is not a real SQLite3 db (16-byte header != "SQLite format 3\u0000"), mirroring classic.
     *  NB: `addMyBibleBook`/`addMySwordBook`/`addESwordBook` return **Unit** -- the classic
     *  `if (book == null)` after them is dead code (Unit == null is always false), so there is NO
     *  null-return to detect; the only validity gate is the SQLite header check. */
    suspend fun commitSqlite(localFile: File, type: SqliteBookType, displayName: String)

    suspend fun commitTtf(localFile: File, displayName: String)

    suspend fun commitCsv(localFile: File, displayName: String)

    suspend fun commitBackgroundImage(localFile: File, fileName: String)

    suspend fun commitStudyPad(unzipFolder: File)
}

/**
 * Thin, side-effect-only adapter over the existing document-install engine. See classic
 * `net.bible.android.view.activity.installzip.InstallZip.installFromFile` (+ its `ZipHandler`/
 * `installEpub`/`installTtf`/`installPromptCsv`/`installBackgroundImage`/`installStudyPads`) for
 * the logic this mirrors -- deliberately not re-implemented here.
 */
class AndroidInstallCommitter : InstallCommitter {

    override suspend fun commitSwordZip(localFile: File, totalEntries: Int, onProgress: (Int) -> Unit) {
        BackupControl.extractAndRegisterModuleArchive(
            newInputStream = { localFile.inputStream() },
            totalEntries = totalEntries,
            onProgress = { percent -> onProgress(percent) },
        )
    }

    override suspend fun commitEpub(localFile: File, displayName: String, deleteExisting: Boolean): Boolean {
        val dir = File(File(SharedConstants.modulesDir, "epub"), displayName)
        if (deleteExisting) {
            deleteEpubModule(dir)
        }
        dir.mkdirs()
        unzipInputStream(localFile.inputStream(), dir)
        return addManuallyInstalledEpubBooks()
    }

    override suspend fun commitSqlite(localFile: File, type: SqliteBookType, displayName: String) {
        val outDir = File(SharedConstants.modulesDir, type.name.lowercase())
        outDir.mkdirs()
        val outFile = File(outDir, displayName)
        localFile.inputStream().use { fIn ->
            // Mirrors classic InstallZip.installFromFile's sqlite header/copy block exactly.
            val header = ByteArray(16)
            fIn.read(header)
            if (String(header) != "SQLite format 3\u0000") {
                throw InvalidInstallFile(displayName)
            }
            FileOutputStream(outFile).use { out ->
                out.write(header)
                fIn.copyTo(out)
            }
        }
        when (type) {
            SqliteBookType.MYBIBLE -> addMyBibleBook(outFile)
            SqliteBookType.MYSWORD -> addMySwordBook(outFile)
            SqliteBookType.ESWORD -> addESwordBook(outFile)
        }
    }

    override suspend fun commitTtf(localFile: File, displayName: String) {
        val outDir = File(SharedConstants.modulesDir, "ttf")
        outDir.mkdirs()
        localFile.copyTo(File(outDir, displayName), overwrite = true)
        addManuallyInstalledTtfBooks()
        AndBibleAddons.clearCaches()
    }

    override suspend fun commitCsv(localFile: File, displayName: String) {
        val outDir = File(SharedConstants.modulesDir, "prompts")
        outDir.mkdirs()
        localFile.copyTo(File(outDir, displayName), overwrite = true)
        addManuallyInstalledCsvPromptBooks()
        AndBibleAddons.clearCaches()
    }

    override suspend fun commitBackgroundImage(localFile: File, fileName: String) {
        val outDir = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR)
        outDir.mkdirs()
        localFile.copyTo(File(outDir, fileName), overwrite = true)
        addManuallyInstalledBackgroundImageBooks()
        AndBibleAddons.clearCaches()
    }

    override suspend fun commitStudyPad(unzipFolder: File) {
        val file = File(unzipFolder, "db/${BookmarkDatabase.dbFileName}")
        importDatabaseFile(SyncableDatabaseDefinition.BOOKMARKS, file)
        unzipFolder.deleteRecursively()
    }
}
