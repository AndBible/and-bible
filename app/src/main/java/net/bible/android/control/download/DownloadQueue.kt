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
import kotlinx.coroutines.withContext
import net.bible.service.download.DownloadManager
import net.bible.service.download.Repository
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.install.DownloadCancelledException
import org.crosswire.jsword.book.install.DownloadException
import org.crosswire.jsword.book.install.InstallException
import java.lang.Exception
import kotlin.coroutines.cancellation.CancellationException
import java.util.*

/**
 * Download a single document at a time.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DownloadQueue(private val onProgress: (DocumentStatus) -> Unit, private val notifier: UserNotifier, private val strings: CoreStrings) {
    private val beingQueued = Collections.synchronizedSet(HashSet<String>())
    private val downloadError = Collections.synchronizedSet(HashSet<String>())
    private val TAG = this.javaClass.simpleName

    suspend fun addDocumentToDownloadQueue(document: Book, repo: Repository) {
        val repoIdentity = document.repoIdentity
        if (!beingQueued.contains(repoIdentity)) {
            beingQueued.add(repoIdentity)
            downloadError.remove(repoIdentity)
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Downloading " + document.osisID + " from repo " + repo.repoName)
                try {
                    repo.downloadDocument(document)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.INSTALLED, 100))
                } catch (e: DownloadCancelledException) {
                    Log.e(TAG, "Cancelled downloading $document", e)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.INSTALL_CANCELLED, 0))
                } catch (e: CancellationException) {
                    // the caller's own coroutine was cancelled (a user cancel arrives as DownloadCancelledException above)
                    Log.i(TAG, "Download of $document was cancelled with its caller")
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.INSTALL_CANCELLED, 0))
                    throw e
                } catch (e: DownloadException) {
                    Log.e(TAG, "Error downloading $document", e)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
                    downloadError.add(repoIdentity)
                    val downloadStatusStr = strings.httpStatusText(e.statusCode)
                    val errorMessage = strings.errorDownloadingStatus( e.uri.toString(), downloadStatusStr, e.statusCode)
                    notifier.showError(errorMessage)
                } catch (e: InstallException) {
                    Log.e(TAG, "Error downloading $document", e)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
                    downloadError.add(repoIdentity)
                    notifier.showError(strings.errorDownloading)
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading $document", e)
                    notifier.showError(strings.errorOccurred, e)
                    onProgress(DocumentStatus(repoIdentity, DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
                    downloadError.add(repoIdentity)
                }
                finally {
                    beingQueued.remove(repoIdentity)
                }
            }
        }
    }

    fun isInQueue(document: Book): Boolean = beingQueued.contains(document.repoIdentity)
    fun isErrorDownloading(document: Book): Boolean = downloadError.contains(document.repoIdentity)

}

val Book.repo: String? get() {
    val repo = getProperty(DownloadManager.REPOSITORY_KEY)
    if(repo?.isEmpty() == true) return null
    return repo
}

val Book.repoIdentity: String get() = "${repo}--${initials}"
