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

import net.bible.android.activity.R
import net.bible.android.control.download.repoIdentity
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.service.common.CommonUtils
import net.bible.service.common.Logger
import net.bible.service.db.DatabaseContainer
import org.crosswire.common.progress.Progress
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.install.InstallException
import org.crosswire.jsword.book.install.InstallManager
import org.crosswire.jsword.book.install.Installer
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.util.*

/**
 * Originally copied from BookInstaller it calls Sword routines related to installation and removal of books and indexes
 *
 * @author DM Smith [dmsmith555 at yahoo dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DownloadManager(
    private val onFailedReposChange: (() -> Unit)?
) {
    private val installManager: InstallManager = InstallManager()
    val failedRepos = TreeSet<String>()

    private fun markFailed(repo: String) {
        failedRepos.add(repo)
        onFailedReposChange?.invoke()
    }

    private fun markSuccess(repo: String) {
        failedRepos.remove(repo)
        onFailedReposChange?.invoke()
    }

    @Throws(InstallException::class)
    fun getDownloadableBooks(filter: BookFilter?, repo: String, refresh: Boolean): List<Book> {
        var documents: List<Book> = emptyList()
        var installer: Installer? = null
        try {
            // If we know the name of the installer we can get it directly
            installer = installManager.getInstaller(repo)
            documents = if (installer == null) {
                log.error("Error getting installer for repo $repo")
                instance.showErrorMsg(R.string.error_occurred, Exception("Error getting installer for repo $repo"))
                emptyList()
            } else {
                // Now we can get the list of books
                log.debug("getting downloadable books")

                if (refresh || installer.books.size == 0) {
                    log.warn("Reloading book list")

                    val indexLastUpdated = installer.indexLastUpdated()
                    if(indexLastUpdated == 0L || indexLastUpdated > CommonUtils.settings.getLong("repo-$repo-updated", 0)) {
                        installer.reloadBookList()
                        CommonUtils.settings.setLong("repo-$repo-updated", indexLastUpdated)
                    }
                }
                // Get a list of all the available books
                installer.getBooks(filter) //$NON-NLS-1$
            }
            markSuccess(repo)
        } catch (e: Exception) {
            markFailed(repo)
            // ignore error because some minor repos are unreliable
            log.error("Fatal error downloading books from $repo", e)
        } catch (oom: OutOfMemoryError) {
            markFailed(repo)
            // eBible repo throws OOM errors on smaller devices
            log.error("Out of memory error downloading books from $repo")
        } finally {
            //free memory
            installer?.close()
            System.gc()
        }
        log.info("number of documents available:" + documents.size)
        return documents
    }

    private val docDao get() = DatabaseContainer.db.swordDocumentInfoDao()

    /**
     * Install a book, overwriting it if the book to be installed is newer.
     *
     * @param repositoryName
     * the name of the repository from which to get the book
     * @param book
     * the book to get
     * @throws BookException
     * @throws InstallException
     */
    @Throws(BookException::class, InstallException::class)
    fun installBook(repositoryName: String, book: Book) {
        val bookInitials = book.initials

        val installer = installManager.getInstaller(repositoryName)
        val jobId = Progress.INSTALL_BOOK.format(book.repoIdentity)
        installer.install(book, jobId)
        // Make sure it refreshes existing doc

        // reload metadata to ensure the correct location is set, otherwise maps won't show
        val metadata = book.bookMetaData as SwordBookMetaData
        metadata.reload { true }

        // InstallWatcher does not know about repository, so let's add it here
        book.putProperty(REPOSITORY_KEY, repositoryName)
        Books.installed().getBook(bookInitials)?.putProperty(REPOSITORY_KEY, repositoryName)
        docDao.getBook(bookInitials)?.run {
            repository = repositoryName
            docDao.update(this)
        }
    }

    companion object {
        const val REPOSITORY_KEY = "SourceRepository"
        const val TAG = "DownloadManager"
        private val log = Logger(DownloadManager::class.java.name)
    }

}
