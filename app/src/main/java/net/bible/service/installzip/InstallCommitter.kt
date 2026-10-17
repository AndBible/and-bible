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

import net.bible.android.control.backup.BackupControl
import net.bible.android.database.BookmarkDatabase
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.ArchiveUtils.unzip
import net.bible.service.db.importDatabaseFile
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import net.bible.service.sword.csvprompt.addManuallyInstalledCsvPromptBooks
import net.bible.service.sword.epub.addOwnedEpubBook
import net.bible.service.sword.epub.deleteOwnedEpubModule
import net.bible.service.sword.epub.epubInitials
import net.bible.service.sword.epub.installEpubModule
import net.bible.service.sword.esword.addESwordBook
import net.bible.service.sword.mybible.addMyBibleBook
import net.bible.service.sword.mysword.addMySwordBook
import net.bible.service.sword.ttf.addManuallyInstalledTtfBooks
import net.bible.sharedcore.platform.AppDirectories
import okio.FileSystem
import okio.Path
import okio.buffer
import org.koin.mp.KoinPlatform

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
 * [DefaultInstallCommitter] for the mapping. Tests for the runner exercise this interface via a
 * fake; [DefaultInstallCommitter] itself is verified by a thin adapter test.
 */
interface InstallCommitter {
    /** SWORD zip. Reopen the local file per call (engine reads once). */
    suspend fun commitSwordZip(localFile: Path, totalEntries: Int, onProgress: (Int) -> Unit)

    /** @return true on success, false if the epub was rejected by the discovery scanner
     *  (`addManuallyInstalledEpubBooks()` genuinely returns Boolean). */
    suspend fun commitEpub(localFile: Path, displayName: String, deleteExisting: Boolean): Boolean

    /** Copies + registers a MyBible/MySword/eSword sqlite book. Throws [InvalidInstallFile] if the
     *  file is not a real SQLite3 db (16-byte header != "SQLite format 3\u0000"), mirroring classic.
     *  NB: `addMyBibleBook`/`addMySwordBook`/`addESwordBook` return **Unit** -- the classic
     *  `if (book == null)` after them is dead code (Unit == null is always false), so there is NO
     *  null-return to detect; the only validity gate is the SQLite header check. */
    suspend fun commitSqlite(localFile: Path, type: SqliteBookType, displayName: String)

    suspend fun commitTtf(localFile: Path, displayName: String)

    suspend fun commitCsv(localFile: Path, displayName: String)

    suspend fun commitBackgroundImage(localFile: Path, fileName: String)

    suspend fun commitStudyPad(unzipFolder: Path)
}

/**
 * Thin, side-effect-only adapter over the existing document-install engine. See classic
 * `net.bible.android.view.activity.installzip.InstallZip.installFromFile` (+ its `ZipHandler`/
 * `installEpub`/`installTtf`/`installPromptCsv`/`installBackgroundImage`/`installStudyPads`) for
 * the logic this mirrors -- deliberately not re-implemented here.
 */
class DefaultInstallCommitter(
    private val backup: BackupControl,
    private val strings: net.bible.sharedcore.platform.CoreStrings,
    private val environment: net.bible.sharedcore.platform.AppEnvironment,
    /** Drops the add-on discovery caches after a font, prompt or background-image file landed (platform add-on discovery). */
    private val clearAddonCaches: () -> Unit,
    /** Resolved lazily, only by [commitSwordZip], so the other commit paths never touch Koin. */
    private val installWatcher: () -> net.bible.android.control.versification.BookInstallWatcher = {
        KoinPlatform.getKoin().get()
    },
) : InstallCommitter {

    private val fileSystem = FileSystem.SYSTEM
    private val modulesDir: Path get() = KoinPlatform.getKoin().get<AppDirectories>().modulesDir

    override suspend fun commitSwordZip(localFile: Path, totalEntries: Int, onProgress: (Int) -> Unit) {
        backup.extractAndRegisterModuleArchive(
            newSource = { fileSystem.source(localFile) },
            totalEntries = totalEntries,
            onProgress = { percent -> onProgress(percent) },
        )
        // Registration fired bookAdded synchronously, which queued the row write that restores the
        // retained cipher key. Success must not be published while the new Book is still locked.
        // Safe: this runs in the install coroutine, never inside the watcher's own ordered queue.
        installWatcher().awaitPendingInstalls()
    }

    override suspend fun commitEpub(localFile: Path, displayName: String, deleteExisting: Boolean): Boolean {
        val dir = modulesDir / "epub" / displayName.trimStart('/')
        return installEpubModule(dir.toFile()) { // L1b-handover(G1): the epub engine still takes a JVM File
            if (deleteExisting) deleteOwnedEpubModule(dir.toFile())
            fileSystem.createDirectories(dir)
            try {
                unzip(localFile, dir)
                addOwnedEpubBook(dir.toFile(), strings, environment)
                org.crosswire.jsword.book.Books.installed().getBook(epubInitials(dir.name)) != null
            } catch (e: Exception) {
                deleteOwnedEpubModule(dir.toFile())
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
        }
    }

    override suspend fun commitSqlite(localFile: Path, type: SqliteBookType, displayName: String) {
        val outDir = modulesDir / type.name.lowercase()
        fileSystem.createDirectories(outDir)
        val outFile = outDir / displayName.trimStart('/')
        fileSystem.source(localFile).buffer().use { fIn ->
            // Mirrors classic InstallZip.installFromFile's sqlite header/copy block exactly.
            val header = ByteArray(16)
            fIn.read(header)
            if (header.decodeToString() != "SQLite format 3\u0000") {
                throw InvalidInstallFile(displayName)
            }
            fileSystem.sink(outFile).buffer().use { out ->
                out.write(header)
                out.writeAll(fIn)
            }
        }
        when (type) {
            SqliteBookType.MYBIBLE -> addMyBibleBook(outFile.toFile()) // L1b-handover(G1): the sqlite book engines still take a JVM File
            SqliteBookType.MYSWORD -> addMySwordBook(outFile.toFile()) // L1b-handover(G1): same
            SqliteBookType.ESWORD -> addESwordBook(outFile.toFile()) // L1b-handover(G1): same
        }
    }

    override suspend fun commitTtf(localFile: Path, displayName: String) {
        val outDir = modulesDir / "ttf"
        fileSystem.createDirectories(outDir)
        fileSystem.copy(localFile, outDir / displayName.trimStart('/'))
        addManuallyInstalledTtfBooks()
        clearAddonCaches()
    }

    override suspend fun commitCsv(localFile: Path, displayName: String) {
        val outDir = modulesDir / "prompts"
        fileSystem.createDirectories(outDir)
        fileSystem.copy(localFile, outDir / displayName.trimStart('/'))
        addManuallyInstalledCsvPromptBooks()
        clearAddonCaches()
    }

    override suspend fun commitBackgroundImage(localFile: Path, fileName: String) {
        val outDir = modulesDir / BACKGROUND_IMAGE_DIR
        fileSystem.createDirectories(outDir)
        fileSystem.copy(localFile, outDir / fileName.trimStart('/'))
        addManuallyInstalledBackgroundImageBooks()
        clearAddonCaches()
    }

    override suspend fun commitStudyPad(unzipFolder: Path) {
        val file = unzipFolder / "db/${BookmarkDatabase.dbFileName}"
        importDatabaseFile(SyncableDatabaseDefinition.BOOKMARKS, file)
        runCatching { fileSystem.deleteRecursively(unzipFolder, mustExist = false) } // best-effort cleanup, as File.deleteRecursively was
    }
}
