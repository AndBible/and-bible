/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.log.Log
import net.bible.android.control.download.repoIdentity
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.db.DatabaseContainer
import net.bible.sharedlogic.sync.withMonitor
import net.bible.service.sword.mybible.MyBibleInstaller
import org.crosswire.common.progress.Progress
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.install.InstallException
import org.crosswire.jsword.book.install.InstallManager
import org.crosswire.jsword.book.install.Installer
import org.crosswire.jsword.book.install.sword.AbstractSwordInstaller
import org.crosswire.jsword.book.install.sword.HttpsSwordInstaller
import org.crosswire.jsword.book.sword.SwordBookMetaData
import kotlin.coroutines.cancellation.CancellationException

/**
 * Originally copied from BookInstaller it calls Sword routines related to installation and removal of books and indexes
 *
 * @author DM Smith [dmsmith555 at yahoo dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DownloadManager(
    private val settings: net.bible.sharedcore.platform.AppSettings,
    private val watcher: BookInstallWatcher,
    private val fileDownloader: GenericFileDownloader,
    private val environment: net.bible.sharedcore.platform.AppEnvironment,
    private val onFailedReposChange: (() -> Unit)? = null
) {
    val customRepositoryDao get() = DatabaseContainer.instance.repoDb.customRepositoryDao()
    private val failedRepoSet = HashSet<String>()

    /** Failed repository names in sorted order, as the former `TreeSet<String>` iterated them. */
    val failedRepos: List<String> get() = withMonitor(failedRepoSet) { failedRepoSet.sorted() }

    private lateinit var installManager: InstallManager
    suspend fun refreshInstallManager() {
        installManager = InstallManager()
        for(r in customRepositoryDao.all()) {
            val installer = when(r.type) {
                "sword-https" -> {
                    HttpsSwordInstaller().apply {
                        setHost(r.host)
                        setPackageDirectory(r.packageDirectory)
                        setCatalogDirectory(r.catalogDirectory)
                    }
                }
                "mybible-https" -> {
                    MyBibleInstaller(r.manifestUrl!!, fileDownloader, environment)
                }
                else -> throw RuntimeException("Invalid repository specification")
            }
            installManager.addInstaller(r.name, installer)
        }
        withMonitor(failedRepoSet) { failedRepoSet.clear() }
        onFailedReposChange?.invoke()
    }

    private fun markFailed(repo: String) {
        withMonitor(failedRepoSet) { failedRepoSet.add(repo) }
        onFailedReposChange?.invoke()
    }

    private fun markSuccess(repo: String) {
        withMonitor(failedRepoSet) { failedRepoSet.remove(repo) }
        onFailedReposChange?.invoke()
    }

    fun getInstallerFor(repo: Repository): Installer?
        = installManager.getInstaller(repo.repoName)

    suspend fun getDownloadableBooks(filter: BookFilter?, repo: String, refresh: Boolean): List<Book> {
        var documents: List<Book> = emptyList()
        var installer: Installer? = null
        try {
            // If we know the name of the installer we can get it directly
            installer = installManager.getInstaller(repo)
            documents = if (installer == null) {
                Log.w(TAG, "No installer found for repo $repo, skipping")
                markFailed(repo)
                emptyList()
            } else {
                // Now we can get the list of books
                Log.d(TAG, "getting downloadable books")

                // An empty SWORD catalog is valid; only a missing or unreadable cache needs recovery.
                // The timestamp alone cannot prove presence because the cache may have been cleared.
                val cacheMissing = if (installer is AbstractSwordInstaller) !installer.hasCachedBookList()
                    else installer.getBooks().isEmpty()
                if (refresh || cacheMissing) {
                    Log.w(TAG, "Reloading book list")

                    val indexLastUpdated = installer.indexLastUpdated()!!
                    if(cacheMissing || indexLastUpdated == 0L || indexLastUpdated > settings.getLong("repo-$repo-updated", 0)) {
                        installer.reloadBookList()
                        settings.setLong("repo-$repo-updated", indexLastUpdated)
                    }
                }
                // Get a list of all the available books
                installer.getBooks(filter) //$NON-NLS-1$
            }
            markSuccess(repo)
        } catch (e: CancellationException) {
            // the caller gave up: not a repository failure
            throw e
        } catch (e: Exception) {
            markFailed(repo)
            // ignore error because some minor repos are unreliable
            Log.e(TAG, "Fatal error downloading books from $repo", e)
        } catch (oom: OutOfMemoryError) { // L1-common-ok: OutOfMemoryError compiles in commonMain
            markFailed(repo)
            // eBible repo throws OOM errors on smaller devices
            Log.e(TAG, "Out of memory error downloading books from $repo")
        } finally {
            //free memory
            installer?.close()
        }
        Log.i(TAG, "number of documents available:" + documents.size)
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
    suspend fun installBook(repositoryName: String, book: Book) {
        val bookInitials = book.initials

        val installer = installManager.getInstaller(repositoryName)!!
        val jobId = Progress.INSTALL_BOOK.replace("%s", book.repoIdentity)
        installer.install(book, jobId)
        // Make sure it refreshes existing doc

        // reload metadata to ensure the correct location is set, otherwise maps won't show
        val metadata = book.bookMetaData as SwordBookMetaData
        try { metadata.reload { true } } catch (e: BookException) {
            Log.e(TAG, "Can't reload metadata", e)
        }

        // InstallWatcher does not know about repository, so let's add it here
        book.putProperty(REPOSITORY_KEY, repositoryName)
        Books.installed().getBook(bookInitials)?.putProperty(REPOSITORY_KEY, repositoryName)
        recordRepository(watcher, bookInitials, repositoryName)
    }

    companion object {
        /**
         * Stores [repositoryName] in the backup-db row of [bookInitials]. Queued behind the row insert that
         * `install()`'s bookAdded event queued ([BookInstallWatcher.updateRow]); a direct DAO update would find
         * no row yet, or be overwritten by the delete-and-reinsert of a reinstall.
         */
        @VisibleForTesting suspend fun recordRepository(watcher: BookInstallWatcher, bookInitials: String, repositoryName: String) {
            watcher.updateRow(bookInitials) { row -> row?.apply { repository = repositoryName } }
        }

        const val REPOSITORY_KEY = "SourceRepository"
        const val TAG = "DownloadManager"
    }

}
