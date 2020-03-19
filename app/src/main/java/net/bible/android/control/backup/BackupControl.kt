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
import net.bible.android.activity.BuildConfig
import net.bible.android.database.DATABASE_VERSION

import net.bible.service.db.DATABASE_NAME
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseContainer.db
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject


/**
 * Support backup and restore of the And bible database which contains bookmarks and notes.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class BackupControl @Inject constructor() {
    /** backup database to custom target (email, drive etc.)
     */
    fun backupDatabaseViaIntent(callingActivity: Activity) {
        db.sync()
        val fileName = DATABASE_NAME
        internalDbBackupDir.mkdirs()
        FileManager.copyFile(fileName, internalDbDir, internalDbBackupDir)
		val subject = getString(R.string.backup_email_subject)
		val message = getString(R.string.backup_email_message)
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
            Dialogs.getInstance().showMsg(R.string.restore_success)
        } else {
            Log.e(TAG, "Error restoring database")
            Dialogs.getInstance().showErrorMsg(R.string.restore_unsuccessfull)
        }
        f.delete()
        return ok
    }

    private fun getString(id: Int): String {
        return BibleApplication.application.getString(id)
    }

    private fun selectModules(context: Context, cb: (selectedBooks: List<Book>) -> Unit) {
        val books = Books.installed().books.sortedBy { it.language }
        val bookNames = books.map { it.name }.toTypedArray()

        val checkedItems = bookNames.map { false }.toBooleanArray()
        val dialog = AlertDialog.Builder(context)
            .setPositiveButton(R.string.okay) {d,_ ->
                val selectedBooks = books.filterIndexed { index, book -> checkedItems[index] }
                cb(selectedBooks)
            }
            .setMultiChoiceItems(bookNames, checkedItems) { _, pos, value ->
                checkedItems[pos] = value
            }
            .setNeutralButton(R.string.select_all, null)
            .setNegativeButton(R.string.cancel, null)
            .setTitle(getString(R.string.backup_modules_title))
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val allSelected = checkedItems.find { !it } == null
                val newValue = !allSelected
                val v = dialog.listView
                for(i in 0 until v.count) {
                    v.setItemChecked(i, newValue)
                    checkedItems[i] = newValue
                }
                (it as Button).text = getString(if(allSelected) R.string.select_all else R.string.select_none)
            }
        }
        dialog.show()

    }

    fun backupModulesViaIntent(callingActivity: Activity) {
        val fileName = "modules.zip"
        internalDbBackupDir.mkdirs()
        val f = File(internalDbBackupDir, fileName)
        selectModules(callingActivity) { books ->
            val modules = books.joinToString(", ") { it.abbreviation }
            val subject = getString(R.string.backup_modules_email_subject)
            val message = BibleApplication.application.getString(R.string.backup_modules_email_message, modules)

            val uri = FileProvider.getUriForFile(callingActivity, BuildConfig.APPLICATION_ID + ".provider", f)
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
    }

    companion object {

        // this is now unused because And Bible databases are held on the SD card to facilitate easier backup by file copy
        private val internalDbDir = File(Environment.getDataDirectory(), "/data/" + SharedConstants.PACKAGE_NAME + "/databases/")
        private val internalDbBackupDir = File(Environment.getDataDirectory(), "/data/" + SharedConstants.PACKAGE_NAME + "/files/backup")

        private val TAG = "BackupControl"
    }
}
