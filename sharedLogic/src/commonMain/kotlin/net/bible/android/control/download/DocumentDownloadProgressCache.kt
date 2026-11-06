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

import org.crosswire.common.progress.JobManager
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book

/**
 * Store download view items for dynamic update as downloading occurs.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentDownloadProgressCache(private val onProgress: (DocumentStatus) -> Unit) {
    private val percentDoneByInitials: MutableMap<String, Int> = HashMap()
    private val progressUpdater: WorkListener
    private val monitorLock = SynchronizedObject()
    private var monitors = 0

    /**
     * Reference counted: the download screen and the activity center each hold one, and the JSword listener
     * stays registered until the last holder stops (so closing the download screen does not blind the activity center).
     */
    fun startMonitoringDownloads() {
        if (synchronized(monitorLock) { monitors++ == 0 }) JobManager.addWorkListener(progressUpdater)
    }

    fun stopMonitoringDownloads() {
        val last = synchronized(monitorLock) { if (monitors == 0) false else --monitors == 0 }
        if (last) JobManager.removeWorkListener(progressUpdater)
    }

    /**
     * Download has progressed and the ui needs updating if this file item is visible
     */
    fun sendProgressEvent(progress: Progress) {
        val jobID = progress.jobID
        if (jobID.startsWith(INSTALL_BOOK_JOB_NAME)) {
            val id = jobID.substring(INSTALL_BOOK_JOB_NAME.length)
            val percentDone = progress.work
            percentDoneByInitials[id] = percentDone
            onProgress(DocumentStatus(id, DocumentStatus.DocumentInstallStatus.BEING_INSTALLED, percentDone))
        }
    }

    fun getPercentDone(document: Book): Int {
        val percentDone = percentDoneByInitials[document.repoIdentity]
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
