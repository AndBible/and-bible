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

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.documentdownload.DocumentDownloadEvent
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import java.util.*

/**
 * Store download view items for dynamic update as downloading occurs.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentDownloadProgressCache {
    private val percentDoneByInitials: MutableMap<String, Int> = HashMap()
    private val progressUpdater: WorkListener
    fun startMonitoringDownloads() {
        JobManager.addWorkListener(progressUpdater)
    }

    fun stopMonitoringDownloads() {
        JobManager.removeWorkListener(progressUpdater)
    }

    /**
     * Download has progressed and the ui needs updating if this file item is visible
     */
    fun sendProgressEvent(progress: Progress) {
        val jobID = progress.jobID
        if (jobID.startsWith(INSTALL_BOOK_JOB_NAME)) {
            val initials = jobID.substring(INSTALL_BOOK_JOB_NAME.length)
            val percentDone = progress.work
            percentDoneByInitials[initials] = percentDone
            ABEventBus.getDefault().post(DocumentDownloadEvent(initials, DocumentStatus.DocumentInstallStatus.BEING_INSTALLED, percentDone))
        }
    }

    fun getPercentDone(document: Book): Int {
        val percentDone = percentDoneByInitials[document.initials]
        return percentDone ?: 0
    }

    companion object {
        private const val INSTALL_BOOK_JOB_NAME = "INSTALL_BOOK-"
    }

    init {
        progressUpdater = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) {
                sendProgressEvent(ev.job)
            }

            override fun workStateChanged(ev: WorkEvent) {
                sendProgressEvent(ev.job)
            }
        }
    }
}
