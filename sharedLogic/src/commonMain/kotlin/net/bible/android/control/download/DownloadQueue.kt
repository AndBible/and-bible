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
package net.bible.android.control.download

import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.bible.service.download.DownloadManager
import net.bible.service.download.Repository
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.install.DownloadCancelledException
import org.crosswire.jsword.book.install.DownloadException
import org.crosswire.jsword.book.install.InstallException
import kotlin.coroutines.cancellation.CancellationException
import net.bible.sharedlogic.sync.withMonitor

/**
 * Download a single document at a time.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DownloadQueue(
    private val onProgress: (DocumentStatus) -> Unit,
    private val notifier: UserNotifier,
    private val strings: CoreStrings,
    /** Remembers each request until it reaches an outcome the user has seen (#3973); null keeps nothing. */
    private val pending: PendingRepoInstalls? = null,
    /** Keeps the process running while a transfer is in flight (iOS: a UIKit background task); none elsewhere. */
    private val keepAlive: DownloadKeepAlive = DownloadKeepAlive.None,
) {
    // Every access holds the set's own monitor, as Collections.synchronizedSet did.
    private val beingQueued = HashSet<String>()
    private val downloadError = HashSet<String>()
    private val TAG = this::class.simpleName ?: "DownloadQueue"

    suspend fun addDocumentToDownloadQueue(document: Book, repo: Repository) =
        addDocumentToDownloadQueue(document, repo.repoName) { repo.downloadDocument(document) }

    /** [transfer] downloads and installs [document] from the repository named [repoName]. */
    internal suspend fun addDocumentToDownloadQueue(document: Book, repoName: String, transfer: suspend () -> Unit) {
        val repoIdentity = document.repoIdentity
        if (!withMonitor(beingQueued) { beingQueued.contains(repoIdentity) }) {
            withMonitor(beingQueued) { beingQueued.add(repoIdentity) }
            withMonitor(downloadError) { downloadError.remove(repoIdentity) }
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Downloading " + document.osisID + " from repo " + repoName)
                val request = PendingRepoInstall(repoName, document.initials)
                // Settled = an outcome the user saw: installed, an error shown or their own cancel. Only an install
                // stopped with the app (scope cancelled, process suspended or killed) stays pending for the next launch.
                var settled = false
                var release: (() -> Unit)? = null
                try {
                    // Bookkeeping must never stop the download itself.
                    try { pending?.add(request) } catch (e: Exception) { Log.e(TAG, "Could not record the pending install of $document", e) }
                    release = keepAlive.hold("download ${document.initials}")
                    transfer()
                    settled = true
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.INSTALLED, 100))
                } catch (e: DownloadCancelledException) {
                    settled = true
                    Log.e(TAG, "Cancelled downloading $document", e)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.INSTALL_CANCELLED, 0))
                } catch (e: CancellationException) {
                    // the caller's own coroutine was cancelled (a user cancel arrives as DownloadCancelledException above)
                    Log.i(TAG, "Download of $document was cancelled with its caller")
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.INSTALL_CANCELLED, 0))
                    throw e
                } catch (e: DownloadException) {
                    settled = true
                    Log.e(TAG, "Error downloading $document", e)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
                    withMonitor(downloadError) { downloadError.add(repoIdentity) }
                    if (e.statusCode in 200..299) {
                        // A successful response followed by a broken transfer is not an HTTP status error.
                        notifier.showError(strings.errorDownloading, e)
                    } else {
                        val downloadStatusStr = strings.httpStatusText(e.statusCode)
                        val errorMessage = strings.errorDownloadingStatus(e.uri.toString(), downloadStatusStr, e.statusCode)
                        notifier.showError(errorMessage)
                    }
                } catch (e: InstallException) {
                    settled = true
                    Log.e(TAG, "Error downloading $document", e)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
                    withMonitor(downloadError) { downloadError.add(repoIdentity) }
                    notifier.showError(strings.errorDownloading)
                } catch (e: Exception) {
                    settled = true
                    Log.e(TAG, "Error downloading $document", e)
                    notifier.showError(strings.errorOccurred, e)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
                    withMonitor(downloadError) { downloadError.add(repoIdentity) }
                }
                finally {
                    if (settled) {
                        try { pending?.forget(listOf(request)) } catch (e: Exception) { Log.e(TAG, "Could not clear the pending install of $document", e) }
                    }
                    release?.invoke()
                    withMonitor(beingQueued) { beingQueued.remove(repoIdentity) }
                }
            }
        }
    }

    fun isInQueue(document: Book): Boolean = withMonitor(beingQueued) { beingQueued.contains(document.repoIdentity) }
    fun isInQueue(request: PendingRepoInstall): Boolean =
        withMonitor(beingQueued) { beingQueued.contains("${request.repository}--${request.initials}") }
    fun isErrorDownloading(document: Book): Boolean = withMonitor(downloadError) { downloadError.contains(document.repoIdentity) }

}

val Book.repo: String? get() {
    val repo = getProperty(DownloadManager.REPOSITORY_KEY)
    if(repo?.isEmpty() == true) return null
    return repo
}

val Book.repoIdentity: String get() = "${repo}--${initials}"
