/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import okio.use
import kotlinx.coroutines.IO
import net.bible.sharedcore.platform.files.BackupFileSniffer
import okio.FileSystem
import okio.Path
import okio.IOException
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.sharedcore.platform.CoreStrings

import net.bible.android.control.document.DocumentChanges
import net.bible.sharedcore.log.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.OLD_DATABASE_VERSION
import net.bible.android.database.SyncableRoomDatabase
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.service.installzip.moduleArchiveTargetName
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseContainer.Companion.maxDatabaseVersion
import net.bible.service.db.OLD_MONOLITHIC_DATABASE_NAME
import net.bible.service.db.readUserVersion
import net.bible.service.download.isPseudoBook
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.safeZipEntryPath
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.common.DbType
import net.bible.service.common.dosDateTime
import net.bible.sharedcore.platform.DateTimeFormats
import org.crosswire.common.compress.GzipStreams
import org.crosswire.common.compress.ZipReader
import org.crosswire.common.compress.ZipWriter
import org.koin.mp.KoinPlatform
import kotlin.time.Clock
import net.bible.service.db.bookmarksDbStats
import net.bible.service.db.deleteAppDatabase
import net.bible.service.db.importDatabaseFile
import net.bible.service.sword.dbPath
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import net.bible.service.sword.backgroundimage.backgroundImageFile
import net.bible.service.sword.backgroundimage.isBackgroundImageModule
import net.bible.service.sword.csvprompt.addManuallyInstalledCsvPromptBooks
import net.bible.service.sword.epub.addManuallyInstalledEpubBooks
import net.bible.service.sword.epub.epubDir
import net.bible.service.sword.epub.isManuallyInstalledEpub
import net.bible.service.sword.esword.addManuallyInstalledESwordBooks
import net.bible.service.sword.esword.isManuallyInstalledESwordBook
import net.bible.service.sword.mybible.addManuallyInstalledMyBibleBooks
import net.bible.service.sword.mybible.isManuallyInstalledMyBibleBook
import net.bible.service.sword.mysword.addManuallyInstalledMySwordBooks
import net.bible.service.sword.mysword.isManuallyInstalledMySwordBook
import net.bible.service.sword.ttf.addManuallyInstalledTtfBooks
import net.bible.service.sword.ttf.isManuallyInstalledTtf
import net.bible.service.sword.ttf.ttfFile
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.crosswire.common.util.NetUtil
import org.crosswire.common.util.KUri
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.book.sword.SwordConstants

const val DATABASE_BACKUP_SUFFIX = ".abdb.zip"
const val MODULE_BACKUP_SUFFIX = ".abmd.zip"

const val DATABASE_BACKUP_NAME = "AndBibleDatabaseBackup$DATABASE_BACKUP_SUFFIX"
const val MODULE_BACKUP_NAME = "AndBibleModulesBackup$MODULE_BACKUP_SUFFIX"

const val ZIP_MIMETYPE = "application/zip"

private val SQLITE_MAGIC = "SQLite format 3\u0000".encodeUtf8()
private val GZIP_MAGIC = "1f8b".decodeHex()

/**
 * [dir] must be an absolute, existing directory; the URI gets a trailing '/' like `File.toURI()`.
 * The `file:` [KUri] of the existing directory [dir], with the trailing slash that marks a directory: what
 * `NetUtilJvm.getURI(File)` (`File.toURI()`) returns for it, built the way jsword's common code builds directory
 * URIs. Used as the library root of newly extracted SWORD books.
 */
@VisibleForTesting fun directoryKUri(dir: Path): KUri {
    val spelling = dir.toString()
    return KUri("file", null, if (spelling.endsWith("/")) spelling else "$spelling/", null)
}


/** What to do with the result of the save/share picker (F125). */
@VisibleForTesting enum class PickerOutcome { CANCELLED, FAILED, DONE }

/** Interprets the picker result: a cancelled save picker is neither an error nor a success. */
@VisibleForTesting fun pickerOutcome(result: DeliveryResult): PickerOutcome = when (result) {
    DeliveryResult.CANCELLED -> PickerOutcome.CANCELLED
    DeliveryResult.FAILED -> PickerOutcome.FAILED
    DeliveryResult.DELIVERED -> PickerOutcome.DONE
}

/**
 * Which destination dialog to run. Branches on whether a chooser was SUPPLIED, never on what it
 * returned: a chooser returning null means the user cancelled, and must abort the export rather
 * than fall through to the platform dialog asking the same question again (the C1 whole-branch
 * review defect — an elvis on the chooser's RESULT cannot tell "no chooser" apart from "chooser
 * ran and was cancelled", so Cancel on the Compose dialog re-opened the platform one). Internal so
 * [net.bible.android.view.activity.backup.SaveOrShareDestinationTest] can pin this contract
 * directly, without booting an Activity.
 */
@VisibleForTesting suspend fun resolveDestination(
    chooseDestination: (suspend () -> SaveOrShare?)?,
    platformPrompt: suspend () -> SaveOrShare?,
): SaveOrShare? = if (chooseDestination != null) chooseDestination() else platformPrompt()

class BackupControl(
    private val dialogs: AppDialogController,
    private val strings: CoreStrings,
    private val env: net.bible.sharedcore.platform.AppEnvironment,
    private val settings: net.bible.sharedcore.platform.AppSettings,
    private val windowControl: net.bible.android.control.page.window.WindowControl,
    private val cloudSync: CloudSync,
    private val notifier: net.bible.sharedcore.platform.UserNotifier,
    private val dirs: net.bible.sharedcore.platform.AppDirectories = KoinPlatform.getKoin().get(),
) {
    // AppDialogRequest.Options ids for askIfRestoreOrImport (D8-1).
    private val RESTORE_VALUE = "restore"
    private val IMPORT_VALUE = "import"

    // AppDialogRequest.Options ids for saveOrShare's platformPrompt fallback (Task 24 Step 2).
    private val SHARE_VALUE = "share"
    private val SAVE_VALUE = "save"

    /**
     * `saveOrShare`'s `platformPrompt` (Task 24 Step 2): an owner-less `AppDialogController` sheet
     * in place of the old `android.app.AlertDialog`, for the callers that pass no `chooseDestination`
     * (NavHostComposeActivity supplies its own for the rest, via `askDestination()`). Visible for tests, not
     * private, so `BackupControlTest` can pin the mapping directly without driving the whole export
     * flow (`activity.awaitIntent` etc.).
     */
    @VisibleForTesting suspend fun classicDestinationPrompt(promptTitle: String, promptMessage: String): SaveOrShare? {
        val result = dialogs.await(
            AppDialogRequest.Options(
                title = promptTitle,
                message = promptMessage,
                options = listOf(
                    SettingsItem.Choice(SHARE_VALUE, strings.share),
                    SettingsItem.Choice(SAVE_VALUE, strings.backupPhoneStorage),
                ),
                dismissText = null,
                asActionSheet = true,
            ),
        )
        return when ((result as? AppDialogResult.Selected)?.value) {
            SHARE_VALUE -> SaveOrShare.SHARE
            SAVE_VALUE -> SaveOrShare.SAVE
            else -> null // Cancel, back, or scrim.
        }
    }

    internal suspend fun saveDbBackupFileViaIntent(host: BackupHost, file: Path) =
        saveOrShare(
            host = host,
            file = file,
            fileName = DATABASE_BACKUP_NAME,
            subject = strings.backupEmailSubject2(env.applicationNameMedium),
            message = strings.backupEmailMessage2(env.applicationNameMedium),
            chooserTitle = strings.sendBackupFile,
            successMsg = strings.backupSuccess2,
            errorMsg = strings.errorOccurred,
        )

    public suspend fun saveOrShare(
        host: BackupHost,
        file: Path,
        fileName: String,
        shareMimeType: String = ZIP_MIMETYPE,
        saveMimeType: String = ZIP_MIMETYPE,
        subject: String? = null,
        message: String? = null,
        chooserTitle: String,
        successMsg: String? = null,
        errorMsg: String = strings.errorOccurred,
        promptTitle: String = strings.backupBackupTitle,
        promptMessage: String = strings.backupBackupMessage,
        // A Compose host supplies its own destination dialog here (NavHostComposeActivity's
        // askDestination()). Defaulted null so a caller with no host-owned chooser instead gets the
        // owner-less AppDialogController fallback below.
        chooseDestination: (suspend () -> SaveOrShare?)? = null,
    ): Boolean {
        val saveOrShare = resolveDestination(chooseDestination) {
            classicDestinationPrompt(promptTitle, promptMessage)
        } ?: return false

        // What File.absolutePath gave: a relative path is resolved against the working directory.
        val absolute = if (file.isAbsolute) file else fileSystem.canonicalize(".".toPath()) / file
        val outcome = pickerOutcome(host.deliver(
            absolute.toString(), fileName, saveOrShare, shareMimeType, saveMimeType, subject, message, chooserTitle,
        ))
        // A cancelled save picker is neither an error nor a success; stay silent.
        if (outcome == PickerOutcome.CANCELLED) return false
        val ok = outcome == PickerOutcome.DONE

        withContext(Dispatchers.Main) {
            if (ok) {
                Log.i(TAG, "Copied database to chosen backup location successfully")
                if(successMsg != null) dialogs.await(AppDialogRequest.Message(
                    title = null, message = successMsg, confirmText = strings.okay,
                ))
            } else {
                Log.e(TAG, "Error copying database to chosen location.")
                host.showErrorReport(errorMsg, null)
            }
        }

        return ok
    }

    private suspend fun restoreOldMonolithicDatabaseFromSource(host: BackupHost, source: PickedSource): Boolean =
        restoreOldMonolithicDatabase(host.openInput(source))

    /** The old (pre-split, version <= [OLD_DATABASE_VERSION]) single-file database, gzipped or not. Closes [source]. */
    @VisibleForTesting
    suspend fun restoreOldMonolithicDatabase(source: Source): Boolean = withContext(Dispatchers.IO) {
        val tmpFile = internalDbBackupDir / OLD_MONOLITHIC_DATABASE_NAME
        try {
            if (!unpackOldMonolithicDatabase(source, tmpFile)) return@withContext false
            // A running sync may need replacement admission to finish. Never wait for it while owning that gate.
            for (def in SyncableDatabaseDefinition.ALL) beforeRestore(def)
            reloadingAfterReplace { DatabaseContainer.replacingDatabases { token -> swapInOldMonolithicDatabase(tmpFile, token) } }
        } finally {
            deleteQuietly(tmpFile)
        }
    }


    /**
     * Test seam (F120): the copy of the validated file into the database directory. An `IOException` (an unreadable
     * source, a full disk) is the answer `false`, which rolls the restore back.
     */
    @VisibleForTesting
    var copyStagedDatabase: (Path, Path) -> Boolean = { from, to ->
        try {
            FileSystem.SYSTEM.copy(from, to)
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error copying the staged database", e)
            false
        }
    }

    /** Unpacks [source] (gunzipping it if needed) into [target]; true only for SQLite of version <= [OLD_DATABASE_VERSION]. */
    private fun unpackOldMonolithicDatabase(source: Source, target: Path): Boolean {
        val buffered = source.buffer()
        val input = if (buffered.rangeEquals(0, GZIP_MAGIC)) GzipStreams.gunzip(buffered).buffer() else buffered
        input.use { stream ->
            if (!stream.request(SQLITE_MAGIC.size.toLong()) || !stream.buffer.rangeEquals(0, SQLITE_MAGIC)) return false
            fileSystem.write(target) { writeAll(stream) }
        }
        val version = readUserVersion(target.toString())
        Log.i(TAG, "Old monolithic backup database, version $version")
        return version <= OLD_DATABASE_VERSION
    }

    /** F120's snapshot: a SIBLING of [internalDbDir], because `Context.databaseList()` lists subdirectories too. */
    private val restoreRollbackDir: Path get() = internalDbDir.parent!! / "db-restore-rollback"

    /**
     * F120. Puts the validated old database [validated] in place of the current databases. Those are moved
     * aside first and moved back if the copy, the rename, opening and splitting, any Room migration (every
     * database is opened) or [afterRestore] fails, so a failure returns false with the user's data untouched.
     * The snapshot is deleted only after all of those. Runs inside [DatabaseContainer.replacingDatabases].
     */
    private suspend fun swapInOldMonolithicDatabase(validated: Path, token: net.bible.service.llm.AiDatabaseLifetime.Replacement): Boolean {
        // Not reset(): with no open instance, reset() BUILDS a container (open, migrate) just to close it,
        // which touches the files about to be snapshotted and runs containerFactory too early.
        DatabaseContainer.dropInstanceWithoutOpeningOwned(token)
        val rollbackDir = restoreRollbackDir
        if (fileSystem.exists(rollbackDir)) {
            // An earlier restore died before cleaning up; its snapshot may be the user's only copy.
            val aside = rollbackDir.parent!! / "${rollbackDir.name}-${Clock.System.now().toEpochMilliseconds()}"
            if (!moveFile(rollbackDir, aside)) {
                Log.e(TAG, "F120: cannot move the leftover $rollbackDir aside; restore aborted")
                return false
            }
        }
        if (!moveAllFiles(internalDbDir, rollbackDir)) {
            Log.e(TAG, "F120: could not move the current databases aside; nothing changed")
            deleteQuietly(rollbackDir)
            return false
        }
        val ok = try {
            val staged = internalDbDir / "$OLD_MONOLITHIC_DATABASE_NAME.staging"
            val placed = copyStagedDatabase(validated, staged) &&
                fileLength(staged) == fileLength(validated) &&
                moveFile(staged, internalDbDir / OLD_MONOLITHIC_DATABASE_NAME)
            if (placed && DatabaseContainer.ready) {
                // Opening the container splits the old file, but the split databases are written at version 1 and
                // Room migrates each one on its first access. sync() touches every database, and afterRestore()
                // is the first real use; both run here so a throwing migration is still rolled back.
                DatabaseContainer.sync()
                afterRestore()
            }
            placed
        } catch (e: Exception) {
            Log.e(TAG, "F120: old monolithic restore failed", e)
            false
        }
        if (!ok) {
            // The instance may be fully open (or half built if `instance` threw after `_instance` was set); close it
            // before the files are deleted and moved.
            DatabaseContainer.dropInstanceWithoutOpeningOwned(token)
            rollBackOldMonolithicRestore(rollbackDir)
            return false
        }
        deleteTreeQuietly(rollbackDir)
        return true
    }

    private fun fileLength(file: Path): Long = fileSystem.metadataOrNull(file)?.size ?: 0L

    /** Test seam: the rename behind [moveFile], so a test can make the put-back of a rollback fail too. */
    @VisibleForTesting
    var atomicMove: (Path, Path) -> Unit = { from, to -> FileSystem.SYSTEM.atomicMove(from, to) }

    /** `File.renameTo`: false instead of an exception when the move fails. */
    private fun moveFile(from: Path, to: Path): Boolean = try {
        atomicMove(from, to)
        true
    } catch (e: IOException) {
        false
    }

    /**
     * `File.delete`: a failure is logged, not thrown. Not recursive: okio refuses a non-empty directory, so a
     * snapshot directory that still holds a user database is kept (F120).
     */
    private fun deleteQuietly(path: Path) {
        try {
            fileSystem.delete(path, mustExist = false)
        } catch (e: IOException) {
            Log.w(TAG, "Could not delete $path", e)
        }
    }

    /** `File.deleteRecursively`: a failure is logged, not thrown. */
    private fun deleteTreeQuietly(path: Path) {
        try {
            fileSystem.deleteRecursively(path, mustExist = false)
        } catch (e: IOException) {
            Log.w(TAG, "Could not delete $path", e)
        }
    }

    private fun regularFilesOf(dir: Path): List<Path> =
        fileSystem.listOrNull(dir).orEmpty().filter { fileSystem.metadataOrNull(it)?.isRegularFile == true }

    /** Moves every file of [from] into [to]. On a failed move, puts back the ones already moved and returns false. */
    private fun moveAllFiles(from: Path, to: Path): Boolean {
        try {
            fileSystem.createDirectories(to)
        } catch (e: IOException) {
            Log.w(TAG, "Could not create $to", e)
        }
        val moved = mutableListOf<Path>()
        for (f in regularFilesOf(from)) {
            val target = to / f.name
            if (!moveFile(f, target)) {
                moved.forEach { moveFile(it, from / it.name) }
                return false
            }
            moved += target
        }
        return true
    }

    /** Deletes whatever the failed attempt created and moves the snapshot back; keeps the snapshot if that fails. */
    private fun rollBackOldMonolithicRestore(rollbackDir: Path) {
        regularFilesOf(internalDbDir).forEach { deleteQuietly(it) }
        if (moveAllFiles(rollbackDir, internalDbDir)) {
            deleteTreeQuietly(rollbackDir)
        } else {
            Log.e(TAG, "F120: could not move the pre-restore databases back; they are kept in $rollbackDir")
        }
    }

    @VisibleForTesting suspend fun selectDatabaseSections(available: List<String>): List<String> =
        selectMany(strings.restoreBackupSections, available, { strings.databaseTitle(it) }, true)

    private suspend fun <T> selectMany(title: String, items: List<T>, label: (T) -> String, preselected: Boolean = false): List<T> {
        val ids = items.indices.map { it.toString() }
        val result = dialogs.await(AppDialogRequest.MultiChoice(
            title = title,
            options = items.mapIndexed { index, item -> SettingsItem.Choice(index.toString(), label(item)) },
            selectedIds = if (preselected) ids else emptyList(),
            confirmText = strings.okay, dismissText = strings.cancel,
            selectAllText = strings.selectAll, selectNoneText = strings.selectNone,
        ))
        val selected = (result as? AppDialogResult.SelectedMany)?.ids.orEmpty().toSet()
        return items.filterIndexed { index, _ -> index.toString() in selected }
    }

    private fun showMessage(message: String) {
        // The notifier preserves the nonblocking foreground dialog and no-host toast fallback.
        notifier.showError(message)
    }

    private suspend fun confirm(message: String): Boolean = dialogs.await(AppDialogRequest.Confirm(
        title = strings.areYouSure, message = message, confirmText = strings.okay, dismissText = strings.cancel,
    )) == AppDialogResult.Ok

    /** Test hook: called with the module files of a SWORD book after they are listed and before they are zipped. */
    @VisibleForTesting var onModuleFilesListed: (List<Path>) -> Unit = {}

    private val fileSystem = FileSystem.SYSTEM

    private fun dosTimeNow(): Long =
        dosDateTime(Clock.System.now().toEpochMilliseconds(), KoinPlatform.getKoin().get<DateTimeFormats>())

    private fun relativeFileName(rootDir: Path, file: Path): String {
        val filePath = fileSystem.canonicalize(file).toString()
        val dirPath = fileSystem.canonicalize(rootDir).toString()
        return filePath.substring(dirPath.length + 1)
    }

    private fun addFile(outFile: ZipWriter, rootDir: Path, file: Path) {
        outFile.putEntry(relativeFileName(rootDir, file), fileSystem.source(file), dosTimeNow())
    }

    private fun addModuleFile(outFile: ZipWriter, moduleFile: Path) {
        val fileNameInsideZip = moduleFile.relativeTo(dirs.modulesDir).toString()
        outFile.putEntry(fileNameInsideZip, fileSystem.source(moduleFile), dosTimeNow())
    }

    /** The regular files below [dir] (following symbolic links); none when [dir] does not exist. */
    private fun filesBelow(dir: Path): List<Path> {
        if (!fileSystem.exists(dir)) return emptyList()
        return fileSystem.listRecursively(dir, followSymlinks = true)
            .filter { fileSystem.metadataOrNull(it)?.isRegularFile == true }
            .toList()
    }

    private fun addFiles(outFile: ZipWriter, rootDir: Path, files: List<Path>) {
        onModuleFilesListed(files)
        for (f in files) {
            addFile(outFile, rootDir, f)
        }
    }

    private fun addModuleDir(outFile: ZipWriter, modDir: Path) {
        addFiles(outFile, dirs.modulesDir, filesBelow(modDir))
    }

    private fun addBookToZip(outFile: ZipWriter, b: Book) {
        val bmd = b.bookMetaData as SwordBookMetaData
        if (b.isManuallyInstalledMyBibleBook) {
            addModuleFile(outFile, b.dbPath)
        } else if (b.isManuallyInstalledMySwordBook) {
            addModuleFile(outFile, b.dbPath)
        } else if (b.isManuallyInstalledESwordBook) {
            addModuleFile(outFile, b.dbPath)
        } else if (b.isManuallyInstalledEpub) {
            addModuleDir(outFile, dirs.modulesDir / b.epubDir!!)
        } else if (b.isManuallyInstalledTtf) {
            // Manually-installed font modules have byte-array metadata (no configFile), so they
            // must be packaged by their .ttf file rather than via the generic SWORD configFile
            // branch below. Skip gracefully if the underlying file has gone missing, so one
            // broken font module can't abort the whole backup/sync operation.
            val ttfFile = b.ttfFile
            if (fileSystem.exists(ttfFile)) {
                addModuleFile(outFile, ttfFile)
            } else {
                Log.w(TAG, "Skipping font module ${b.initials}: file not found $ttfFile")
            }
        } else if (b.isBackgroundImageModule) {
            // Background-image modules also have byte-array metadata (no configFile), so package
            // their single image file rather than via the generic SWORD branch below. Skip
            // gracefully if the file is missing, so one broken module can't abort the whole
            // backup/sync operation.
            val imageFile = b.backgroundImageFile
            if (fileSystem.exists(imageFile)) {
                addModuleFile(outFile, imageFile)
            } else {
                Log.w(TAG, "Skipping background-image module ${b.initials}: file not found $imageFile")
            }
        } else {
            // Books constructed from byte-array metadata have no configFile — there is no .conf on
            // disk to locate the module root from. Every such type AndBible creates has its own
            // branch above, but an unhandled one (e.g. the MyDocument pseudo-books of OSTicket 3392)
            // must be skipped rather than abort the whole backup/sync operation with an NPE.
            val configFile = bmd.configFile
            if (configFile == null) {
                Log.w(TAG, "Skipping ${b.initials}: no SWORD config file (not a file-backed module)")
                return
            }
            val rootDir = configFile.parent!!.parent!!
            addFile(outFile, rootDir, configFile)
            val dataPath = bmd.getProperty("DataPath")
            val dataDir = rootDir.resolve(dataPath!!.trimStart('/'), normalize = true).run {
                if (listOf(
                        BookCategory.DICTIONARY,
                        BookCategory.GENERAL_BOOK,
                        BookCategory.MAPS
                    ).contains(b.bookCategory)
                )
                    parent!!
                else this
            }
            addFiles(outFile, rootDir, filesBelow(dataDir))
        }
    }

    /**
     * Writes a ZIP to `<zipFile>.tmp` and moves it to [zipFile] only when complete, so a backup that fails halfway
     * (a module file vanishing, a full disk) leaves nothing under the final name that looks like a finished archive.
     */
    private fun writeZipAtomically(zipFile: Path, write: (ZipWriter) -> Unit) {
        val tmp = zipFile.parent!! / "${zipFile.name}.tmp"
        // a failed backup must not leave a stale archive that could be shared as a fresh backup
        fileSystem.delete(zipFile, mustExist = false)
        try {
            ZipWriter(fileSystem.sink(tmp)).use(write)
            fileSystem.atomicMove(tmp, zipFile)
        } catch (t: Throwable) {
            fileSystem.delete(tmp, mustExist = false)
            throw t
        }
    }

    private suspend fun createModulesZip(books: List<Book>, zipFile: Path) {
        val manifest = AndBibleBackupManifest(backupType = BackupType.MODULE_BACKUP, andBibleVersion = env.versionNumber.toInt())

        withContext(Dispatchers.IO) {
            writeZipAtomically(zipFile) { outFile ->
                manifest.saveToZip(outFile, dosTimeNow())
                for (b in books) {
                    addBookToZip(outFile, b)
                }
            }
        }
    }

    suspend fun createSingleModuleZip(book: Book, zipFile: Path) = withContext(Dispatchers.IO) {
        val manifest = AndBibleBackupManifest(backupType = BackupType.MODULE_BACKUP, andBibleVersion = env.versionNumber.toInt())
        writeZipAtomically(zipFile) { outFile ->
            manifest.saveToZip(outFile, dosTimeNow())
            addBookToZip(outFile, book)
        }
    }

    fun clearBackupDir() {
        deleteTreeQuietly(internalDbBackupDir)

        val fileList = fileSystem.listOrNull(env.dbBackupPath.toPath()) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val maxAge = 3*2592000000L // 3*30 days in milliseconds
        for(f in fileList) {
            if(now - (fileSystem.metadataOrNull(f)?.lastModifiedAtMillis ?: 0L) > maxAge) {
                deleteQuietly(f)
            }
        }
    }

    suspend fun backupModulesViaIntent(host: BackupHost)  = withContext(Dispatchers.Main)   {
        val fileName = MODULE_BACKUP_NAME
        val zipFile = internalDbBackupDir / fileName // the getter creates the directory
        val books = selectMany(
            strings.backupModulesTitle,
            Books.installed().getBooks().filter { !it.isPseudoBook && !it.isMyDocument }.sortedBy { it.language },
            { strings.somethingWithParenthesis(it.name, "${it.initials}, ${it.language.code}") },
        )

        if (books.isEmpty()) return@withContext

        val progressId = dialogs.show(AppDialogRequest.Progress(title = null, message = strings.pleaseWait))
        try {
            createModulesZip(books, zipFile)
        } finally {
            dialogs.dismiss(progressId)
        }

        val modulesString = books.joinToString(", ") { it.abbreviation }
        val subject = strings.backupModulesEmailSubject2(env.applicationNameMedium)
        val message = strings.backupModulesEmailMessage2(env.applicationNameMedium, modulesString)

        saveOrShare(
            host = host,
            file = zipFile,
            fileName = fileName,
            subject = subject,
            message = message,
            chooserTitle = strings.sendBackupFile,
            successMsg = strings.backupModulesSuccess,
            errorMsg = strings.errorOccurred,
        )
    }

    suspend fun backupApp(host: BackupHost) {

        val tempFile = internalDbBackupDir / strings.apkFile
        withContext(Dispatchers.IO) {
            fileSystem.delete(tempFile, mustExist = false)
            fileSystem.copy(requireNotNull(env.apkPath).toPath(), tempFile)
        }

        saveOrShare(
            host,
            file = tempFile,
            fileName = "and-bible.apk",
            chooserTitle = strings.backupApp2,
            // MIME of .apk is "application/vnd.android.package-archive".
            // but Bluetooth does not accept this. Let's use "*/*" instead.
            shareMimeType = "*/*" ,
            saveMimeType = "application/vnd.android.package-archive",
        )
    }

    /**
     * Saves the open windows, flushes queued settings writes, vacuums and checkpoints the databases when they are
     * open, then zips them ([zipDatabaseFiles]).
     */
    suspend fun makeDatabaseBackupFile(): Path? {
        if(env.isInitialized && DatabaseContainer.ready) {
            windowControl.windowRepository.saveIntoDb()
            // The save is an ordered write-through: the zip must not read the file before it lands.
            windowControl.windowRepository.persistence.awaitPersisted()
            // A setting changed just before "backup now" may still be queued in the store's writer.
            DatabaseContainer.instance.settingsStore.flush()
            DatabaseContainer.vacuum()
            DatabaseContainer.sync()
        }
        return zipDatabaseFiles()
    }

    /**
     * Zips the database files as they are on disk, with the backup manifest; null when there are none. Without
     * [makeDatabaseBackupFile]'s vacuum and checkpoint: the pre-migration safety backup runs while the container is
     * still being built, when the databases are not open yet.
     */
    fun zipDatabaseFiles(): Path? {
        val zipFile = internalDbBackupDir / DATABASE_BACKUP_NAME
        fileSystem.delete(zipFile, mustExist = false)

        val dbDir = internalDbDir
        val files = ALL_DB_FILENAMES.map { dbDir / it }.filter { fileSystem.exists(it) }
        if(files.isEmpty()) return null

        val manifest = AndBibleBackupManifest(
            backupType = BackupType.DB_BACKUP, contains = setOf(
                DbType.BOOKMARKS, DbType.WORKSPACES, DbType.READINGPLANS, DbType.REPOSITORIES, DbType.SETTINGS,
                DbType.MYDOCUMENTS, DbType.AI_SETTINGS
            ),
            andBibleVersion = env.versionNumber.toInt()
        )

        writeZipAtomically(zipFile) { outFile ->
            manifest.saveToZip(outFile, dosTimeNow())
            for(b in files) {
                outFile.putEntry("db/${b.name}", fileSystem.source(b), dosTimeNow())
            }
        }
        return zipFile
    }

    suspend fun startBackupAppDatabase(host: BackupHost) = withContext(Dispatchers.IO) {
        val backupZipFile = makeDatabaseBackupFile()?: run {
            dialogs.await(AppDialogRequest.Message(title = null, message = strings.errorOccurred, confirmText = strings.okay))
            return@withContext
        }
        saveDbBackupFileViaIntent(host, backupZipFile)
    }

    suspend fun restoreAppDatabaseViaIntent(host: BackupHost) {
        val source = host.pickBackupFile() ?: return
        try {
            restoreAppDatabaseFromSourceWithUI(host, source)
        } catch (e: Exception) {
            host.showErrorReport(e.message ?: strings.errorOccurred, e)
        }
    }

    suspend fun restoreAppDatabaseFromSourceWithUI(host: BackupHost, source: PickedSource): Boolean {
        val filetype = withContext(Dispatchers.IO) {
            val input = runCatching { host.openInput(source) }
                .onFailure { Log.e(TAG, "Unexpected exception when opening backup for file type detection", it) }
                .getOrNull()
            if (input == null) BackupFileType.UNKNOWN else BackupFileSniffer.sniff { input }
        }
        Log.i(TAG, "Filetype: $filetype")
        return if (filetype == BackupFileType.SQLITE3) {
            restoreOldMonolithicDatabaseFromSourceWithUI(host, source)
        } else {
            restoreDatabaseZipFileInputStreamWithUI(host, source)
        }
    }

    private fun isSqliteFile(file: Path): Boolean = fileSystem.source(file).buffer().use {
        it.request(SQLITE_MAGIC.size.toLong()) && it.buffer.rangeEquals(0, SQLITE_MAGIC)
    }

    private fun verifyDatabaseBackupFile(file: Path): Boolean {
        if(!isSqliteFile(file)) return false
        val version = readUserVersion(file.toString())
        return version <= maxDatabaseVersion(file.name)
    }

    private suspend fun beforeRestore(category: SyncableDatabaseDefinition) {
        if(DatabaseContainer.ready && cloudSync.signedIn) {
            category.setSyncEnabled(settings, false)
            notifier.showInfo(strings.disablingSync)
            cloudSync.waitUntilFinished()
        }
    }

    private suspend fun afterRestore(restoredSelection: List<SyncableDatabaseDefinition>? = null) {
        val selection: List<SyncableDatabaseDefinition> = restoredSelection?: SyncableDatabaseDefinition.ALL.toList()
        for(s in selection) {
            s.setSyncEnabled(settings, false)
            val db: SyncableRoomDatabase? = when(s) {
                SyncableDatabaseDefinition.BOOKMARKS -> DatabaseContainer.instance.bookmarkDb
                SyncableDatabaseDefinition.READINGPLANS -> DatabaseContainer.instance.readingPlanDb
                SyncableDatabaseDefinition.WORKSPACES -> DatabaseContainer.instance.workspaceDb
                SyncableDatabaseDefinition.MYDOCUMENTS -> DatabaseContainer.instance.myDocumentDb
                SyncableDatabaseDefinition.AI_SETTINGS -> DatabaseContainer.instance.aiSettingsDb
                SyncableDatabaseDefinition.PROGRESS -> DatabaseContainer.instance.progressDb
            }
            if(db != null) {
                db.syncDao().clearSyncStatus()
                db.syncDao().clearSyncConfiguration()
            }
        }
    }

    /**
     * F115. Runs [body] and, however it ends (normally, by an exception, or cancelled), calls
     * [DatabaseContainer.notifyDatabaseRestored] iff a [DatabaseContainer.replacingDatabases] ran inside it. The epoch bump froze
     * the live `WindowRepository`'s saving; only the reload this triggers releases it. The ONLY caller of
     * [DatabaseContainer.notifyDatabaseRestored] in this file (BackupControlReplaceGuardTest).
     */
    @VisibleForTesting suspend fun <T> reloadingAfterReplace(body: suspend () -> T): T {
        val epoch = DatabaseContainer.replaceEpoch
        try {
            return body()
        } finally {
            if (DatabaseContainer.replaceEpoch != epoch) DatabaseContainer.notifyDatabaseRestored()
        }
    }

    private suspend fun restoreDatabaseZipFileInputStreamWithUI(
        host: BackupHost,
        source: PickedSource
    ): Boolean = withContext(Dispatchers.IO) {
        // Task 30 Step 1: no more Hourglass -- progressId tracks whichever Progress (of the several
        // shown across this function) is currently live, so the outer finally always dismisses
        // whatever is still up on any exit path (return, exception, or cancellation), the same
        // guarantee Hourglass's Job-completion hook gave for free.
        var progressId: Long? = null
        fun showProgress() {
            progressId?.let { dialogs.dismiss(it) }
            progressId = dialogs.show(AppDialogRequest.Progress(title = null, message = strings.pleaseWait))
        }
        fun dismissProgress() {
            progressId?.let { dialogs.dismiss(it) }
            progressId = null
        }
        try {
            notifier.showInfo(strings.downloadingBackup)
            showProgress()

            val tmpFile = internalDbBackupDir / "database.zip"
            val unzipFolder = internalDbBackupDir / "unzip"

            fileSystem.createDirectories(unzipFolder)

            try {
                host.openInput(source).use { input -> fileSystem.write(tmpFile) { writeAll(input) } }
                net.bible.service.common.ArchiveUtils.unzip(tmpFile, unzipFolder)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing backup file", e)
                throw IOException("Failed to process backup file: ${e.message}")
            }

            try {
                val containedBackups = ALL_DB_FILENAMES.map { unzipFolder / "db/${it}" }
                    .filter { file -> fileSystem.exists(file) && verifyDatabaseBackupFile(file) }
                    .map { file -> file.name }

                dismissProgress()
                if (containedBackups.isEmpty()) {
                    showMessage(strings.restoreUnsuccessfull)
                    return@withContext false
                }
                val selection =
                    if (containedBackups.size > 1)
                        selectDatabaseSections(containedBackups)
                    else
                        containedBackups
                if (selection.isEmpty()) {
                    return@withContext false
                }
                showProgress()
                restoreSelectedFiles(selection, unzipFolder, object : RestorePrompts {
                    override suspend fun restoreOrImport(category: SyncableDatabaseDefinition, backupFile: Path) =
                        askIfRestoreOrImport(category, backupFile)
                    override suspend fun confirmOverwrite(category: SyncableDatabaseDefinition) =
                        confirm(
                            strings.overwriteSomething(
                                strings.syncCategoryContents(category.name)
                            )
                        )
                })
                Log.i(TAG, "Restored database successfully")
            } finally {
                deleteQuietly(tmpFile)
                deleteTreeQuietly(unzipFolder)
                dismissProgress()
            }
            true
        } finally {
            progressId?.let { dialogs.dismiss(it) }
        }
    }

    /** The two questions the zip restore asks per category; a seam so tests can answer them (D1 final review I1). */
    @VisibleForTesting interface RestorePrompts {
        /** true = restore (overwrite), false = import (merge), null = skip this category. */
        suspend fun restoreOrImport(category: SyncableDatabaseDefinition, backupFile: Path): Boolean?
        /** The "are you sure" before an overwrite; false skips this category. */
        suspend fun confirmOverwrite(category: SyncableDatabaseDefinition): Boolean
    }

    /**
     * What the user chose for every selected file of a zip restore. [restoredSelection] keeps the old flow's
     * semantics: a category answered "Restore" is in it (and had [beforeRestore]) even when its "are you sure"
     * was then answered No.
     */
    internal class RestorePlan(
        val toRestore: List<Pair<String, Path>>,
        val toImport: List<Pair<SyncableDatabaseDefinition, Path>>,
        val restoredSelection: List<SyncableDatabaseDefinition>,
    )

    /**
     * Phase 1 of the zip restore (D1 final review I1): asks every question for every selected file of
     * [unzipFolder]`/db/` BEFORE any database is closed. Under Room 3 a closed database never reopens, so the old
     * flow (close and overwrite file N, then wait on the user's answer for file N+1) crashed any DAO call made
     * while a dialog was up. Nothing is closed, copied or imported here.
     */
    internal suspend fun collectRestoreDecisions(selection: List<String>, unzipFolder: Path, prompts: RestorePrompts): RestorePlan {
        val toRestore = ArrayList<Pair<String, Path>>()
        val toImport = ArrayList<Pair<SyncableDatabaseDefinition, Path>>()
        val restoredSelection = ArrayList<SyncableDatabaseDefinition>()
        for (fileName in selection) {
            val category = SyncableDatabaseDefinition.filenameToCategory[fileName]
            val f = unzipFolder / "db/${fileName}"
            val restore = if (category != null) prompts.restoreOrImport(category, f) else true
            if (restore == null) continue
            if (restore) {
                if (category != null) {
                    restoredSelection.add(category)
                    beforeRestore(category)
                }
                val areYouSure = if (category != null) prompts.confirmOverwrite(category) else true
                if (!areYouSure) continue
                toRestore.add(fileName to f)
            } else {
                toImport.add(category!! to f)
            }
        }
        return RestorePlan(toRestore, toImport, restoredSelection)
    }

    /**
     * The zip restore after the user picked the sections: asks every question first ([collectRestoreDecisions]),
     * then closes and overwrites every file to restore in one stretch with no user prompt in between, calls
     * [DatabaseContainer.reset] at once, runs the imports (they need the new container) and finally clears the
     * restored categories' sync state ([afterRestore]).
     */
    @VisibleForTesting suspend fun restoreSelectedFiles(
        selection: List<String>,
        unzipFolder: Path,
        prompts: RestorePrompts,
    ) = reloadingAfterReplace {
        val plan = collectRestoreDecisions(selection, unzipFolder, prompts)
        DatabaseContainer.replacingDatabases { token ->
            for ((fileName, f) in plan.toRestore) {
                Log.i(TAG, "Restoring $fileName")
                if (DatabaseContainer.ready) DatabaseContainer.instance.closeForReplaceOwned(fileName, token)
                val targetFilePath = env.databasePath(fileName)
                fileSystem.copy(f, targetFilePath.toPath())
                for (leftover in listOf("-journal", "-shm", "-wal", ".lck")) {
                    deleteQuietly("$targetFilePath$leftover".toPath())
                }
            }
            DatabaseContainer.resetOwned(token)
            for ((category, f) in plan.toImport) importDatabaseFile(category, f)
        }
        if (DatabaseContainer.ready) {
            DatabaseContainer.instance
            afterRestore(plan.restoredSelection)
        }
    }

    suspend fun askIfRestoreOrImport(category: SyncableDatabaseDefinition, backupFile: Path): Boolean?  = withContext(Dispatchers.Main) {
        val contents = if (category == SyncableDatabaseDefinition.BOOKMARKS && DatabaseContainer.ready) {
            " (${bookmarksDbStats(category, backupFile, strings)})"
        } else ""
        val message =
            strings.askRestoreOrImport(strings.syncCategoryContents(category.name) + contents)
        val result = dialogs.await(
            AppDialogRequest.Options(
                title = strings.syncCategoryContents(category.name),
                message = message,
                options = listOf(
                    SettingsItem.Choice(RESTORE_VALUE, strings.restore),
                    SettingsItem.Choice(IMPORT_VALUE, strings.import2),
                ),
                dismissText = strings.cancel,
                asActionSheet = false,
            ),
        )
        when ((result as? AppDialogResult.Selected)?.value) {
            RESTORE_VALUE -> true
            IMPORT_VALUE -> false
            else -> null // Cancel button, back, or scrim -- D8-1: no longer aliased to Import.
        }
    }

    private suspend fun restoreOldMonolithicDatabaseFromSourceWithUI(
        host: BackupHost,
        source: PickedSource
    ): Boolean {
        val result2 = dialogs.await(AppDialogRequest.Message(
            title = null, message = strings.restoreConfirmation,
            confirmText = strings.okay, dismissText = strings.cancel, cancellable = true,
        ))
        if(result2 != AppDialogResult.Ok) return false
        var result: Boolean
        notifier.showInfo(strings.loadingBackup)
        val progressId = dialogs.show(AppDialogRequest.Progress(title = null, message = strings.pleaseWait))
        try {
            withContext(Dispatchers.IO) {
                result = if (restoreOldMonolithicDatabaseFromSource(host, source)) {
                    Log.i(TAG, "Restored database successfully")
                    showMessage(strings.restoreSuccess)
                    true
                } else {
                    showMessage(strings.restoreUnsuccessfull)
                    false
                }
            }
        } finally {
            dialogs.dismiss(progressId)
        }
        return result
    }

    /**
     * Extract a module zip archive into the SWORD download directory and register the
     * resulting books with JSword, without any Activity UI.
     *
     * This is the shared, headless core of module installation. The interactive install flow
     * behind [net.bible.android.view.activity.installzip.InstallZipComposeActivity] delegates
     * here (via `DocumentInstallService`/[net.bible.service.installzip.InstallCommitter], passing
     * a progress callback), and the cloud document-sync layer
     * ([net.bible.service.cloudsync.documents.DocumentArchiver]) also delegates here so that the
     * install path is not duplicated. The link named the classic `InstallZip` Activity until
     * slice S16 deleted it; the import that resolved that link had to go with it, since an
     * unresolved import is a compile error rather than a mere Dokka warning.
     *
     * The archive may contain an [ANDBIBLE_BACKUP_MANIFEST_FILENAME] manifest entry
     * (which is skipped) plus module files at modulesDir-relative paths: SWORD conf files
     * under mods.d plus data under modules, or sqlite-based MyBible/MySword/e-Sword files,
     * or an epub directory tree. After extraction, all addManuallyInstalled discovery
     * functions run so newly extracted books of every supported type are registered.
     *
     * @param newSource supplies a fresh [Source] over the archive (called once; closed by the extraction).
     * @param totalEntries number of zip entries, used only to scale [onProgress]; pass 0 to
     *   skip progress scaling.
     * @param onProgress optional progress callback receiving a 0..100 percentage.
     * @throws IOException on extraction failure.
     */
    suspend fun extractAndRegisterModuleArchive(
        newSource: () -> Source,
        totalEntries: Int = 0,
        onProgress: ((percent: Int) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val confFiles = ArrayList<Path>()
        val targetDirectory = SwordBookPath.getSwordDownloadDir()
        val errors: MutableList<String> = mutableListOf()
        ZipReader(newSource()).use { zip ->
            var entryNum = 0
            while (true) {
                val ze = zip.nextEntry() ?: break
                val name = safeZipEntryPath(ze.name)
                    ?: throw IOException("Unsafe ZIP entry name")
                if (name == ANDBIBLE_BACKUP_MANIFEST_FILENAME) continue

                val file = targetDirectory / moduleArchiveTargetName(name)
                if (name.startsWith(SwordConstants.DIR_CONF) && name.endsWith(SwordConstants.EXTENSION_CONF))
                    confFiles.add(file)

                val dir = if (ze.isDirectory) file else file.parent
                if (dir != null) fileSystem.createDirectories(dir)

                if (ze.isDirectory) continue
                try {
                    fileSystem.write(file) { writeAll(zip.entry) }
                } catch (e: IOException) {
                    errors.add(file.name)
                    Log.e(TAG, "Error in writing ${file.name}", e)
                }
                entryNum++
                if (onProgress != null && totalEntries > 0) {
                    onProgress((entryNum.toFloat() / totalEntries.toFloat() * 100).toInt())
                }
            }
            if (errors.isNotEmpty()) {
                throw IOException("Could not write module files: ${errors.joinToString(", ")}")
            }
        }
        // Load configuration files & register SWORD books
        val bookDriver = SwordBookDriver.instance()
        for (confFile in confFiles) {
            val me = SwordBookMetaData(confFile, directoryKUri(targetDirectory))
            me.setDriver(bookDriver)
            SwordBookDriver.registerNewBook(me)
        }
        // Discover & register all other (manually installed) book types
        addManuallyInstalledMyBibleBooks()
        addManuallyInstalledMySwordBooks()
        addManuallyInstalledESwordBooks()
        addManuallyInstalledEpubBooks(strings, env)
        addManuallyInstalledTtfBooks()
        addManuallyInstalledBackgroundImageBooks()
        addManuallyInstalledCsvPromptBooks()
    }

    /**
     * Install an `.abmd.zip` module archive (the format produced by [createSingleModuleZip])
     * headlessly — no Activity, dialogs or progress UI — and report whether the expected
     * document is now present in [Books.installed].
     *
     * @param file the archive to install.
     * @param expectedInitials initials of the document expected to appear after install;
     *   if null, success is reported when [Books.installed] grew.
     * @return true if installation succeeded and the document is registered.
     */
    suspend fun installModuleArchive(file: Path, expectedInitials: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val countBefore = Books.installed().getBooks().size
            try {
                extractAndRegisterModuleArchive({ fileSystem.source(file) })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install module archive ${file.name}", e)
                return@withContext false
            }
            val installed = Books.installed()
            val ok = if (expectedInitials != null) {
                installed.getBook(expectedInitials) != null
            } else {
                installed.getBooks().size > countBefore
            }
            if (ok) {
                DocumentChanges.notifyInstalledChanged()
            }
            ok
        }

    private val internalDbDir: Path get() = env.databasePath(OLD_MONOLITHIC_DATABASE_NAME).toPath().parent!!
    val internalDbBackupDir: Path get() = (dirs.internalFilesDir / "backup").also { fileSystem.createDirectories(it) }

    suspend fun restoreFromLocalBackupFile(host: BackupHost, file: Path) {
        restoreAppDatabaseFromSourceWithUI(host, PickedSource(KUri("file", null, file.toString(), null).toString(), file.name, null))
    }

    suspend fun resetDatabase(
        dbFileName: String,
        syncCategory: SyncableDatabaseDefinition?
    ) {
        val dbName = strings.databaseTitle(dbFileName)
        val confirmed = confirm(strings.resetDatabaseConfirm(dbName))
        if (!confirmed) return

        withContext(Dispatchers.IO) {
            reloadingAfterReplace {
                if (syncCategory != null) beforeRestore(syncCategory)
                DatabaseContainer.replacingDatabases { token ->
                    if (DatabaseContainer.ready) {
                        DatabaseContainer.instance.closeForReplaceOwned(dbFileName, token)
                    }

                    deleteAppDatabase(dbFileName) // the file, its journal/WAL leftovers and Room's .lck

                    DatabaseContainer.resetOwned(token)
                    if (DatabaseContainer.ready) {
                        DatabaseContainer.instance
                        if (syncCategory != null) {
                            afterRestore(listOf(syncCategory))
                        }
                    }
                }
            }
            }

        showMessage(strings.resetDatabaseSuccess)
    }

    data class BackupFileInfo(
        val appVersion: Int?,
        val displayDate: String,
        val file: Path
    )

    fun parseBackupFiles(files: List<Path>): List<BackupFileInfo> {
        // Format: dbBackup-{appVer}-{v1}-{v2}-...-{yyyyMMdd}-{HHmmss}.abdb.zip
        val regex = Regex("""dbBackup-(\d+)-(?:[\d]+-)*(\d{8})-(\d{6})\.abdb\.zip""")
        return files.map { f ->
            val name = f.name
            val match = regex.find(name)
            if (match != null) {
                val appVersion = match.groupValues[1].toIntOrNull()
                val (date, time) = match.groupValues[2] to match.groupValues[3]
                // The names the app writes are valid dates; the groups are reassembled, not re-parsed.
                val displayDate = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)} " +
                    "${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
                BackupFileInfo(appVersion, displayDate, f)
            } else {
                BackupFileInfo(null, name, f)
            }
        }
    }

    private val TAG = "BackupControl"
}
