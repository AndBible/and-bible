/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_install_zip.*

/**
 * Install SWORD module from a zip file
 *
 * @author Tuomas Airaksinen [tuomas.airaksinen at gmail dot com]
 */

internal class ModuleExists : Exception() {
    companion object {
        private val serialVersionUID = 1L
    }
}

internal class InvalidModule : Exception() {
    companion object {
        private val serialVersionUID = 1L
    }
}

internal val TAG = "InstallZip"

internal class ZipHandler(private val uri: Uri, private val parent: InstallZip) : AsyncTask<Void, Int, Int>() {
    private var totalEntries = 0

    @Throws(IOException::class, ModuleExists::class, InvalidModule::class)
    private fun checkZipFile() {
        var modsDirFound = false
        var modulesFound = false
        var entry: ZipEntry?

        val targetDirectory = SwordBookPath.getSwordDownloadDir()

        val zin = ZipInputStream(parent.contentResolver.openInputStream(uri))

        entry = zin.nextEntry

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
        val zin = ZipInputStream(parent.contentResolver.openInputStream(uri))

        val confFiles = ArrayList<File>()
        val targetDirectory = SwordBookPath.getSwordDownloadDir()
        try {
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

                if (ze.isDirectory)
                    continue
                val fout = FileOutputStream(file)
                try {
                    count = zin.read(buffer)
                    while (count != -1) {
                        fout.write(buffer, 0, count)
                        count = zin.read(buffer)
                    }
                } finally {
                    fout.close()
                }
                publishProgress(++entryNum)
                ze = zin.nextEntry
            }
        } finally {
            zin.close()
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
        when (result) {
            R_ERROR -> Toast.makeText(this.parent, R.string.error_occurred, Toast.LENGTH_SHORT).show()
            R_INVALID_MODULE -> Toast.makeText(this.parent, R.string.invalid_module, Toast.LENGTH_SHORT).show()
            R_MODULE_EXISTS -> Toast.makeText(this.parent, R.string.module_already_installed, Toast.LENGTH_SHORT).show()
            R_OK -> {
                Toast.makeText(this.parent, R.string.install_zip_successfull, Toast.LENGTH_SHORT).show()
                finishResult = Download.RESULT_OK
            }
        }

        parent.setResult(finishResult)
        parent.finish()
    }

    protected fun onProgressUpdate(vararg values: Int) {
        parent.updateProgress(values, totalEntries)
    }

    companion object {
        private val R_ERROR = 1
        private val R_INVALID_MODULE = 2
        private val R_MODULE_EXISTS = 3
        private val R_OK = 4
    }
}

class InstallZip : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Install from Zip starting")
        setContentView(R.layout.activity_install_zip)
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/zip"
        startActivityForResult(intent, PICK_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            PICK_FILE -> if (resultCode == Activity.RESULT_OK) {
                val uri = data.data
                installZipLabel.text = getString(R.string.checking_zip_file)
                val zh = ZipHandler(uri, this)
                zh.execute()
            } else if (resultCode == Activity.RESULT_CANCELED)
                finish()
        }
    }

    override fun onBackPressed() {}

    fun updateProgress(values: IntArray, totalEntries: Int) {
        if (values[0] == 1)
            installZipLabel.text = getString(R.string.extracting_zip_file)

        val progressNow = Math
                .round(values[0].toFloat() / totalEntries.toFloat() * progressBar.max)
        progressBar.progress = progressNow
    }

    companion object {
        private val PICK_FILE = 1
    }
}
