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

import android.util.Log
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.service.common.CommonUtils.megabytesFree
import net.bible.service.download.DownloadManager
import net.bible.service.download.RepoFactory
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.Progress.INSTALL_BOOK
import org.crosswire.common.util.Language
import org.crosswire.common.util.LucidException
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.util.*

/** Support the download screen
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DownloadControl(
    private val downloadQueue: DownloadQueue,
    private val swordDocumentFacade: SwordDocumentFacade)
{
    private val documentDownloadProgressCache: DocumentDownloadProgressCache = DocumentDownloadProgressCache()

    /** pre-download document checks
     */
    fun checkDownloadOkay(): Boolean {
        var okay = true
        if (megabytesFree < SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
            instance.showErrorMsg(R.string.storage_space_warning)
            okay = false
        }
        return okay
    }

    /** @return a list of all available docs that have not already been downloaded, have no lang, or don't work
     */
    suspend fun getDownloadableDocuments(repoFactory: RepoFactory, refresh: Boolean): List<Book> = try {
        val availableDocs = swordDocumentFacade.getDownloadableDocuments(repoFactory, refresh)

        // there are a number of books we need to filter out of the download list for various reasons
        val iter = availableDocs.iterator()
        while (iter.hasNext()) {
            val doc = iter.next()
            if (doc.language == null) {
                Log.i(TAG, "Ignoring " + doc.initials + " because it has no language")
                iter.remove()
            } else if (doc.isQuestionable) {
                Log.i(TAG, "Ignoring " + doc.initials + " because it is questionable")
                iter.remove()
            } else if (doc.initials.equals("westminster", ignoreCase = true)) {
                Log.i(TAG, "Ignoring " + doc.initials + " because some sections are too large for a mobile phone e.g. Q91-150")
                iter.remove()
            } else if (doc.initials.equals("BDBGlosses_Strongs", ignoreCase = true)) {
                Log.i(TAG, "Ignoring " + doc.initials + " because I still need to make it work")
                iter.remove()
            } else if (doc.initials.equals("passion", ignoreCase = true)) {
                Log.i(TAG, "Ignoring " + doc.initials)
                iter.remove()
            } else if (doc.initials == "WebstersDict") {
                Log.i(TAG, "Ignoring " + doc.initials + " because it is too big and crashes dictionary code")
                iter.remove()
            }
        }

        // get fonts.properties at the same time as repo list, or if not yet downloaded
        // the download happens in another thread
        availableDocs.sort()
        availableDocs
    } catch (e: Exception) {
        Log.e(TAG, "Error downloading document list", e)
        ArrayList()
    }

    fun sortLanguages(languages: Collection<Language>?): List<Language> {
        val languageList: MutableList<Language> = ArrayList()
        if (languages != null) {
            languageList.addAll(languages)

            RelevantLanguageSorter.sort(languageList, Books.installed().books)
        }
        return languageList
    }

    @Throws(LucidException::class)
    suspend fun downloadDocument(repoFactory: RepoFactory, document: Book) {
        Log.i(TAG, "Download requested")

        // ensure SBMD is fully, not just partially, loaded
        val bmd = document.bookMetaData
        if (bmd != null && bmd is SwordBookMetaData) {
            // load full bmd but must retain repo key
            val repoKey = bmd.getProperty(DownloadManager.REPOSITORY_KEY)
            bmd.reload()
            bmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
        }
        if (!downloadQueue.isInQueue(document)) {

            // the download happens in another thread
            val repo = repoFactory.getRepoForBook(document)
            downloadQueue.addDocumentToDownloadQueue(document, repo)
        }
    }


    fun cancelDownload(document: Book) {
        val job = JobManager.findJob(INSTALL_BOOK.format(document.repoIdentity))
        job?.cancel()
    }

    /** return install status - installed, not inst, or upgrade  */
    fun getDocumentStatus(document: Book): DocumentStatus {
        val id = document.repoIdentity
        if (downloadQueue.isInQueue(document)) {
            return DocumentStatus(id, DocumentInstallStatus.BEING_INSTALLED, documentDownloadProgressCache.getPercentDone(document))
        }
        if (downloadQueue.isErrorDownloading(document)) {
            return DocumentStatus(id, DocumentInstallStatus.ERROR_DOWNLOADING, 0)
        }
        val installedBook = swordDocumentFacade.getDocumentByInitials(document.initials)
        val differentRepo = installedBook?.repo != null && installedBook.repo != document.repo
        return if (installedBook != null && !differentRepo) {
            // see if the new document is a later version
            try {
                val newVersionObj = Version(document.bookMetaData.getProperty("Version"))
                val installedVersionObj = Version(installedBook.bookMetaData.getProperty("Version"))
                if (newVersionObj > installedVersionObj) {
                    return DocumentStatus(id, DocumentInstallStatus.UPGRADE_AVAILABLE, 100)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error comparing versions", e)
                // probably not the same version if an error occurred comparing
                return DocumentStatus(id, DocumentInstallStatus.UPGRADE_AVAILABLE, 100)
            }
            // otherwise same document is already installed
            DocumentStatus(id, DocumentInstallStatus.INSTALLED, 100)
        } else {
            DocumentStatus(id, DocumentInstallStatus.NOT_INSTALLED, 0)
        }
    }

    fun startMonitoringDownloads() {
        documentDownloadProgressCache.startMonitoringDownloads()
    }

    fun stopMonitoringDownloads() {
        documentDownloadProgressCache.stopMonitoringDownloads()
    }

    companion object {
        private const val TAG = "DownloadControl"
    }
}
