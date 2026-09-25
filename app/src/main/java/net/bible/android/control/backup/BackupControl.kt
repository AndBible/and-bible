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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import io.requery.android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.database.OLD_DATABASE_VERSION
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.MainBibleAfterRestore
import net.bible.android.view.activity.page.UpdateMainBibleActivityDocuments
import net.bible.android.view.activity.page.application
import net.bible.android.view.util.Hourglass
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.windowControl
import net.bible.service.common.FileManager
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseContainer.Companion.maxDatabaseVersion
import net.bible.service.db.OLD_MONOLITHIC_DATABASE_NAME
import net.bible.service.download.isPseudoBook
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.common.CommonUtils.determineFileType
import net.bible.service.common.DbType
import net.bible.service.db.bookmarksDbStats
import net.bible.service.db.importDatabaseFile
import net.bible.service.sword.dbFile
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
import org.koin.java.KoinJavaComponent
import org.crosswire.common.util.NetUtil
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.book.sword.SwordConstants
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val DATABASE_BACKUP_SUFFIX = ".abdb.zip"
const val MODULE_BACKUP_SUFFIX = ".abmd.zip"

const val DATABASE_BACKUP_NAME = "AndBibleDatabaseBackup$DATABASE_BACKUP_SUFFIX"
const val MODULE_BACKUP_NAME = "AndBibleModulesBackup$MODULE_BACKUP_SUFFIX"

const val ZIP_MIMETYPE = "application/zip"

enum class SaveOrShare {SAVE, SHARE}

/**
 * Which destination dialog to run. Branches on whether a chooser was SUPPLIED, never on what it
 * returned: a chooser returning null means the user cancelled, and must abort the export rather
 * than fall through to the platform dialog asking the same question again (the C1 whole-branch
 * review defect — an elvis on the chooser's RESULT cannot tell "no chooser" apart from "chooser
 * ran and was cancelled", so Cancel on the Compose dialog re-opened the platform one). Internal so
 * [net.bible.android.view.activity.backup.SaveOrShareDestinationTest] can pin this contract
 * directly, without booting an Activity.
 */
internal suspend fun resolveDestination(
    chooseDestination: (suspend () -> SaveOrShare?)?,
    platformPrompt: suspend () -> SaveOrShare?,
): SaveOrShare? = if (chooseDestination != null) chooseDestination() else platformPrompt()

/**
 * Maps each backed-up database filename to its user-facing title resource.
 *
 * This is the single source of truth for database titles shown in backup/restore UI.
 * Every filename in [ALL_DB_FILENAMES] must have an entry here — see
 * `BackupControlTest` which guards this invariant. A missing entry previously caused
 * an `IllegalStateException: Unknown database file: ...` crash when restoring a backup
 * that contained a database not yet listed (e.g. progress.sqlite3).
 */
val databaseTitleResIds: Map<String, Int> = mapOf(
    BookmarkDatabase.dbFileName to R.string.db_bookmarks,
    ReadingPlanDatabase.dbFileName to R.string.reading_plans_plural,
    WorkspaceDatabase.dbFileName to R.string.help_workspaces_title,
    RepoDatabase.dbFileName to R.string.db_repositories,
    SettingsDatabase.dbFileName to R.string.settings,
    MyDocumentDatabase.dbFileName to R.string.my_documents_title,
    AiSettingsDatabase.dbFileName to R.string.ai_settings_sync_title,
    ProgressDatabase.dbFileName to R.string.progress_sync_title,
)

object BackupControl {
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    // AppDialogRequest.Options ids for askIfRestoreOrImport (D8-1).
    private const val RESTORE_VALUE = "restore"
    private const val IMPORT_VALUE = "import"

    // AppDialogRequest.Options ids for saveOrShare's platformPrompt fallback (Task 24 Step 2).
    private const val SHARE_VALUE = "share"
    private const val SAVE_VALUE = "save"

    /**
     * `saveOrShare`'s `platformPrompt` (Task 24 Step 2): an owner-less `AppDialogController` sheet
     * in place of the old `android.app.AlertDialog`, for the callers that pass no `chooseDestination`
     * (NavHostComposeActivity supplies its own for the rest, via `askDestination()`). `internal`, not
     * private, so `BackupControlTest` can pin the mapping directly without driving the whole export
     * flow (`activity.awaitIntent` etc.).
     */
    internal suspend fun classicDestinationPrompt(activity: ActivityBase, promptTitle: Int, promptMessage: Int): SaveOrShare? {
        val result = dialogs.await(
            AppDialogRequest.Options(
                title = activity.getString(promptTitle),
                message = activity.getString(promptMessage),
                options = listOf(
                    SettingsItem.Choice(SHARE_VALUE, activity.getString(R.string.share)),
                    SettingsItem.Choice(SAVE_VALUE, activity.getString(R.string.backup_phone_storage)),
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

    internal suspend fun saveDbBackupFileViaIntent(activity: ActivityBase, file: File) =
        saveOrShare(
            activity = activity,
            file = file,
            fileName = DATABASE_BACKUP_NAME,
            subject = activity.getString(R.string.backup_email_subject_2, CommonUtils.applicationNameMedium),
            message = activity.getString(R.string.backup_email_message_2, CommonUtils.applicationNameMedium),
            chooserTitle = activity.getString(R.string.send_backup_file),
            successMsg = R.string.backup_success2,
            errorMsg = R.string.error_occurred,
        )

    public suspend fun saveOrShare(
        activity: ActivityBase,
        file: File,
        fileName: String,
        shareMimeType: String = ZIP_MIMETYPE,
        saveMimeType: String = ZIP_MIMETYPE,
        subject: String? = null,
        message: String? = null,
        chooserTitle: String,
        successMsg: Int? = null,
        errorMsg: Int = R.string.error_occurred,
        promptTitle: Int = R.string.backup_backup_title,
        promptMessage: Int = R.string.backup_backup_message,
        // A Compose host supplies its own destination dialog here (NavHostComposeActivity's
        // askDestination()). Defaulted null so a caller with no host-owned chooser instead gets the
        // owner-less AppDialogController fallback below.
        chooseDestination: (suspend () -> SaveOrShare?)? = null,
    ): Boolean {
        val saveOrShare = resolveDestination(chooseDestination) {
            classicDestinationPrompt(activity, promptTitle, promptMessage)
        } ?: return false

        val uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", file)
        val intent = when(saveOrShare) {
            SaveOrShare.SAVE -> {
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    type = saveMimeType
                }
            }
            SaveOrShare.SHARE -> {
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if(subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                    if(message != null) putExtra(Intent.EXTRA_TEXT, message)
                    type = shareMimeType
                }
            }
        }
        val chooserIntent = Intent.createChooser(intent, chooserTitle)
        val result = activity.awaitIntent(chooserIntent)
        val ok = if (saveOrShare == SaveOrShare.SAVE) {
            result.data?.data?.let { destinationUri ->
                withContext(Dispatchers.IO) {
                    val hourglass = Hourglass(activity)
                    hourglass.show()

                    val out = BibleApplication.application.contentResolver.openOutputStream(destinationUri)!!
                    val inputStream = FileInputStream(file)

                    var ok = true
                    try {
                        out.use {
                            inputStream.copyTo(out)
                        }
                    } catch (ex: IOException) {
                        Log.e(TAG, ex.message ?: "Error occurred in backuping db")
                        ok = false
                    }
                    hourglass.dismiss()
                    ok
                }
            } ?: false
        } else result.resultCode == Activity.RESULT_OK || result.resultCode == Activity.RESULT_CANCELED

        withContext(Dispatchers.Main) {
            if (ok) {
                Log.i(TAG, "Copied database to chosen backup location successfully")
                if(successMsg != null) Dialogs.showMsg2(activity, successMsg)
            } else {
                Log.e(TAG, "Error copying database to chosen location.")
                ErrorReportControl.showErrorDialog(activity, activity.getString(errorMsg))
            }
        }

        return ok
    }

    private suspend fun restoreOldMonolithicDatabaseFromInputStream(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val fileName = OLD_MONOLITHIC_DATABASE_NAME
        internalDbBackupDir.mkdirs()
        val tmpFile = File(internalDbBackupDir, fileName)
        var ok = false
        val header = ByteArray(2)
        val gzHeaderBytes = byteArrayOf(0x1f.toByte(), 0x8b.toByte())

        val inputStream = application.contentResolver.openInputStream(uri) ?: throw IOException("Failed to open input stream")
        val bufferedInputStream = BufferedInputStream(inputStream)
        bufferedInputStream.mark(2)
        bufferedInputStream.read(header)
        bufferedInputStream.reset()

        val input = if(header.contentEquals(gzHeaderBytes)) {
            GZIPInputStream(bufferedInputStream)
        } else {
            bufferedInputStream
        }

        input.use {inputStream ->
            val dbHeader = ByteArray(16)
            inputStream.read(dbHeader)
            if(String(dbHeader) == "SQLite format 3\u0000") {
                val out = FileOutputStream(tmpFile)
                withContext(Dispatchers.IO) {
                    out.use {
                        out.write(dbHeader)
                        inputStream.copyTo(out)
                    }
                    val version = SQLiteDatabase.openDatabase(tmpFile.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                        it.version
                    }
                    if(version <= OLD_DATABASE_VERSION) {
                        Log.i(TAG, "Loading from backup database with version $version")
                        for (def in SyncableDatabaseDefinition.ALL) {
                            beforeRestore(def)
                        }
                        DatabaseContainer.reset()
                        // When restoring old style db, we need to remove all databases first
                        deleteAllDatabases()
                        ok = FileManager.copyFile(fileName, internalDbBackupDir, internalDbDir)
                        if(DatabaseContainer.ready) {
                            DatabaseContainer.instance // initialize (migrate etc)
                            afterRestore()
                        }
                    }
                }
            }
        }
        tmpFile.delete()

        return@withContext ok
    }

    fun deleteAllDatabases() {
        application.databaseList().forEach { name ->
            application.deleteDatabase(name)
        }
    }

    private fun getString(id: Int): String {
        return BibleApplication.application.getString(id)
    }

    /**
     * One of spec §3.2 finding 9's three hand-written select-all/none copies (Task 19). Cancel and
     * "OK with nothing checked" already both resumed `emptyList()` in the old code (see the
     * `setNegativeButton`/`setOnCancelListener`/select-none-then-OK arms above, all identical) — the
     * one D site of the two this batch converts where [Dialogs.multiselect]'s own Cancel-is-empty
     * behaviour is not a behaviour change at all, just the existing one.
     *
     * `internal`, not `private`: its only production caller is deep inside the restore flow (a real
     * zip with 2+ valid database files), so `BackupControlSelectDatabaseSectionsTest` calls this
     * directly rather than reconstructing that whole pipeline.
     */
    internal suspend fun selectDatabaseSections(context: Context, available: List<String>): List<String> =
        Dialogs.multiselect(
            context,
            getString(R.string.restore_backup_sections),
            available,
            itemToString = { name ->
                context.getString(databaseTitleResIds[name] ?: throw IllegalStateException("Unknown database file: $name"))
            },
            preSelected = { true },
        )

    private fun relativeFileName(rootDir: File, file: File): String {
        val filePath = file.canonicalPath
        val dirPath = rootDir.canonicalPath
        assert(filePath.startsWith(dirPath))
        return filePath.substring(dirPath.length + 1)
    }

    private fun addFile(outFile: ZipOutputStream, rootDir: File, file: File) {
        FileInputStream(file).use { inFile ->
            BufferedInputStream(inFile).use { origin ->
                val entry = ZipEntry(relativeFileName(rootDir, file))
                outFile.putNextEntry(entry)
                origin.copyTo(outFile)
            }
        }
    }

    private fun addModuleFile(outFile: ZipOutputStream, moduleFile: File) {
        FileInputStream(moduleFile).use { inFile ->
            BufferedInputStream(inFile).use { origin ->
                val fileNameInsideZip = moduleFile.relativeTo(moduleDir).path
                val entry = ZipEntry(fileNameInsideZip)
                outFile.putNextEntry(entry)
                origin.copyTo(outFile)
            }
        }
    }

    private fun addModuleDir(outFile: ZipOutputStream, modDir: File) {
        for (f in modDir.walkTopDown().filter { it.isFile }) {
            addFile(outFile, moduleDir, f)
        }
    }

    private fun addBookToZip(outFile: ZipOutputStream, b: Book) {
        val bmd = b.bookMetaData as SwordBookMetaData
        if (b.isManuallyInstalledMyBibleBook) {
            addModuleFile(outFile, b.dbFile)
        } else if (b.isManuallyInstalledMySwordBook) {
            addModuleFile(outFile, b.dbFile)
        } else if (b.isManuallyInstalledESwordBook) {
            addModuleFile(outFile, b.dbFile)
        } else if (b.isManuallyInstalledEpub) {
            addModuleDir(outFile, File(SharedConstants.modulesDir, b.epubDir))
        } else if (b.isManuallyInstalledTtf) {
            // Manually-installed font modules have byte-array metadata (no configFile), so they
            // must be packaged by their .ttf file rather than via the generic SWORD configFile
            // branch below. Skip gracefully if the underlying file has gone missing, so one
            // broken font module can't abort the whole backup/sync operation.
            val ttfFile = b.ttfFile
            if (ttfFile.exists()) {
                addModuleFile(outFile, ttfFile)
            } else {
                Log.w(TAG, "Skipping font module ${b.initials}: file not found ${ttfFile.path}")
            }
        } else if (b.isBackgroundImageModule) {
            // Background-image modules also have byte-array metadata (no configFile), so package
            // their single image file rather than via the generic SWORD branch below. Skip
            // gracefully if the file is missing, so one broken module can't abort the whole
            // backup/sync operation.
            val imageFile = b.backgroundImageFile
            if (imageFile.exists()) {
                addModuleFile(outFile, imageFile)
            } else {
                Log.w(TAG, "Skipping background-image module ${b.initials}: file not found ${imageFile.path}")
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
            val rootDir = configFile.parentFile!!.parentFile!!
            addFile(outFile, rootDir, configFile)
            val dataPath = bmd.getProperty("DataPath")
            val dataDir = File(rootDir, dataPath).run {
                if (listOf(
                        BookCategory.DICTIONARY,
                        BookCategory.GENERAL_BOOK,
                        BookCategory.MAPS
                    ).contains(b.bookCategory)
                )
                    parentFile
                else this
            }
            for (f in dataDir.walkTopDown().filter { it.isFile }) {
                addFile(outFile, rootDir, f)
            }
        }
    }

    private suspend fun createModulesZip(books: List<Book>, zipFile: File) {
        val manifest = AndBibleBackupManifest(backupType = BackupType.MODULE_BACKUP)

        withContext(Dispatchers.IO) {
            FileOutputStream(zipFile).use { out ->
                ZipOutputStream(out).use { outFile ->
                    manifest.saveToZip(outFile)
                    for (b in books) {
                        addBookToZip(outFile, b)
                    }
                }
            }
        }
    }

    suspend fun createSingleModuleZip(book: Book, zipFile: File) = withContext(Dispatchers.IO) {
        val manifest = AndBibleBackupManifest(backupType = BackupType.MODULE_BACKUP)
        FileOutputStream(zipFile).use { out ->
            ZipOutputStream(out).use { outFile ->
                manifest.saveToZip(outFile)
                addBookToZip(outFile, book)
            }
        }
    }

    fun clearBackupDir() {
        internalDbBackupDir.deleteRecursively()

        val fileList = CommonUtils.dbBackupPath.listFiles() ?: return
        val now = Date().time
        val maxAge = 3*2592000000L // 3*30 days in milliseconds
        for(f in fileList) {
            if(now - f.lastModified() > maxAge) {
                f.delete()
            }
        }
    }

    suspend fun backupModulesViaIntent(callingActivity: ActivityBase)  = withContext(Dispatchers.Main)   {
        val fileName = MODULE_BACKUP_NAME
        internalDbBackupDir.mkdirs()
        val zipFile = File(internalDbBackupDir, fileName)
        val books = Dialogs.multiselect(
            callingActivity,
            R.string.backup_modules_title,
            Books.installed().books.filter { !it.isPseudoBook && !it.isMyDocument }.sortedBy { it.language }
        ) {
            callingActivity.getString(R.string.something_with_parenthesis, it.name, "${it.initials}, ${it.language.code}")
        }

        if (books.isEmpty()) return@withContext

        val hourglass = Hourglass(callingActivity)
        hourglass.show()
        createModulesZip(books, zipFile)
        hourglass.dismiss()

        val modulesString = books.joinToString(", ") { it.abbreviation }
        val subject = BibleApplication.application.getString(R.string.backup_modules_email_subject_2, CommonUtils.applicationNameMedium)
        val message = BibleApplication.application.getString(R.string.backup_modules_email_message_2, CommonUtils.applicationNameMedium, modulesString)

        saveOrShare(
            activity = callingActivity,
            file = zipFile,
            fileName = fileName,
            subject = subject,
            message = message,
            chooserTitle = getString(R.string.send_backup_file),
            successMsg = R.string.backup_modules_success,
            errorMsg = R.string.error_occurred,
        )
    }

    suspend fun backupApp(callingActivity: ActivityBase) {
        internalDbBackupDir.mkdirs()

        val app: ApplicationInfo = callingActivity.applicationContext.applicationInfo

        val tempFile = File(internalDbBackupDir, callingActivity.getString(R.string.apk_file))
        withContext(Dispatchers.IO) {
            tempFile.delete()
            File(app.sourceDir).copyTo(tempFile)
        }

        saveOrShare(
            callingActivity,
            file = tempFile,
            fileName = "and-bible.apk",
            chooserTitle = getString(R.string.backup_app2),
            // MIME of .apk is "application/vnd.android.package-archive".
            // but Bluetooth does not accept this. Let's use "*/*" instead.
            shareMimeType = "*/*" ,
            saveMimeType = "application/vnd.android.package-archive",
        )
    }

    fun makeDatabaseBackupFile(): File? {
        if(CommonUtils.initialized && DatabaseContainer.ready) {
            windowControl.windowRepository.saveIntoDb()
            DatabaseContainer.vacuum()
            DatabaseContainer.sync()
        }
        internalDbBackupDir.mkdirs()
        val zipFile = File(internalDbBackupDir, DATABASE_BACKUP_NAME)
        if(zipFile.exists()) zipFile.delete()

        fun addFileToZip(outFile: ZipOutputStream, file: File) {
            FileInputStream(file).use { inFile ->
                BufferedInputStream(inFile).use { origin ->
                    val entry = ZipEntry("db/${file.name}")
                    outFile.putNextEntry(entry)
                    origin.copyTo(outFile)
                }
            }
        }
        val files = ALL_DB_FILENAMES.map {File(internalDbDir, it)}.filter {it.exists()}
        if(files.isEmpty()) return null

        val manifest = AndBibleBackupManifest(
            backupType = BackupType.DB_BACKUP, contains = setOf(
                DbType.BOOKMARKS, DbType.WORKSPACES, DbType.READINGPLANS, DbType.REPOSITORIES, DbType.SETTINGS,
                DbType.MYDOCUMENTS, DbType.AI_SETTINGS
            )
        )

        ZipOutputStream(FileOutputStream(zipFile)).use { outFile ->
            manifest.saveToZip(outFile)
            for(b in files) {
                addFileToZip(outFile, b)
            }
        }
        return zipFile
    }

    suspend fun startBackupAppDatabase(callingActivity: ActivityBase) = withContext(Dispatchers.IO) {
        val backupZipFile = makeDatabaseBackupFile()?: run {
            Dialogs.showMsg2(callingActivity, R.string.error_occurred)
            return@withContext
        }
        saveDbBackupFileViaIntent(callingActivity, backupZipFile)
    }

    enum class AbDbFileType {SQLITE3, ZIP, UNKNOWN}

    suspend fun restoreAppDatabaseViaIntent(activity: ActivityBase) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/*" // both new .abdb zip files as well as old monolithing .db files (sqlite3)
        val result = activity.awaitIntent(intent)
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data!!
            try {
                restoreAppDatabaseFromUriWithUI(activity, uri)
            } catch (e: Exception) {
                ErrorReportControl.showErrorDialog(activity, e.message ?: getString(R.string.error_occurred), exception = e)
            }
        }
    }

    suspend fun restoreAppDatabaseFromUriWithUI(activity: ActivityBase, uri: Uri): Boolean {
        val filetype = determineFileType(uri)
        Log.i(TAG, "Filetype: $filetype")

        return if(filetype == AbDbFileType.SQLITE3) {
            restoreOldMonolithicDatabaseFromUriWithUI(activity, uri)
        } else {
            restoreDatabaseZipFileInputStreamWithUI(activity, uri)
        }
    }

    private suspend fun isSqliteFile(inputStream: BufferedInputStream): Boolean = withContext(Dispatchers.IO) {
        val header = ByteArray(16)
        inputStream.mark(16)
        inputStream.read(header)
        inputStream.reset()
        val headerString = String(header)
        headerString == "SQLite format 3\u0000"
    }

    private suspend fun verifyDatabaseBackupFile(file: File): Boolean {
        val inputStream = BufferedInputStream(file.inputStream())
        if(!isSqliteFile(inputStream)) return false
        val version = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version }
        return version <= maxDatabaseVersion(file.name)
    }

    private suspend fun beforeRestore(category: SyncableDatabaseDefinition) {
        if(DatabaseContainer.ready && CloudSync.signedIn) {
            category.syncEnabled = false
            ABEventBus.post(ToastEvent(R.string.disabling_sync))
            CloudSync.waitUntilFinished()
        }
    }

    private suspend fun afterRestore(restoredSelection: List<SyncableDatabaseDefinition>? = null) {
        val selection: List<SyncableDatabaseDefinition> = restoredSelection?: SyncableDatabaseDefinition.ALL.toList()
        for(s in selection) {
            s.syncEnabled = false
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

    private suspend fun restoreDatabaseZipFileInputStreamWithUI(
        activity: ActivityBase,
        uri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        val hourglass = Hourglass(activity)
        ABEventBus.post(ToastEvent(getString(R.string.downloading_backup)))
        hourglass.show()

        val tmpFile = File(internalDbBackupDir, "database.zip")
        val unzipFolder = File(internalDbBackupDir, "unzip")

        unzipFolder.mkdirs()

        try {
            val inputStream = application.contentResolver.openInputStream(uri) ?: throw IOException("Failed to open input stream")
            tmpFile.outputStream().use { inputStream.copyTo(it) }
            CommonUtils.unzipFile(tmpFile, unzipFolder)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing backup file", e)
            throw IOException("Failed to process backup file: ${e.message}")
        }

        val restoredSelection =
            Closeable {
                tmpFile.delete()
                unzipFolder.deleteRecursively()
                activity.lifecycleScope.launch(Dispatchers.Main) { hourglass.dismiss() }
            }.use {
                val containedBackups = ALL_DB_FILENAMES.map { File(unzipFolder, "db/${it}") }
                    .filter { file -> file.exists() && verifyDatabaseBackupFile(file) }
                    .map { file -> file.name }

                hourglass.dismiss()
                if (containedBackups.isEmpty()) {
                    Dialogs.showMsg(R.string.restore_unsuccessfull)
                    return@withContext false
                }
                val selection =
                    if (containedBackups.size > 1)
                        selectDatabaseSections(activity, containedBackups)
                    else
                        containedBackups
                val restoredSelection = ArrayList<SyncableDatabaseDefinition>()
                if (selection.isEmpty()) {
                    return@withContext false
                }
                hourglass.show()
                for (fileName in selection) {
                    val category = SyncableDatabaseDefinition.filenameToCategory[fileName]
                    val f = File(unzipFolder, "db/${fileName}")
                    val restore =
                        if (category != null)
                            askIfRestoreOrImport(category, f, activity)
                        else true
                    if (restore == null) continue

                    if (restore) {
                        if(category != null) {
                            restoredSelection.add(category)
                            beforeRestore(category)
                        }

                        val areYouSure = if (category != null) {
                            Dialogs.simpleQuestion(
                                activity,
                                activity.getString(R.string.overwrite_something,
                                    getString(category.contentDescription)
                                )
                            )
                        } else true
                        if (!areYouSure) continue
                        Log.i(TAG, "Restoring $fileName")
                        if (DatabaseContainer.ready) DatabaseContainer.instance.dbByFilename[fileName]?.close()
                        val targetFilePath = activity.getDatabasePath(fileName).path
                        val targetFile = File(targetFilePath)
                        f.copyTo(targetFile, overwrite = true)
                        File("$targetFilePath-journal").delete()
                        File("$targetFilePath-shm").delete()
                        File("$targetFilePath-wal").delete()
                    } else {
                        importDatabaseFile(category!!, f)
                    }
                }
                DatabaseContainer.reset()
                restoredSelection
            }
        hourglass.show()
        if (DatabaseContainer.ready) {
            DatabaseContainer.instance
            afterRestore(restoredSelection)
        }
        hourglass.dismiss()
        Log.i(TAG, "Restored database successfully")
        ABEventBus.post(MainBibleAfterRestore())
        true
    }

    suspend fun askIfRestoreOrImport(category: SyncableDatabaseDefinition, backupFile: File, context: ActivityBase): Boolean?  = withContext(Dispatchers.Main) {
        val contents = if (category == SyncableDatabaseDefinition.BOOKMARKS && DatabaseContainer.ready) {
            " (${bookmarksDbStats(category, backupFile)})"
        } else ""
        val message =
            context.getString(R.string.ask_restore_or_import, context.getString(category.contentDescription) + contents)
        val result = dialogs.await(
            AppDialogRequest.Options(
                title = context.getString(category.contentDescription),
                message = message,
                options = listOf(
                    SettingsItem.Choice(RESTORE_VALUE, context.getString(R.string.restore)),
                    SettingsItem.Choice(IMPORT_VALUE, context.getString(R.string.import2)),
                ),
                dismissText = context.getString(R.string.cancel),
                asActionSheet = false,
            ),
        )
        when ((result as? AppDialogResult.Selected)?.value) {
            RESTORE_VALUE -> true
            IMPORT_VALUE -> false
            else -> null // Cancel button, back, or scrim -- D8-1: no longer aliased to Import.
        }
    }

    private suspend fun restoreOldMonolithicDatabaseFromUriWithUI(
        activity: ActivityBase,
        uri: Uri
    ): Boolean {
        val result2 = Dialogs.showMsg2(activity, R.string.restore_confirmation, true)
        if(result2 != Dialogs.Result.OK) return false
        var result: Boolean
        ABEventBus.post(ToastEvent(getString(R.string.loading_backup)))
        val hourglass = Hourglass(activity)
        hourglass.show()
        withContext(Dispatchers.IO) {
            result = if (restoreOldMonolithicDatabaseFromInputStream(uri)) {
                Log.i(TAG, "Restored database successfully")
                ABEventBus.post(MainBibleAfterRestore())
                Dialogs.showMsg(R.string.restore_success)
                true
            } else {
                Dialogs.showMsg(R.string.restore_unsuccessfull)
                false
            }
        }
        hourglass.dismiss()
        return result
    }

    /**
     * "Restore documents" from the Backup destination: open InstallZip (slice 8 D2). In-graph on the nav
     * host. The old `awaitIntent` + `if (result.data?.data == null) return` + post was dead (finding M6):
     * InstallZip attaches no result data, and `DocumentInstallService.postTerminalEvents` already posts
     * `UpdateMainBibleActivityDocuments` when an install finishes.
     */
    fun restoreModulesViaIntent(activity: ActivityBase) {
        if (activity is NavHostComposeActivity) {
            activity.openInstallZip()
        } else {
            activity.startActivity(NavHostComposeActivity.intentFor(activity, NavRoutes.installZip()))
        }
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
     * @param newInputStream supplies a fresh [InputStream] over the archive (called once).
     * @param totalEntries number of zip entries, used only to scale [onProgress]; pass 0 to
     *   skip progress scaling.
     * @param onProgress optional progress callback receiving a 0..100 percentage.
     * @throws IOException on extraction failure.
     */
    suspend fun extractAndRegisterModuleArchive(
        newInputStream: () -> InputStream,
        totalEntries: Int = 0,
        onProgress: ((percent: Int) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val confFiles = ArrayList<File>()
        val targetDirectory = SwordBookPath.getSwordDownloadDir()
        val errors: MutableList<String> = mutableListOf()
        ZipInputStream(newInputStream()).use { zIn ->
            var ze: ZipEntry? = zIn.nextEntry
            var entryNum = 0
            val buffer = ByteArray(8192)
            while (ze != null) {
                val name = ze.name.replace('\\', '/')
                if (name == ANDBIBLE_BACKUP_MANIFEST_FILENAME) {
                    ze = zIn.nextEntry
                    continue
                }

                val file = File(targetDirectory, name)
                if (name.startsWith(SwordConstants.DIR_CONF) && name.endsWith(SwordConstants.EXTENSION_CONF))
                    confFiles.add(file)

                val dir = if (ze.isDirectory) file else file.parentFile

                if (dir != null && !dir.isDirectory && !(dir.mkdirs() || dir.isDirectory))
                    throw IOException()

                if (ze.isDirectory) {
                    ze = zIn.nextEntry
                    continue
                }
                try {
                    FileOutputStream(file).use { fOut ->
                        var count = zIn.read(buffer)
                        while (count != -1) {
                            fOut.write(buffer, 0, count)
                            count = zIn.read(buffer)
                        }
                    }
                } catch (e: IOException) {
                    errors.add(file.name)
                    Log.e(TAG, "Error in writing ${file.name}", e)
                }
                entryNum++
                if (onProgress != null && totalEntries > 0) {
                    onProgress((entryNum.toFloat() / totalEntries.toFloat() * 100).toInt())
                }
                ze = zIn.nextEntry
            }
            if (errors.isNotEmpty()) {
                throw IOException("Could not write module files: ${errors.joinToString(", ")}")
            }
        }
        // Load configuration files & register SWORD books
        val bookDriver = SwordBookDriver.instance()
        for (confFile in confFiles) {
            val me = SwordBookMetaData(confFile, NetUtil.getURI(targetDirectory))
            me.driver = bookDriver
            SwordBookDriver.registerNewBook(me)
        }
        // Discover & register all other (manually installed) book types
        addManuallyInstalledMyBibleBooks()
        addManuallyInstalledMySwordBooks()
        addManuallyInstalledESwordBooks()
        addManuallyInstalledEpubBooks()
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
    suspend fun installModuleArchive(file: File, expectedInitials: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val countBefore = Books.installed().books.size
            try {
                extractAndRegisterModuleArchive({ FileInputStream(file) })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install module archive ${file.name}", e)
                return@withContext false
            }
            val installed = Books.installed()
            val ok = if (expectedInitials != null) {
                installed.getBook(expectedInitials) != null
            } else {
                installed.books.size > countBefore
            }
            if (ok) {
                ABEventBus.post(UpdateMainBibleActivityDocuments())
            }
            ok
        }

    /**
     * Open Backup & restore and suspend until the user leaves it (slice 8 C3, plan Correction 4).
     *
     * On the nav host it is an in-graph destination: `awaitBackupDestination` navigates and waits for the
     * entry to leave the back stack. An `awaitIntent` there would be an F53-shaped self-launch that the
     * platform answers with an immediate synthetic cancel, returning at once -- and
     * `ErrorReportControl.showErrorDialog`'s loop would put its dialog straight back over Backup.
     * From any other Activity (`StartupActivity`'s crash check, before DB init) it is a real
     * cross-Activity launch of the host on `BACKUP`, which starts uninitialised (spec §3.1 rule 2).
     */
    suspend fun backupPopup(activity: ActivityBase) {
        if (activity is NavHostComposeActivity) {
            activity.awaitBackupDestination()
        } else {
            awaitBackupFromAnotherActivity(activity)
        }
    }

    /** See [backupPopup]. The `check` is what `SelfLaunchRouteKindGuardTest` reads to exempt this await. */
    private suspend fun awaitBackupFromAnotherActivity(activity: ActivityBase) {
        check(activity !is NavHostComposeActivity) { "the nav host opens Backup in-graph (awaitBackupDestination)" }
        activity.awaitIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.BACKUP))
    }

    // Tracks SharedConstants.modulesDir live rather than capturing it once at object load, so
    // zip-entry relativization stays correct if the modules dir changes (e.g. across tests).
    private val moduleDir: File get() = SharedConstants.modulesDir
    private lateinit var internalDbDir : File
    val internalDbBackupDir: File // copy of db is created in this dir when doing backups
        get() {
            val file = File(SharedConstants.internalFilesDir, "/backup")
            file.mkdirs()
            return file
        }
    fun setupDirs(context: Context) {
        internalDbDir = File(context.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME).parent!!)
    }

    suspend fun restoreFromLocalBackupFile(activity: ActivityBase, file: File) {
        val uri = Uri.fromFile(file)
        restoreAppDatabaseFromUriWithUI(activity, uri)
    }

    suspend fun resetDatabase(
        activity: ActivityBase,
        dbFileName: String,
        nameResId: Int,
        syncCategory: SyncableDatabaseDefinition?
    ) {
        val dbName = activity.getString(nameResId)
        val confirmed = Dialogs.simpleQuestion(
            activity,
            activity.getString(R.string.reset_database_confirm, dbName)
        )
        if (!confirmed) return

        withContext(Dispatchers.IO) {
            if (syncCategory != null) {
                beforeRestore(syncCategory)
            }

            if (DatabaseContainer.ready) {
                DatabaseContainer.instance.dbByFilename[dbFileName]?.close()
            }

            val dbPath = activity.getDatabasePath(dbFileName).path
            File(dbPath).delete()
            File("$dbPath-journal").delete()
            File("$dbPath-shm").delete()
            File("$dbPath-wal").delete()

            DatabaseContainer.reset()
            if (DatabaseContainer.ready) {
                DatabaseContainer.instance
                if (syncCategory != null) {
                    afterRestore(listOf(syncCategory))
                }
            }
        }

        ABEventBus.post(MainBibleAfterRestore())
        Dialogs.showMsg(R.string.reset_database_success)
    }

    data class BackupFileInfo(
        val appVersion: Int?,
        val displayDate: String,
        val file: File
    )

    fun parseBackupFiles(files: List<File>): List<BackupFileInfo> {
        val dateParser = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return files.map { f ->
            val name = f.name
            // Format: dbBackup-{appVer}-{v1}-{v2}-...-{yyyyMMdd}-{HHmmss}.abdb.zip
            val regex = Regex("""dbBackup-(\d+)-(?:[\d]+-)*(\d{8})-(\d{6})\.abdb\.zip""")
            val match = regex.find(name)
            if (match != null) {
                val appVersion = match.groupValues[1].toIntOrNull()
                val dateStr = "${match.groupValues[2]}-${match.groupValues[3]}"
                val date = try { dateParser.parse(dateStr) } catch (_: Exception) { null }
                val displayDate = if (date != null) dateFormatter.format(date) else name
                BackupFileInfo(appVersion, displayDate, f)
            } else {
                BackupFileInfo(null, name, f)
            }
        }
    }

    private const val TAG = "BackupControl"
}
