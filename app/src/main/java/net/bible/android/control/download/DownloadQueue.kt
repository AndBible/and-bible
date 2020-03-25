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
package net.bible.android.control.download

import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.service.common.Logger
import net.bible.service.download.RepoBase
import org.crosswire.jsword.book.Book
import java.util.*
import java.util.concurrent.ExecutorService

/**
 * Download a single document at a time.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DownloadQueue(private val executorService: ExecutorService) {
    private val beingQueued = Collections.synchronizedSet(HashSet<String>())
    private val downloadError = Collections.synchronizedSet(HashSet<String>())
    private val log = Logger(this.javaClass.simpleName)

    fun addDocumentToDownloadQueue(document: Book, repo: RepoBase) {
        if (!beingQueued.contains(document.initials)) {
            beingQueued.add(document.initials)
            downloadError.remove(document.initials)
            executorService.submit {
                log.info("Downloading " + document.initials + " from repo " + repo.repoName)
                try {
                    repo.downloadDocument(document)
                    ABEventBus.getDefault().post(DocumentDownloadEvent(document.initials, DocumentStatus.DocumentInstallStatus.INSTALLED, 100))
                } catch (e: Exception) {
                    log.error("Error downloading $document", e)
                    handleDownloadError(document)
                } finally {
                    beingQueued.remove(document.initials)
                }
            }
        }
    }

    private fun handleDownloadError(document: Book) {
        ABEventBus.getDefault().post(DocumentDownloadEvent(document.initials, DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0))
        downloadError.add(document.initials)
        instance.showErrorMsg(R.string.error_downloading)
    }

    fun isInQueue(document: Book): Boolean {
        return beingQueued.contains(document.initials)
    }

    fun isErrorDownloading(document: Book): Boolean {
        return downloadError.contains(document.initials)
    }

}
