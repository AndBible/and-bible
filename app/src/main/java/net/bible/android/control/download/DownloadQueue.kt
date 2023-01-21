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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.Logger
import net.bible.service.download.DownloadManager
import net.bible.service.download.Repository
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.install.DownloadCancelledException
import org.crosswire.jsword.book.install.DownloadException
import org.crosswire.jsword.book.install.InstallException
import java.lang.Exception
import java.util.*

/**
 * Download a single document at a time.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DownloadQueue {
    private val beingQueued = Collections.synchronizedSet(HashSet<String>())
    private val downloadError = Collections.synchronizedSet(HashSet<String>())
    private val log = Logger(this.javaClass.simpleName)

    private fun httpError(code: Int): String {
        val httpErrors = mapOf(
            403 to R.string.http_forbidden,
            404 to R.string.http_not_found,
        )
        val msgId = httpErrors[code] ?: R.string.http_unknown
        return application.getString(msgId)
    }

    suspend fun addDocumentToDownloadQueue(document: Book, repo: Repository) {
        val repoIdentity = document.repoIdentity
        if (!beingQueued.contains(repoIdentity)) {
            beingQueued.add(repoIdentity)
            downloadError.remove(repoIdentity)
            withContext(Dispatchers.IO) {
                log.info("Downloading " + document.osisID + " from repo " + repo.repoName)
                try {
                    repo.downloadDocument(document)
                    ABEventBus.post(DocumentDownloadEvent(repoIdentity,
                        DocumentStatus.DocumentInstallStatus.INSTALLED, 100))
                } catch (e: DownloadCancelledException) {
                    log.error("Cancelled downloading $document", e)
                    ABEventBus.post(DocumentDownloadEvent(repoIdentity,
                        DocumentStatus.DocumentInstallStatus.INSTALL_CANCELLED, 0))
                } catch (e: DownloadException) {
                    log.error("Error downloading $document", e)
                    ABEventBus.post(DocumentDownloadEvent(repoIdentity,
                        DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
                    downloadError.add(repoIdentity)
                    val downloadStatusStr = httpError(e.statusCode)
                    val errorMessage = application.getString(R.string.error_downloading_status, e.uri.toString(), downloadStatusStr, e.statusCode)
                    Dialogs.showErrorMsg(errorMessage)
                } catch (e: InstallException) {
                    log.error("Error downloading $document", e)
                    ABEventBus.post(DocumentDownloadEvent(repoIdentity,
                        DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
                    downloadError.add(repoIdentity)
                    Dialogs.showErrorMsg(R.string.error_downloading)
                } catch (e: Exception) {
                    log.error("Error downloading $document", e)
                    Dialogs.showErrorMsg(R.string.error_occurred, e)
                    ABEventBus.post(DocumentDownloadEvent(repoIdentity,
                        DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
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
