/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ResolveInfoFlags
import io.requery.android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BackupViewBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.OLD_DATABASE_VERSION
import net.bible.android.database.ReadingPlanDatabase
import net.bible.android.database.RepoDatabase
import net.bible.android.database.SettingsDatabase
import net.bible.android.database.SyncableRoomDatabase
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.android.view.activity.page.MainBibleActivity
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
import net.bible.service.cloudsync.CloudSync
import net.bible.service.sword.dbFile
import net.bible.service.sword.mybible.isMyBibleBook
import net.bible.service.sword.mysword.isMySwordBook
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val DATABASE_BACKUP_NAME = "AndBibleDatabaseBackup.abdb"
const val MODULE_BACKUP_NAME = "AndBibleModulesBackup.abmd"

const val ZIP_MIMETYPE = "application/zip"

object BackupControl {
    /** Backup database to Uri returned from ACTION_CREATE_DOCUMENT intent
     */
    private suspend fun backupDatabaseToUri(activity: ActivityBase, uri: Uri, file: File) = withContext(Dispatchers.IO) {
        val hourglass = Hourglass(activity)
        hourglass.show()

        val out = BibleApplication.application.contentResolver.openOutputStream(uri)!!
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
        withContext(Dispatchers.Main) {
            if (ok) {
                Log.i(TAG, "Copied database to chosen backup location successfully")
                Dialogs.showMsg2(activity, R.string.backup_success2)
            } else {
                Log.e(TAG, "Error copying database to chosen location.")
                ErrorReportControl.showErrorDialog(activity, activity.getString(R.string.error_occurred))
            }
        }
    }

    internal suspend fun saveDbBackupFileViaIntent(activity: ActivityBase, file: File) = withContext(Dispatchers.IO) {
        val hourglass = Hourglass(activity)
        hourglass.show()

        val subject = activity.getString(R.string.backup_email_subject_2, CommonUtils.applicationNameMedium)
        val message = activity.getString(R.string.backup_email_message_2, CommonUtils.applicationNameMedium)
        val uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", file)
		val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            type = ZIP_MIMETYPE
        }
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = ZIP_MIMETYPE
            putExtra(Intent.EXTRA_TITLE, DATABASE_BACKUP_NAME)
        }

		val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.send_backup_file))
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(saveIntent))
        grantUriReadPermissions(chooserIntent, uri)
        hourglass.dismiss()
		activity.awaitIntent(chooserIntent).data?.data?.let { destinationUri ->
            backupDatabaseToUri(activity, destinationUri, file)
        }
    }

    private suspend fun restoreOldMonolithicDatabaseFromInputStream(possiblyGzippedInputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        val fileName = OLD_MONOLITHIC_DATABASE_NAME
        internalDbBackupDir.mkdirs()
        val tmpFile = File(internalDbBackupDir, fileName)
        var ok = false
        val header = ByteArray(2)
        val gzHeaderBytes = byteArrayOf(0x1f.toByte(), 0x8b.toByte())

        val bufferedInputStream = BufferedInputStream(possiblyGzippedInputStream)
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
                    val version = SQLiteDatabase.openDatabase(tmpFile.path, null, SQLiteDatabase.OPEN_READONLY).use {
                        it.version
                    }
                    if(version <= OLD_DATABASE_VERSION) {
                        Log.i(TAG, "Loading from backup database with version $version")
                        DatabaseContainer.reset()
                        // When restoring old style db, we need to remove all databases first
                        application.databaseList().forEach { name ->
                            application.deleteDatabase(name)
                        }
                        ok = FileManager.copyFile(fileName, internalDbBackupDir, internalDbDir)
                        if(DatabaseContainer.ready) {
                            DatabaseContainer.instance // initialize (migrate etc)
                        }
                    }
                }
            }
        }
        tmpFile.delete()

        return@withContext ok
    }

    private fun getString(id: Int): String {
        return BibleApplication.application.getString(id)
    }

    private suspend fun selectModules(context: Context): List<Book>? {
        var result: List<Book>? = null
        withContext(Dispatchers.Main) {
            result = suspendCoroutine {
                val books = Books.installed().books.filter { !it.isPseudoBook }.sortedBy { it.language }
                val bookNames = books.map {
                    context.getString(R.string.something_with_parenthesis, it.name, "${it.initials}, ${it.language.code}")
                }.toTypedArray()

                val checkedItems = bookNames.map { false }.toBooleanArray()
                val dialog = AlertDialog.Builder(context)
                    .setPositiveButton(R.string.okay) { d, _ ->
                        val selectedBooks = books.filterIndexed { index, book -> checkedItems[index] }
                        if(selectedBooks.isEmpty()) {
                            it.resume(null)
                        } else {
                            it.resume(selectedBooks)
                        }
                    }
                    .setMultiChoiceItems(bookNames, checkedItems) { _, pos, value ->
                        checkedItems[pos] = value
                    }
                    .setNeutralButton(R.string.select_all) { _, _ -> it.resume(null) }
                    .setNegativeButton(R.string.cancel) { _, _ -> it.resume(null) }
                    .setOnCancelListener { _ -> it.resume(null)}
                    .setTitle(getString(R.string.backup_modules_title))
                    .create()

                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        val allSelected = checkedItems.find { !it } == null
                        val newValue = !allSelected
                        val v = dialog.listView
                        for (i in 0 until v.count) {
                            v.setItemChecked(i, newValue)
                            checkedItems[i] = newValue
                        }
                        (it as Button).text = getString(if (allSelected) R.string.select_all else R.string.select_none)
                    }
                }
                dialog.show()
                CommonUtils.fixAlertDialogButtons(dialog)
            }
        }
        return result
    }

    private suspend fun selectDatabaseSections(context: Context, available: List<String>): List<String> {
        var result: List<String>
        withContext(Dispatchers.Main) {
            result = suspendCoroutine {
                val backupNames = available.map {
                    when(it) {
                        BookmarkDatabase.dbFileName -> context.getString(R.string.db_bookmarks)
                        ReadingPlanDatabase.dbFileName -> context.getString(R.string.reading_plans_plural)
                        WorkspaceDatabase.dbFileName -> context.getString(R.string.help_workspaces_title)
                        RepoDatabase.dbFileName -> context.getString(R.string.db_repositories)
                        SettingsDatabase.dbFileName -> context.getString(R.string.settings)
                        else -> throw IllegalStateException("Unknown database file: $it")
                    }
                }.toTypedArray()

                val checkedItems = backupNames.map { true }.toBooleanArray()
                val dialog = AlertDialog.Builder(context)
                    .setPositiveButton(R.string.okay) { d, _ ->
                        val selectedBooks = available.filterIndexed { index, book -> checkedItems[index] }
                        if (selectedBooks.isEmpty()) {
                            it.resume(emptyList())
                        } else {
                            it.resume(selectedBooks)
                        }
                    }
                    .setMultiChoiceItems(backupNames, checkedItems) { _, pos, value ->
                        checkedItems[pos] = value
                    }
                    .setNeutralButton(R.string.select_all) { _, _ -> it.resume(emptyList()) }
                    .setNegativeButton(R.string.cancel) { _, _ -> it.resume(emptyList()) }
                    .setOnCancelListener { _ -> it.resume(emptyList())}
                    .setTitle(getString(R.string.restore_backup_sections))
                    .create()

                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        val allSelected = checkedItems.find { !it } == null
                        val newValue = !allSelected
                        val v = dialog.listView
                        for (i in 0 until v.count) {
                            v.setItemChecked(i, newValue)
                            checkedItems[i] = newValue
                        }
                        (it as Button).text = getString(if (allSelected) R.string.select_all else R.string.select_none)
                    }
                }
                dialog.show()
                CommonUtils.fixAlertDialogButtons(dialog)
            }
        }
        return result
    }

    private suspend fun createModulesZip(books: List<Book>, zipFile: File) {
        fun relativeFileName(rootDir: File, file: File): String {
            val filePath = file.canonicalPath
            val dirPath = rootDir.canonicalPath
            assert(filePath.startsWith(dirPath))
            return filePath.substring(dirPath.length + 1)
        }

        fun addFile(outFile: ZipOutputStream, rootDir: File, configFile: File) {
            FileInputStream(configFile).use { inFile ->
                BufferedInputStream(inFile).use { origin ->
                    val entry = ZipEntry(relativeFileName(rootDir, configFile))
                    outFile.putNextEntry(entry)
                    origin.copyTo(outFile)
                }
            }
        }

        fun addModuleFile(outFile: ZipOutputStream, moduleFile: File) {
            FileInputStream(moduleFile).use { inFile ->
                BufferedInputStream(inFile).use { origin ->
                    val fileNameInsideZip = moduleFile.relativeTo(moduleDir).path
                    val entry = ZipEntry(fileNameInsideZip)
                    outFile.putNextEntry(entry)
                    origin.copyTo(outFile)
                }
            }
        }

        withContext(Dispatchers.IO) {
            FileOutputStream(zipFile).use { out ->
                ZipOutputStream(out).use { outFile ->
                    for (b in books) {
                        val bmd = b.bookMetaData as SwordBookMetaData
                        if (b.isMyBibleBook) {
                            addModuleFile(outFile, b.dbFile)
                        } else if (b.isMySwordBook) {
                            addModuleFile(outFile, b.dbFile)
                        } else {
                            val configFile = bmd.configFile
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
                }
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

    private suspend fun backupModulesToUri(uri: Uri): Boolean {
        // at this point the zip file has already been created
        val fileName = MODULE_BACKUP_NAME
        val zipFile = File(internalDbBackupDir, fileName)
        val out = BibleApplication.application.contentResolver.openOutputStream(uri)!!
        val inputStream = FileInputStream(zipFile)
        var ok = true
        try {
            withContext(Dispatchers.IO) {
                inputStream.copyTo(out)
                out.close()
            }
        } catch (ex: IOException) {
            ok = false
        }
        return ok
    }

    suspend fun backupModulesViaIntent(callingActivity: ActivityBase)  = withContext(Dispatchers.Main)   {
        val fileName = MODULE_BACKUP_NAME
        internalDbBackupDir.mkdirs()
        val zipFile = File(internalDbBackupDir, fileName)
        val books = selectModules(callingActivity) ?: return@withContext

        val hourglass = Hourglass(callingActivity)
        hourglass.show()
        createModulesZip(books, zipFile)
        hourglass.dismiss()

        // send intent to pick file
        var ok = true

        val modulesString = books.joinToString(", ") { it.abbreviation }
        val subject = BibleApplication.application.getString(R.string.backup_modules_email_subject_2, CommonUtils.applicationNameMedium)
        val message = BibleApplication.application.getString(R.string.backup_modules_email_message_2, CommonUtils.applicationNameMedium, modulesString)

        val uri = FileProvider.getUriForFile(callingActivity, BuildConfig.APPLICATION_ID + ".provider", zipFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            type = "application/zip"
        }
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.send_backup_file))
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(saveIntent))
        grantUriReadPermissions(chooserIntent, uri)

        callingActivity.awaitIntent(chooserIntent).data?.data?.let {
            ok = backupModulesToUri(it)
        }

        hourglass.dismiss()
        if (ok) {
            Log.i(TAG, "Copied modules to chosen backup location successfully")
            Dialogs.showMsg(R.string.backup_modules_success)
        } else {
            Log.e(TAG, "Error copying modules to chosen location.")
            Dialogs.showErrorMsg(R.string.error_occurred)
        }

    }

    private fun grantUriReadPermissions(chooserIntent: Intent, uri: Uri) {
        val resInfoList = if (Build.VERSION.SDK_INT >= 33) {
            BibleApplication.application.packageManager.queryIntentActivities(chooserIntent, ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            BibleApplication.application.packageManager.queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            BibleApplication.application.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun backupApp(callingActivity: ActivityBase) {
        internalDbBackupDir.mkdirs()

        val app: ApplicationInfo = callingActivity.applicationContext.applicationInfo

        val tempFile = File(internalDbBackupDir, "and-bible.apk")
        withContext(Dispatchers.IO) {
            tempFile.delete()
            File(app.sourceDir).copyTo(tempFile)
        }
        val fileUri = FileProvider.getUriForFile(callingActivity, BuildConfig.APPLICATION_ID + ".provider", tempFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            // MIME of .apk is "application/vnd.android.package-archive".
            // but Bluetooth does not accept this. Let's use "*/*" instead.
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, fileUri)
        }
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_TITLE, "and-bible.apk")
        }

        val chooserIntent = Intent.createChooser(shareIntent, callingActivity.getString(R.string.backup_app2))
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(saveIntent))
        grantUriReadPermissions(chooserIntent, fileUri)

        withContext(Dispatchers.Main) {
            callingActivity.awaitIntent(chooserIntent).data?.data?.let { destinationUri ->
                withContext(Dispatchers.IO) {
                    callingActivity.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        callingActivity.contentResolver.openInputStream(fileUri)?.use {  inputStream ->
                            try {
                                inputStream.copyTo(outputStream)
                            } catch (ex: IOException) {
                                Log.e(TAG, ex.message ?: "Error occurred while trying to backup the app (apk)")
                            }
                        }
                    }
                }
            }
        }
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

        ZipOutputStream(FileOutputStream(zipFile)).use { outFile ->
            for(b in files) {
                addFileToZip(outFile, b)
            }
        }
        return zipFile
    }

    suspend fun startBackupAppDatabase(callingActivity: ActivityBase) = withContext(Dispatchers.IO) {
        val backupZipFile = makeDatabaseBackupFile()!!
        saveDbBackupFileViaIntent(callingActivity, backupZipFile)
        backupZipFile.delete()
    }

    enum class AbDbFileType {
        SQLITE3, ZIP, UNKNOWN
    }

    private suspend fun determineFileType(inputStream: BufferedInputStream): AbDbFileType = withContext(Dispatchers.IO) {
        val header = ByteArray(16)
        inputStream.mark(16)
        inputStream.read(header)
        inputStream.reset()
        val headerString = String(header)
        if(headerString == "SQLite format 3\u0000")
            AbDbFileType.SQLITE3
        else if(headerString.startsWith("PK\u0003\u0004"))
            AbDbFileType.ZIP
        else
            AbDbFileType.UNKNOWN
    }

    suspend fun restoreAppDatabaseViaIntent(activity: ActivityBase) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/*" // both new .abdb zip files as well as old monolithing .db files (sqlite3)
        val result = activity.awaitIntent(intent)
        if (result.resultCode == Activity.RESULT_OK) {
            val inputStream = try {
                activity.contentResolver.openInputStream(result.data?.data!!)
            } catch (e: FileNotFoundException) {null} ?: return
            restoreAppDatabaseFromInputStreamWithUI(activity, inputStream)
        }
    }

    suspend fun restoreAppDatabaseFromInputStreamWithUI(activity: ActivityBase, inputStream: InputStream): Boolean {
        val bufferedInputStream = BufferedInputStream(inputStream)
        val filetype = determineFileType(bufferedInputStream)
        Log.i(TAG, "Filetype: $filetype")

        return if(filetype == AbDbFileType.SQLITE3) {
            restoreOldMonolithicDatabaseFromFileInputStreamWithUI(activity, bufferedInputStream)
        } else {
            restoreDatabaseZipFileInputStreamWithUI(activity, bufferedInputStream)
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
        val version = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        return version <= maxDatabaseVersion(file.name)
    }

    private suspend fun restoreDatabaseZipFileInputStreamWithUI(
        activity: ActivityBase,
        inputStream: InputStream
    ): Boolean = withContext(Dispatchers.IO) {
        val hourglass = Hourglass(activity)
        ABEventBus.post(ToastEvent(getString(R.string.downloading_backup)))
        hourglass.show()

        val tmpFile = File(internalDbBackupDir, "database.zip")
        val unzipFolder = File(internalDbBackupDir, "unzip")

        unzipFolder.mkdirs()

        tmpFile.outputStream().use {inputStream.copyTo(it) }
        CommonUtils.unzipFile(tmpFile, unzipFolder)

        val selection =
            Closeable {
                tmpFile.delete()
                unzipFolder.deleteRecursively()
            }.use {
                val containedBackups = ALL_DB_FILENAMES.map { File(unzipFolder, "db/${it}") }
                    .filter { file -> file.exists() && verifyDatabaseBackupFile(file) }
                    .map { file -> file.name }

                hourglass.dismiss()
                if (containedBackups.isEmpty()) {
                    Dialogs.showMsg(R.string.restore_unsuccessfull)
                    return@withContext false
                }
                val selection = selectDatabaseSections(activity, containedBackups)

                if (selection.isEmpty()) {
                    return@withContext false
                }
                hourglass.show()
                DatabaseContainer.reset()
                for (fileName in selection) {
                    val f = File(unzipFolder, "db/${fileName}")
                    Log.i(TAG, "Restoring $fileName")
                    val targetFilePath = activity.getDatabasePath(fileName).path
                    val targetFile = File(targetFilePath)
                    f.copyTo(targetFile, overwrite = true)
                    File("$targetFilePath-journal").delete()
                    File("$targetFilePath-shm").delete()
                    File("$targetFilePath-wal").delete()
                }
                selection
            }
        if (DatabaseContainer.ready) {
            DatabaseContainer.instance
            for(s in selection) {
                val db: SyncableRoomDatabase? = when(s) {
                    BookmarkDatabase.dbFileName -> DatabaseContainer.instance.bookmarkDb
                    ReadingPlanDatabase.dbFileName -> DatabaseContainer.instance.readingPlanDb
                    WorkspaceDatabase.dbFileName -> DatabaseContainer.instance.workspaceDb
                    else -> null
                }
                if(db != null) {
                    db.syncDao().clearSyncStatus()
                    db.syncDao().clearSyncConfiguration()
                }
            }
            if(CommonUtils.isGoogleDriveSyncEnabled) {
                CloudSync.signIn(activity)
                CloudSync.start()
                CloudSync.waitUntilFinished()
            }
        }
        hourglass.dismiss()
        Log.i(TAG, "Restored database successfully")
        ABEventBus.post(MainBibleActivity.MainBibleAfterRestore())
        Dialogs.showMsg(R.string.restore_success2)
        true
    }


    private suspend fun restoreOldMonolithicDatabaseFromFileInputStreamWithUI(
        activity: ActivityBase,
        inputStream: InputStream
    ): Boolean {
        val result2 = Dialogs.showMsg2(activity, R.string.restore_confirmation, true)
        if(result2 != Dialogs.Result.OK) return false
        var result: Boolean
        ABEventBus.post(ToastEvent(getString(R.string.loading_backup)))
        val hourglass = Hourglass(activity)
        hourglass.show()
        withContext(Dispatchers.IO) {
            result = if (restoreOldMonolithicDatabaseFromInputStream(inputStream)) {
                Log.i(TAG, "Restored database successfully")
                ABEventBus.post(MainBibleActivity.MainBibleAfterRestore())
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

    suspend fun restoreModulesViaIntent(activity: ActivityBase) {
        val intent = Intent(activity, InstallZip::class.java)
        val result = activity.awaitIntent(intent)
        if(result.data?.data == null) return

        ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
    }

    suspend fun backupPopup(activity: ActivityBase) {
        val intent = Intent(activity, BackupActivity::class.java)
        activity.awaitIntent(intent)
    }

    private var moduleDir: File = SharedConstants.modulesDir
    private lateinit var internalDbDir : File
    private val internalDbBackupDir: File // copy of db is created in this dir when doing backups
        get() {
            val file = File(SharedConstants.internalFilesDir, "/backup")
            file.mkdirs()
            return file
        }
    fun setupDirs(context: Context) {
        internalDbDir = File(context.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME).parent!!)
    }

    private const val TAG = "BackupControl"
}

class BackupActivity: ActivityBase() {
    lateinit var binding: BackupViewBinding
    override val doNotInitializeApp: Boolean = true

    override fun onBackPressed() {
        updateSelectionOptions()
        super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            android.R.id.home -> {
                updateSelectionOptions()
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildActivityComponent().inject(this)
        binding = BackupViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            toggleBackupApplication.isChecked = CommonUtils.settings.getBoolean("backup_application", false)
            toggleBackupDatabase.isChecked = CommonUtils.settings.getBoolean("backup_database", true)
            toggleBackupDocuments.isChecked = CommonUtils.settings.getBoolean("backup_documents", false)
            toggleRestoreDatabase.isChecked = CommonUtils.settings.getBoolean("restore_database", true)
            toggleRestoreDocuments.isChecked = CommonUtils.settings.getBoolean("restore_documents", false)

            buttonBackup.setOnClickListener {
                updateSelectionOptions()
                when {
                    toggleBackupApplication.isChecked -> lifecycleScope.launch { BackupControl.backupApp(this@BackupActivity) }
                    toggleBackupDatabase.isChecked -> lifecycleScope.launch { BackupControl.startBackupAppDatabase(this@BackupActivity) }
                    toggleBackupDocuments.isChecked -> lifecycleScope.launch { BackupControl.backupModulesViaIntent(this@BackupActivity) }
                }
            }
            buttonRestore.setOnClickListener {
                updateSelectionOptions()
                when {
                    toggleRestoreDatabase.isChecked -> lifecycleScope.launch { BackupControl.restoreAppDatabaseViaIntent(this@BackupActivity) }
                    toggleRestoreDocuments.isChecked -> lifecycleScope.launch { BackupControl.restoreModulesViaIntent(this@BackupActivity) }
                }
            }
            CommonUtils.dbBackupPath.listFiles()?.sortedByDescending { it.name }?.forEach { f ->
                val b = Button(this@BackupActivity)
                val s = f.name
                b.text = s
                b.setOnClickListener {
                    lifecycleScope.launch { BackupControl.saveDbBackupFileViaIntent(this@BackupActivity, f) }
                }
                backupDbButtons.addView(b)
            }
            if(backupDbButtons.childCount == 0) {
                importExportTitle.visibility = View.GONE
            }
        }
    }

    private fun updateSelectionOptions() {
        if(!CommonUtils.initialized) return
        // update widget share option settings
        CommonUtils.settings.apply {
            setBoolean("backup_application", binding.toggleBackupApplication.isChecked)
            setBoolean("backup_database", binding.toggleBackupDatabase.isChecked)
            setBoolean("backup_documents", binding.toggleBackupDocuments.isChecked)
            setBoolean("restore_database", binding.toggleRestoreDatabase.isChecked)
            setBoolean("restore_documents", binding.toggleRestoreDocuments.isChecked)
        }
    }
}
