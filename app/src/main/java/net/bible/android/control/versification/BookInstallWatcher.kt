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
package net.bible.android.control.versification

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Job
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.android.database.SwordDocumentInfo
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.documents.isSyncableDocument
import net.bible.service.cloudsync.documents.shouldAutoUpload
import net.bible.service.common.AndBibleAddons
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.DownloadManager
import net.bible.service.sword.SwordContentFacade
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksEvent
import org.crosswire.jsword.book.BooksListener
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.versification.VersificationsMapper

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookInstallWatcher(
    private val launcher: OrderedLauncher,
    private val syncStarter: DocumentSyncStarter,
) {
    /** For tests only: runs at the start of every backup-db write, inside the ordered launch. */
    @VisibleForTesting
    internal var beforeWrite: suspend () -> Unit = {}

    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()

    /**
     * Idempotent per PROCESS: JSword's installed-books list is static, while a Koin restart builds a new
     * watcher instance. The previously registered listener (of any instance) is removed first, so exactly
     * one watcher listener, the latest, stays registered.
     */
    fun startListening() {
        synchronized(Companion) {
            registered?.let { Books.installed().removeBooksListener(it) }
            Books.installed().removeBooksListener(listener)
            Books.installed().addBooksListener(listener)
            registered = listener
        }
    }

    /**
     * Install/uninstall events fire in quick succession (e.g. replace = remove + add); the backup-db writes
     * are launched under one key so they land in event order (add then remove leaves the book absent).
     */
    internal val listener: BooksListener = object : BooksListener {
        override fun bookAdded(ev: BooksEvent) {
            val book = ev.book!!
            Activator.deactivate(book)
            initialiseRequiredMapping(book)
            launcher.launch(DB_KEY) { addBookToDb(book) }
            // Suppress the echo: a module installed *by* a sync download must not immediately
            // be auto-pushed back to the cloud it just came from.
            //
            // isSyncableDocument is essential here, not merely defensive: this listener fires for
            // *every* book registered into JSword, including the MyDocument pseudo-books that
            // MyDocumentBookManager registers at startup. Those have no configFile, so packaging
            // them threw an NPE and raised a user-facing error notification (OSTicket 3392).
            if (book.isSyncableDocument
                && !DocumentSync.isInstallingFromSync(book.initials)
                && shouldAutoUpload(
                    DocumentSyncSettings.enabled,
                    DocumentSyncSettings.autoUpload,
                    DocumentSyncSettings.blockList.isBlocked(book.initials),
                    DocumentSyncSettings.isAutoTransferAllowed,
                )
            ) {
                syncStarter.pushDocuments(listOf(book.initials))
            }
            AndBibleAddons.clearCaches()
            SwordContentFacade.clearCaches()
        }

        override fun bookRemoved(ev: BooksEvent) {
            // Document sync: local uninstall does NOT propagate to the cloud by default.
            // "Remove from sync" (tombstone) is an explicit action in CloudDocumentsActivity.
            AndBibleAddons.clearCaches()
            val book = ev.book!!
            launcher.launch(DB_KEY) { removeBookFromDb(book) }
            SwordContentFacade.clearCaches()
        }
    }

    private suspend fun addBookToDb(book: Book) {
        beforeWrite()
        // if book is already installed, we remove it, else it deletes nothing
        Log.i(DownloadManager.TAG, "Adding ${book.name} to document backup database")
        docDao.deleteByOsisId(book.initials)
        // insert the new book info into backup db
        docDao.insert(SwordDocumentInfo(
            book.initials,
            book.name,
            book.abbreviation,
            book.language.name,
            ""
        ))
    }

    /**
     * Changes the backup-db row of [initials] AFTER every install/uninstall write already queued.
     *
     * JSword fires [BooksListener.bookAdded] synchronously inside `install()`, but the row insert it causes is
     * queued under [DB_KEY]. Code that amends the row right after an install (the repository name in
     * `DownloadManager.installBook`, the cipher key in `DocumentSync`) must therefore queue behind it:
     * a direct DAO read/update could run before the insert (row missing: change lost) or be overwritten by it
     * (reinstall: delete-and-reinsert without the field).
     *
     * [update] gets the current row (null when there is none) and returns the row to store, or null to leave
     * the table unchanged. The write runs in the application scope, so cancelling a caller cannot skip it.
     */
    fun launchRowUpdate(initials: String, update: (SwordDocumentInfo?) -> SwordDocumentInfo?): Job =
        launcher.launch(DB_KEY) {
            val updated = update(docDao.getBook(initials)) ?: return@launch
            docDao.insert(updated) // REPLACE: updates an existing row, inserts a missing one
        }

    /** [launchRowUpdate], suspending until the row write has run. */
    suspend fun updateRow(initials: String, update: (SwordDocumentInfo?) -> SwordDocumentInfo?) {
        launchRowUpdate(initials, update).join()
    }

    private suspend fun removeBookFromDb(book: Book) {
        beforeWrite()
        docDao.deleteByOsisId(book.initials)
    }

    /**
     * pre-initialise mappings to prevent pauses during interaction
     */
    @Synchronized
    private fun initialiseRequiredMapping(book: Book) {
        if (book is SwordBook) {
            val versification = book.versification
            // initialise in a background thread to allow normal startup to continue
            Thread {
                Log.i(TAG, "AVMAP Initialise v11n mappings for " + versification.name)
                VersificationsMapper.instance().ensureMappingDataLoaded(versification)
            }.start()
        }
    }

    private companion object {
        /** Listener most recently registered by any instance; guarded by synchronized(Companion). */
        @Volatile
        var registered: BooksListener? = null

        const val TAG = "BookInstallWatcher"
        const val DB_KEY = "book-install"
    }
}
