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
import androidx.core.content.FileProvider
import net.bible.android.activity.BuildConfig
import net.bible.android.database.DATABASE_VERSION
import net.bible.service.db.DATABASE_NAME
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseContainer.db
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
		val subject = callingActivity.getString(R.string.backup_email_subject)
		val message = callingActivity.getString(R.string.backup_email_message)
        val f = File(internalDbBackupDir, fileName)
        val uri = FileProvider.getUriForFile(callingActivity, BuildConfig.APPLICATION_ID + ".provider", f)
		val email = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            type = "application/x-sqlite3"
        }
		val chooserIntent = Intent.createChooser(email, callingActivity.getString(R.string.send_backup_file))
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		callingActivity.startActivity(chooserIntent)
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

    companion object {

        // this is now unused because And Bible databases are held on the SD card to facilitate easier backup by file copy
        private val internalDbDir = File(Environment.getDataDirectory(), "/data/" + SharedConstants.PACKAGE_NAME + "/databases/")
        private val internalDbBackupDir = File(Environment.getDataDirectory(), "/data/" + SharedConstants.PACKAGE_NAME + "/files/backup")

        private val TAG = "BackupControl"
    }
}
