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
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BackupViewBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.DATABASE_VERSION
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.android.view.activity.page.MainBibleActivity.Companion._mainBibleActivity
import net.bible.android.view.util.Hourglass
import net.bible.service.common.CommonUtils
import net.bible.service.common.FileManager
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
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


object BackupControl {

    /** Backup database to Uri returned from ACTION_CREATE_DOCUMENT intent
     */
    private suspend fun backupDatabaseToUri(activity: ActivityBase, uri: Uri, file: File)  {
        val hourglass = Hourglass(activity)
        hourglass.show()

        val out = BibleApplication.application.contentResolver.openOutputStream(uri)!!
        val inputStream = FileInputStream(file)

        var ok = true
        try {
            withContext(Dispatchers.IO) {
                inputStream.copyTo(out)
                out.close()
            }
        } catch (ex: IOException) {
            Log.e(TAG, ex.message ?: "Error occurred in backuping db")
            ok = false
        }
        hourglass.dismiss()
        withContext(Dispatchers.Main) {
            if (ok) {
                Log.d(TAG, "Copied database to chosen backup location successfully")
                Dialogs.instance.showMsg2(activity, R.string.backup_success2)
            } else {
                Log.e(TAG, "Error copying database to chosen location.")
                ErrorReportControl.showErrorDialog(activity, activity.getString(R.string.error_occurred))
            }
        }
    }

    /** backup database to custom target (email, drive etc.) via ACTION_SEND intent
     */
    private suspend fun backupDatabaseViaSendIntent(callingActivity: ActivityBase, file: File) {
        val hourglass = Hourglass(callingActivity)
        hourglass.show()

        internalDbBackupDir.mkdirs()
        val targetFile =  File(internalDbBackupDir, file.name)
        file.copyTo(targetFile)
		val subject = callingActivity.getString(R.string.backup_email_subject_2, CommonUtils.applicationNameMedium)
		val message = callingActivity.getString(R.string.backup_email_message_2, CommonUtils.applicationNameMedium)
        val uri = FileProvider.getUriForFile(callingActivity, BuildConfig.APPLICATION_ID + ".provider", targetFile)
		val email = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
            type = "application/x-sqlite3"
        }
		val chooserIntent = Intent.createChooser(email, getString(R.string.send_backup_file))
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        hourglass.dismiss()
		callingActivity.awaitIntent(chooserIntent)
    }

    fun resetDatabase() {
        val f = File(internalDbDir, DATABASE_NAME)
        f.delete()
    }

    /** restore database from custom source
     */
    suspend fun restoreDatabaseViaIntent(inputStream: InputStream): Boolean {
        val fileName = DATABASE_NAME
        internalDbBackupDir.mkdirs()
        val f = File(internalDbBackupDir, fileName)
        var ok = false
        val header = ByteArray(16)
        inputStream.read(header)
        if(String(header) == "SQLite format 3\u0000") {
            val out = FileOutputStream(f)
            withContext(Dispatchers.IO) {
                out.write(header)
                inputStream.copyTo(out)
                out.close()
                val sqlDb = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
                if (sqlDb.version <= DATABASE_VERSION) {
                    Log.d(TAG, "Loading from backup database with version ${sqlDb.version}")
                    DatabaseContainer.reset()
                    BibleApplication.application.deleteDatabase(DATABASE_NAME)
                    ok = FileManager.copyFile(fileName, internalDbBackupDir, internalDbDir)
                }
                sqlDb.close()
            }
        }

        if(!ok) {
            withContext(Dispatchers.Main) {
                Log.e(TAG, "Error restoring database")
                Dialogs.instance.showErrorMsg(R.string.restore_unsuccessfull)
            }
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

    private suspend fun createZip(books: List<Book>, zipFile: File) {
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

        withContext(Dispatchers.IO) {
            ZipOutputStream(FileOutputStream(zipFile)).use { outFile ->
                for(b in books) {
                    val bmd = b.bookMetaData as SwordBookMetaData
                    val configFile = bmd.configFile
                    val rootDir = configFile.parentFile!!.parentFile!!

                    addFile(outFile, rootDir, configFile)
                    val dataPath = bmd.getProperty("DataPath")
                    val dataDir = File(rootDir, dataPath).run {
                        if(listOf(BookCategory.DICTIONARY, BookCategory.GENERAL_BOOK, BookCategory.MAPS).contains(b.bookCategory))
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
        createZip(books, zipFile)
        hourglass.dismiss()

        // send intent to pick file
        var ok = true
        val result = suspendCoroutine<BackupResult> {
            AlertDialog.Builder(callingActivity)
                .setTitle(callingActivity.getString(R.string.backup_backup_title))
                .setMessage(callingActivity.getString(R.string.backup_backup_message))
                .setNegativeButton(callingActivity.getString(R.string.backup_phone_storage)) { dialog, which ->
                    it.resume(BackupResult.STORAGE)
                }
                .setPositiveButton(callingActivity.getString(R.string.backup_share)) { dialog, which ->
                    it.resume(BackupResult.SHARE)
                }

                .setNeutralButton(callingActivity.getString(R.string.cancel), ){ _, _ ->
                    it.resume(BackupResult.CANCEL)
                }
                .show()
        }
        hourglass.show()
        when(result) {
            BackupResult.STORAGE -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
                val r = callingActivity.awaitIntent(intent)?.resultData?.data
                ok = if(r == null) false else backupModulesToUri(r)
            }
            BackupResult.SHARE -> {
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
                callingActivity.awaitIntent(chooserIntent)
            }
            BackupResult.CANCEL -> {
                hourglass.dismiss()
                return@withContext
            }
        }
        hourglass.dismiss()
        if (ok) {
            Log.d(TAG, "Copied modules to chosen backup location successfully")
            Dialogs.instance.showMsg(R.string.backup_modules_success)
        } else {
            Log.e(TAG, "Error copying modules to chosen location.")
            Dialogs.instance.showErrorMsg(R.string.error_occurred)
        }

    }

    suspend fun backupApp(callingActivity: ActivityBase) {
        internalDbBackupDir.mkdirs()

        val app: ApplicationInfo = callingActivity.applicationContext.applicationInfo
        val intent = Intent(Intent.ACTION_SEND)

        // MIME of .apk is "application/vnd.android.package-archive".
        // but Bluetooth does not accept this. Let's use "*/*" instead.
        intent.type = "*/*"
        val tempFile = File(internalDbBackupDir, "and-bible.apk")
        withContext(Dispatchers.IO) {
            tempFile.delete()
            File(app.sourceDir).copyTo(tempFile)
        }

        intent.putExtra(Intent.EXTRA_STREAM,
            FileProvider.getUriForFile(callingActivity, BuildConfig.APPLICATION_ID + ".provider", tempFile))

        withContext(Dispatchers.Main) {
            callingActivity.awaitIntent(Intent.createChooser(intent, callingActivity.getString(R.string.backup_app)))
        }
    }

    enum class BackupResult {STORAGE, SHARE, CANCEL}
    suspend fun startBackupAppDatabase(callingActivity: ActivityBase) {
        val result = withContext(Dispatchers.Main) {
            suspendCoroutine <BackupResult> {
                AlertDialog.Builder(callingActivity)
                    .setTitle(callingActivity.getString(R.string.backup_backup_title))
                    .setMessage(callingActivity.getString(R.string.backup_backup_message))
                    .setNegativeButton(callingActivity.getString(R.string.backup_phone_storage)) { _, _ ->
                        it.resume(BackupResult.STORAGE)
                    }
                    .setPositiveButton(callingActivity.getString(R.string.backup_share)) { _, _ ->
                        it.resume(BackupResult.SHARE)
                    }
                    .setNeutralButton(callingActivity.getString(R.string.cancel)) {_, _ -> it.resume(BackupResult.CANCEL)}
                    .show()
            }
        }
        when(result) {
            BackupResult.STORAGE -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/x-sqlite3"
                    putExtra(Intent.EXTRA_TITLE, DATABASE_NAME)
                }
                val r = callingActivity.awaitIntent(intent)?.resultData?.data ?: return

                _mainBibleActivity?.windowRepository?.saveIntoDb()
                db.sync()
                GlobalScope.launch(Dispatchers.IO) {
                    backupDatabaseToUri(callingActivity, r, dbFile)
                }
            }
            BackupResult.SHARE -> {
                _mainBibleActivity?.windowRepository?.saveIntoDb()
                db.sync()
                backupDatabaseViaSendIntent(callingActivity, dbFile)
            }
            BackupResult.CANCEL -> {}
        }
    }

    private val dbFile get() = BibleApplication.application.getDatabasePath(DATABASE_NAME)

    suspend fun startBackupOldAppDatabase(callingActivity: ActivityBase, file: File) {
        val result = withContext(Dispatchers.Main) {
            suspendCoroutine <BackupResult> {
                AlertDialog.Builder(callingActivity)
                    .setTitle(callingActivity.getString(R.string.backup_backup_title))
                    .setMessage(callingActivity.getString(R.string.backup_backup_message))
                    .setNegativeButton(callingActivity.getString(R.string.backup_phone_storage)) { _, _ ->
                        it.resume(BackupResult.STORAGE)
                    }
                    .setPositiveButton(callingActivity.getString(R.string.backup_share)) { _, _ ->
                        it.resume(BackupResult.SHARE)
                    }
                    .setNeutralButton(callingActivity.getString(R.string.cancel)) {_, _ -> it.resume(BackupResult.CANCEL)}
                    .show()
            }
        }
        when(result) {
            BackupResult.STORAGE -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/x-sqlite3"
                    putExtra(Intent.EXTRA_TITLE, file.name)
                }
                val r = callingActivity.awaitIntent(intent)?.resultData?.data ?: return

                _mainBibleActivity?.windowRepository?.saveIntoDb()
                db.sync()
                GlobalScope.launch(Dispatchers.IO) {
                    backupDatabaseToUri(callingActivity, r, file)
                }
            }
            BackupResult.SHARE -> {
                backupDatabaseViaSendIntent(callingActivity, file)
            }
            BackupResult.CANCEL -> {}
        }
    }

    suspend fun restoreAppDatabaseViaIntent(activity: ActivityBase) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/*"
        val result = activity.awaitIntent(intent) ?: return
        if (result.resultCode == Activity.RESULT_OK) {
            val result2 = Dialogs.instance.showMsg2(activity, R.string.restore_confirmation, true)
            if(result2 != Dialogs.Result.OK) return
            ABEventBus.getDefault().post(ToastEvent(getString(R.string.loading_backup)))
            val hourglass = Hourglass(activity)
            hourglass.show()
            withContext(Dispatchers.IO) {
                val inputStream = try {
                    activity.contentResolver.openInputStream(result.resultData.data!!)
                } catch (e: FileNotFoundException) {null}
                if (inputStream != null && restoreDatabaseViaIntent(inputStream)) {
                    Log.d(TAG, "Restored database successfully")
                    withContext(Dispatchers.Main) {
                        _mainBibleActivity?.afterRestore()
                    }
                } else {
                    Dialogs.instance.showMsg(R.string.restore_unsuccessfull)
                }
            }
            hourglass.dismiss()
        }
    }

    suspend fun restoreModulesViaIntent(activity: ActivityBase) {
        val intent = Intent(activity, InstallZip::class.java)
        val result = activity.awaitIntent(intent)
        if(result?.resultData?.data == null) return

        _mainBibleActivity?.updateDocuments()
    }

    suspend fun backupPopup(activity: ActivityBase) {
        val intent = Intent(activity, BackupActivity::class.java)
        activity.awaitIntent(intent)
    }

    private lateinit var internalDbDir : File
    private lateinit var internalDbBackupDir: File // copy of db is created in this dir when doing backups
    private const val MODULE_BACKUP_NAME = "modules.zip"
    fun setupDirs(context: Context) {
        internalDbDir = File(context.getDatabasePath(DATABASE_NAME).parent!!)
        internalDbBackupDir = File(context.filesDir, "/backup")
    }

    private const val TAG = "BackupControl"
}

class BackupActivity: ActivityBase() {
    lateinit var binding: BackupViewBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildActivityComponent().inject(this)
        binding = BackupViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            restoreModules.text = "${getString(R.string.install_zip)} / ${getString(R.string.restore_modules)}"

            backupApp.setOnClickListener { GlobalScope.launch { BackupControl.backupApp(this@BackupActivity) } }
            backupAppDatabase.setOnClickListener { GlobalScope.launch { BackupControl.startBackupAppDatabase(this@BackupActivity) } }
            backupModules.setOnClickListener { GlobalScope.launch { BackupControl.backupModulesViaIntent(this@BackupActivity) } }
            restoreAppDatabase.setOnClickListener { GlobalScope.launch { BackupControl.restoreAppDatabaseViaIntent(this@BackupActivity) } }
            restoreModules.setOnClickListener { GlobalScope.launch { BackupControl.restoreModulesViaIntent(this@BackupActivity) } }
            CommonUtils.dbBackupPath.listFiles()?.forEach { f ->
                val b = Button(this@BackupActivity)
                val s = f.name
                b.text = s
                b.setOnClickListener {
                    GlobalScope.launch { BackupControl.startBackupOldAppDatabase(this@BackupActivity, f) }
                }
                backupDbButtons.addView(b)
            }
            if(backupDbButtons.childCount == 0) {
                importExportTitle.visibility = View.GONE
            }
        }
    }
}
