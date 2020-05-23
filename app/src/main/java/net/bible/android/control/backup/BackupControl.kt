/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import android.util.Log

import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.FileManager

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.widget.Button
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.BuildConfig
import net.bible.android.database.DATABASE_VERSION
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.android.view.util.Hourglass
import net.bible.service.common.CommonUtils

import net.bible.service.db.DATABASE_NAME
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseContainer.db
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume


/**
 * Support backup and restore of the And bible database which contains bookmarks and notes.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class BackupControl @Inject constructor() {

    /** return true if a backup has been done and the file is on the sd card.
     */
    private val isBackupFileExists: Boolean
        get() = File(SharedConstants.backupDir, DATABASE_NAME).exists()

    /** backup database to sd card
     */
    fun backupDatabase() {
        mainBibleActivity.windowRepository.saveIntoDb()
        db.sync()
        val ok = FileManager.copyFile(DATABASE_NAME, internalDbDir, SharedConstants.backupDir)

        if (ok) {
            Log.d(TAG, "Copied database to internal memory successfully")
            Dialogs.instance.showMsg(R.string.backup_success, SharedConstants.backupDir.absolutePath)
        } else {
            Log.e(TAG, "Error copying database to internal memory")
            Dialogs.instance.showErrorMsg(R.string.error_occurred)
        }
    }

    /** backup database to custom target (email, drive etc.)
     */
    fun backupDatabaseViaIntent(callingActivity: Activity) {
        db.sync()
        val fileName = DATABASE_NAME
        internalDbBackupDir.mkdirs()
        FileManager.copyFile(fileName, internalDbDir, internalDbBackupDir)
		val subject = callingActivity.getString(R.string.backup_email_subject_2, CommonUtils.applicationNameMedium)
		val message = callingActivity.getString(R.string.backup_email_message_2, CommonUtils.applicationNameMedium)
        val f = File(internalDbBackupDir, fileName)
        val uri = FileProvider.getUriForFile(callingActivity, BuildConfig.APPLICATION_ID + ".provider", f)
		val email = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            type = "application/x-sqlite3"
        }
		val chooserIntent = Intent.createChooser(email, getString(R.string.send_backup_file))
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		callingActivity.startActivity(chooserIntent)
    }

    fun resetDatabase() {
        val f = File(internalDbDir, DATABASE_NAME)
        f.delete()
    }

    /** backup database from custom source
     */
    fun restoreDatabaseViaIntent(inputStream: InputStream): Boolean {
        val fileName = DATABASE_NAME
        internalDbBackupDir.mkdirs()
        val f = File(internalDbBackupDir, fileName)
        var ok = false
        val header = ByteArray(16)
        inputStream.read(header)
        if(String(header) == "SQLite format 3\u0000") {
            val out = FileOutputStream(f)
            out.write(header)
            out.write(inputStream.readBytes())
            out.close()
            val sqlDb = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
            if(sqlDb.version <= DATABASE_VERSION) {
                Log.d(TAG, "Loading from backup database with version ${sqlDb.version}")
                DatabaseContainer.reset()
                BibleApplication.application.deleteDatabase(DATABASE_NAME)
                ok = FileManager.copyFile(fileName, internalDbBackupDir, internalDbDir)
            }
            sqlDb.close()
        }

        if (ok) {
            ABEventBus.getDefault().post(SynchronizeWindowsEvent(true))
            Log.d(TAG, "Restored database successfully")
            Dialogs.instance.showMsg(R.string.restore_success)
        } else {
            Log.e(TAG, "Error restoring database")
            Dialogs.instance.showErrorMsg(R.string.restore_unsuccessfull)
        }
        f.delete()
        return ok
    }

    private fun getString(id: Int): String {
        return BibleApplication.application.getString(id)
    }

    private suspend fun selectModules(context: Context): List<Book>? {
        var result: List<Book>? = null
        withContext(Dispatchers.Main) {
            result = suspendCoroutine {
                val books = Books.installed().books.sortedBy { it.language }
                val bookNames = books.map {
                    context.getString(R.string.something_with_parenthesis, it.name, it.language.code)
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
            }
        }
        return result
    }

    private suspend fun createZip(books: List<Book>, zipFile: File) {
        fun relativeFileName(rootDir: File, file: File): String {
            val filePath = file.canonicalPath
            val dirPath = rootDir.canonicalPath
            assert(filePath.startsWith(dirPath))
            return filePath.substring(dirPath.length + 1)
        }

        fun addFile(outFile: ZipOutputStream, rootDir: File, configFile: File) {
            FileInputStream(configFile).use {inFile ->
                BufferedInputStream(inFile).use { origin ->
                    val entry = ZipEntry(relativeFileName(rootDir, configFile))
                    outFile.putNextEntry(entry)
                    origin.copyTo(outFile)
                }
            }
        }

        withContext(Dispatchers.IO) {
            ZipOutputStream(FileOutputStream(zipFile)).use {outFile ->
                for(b in books) {
                    val bmd = b.bookMetaData as SwordBookMetaData
                    val configFile = bmd.configFile
                    val rootDir = configFile.parentFile!!.parentFile!!

                    addFile(outFile, rootDir, configFile)
                    val dataPath = bmd.getProperty("DataPath")
                    val dataDir = File(rootDir, dataPath).run {
                        if(listOf(BookCategory.DICTIONARY, BookCategory.GENERAL_BOOK).contains(b.bookCategory))
                            parentFile
                        else this
                    }
                    for(f in dataDir.walkTopDown().filter { it.isFile }) {
                        addFile(outFile, rootDir, f)
                    }
                }
            }
        }
    }

    fun clearBackupDir() {
        internalDbBackupDir.deleteRecursively()
    }

    suspend fun backupModulesViaIntent(callingActivity: Activity) = withContext(Dispatchers.Main) {
        val fileName = "modules.zip"
        internalDbBackupDir.mkdirs()
        val zipFile = File(internalDbBackupDir, fileName)
        val books = selectModules(callingActivity) ?: return@withContext

        val hourglass = Hourglass(callingActivity)
        hourglass.show()
        createZip(books, zipFile)
        hourglass.dismiss()

        val modulesString = books.joinToString(", ") { it.abbreviation }
        val subject = BibleApplication.application.getString(R.string.backup_modules_email_subject_2, CommonUtils.applicationNameMedium)
        val message = BibleApplication.application.getString(R.string.backup_modules_email_message_2, CommonUtils.applicationNameMedium, modulesString)

        val uri = FileProvider.getUriForFile(callingActivity, BuildConfig.APPLICATION_ID + ".provider", zipFile)
        val email = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            type = "application/zip"
        }
        val chooserIntent = Intent.createChooser(email, getString(R.string.send_backup_file))
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        callingActivity.startActivity(chooserIntent)
    }

    /** restore database from sd card
     */
    fun restoreDatabase() {
        if (!isBackupFileExists) {
            Dialogs.instance.showErrorMsg(R.string.error_no_backup_file)
        } else {
            Dialogs.instance.showMsg(R.string.restore_confirmation, true) {
                BibleApplication.application.deleteDatabase(DATABASE_NAME)
                val ok = FileManager.copyFile(DATABASE_NAME, SharedConstants.backupDir, internalDbDir)

                if (ok) {
                    DatabaseContainer.reset()
                    ABEventBus.getDefault().post(SynchronizeWindowsEvent(true))
                    Log.d(TAG, "Copied database from internal memory successfully")
                    Dialogs.instance.showMsg(R.string.restore_success, SharedConstants.backupDir.name)
                } else {
                    Log.e(TAG, "Error copying database from internal memory")
                    Dialogs.instance.showErrorMsg(R.string.error_occurred)
                }
            }
        }
    }

    companion object {

        // this is now unused because And Bible databases are held on the SD card to facilitate easier backup by file copy
        private val internalDbDir = File(Environment.getDataDirectory(), "/data/" + SharedConstants.PACKAGE_NAME + "/databases/")
        private val internalDbBackupDir = File(Environment.getDataDirectory(), "/data/" + SharedConstants.PACKAGE_NAME + "/files/backup")

        private val TAG = "BackupControl"
    }
}
