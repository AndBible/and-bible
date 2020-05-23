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

package net.bible.android.view.activity.installzip

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import net.bible.android.activity.R

import org.crosswire.common.util.NetUtil
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.book.sword.SwordConstants

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_install_zip.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.view.activity.base.ActivityBase
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

/**
 * Install SWORD module from a zip file
 *
 * @author Tuomas Airaksinen [tuomas.airaksinen at gmail dot com]
 */

class ModulesExists(val files: List<String>) : Exception()

class InvalidModule : Exception()

const val TAG = "InstallZip"

class ZipHandler(
        private val newInputStream: () -> InputStream?,
        private val updateProgress: (progress: Int) -> Unit,
        private val finish: (finishResult: Int) -> Unit,
        private val activity: Activity
) {
    private var totalEntries = 0

    @Throws(IOException::class, ModulesExists::class, InvalidModule::class)
    private suspend fun checkZipFile() = withContext(Dispatchers.IO) {
        var modsDirFound = false
        var modulesFound = false
        var entry: ZipEntry?

        val targetDirectory = SwordBookPath.getSwordDownloadDir()
        val zin = ZipInputStream(newInputStream())
        try {
            entry = zin.nextEntry
        } catch (e: IllegalArgumentException) {
            throw InvalidModule()
        }
        val existingFiles = mutableListOf<String>()

        while (entry != null) {
            totalEntries++
            val name = entry.name.replace('\\', '/')
            val targetFile = File(targetDirectory, name)
            if (!entry.isDirectory && targetFile.exists()) {
                existingFiles.add(targetFile.relativeTo(targetDirectory).canonicalPath)
            }
            if (name.startsWith(SwordConstants.DIR_CONF + "/") && name.endsWith(SwordConstants.EXTENSION_CONF))
                modsDirFound = true
            else if (name.startsWith(SwordConstants.DIR_CONF + "/")) {
            } else if (name.startsWith(SwordConstants.DIR_DATA + "/"))
                modulesFound = true
            else {
                zin.close()
                throw InvalidModule()
            }
            entry = zin.nextEntry
        }

        if (!(modsDirFound && modulesFound)) {
            zin.close()
            throw InvalidModule()
        }
        zin.close()
        if(existingFiles.isNotEmpty())
            throw ModulesExists(existingFiles)
    }


    @Throws(IOException::class, BookException::class)
    private suspend fun installZipFile() = withContext(Dispatchers.IO) {
        val zin = ZipInputStream(newInputStream())

        val confFiles = ArrayList<File>()
        val targetDirectory = SwordBookPath.getSwordDownloadDir()
        zin.use { zin ->
            var ze: ZipEntry?
            var count: Int
            var entryNum = 0
            val buffer = ByteArray(8192)
            ze = zin.nextEntry
            while (ze != null) {
                val name = ze.name.replace('\\', '/')

                val file = File(targetDirectory, name)
                if (name.startsWith(SwordConstants.DIR_CONF) && name.endsWith(SwordConstants.EXTENSION_CONF))
                    confFiles.add(file)

                val dir = if (ze.isDirectory) file else file.parentFile

                if (!dir.isDirectory && !(dir.mkdirs() || dir.isDirectory))
                    throw IOException()

                if (ze.isDirectory) {
                    ze = zin.nextEntry
                    continue
                }
                val fout = FileOutputStream(file)
                fout.use { fout ->
                    count = zin.read(buffer)
                    while (count != -1) {
                        fout.write(buffer, 0, count)
                        count = zin.read(buffer)
                    }
                }
                onProgressUpdate(++entryNum)
                ze = zin.nextEntry
            }
        }
        // Load configuration files & register books
        val bookDriver = SwordBookDriver.instance()
        for (confFile in confFiles) {
            val me = SwordBookMetaData(confFile, NetUtil.getURI(targetDirectory))
            me.driver = bookDriver
            SwordBookDriver.registerNewBook(me)
        }
    }

    suspend fun execute() = withContext(Dispatchers.Main) {
        var finishResult = Activity.RESULT_CANCELED
        var doInstall = false

        var result = try {
            checkZipFile()
            doInstall = true
            R_OK
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred", e)
            R_ERROR
        } catch (e: InvalidModule) {
            R_INVALID_MODULE
        } catch (e: ModulesExists) {
            doInstall = suspendCoroutine<Boolean> {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.overwrite_files_title)
                    .setMessage(activity.getString(R.string.overwrite_files, "\n" + e.files.joinToString("\n")))
                    .setPositiveButton(R.string.yes) {_, _ -> it.resume(true)}
                    .setNeutralButton(R.string.cancel) {_, _ -> it.resume(false)}
                    .show()
            }
            if(doInstall) R_OK else R_CANCEL
        }

        if(doInstall) {
            result = try {
                installZipFile()
                finishResult = Activity.RESULT_OK
                R_OK
            } catch (e: BookException) {
                Log.e(TAG, "Error occurred", e)
                R_ERROR
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred", e)
                R_ERROR
            }
        }

        val bus = ABEventBus.getDefault()
        when (result) {
            R_ERROR -> bus.post(ToastEvent(R.string.error_occurred))
            R_CANCEL -> bus.post(ToastEvent(R.string.install_zip_canceled))
            R_INVALID_MODULE -> bus.post(ToastEvent(R.string.invalid_module))
            R_OK -> bus.post(ToastEvent(R.string.install_zip_successfull))
        }
        finish(finishResult)

    }

    private suspend fun onProgressUpdate(value: Int)  = withContext(Dispatchers.Main) {
        val progressNow = (value.toFloat() / totalEntries.toFloat() * 100).roundToInt()
        updateProgress(progressNow/totalEntries)
    }

    companion object {
        private const val R_ERROR = 1
        private const val R_INVALID_MODULE = 2
        private const val R_CANCEL = 3
        private const val R_OK = 4
    }
}

class InstallZip : ActivityBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Install from Zip starting")
        setContentView(R.layout.activity_install_zip)
        super.buildActivityComponent().inject(this)
        when(intent?.action) {
            Intent.ACTION_VIEW -> installZip(intent!!.data!!)
            Intent.ACTION_SEND -> installZip(intent.getParcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> {
                for (uri in intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) {
                    installZip(uri)
                }
            }
            else -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "application/zip"
                startActivityForResult(intent, PICK_FILE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_FILE -> if (resultCode == Activity.RESULT_OK) {
                installZip(data!!.data!!)
            } else if (resultCode == Activity.RESULT_CANCELED)
                finish()
        }
    }

    private fun installZip(uri: Uri) {
        installZipLabel.text = getString(R.string.checking_zip_file)

        val zh = ZipHandler(
                {contentResolver.openInputStream(uri)},
                {percent -> updateProgress(percent)},
                {finishResult -> setResult(finishResult); finish() },
            this
        )
        GlobalScope.launch(Dispatchers.Main) {
            loadingIndicator.visibility = View.VISIBLE
            zh.execute()
            loadingIndicator.visibility = View.GONE
        }
    }

    override fun onBackPressed() {}

    private fun updateProgress(percentValue: Int) {
        if (percentValue == 1)
            installZipLabel.text = getString(R.string.extracting_zip_file)

        progressBar.progress = percentValue
    }

    companion object {
        private const val PICK_FILE = 1
    }
}
