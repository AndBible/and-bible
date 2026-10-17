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
package net.bible.service.download

import net.bible.sharedcore.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.util.LucidException
import org.crosswire.common.util.NetUtil
import org.crosswire.common.util.KFiles
import org.crosswire.common.util.KUri
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.JSMsg
import org.crosswire.jsword.book.install.InstallException
import net.bible.sharedlogic.sync.withMonitor
import okio.IOException
import kotlin.coroutines.cancellation.CancellationException
import net.bible.sharedcore.http.DownloadHttp

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class GenericFileDownloader(
    scope: CoroutineScope? = null,
    private val settings: net.bible.sharedcore.platform.AppSettings,
    private val notifier: net.bible.sharedcore.platform.UserNotifier,
    private val strings: net.bible.sharedcore.platform.CoreStrings,
    private val onErrorsChange: (() -> Unit)? = null,
    private val http: DownloadHttp,
    /** Copies the finished temp download over the target; keeps the free-space check in `FileManager`. */
    private val copyFile: (from: okio.Path, to: okio.Path) -> Boolean,
) {
    private val errorSet = HashSet<KUri>()

    /** Failed sources, in the order the former `TreeSet<URI>` iterated them ([uriOrder]). */
    val errors: List<KUri> get() = withMonitor(errorSet) { errorSet.sortedWith(uriOrder) }

    private fun addError(uri: KUri) {
        withMonitor(errorSet) { errorSet.add(uri) }
        onErrorsChange?.invoke()
    }

    private fun removeError(uri: KUri) {
        withMonitor(errorSet) { errorSet.remove(uri) }
        onErrorsChange?.invoke()
    }

    val scope = scope ?: CoroutineScope(Dispatchers.Main)

    fun downloadFileInBackground(source: KUri, target: okio.Path, description: String) =
        scope.launch(Dispatchers.IO) {
            // So now we know what we want to install - all we need to do
            // is installer.install(name) however we are doing it in the
            // background so we create a job for it.

            downloadFileNow(source, target, description)
        }

    private suspend fun downloadFileNow(source: KUri, target: okio.Path, description: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting generic download thread - file:" + target.name)
        try {
            // Delete the file, if present
            if (KFiles.exists(target)) {
                Log.i(TAG, "deleting file")
                KFiles.delete(target)
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

    /** Last-Modified via a GET-headers probe; null when the probe fails. */
    suspend fun lastModified(url: KUri): Long? =
        try {
            http.headers(url.toString()).lastModified
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not check last modified time for $url")
            null
        }

    suspend fun downloadFile(source: KUri, target: okio.Path, description: String, reportError: Boolean = true) = withContext(Dispatchers.IO) {
        downloadFileWithProgress(
            source = source,
            target = target,
            description = description,
            reportError = reportError
        )
    }
    suspend fun downloadFileWithProgress(
        source: KUri,
        target: okio.Path,
        description: String = "",
        reportError: Boolean = true,
        jobId: String? = null,
        notifyUser: Boolean = false,
    ): Boolean {
        val lastUpdated = lastModified(source)
        if(KFiles.isFile(target)) {
            val lastChecked = settings.getLong("last-downloaded-${source}", 0)
            if (lastUpdated != null && lastUpdated <= lastChecked) return true
        }
        settings.setLong("last-downloaded-${source}", lastUpdated)

        val jobName = JSMsg.gettext("Downloading : {0}", target.name + " " + description)
        val job = if(jobId != null) {
            org.crosswire.common.progress.JobManagerJvm.createJob(jobId, jobName, null)
        } else {
            JobManager.createJob(jobName)
        }

        job.isNotifyUser = notifyUser

        // Don't bother setting a size, we'll do it later.
        job.beginJob(jobName)
        var success = false
        var temp: KUri? = null
        try {
            // TRANSLATOR: Progress label indicating the Initialization of installing of a book.
            job.sectionName = JSMsg.gettext("Initializing")
            temp = NetUtil.getTemporaryURI("swd", ".tmp")
            copy(job, source, temp)

            // Once the download is complete, we need to continue
            job.isCancelable = false
            if (!job.isFinished) {
                val tempFile = NetUtil.toPath(temp)
                if (!copyFile(tempFile, target)) {
                    Log.e(TAG, "Download Error renaming temp file $tempFile to:$target")
                    notifier.showError(strings.errorOccurred)
                    job.cancel()
                }
            }
            removeError(source)
            success = true
        } catch (e: IOException) {
            if(reportError) addError(source)
            Log.e(TAG, "Failed to download ${source}", e)
            job.cancel()
        } catch (e: InstallException) {
            if(reportError) addError(source)
            Log.e(TAG, "Failed to download ${source}", e)
            job.cancel()
        } catch (e: CancellationException) {
            // the calling coroutine was cancelled: end the job too, so its listeners see it finish
            job.cancel()
            throw e
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
        return success
    }

    private suspend fun copy(job: Progress?, source: KUri, dest: KUri?) {
        Log.i(TAG, "Downloading $source to $dest")
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
        /**
         * Reproduces `java.net.URI.compareTo` for hierarchical URIs with registry-style (domain) hosts, which all
         * real download sources are: scheme and host ignoring case, then user info, port, path, query, fragment,
         * a missing part first, so [errors] reads as the former `TreeSet<URI>` did. Not `toString()` order:
         * `a.com/x` and `a.com.au/x` sort the other way round. An approximation for opaque URIs, a non-domain
         * authority, and user info, query and fragment, which are compared decoded here and raw by `URI`.
         */
        internal val uriOrder = Comparator<KUri> { a, b ->
            cmp(a.getScheme(), b.getScheme(), ignoreCase = true).takeIf { it != 0 }
                ?: cmp(a.getUserInfo(), b.getUserInfo()).takeIf { it != 0 }
                ?: cmp(a.getHost(), b.getHost(), ignoreCase = true).takeIf { it != 0 }
                ?: a.getPort().compareTo(b.getPort()).takeIf { it != 0 }
                ?: cmp(a.getRawPath(), b.getRawPath()).takeIf { it != 0 }
                ?: cmp(a.getQuery(), b.getQuery()).takeIf { it != 0 }
                ?: cmp(a.getFragment(), b.getFragment())
        }

        private fun cmp(s: String?, t: String?, ignoreCase: Boolean = false): Int = when {
            s == null -> if (t == null) 0 else -1
            t == null -> 1
            ignoreCase -> s.lowercase().compareTo(t.lowercase())
            else -> s.compareTo(t)
        }

        private const val TAG = "GenericFileDownloader"
    }
}
