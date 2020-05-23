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
package net.bible.service.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.service.common.FileManager.copyFile
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.util.LucidException
import org.crosswire.common.util.NetUtil
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.JSMsg
import org.crosswire.jsword.book.install.InstallException
import java.io.File
import java.io.IOException
import java.lang.ArithmeticException
import java.net.URI
import java.util.*

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class GenericFileDownloader(
    private val onErrorsChange: (() -> Unit)? = null
) {
    val errors = TreeSet<URI>()

    private fun addError(uri: URI) {
        errors.add(uri)
        onErrorsChange?.invoke()
    }

    private fun removeError(uri: URI) {
        errors.remove(uri)
        onErrorsChange?.invoke()
    }

    fun downloadFileInBackground(source: URI, target: File, description: String) =
        GlobalScope.launch(Dispatchers.IO) {
            // So now we know what we want to install - all we need to do
            // is installer.install(name) however we are doing it in the
            // background so we create a job for it.

            downloadFileNow(source, target, description)
        }

    private suspend fun downloadFileNow(source: URI, target: File, description: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting generic download thread - file:" + target.name)
        try {
            // Delete the file, if present
            if (target.exists()) {
                Log.d(TAG, "deleting file")
                target.delete()
            }
            try {
                downloadFile(source, target, description)
            } catch (e: Exception) {
                throw RuntimeException("IO Error downloading file $source", e)
            }
            Log.i(TAG, "Finished downloading $source")
            removeError(source)
        } catch (e: Exception) {
            addError(source)
            Log.e(TAG, "Error downloading $source", e)
        }
    }

    suspend fun downloadFile(source: URI, target: File, description: String, reportError: Boolean = true) = withContext(Dispatchers.IO) {
        val jobName = JSMsg.gettext("Downloading : {0}", target.name + " " + description)
        val job = JobManager.createJob(jobName)

        // Don't bother setting a size, we'll do it later.
        job.beginJob(jobName)

        var temp: URI? = null
        try {
            // TRANSLATOR: Progress label indicating the Initialization of installing of a book.
            job.sectionName = JSMsg.gettext("Initializing")
            temp = NetUtil.getTemporaryURI("swd", ".tmp")
            copy(job, source, temp)

            // Once the download is complete, we need to continue
            job.isCancelable = false
            if (!job.isFinished) {
                val tempFile = NetUtil.getAsFile(temp)
                if (!copyFile(tempFile, target)) {
                    Log.e(TAG, "Download Error renaming temp file $tempFile to:$target")
                    Dialogs.instance.showErrorMsg(getResourceString(R.string.error_occurred))
                    job.cancel()
                }
            }
            removeError(source)
        } catch (e: IOException) {
            if(reportError) addError(source)
            Log.e(TAG, "Failed to download ${source}", e)
            job.cancel()
        } catch (e: InstallException) {
            if(reportError) addError(source)
            Log.e(TAG, "Failed to download ${source}", e)
            job.cancel()
        } finally {
            job.done()
            // tidy up after ourselves
            // This is a best effort. If for some reason it does not delete now
            // it will automatically be deleted when the JVM exits normally.
            if (temp != null) {
                try {
                    NetUtil.delete(temp)
                } catch (e: IOException) {
                    Log.w(TAG, "Error deleting temp download file:" + e.message)
                }
            }
        }
    }

    @Throws(InstallException::class)
    private fun copy(job: Progress?, source: URI, dest: URI?) {
        Log.d(TAG, "Downloading $source to $dest")
        if (job != null) {
            // TRANSLATOR: Progress label for downloading one or more files.
            job.sectionName = JSMsg.gettext("Downloading files")
        }

        // last 2 params are proxies which I hope we can ignore on Android
        val wr = WebResource(source, null, null)
        try {
            wr.copy(dest, job)
        } catch (le: Exception) {
            if (le is ArithmeticException || le is LucidException) {
                // TRANSLATOR: Common error condition: {0} is a placeholder for the URL of what could not be found.
                throw InstallException(JSMsg.gettext("Unable to find: {0}", source.toString()), le)
            } else
                throw le
        } finally {
            wr.shutdown()
        }
    }

    companion object {
        private const val TAG = "GenericFileDownloader"
    }
}
