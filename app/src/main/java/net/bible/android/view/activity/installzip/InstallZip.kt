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
import net.bible.android.view.activity.download.Download

import org.crosswire.common.util.NetUtil
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.book.sword.SwordConstants

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_install_zip.*
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import java.io.InputStream

/**
 * Install SWORD module from a zip file
 *
 * @author Tuomas Airaksinen [tuomas.airaksinen at gmail dot com]
 */

internal class ModuleExists : Exception() {
    companion object {
        private const val serialVersionUID = 1L
    }
}

internal class InvalidModule : Exception() {
    companion object {
        private const val serialVersionUID = 1L
    }
}

internal const val TAG = "InstallZip"

internal class ZipHandler(
        private val newInputStream: () -> InputStream?,
        private val updateProgress: (progress: Int) -> Unit,
        private val finish: (finishResult: Int) -> Unit
) : AsyncTask<Void, Int, Int>() {
    private var totalEntries = 0

    @Throws(IOException::class, ModuleExists::class, InvalidModule::class)
    private fun checkZipFile() {
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

        while (entry != null) {
            totalEntries++
            val name = entry.name.replace('\\', '/')
            val targetFile = File(targetDirectory, name)
            if (!entry.isDirectory && targetFile.exists()) {
                zin.close()
                throw ModuleExists()
            }
            if (name.startsWith(SwordConstants.DIR_CONF + "/") && name.endsWith(SwordConstants.EXTENSION_CONF))
                modsDirFound = true
            else if (name.startsWith(SwordConstants.DIR_CONF + "/")) {
            } else if (name.startsWith(SwordConstants.DIR_DATA + "/"))
                modulesFound = true
            else {
                run {
                    zin.close()
                    throw InvalidModule()
                }
            }
            entry = zin.nextEntry
        }

        if (!(modsDirFound && modulesFound)) {
            zin.close()
            throw InvalidModule()
        }

        zin.close()
    }

    @Throws(IOException::class, BookException::class)
    private fun installZipFile() {
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
                publishProgress(++entryNum)
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

    override fun doInBackground(vararg params: Void): Int? {
        try {
            checkZipFile()
            installZipFile()
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred", e)
            return R_ERROR
        } catch (e: BookException) {
            Log.e(TAG, "Error occurred", e)
            return R_ERROR
        } catch (e: InvalidModule) {
            return R_INVALID_MODULE
        } catch (e: ModuleExists) {
            return R_MODULE_EXISTS
        }

        return R_OK
    }

    override fun onPostExecute(result: Int?) {
        var finishResult = Download.RESULT_CANCELED
        val bus = ABEventBus.getDefault()
        when (result) {
            R_ERROR -> bus.post(ToastEvent(R.string.error_occurred))
            R_INVALID_MODULE -> bus.post(ToastEvent(R.string.invalid_module))
            R_MODULE_EXISTS -> bus.post(ToastEvent(R.string.module_already_installed))
            R_OK -> {
                bus.post(ToastEvent(R.string.install_zip_successfull))
                finishResult = Download.RESULT_OK
            }
        }
        finish(finishResult)
    }

    override fun onProgressUpdate(vararg values: Int?) {
        val firstValue = values[0] as Int
        val progressNow = Math
                .round(firstValue.toFloat() / totalEntries.toFloat() * 100)
        updateProgress(progressNow/totalEntries)
    }

    companion object {
        private const val R_ERROR = 1
        private const val R_INVALID_MODULE = 2
        private const val R_MODULE_EXISTS = 3
        private const val R_OK = 4
    }
}

class InstallZip : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Install from Zip starting")
        setContentView(R.layout.activity_install_zip)
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
                {finishResult -> setResult(finishResult); finish() }
        )
        zh.execute()
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
