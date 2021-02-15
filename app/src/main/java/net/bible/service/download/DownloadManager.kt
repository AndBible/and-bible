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

import net.bible.android.activity.R
import net.bible.android.database.DocumentBackup
import net.bible.android.database.DocumentBackupDao
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.service.common.Logger
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.install.InstallException
import org.crosswire.jsword.book.install.InstallManager
import org.crosswire.jsword.book.install.Installer
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.util.*
import kotlin.collections.ArrayList

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
    private val docDao get() = DatabaseContainer.db.documentBackupDao()
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
        var documents: List<Book> = ArrayList()
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
                    installer.reloadBookList()
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
    fun installBook(repositoryName: String?, book: Book) {
        // Delete the book, if present
        // At the moment, JSword will not re-install. Later it will, if the
        // remote version is greater.
        val bookInitials = book.initials
        val installedBook = Books.installed().getBook(bookInitials)
        installedBook?.let { unregisterBook(it) }
        // An installer knows how to install books
        val installer = installManager.getInstaller(repositoryName)
        installer.install(book)
        // reload metadata to ensure the correct location is set, otherwise maps won't show
        (book.bookMetaData as SwordBookMetaData).reload { true }
        // update AndBible DB with books user has installed
        updateDocsDB(book);
    }

    private fun updateDocsDB(book: Book) {
        // if book is already installed, we remove it, else it deletes nothing
        docDao.deleteByOsisId(book.osisID)

        // insert the new book info into backup db
        val list = listOf(
            DocumentBackup(
                book.osisID,
                book.name,
                book.abbreviation,
                book.language.name,
                book.getProperty(REPOSITORY_KEY)
            )
        )
        docDao.insertDocuments(list)
    }

    /**
     * Unregister a book from Sword registry.
     *
     * This used to delete the book but there is an mysterious bug in deletion (see below).
     *
     * @param book
     * the book to delete
     * @throws BookException
     */
    @Throws(BookException::class)
    private fun unregisterBook(book: Book) {
        // this just seems to work so leave it here
        // I used to think that the next delete was better - what a mess
        // see this for potential problem: http://stackoverflow.com/questions/20437626/file-exists-returns-false-for-existing-file-in-android
        // does file.exists return an incorrect value?
        // To see the problem, reverse the commented lines below, and try downloading 2 or more Bibles that are already installed
        Books.installed().removeBook(book)

        // Avoid deleting all dir and files because "Java is known not to delete files immediately, so mkdir may fail sometimes"
        // http://stackoverflow.com/questions/617414/create-a-temporary-directory-in-java
        //
        // Actually do the delete
        // This should be a call on installer.
        //book.getDriver().delete(book);
    }// Ask the Install Manager for a map of all known remote repositories
    // sites

    companion object {
        const val REPOSITORY_KEY = "repository"
        private val log = Logger(DownloadManager::class.java.name)
    }

}
