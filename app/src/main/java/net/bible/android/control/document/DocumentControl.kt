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

package net.bible.android.control.document

import net.bible.sharedcore.log.Log
import net.bible.android.common.toV11n
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.window.WindowControl
import net.bible.service.download.FakeBookFactory
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.hideFromSelector
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.SwordEnvironmentInitialisation

import org.crosswire.common.util.Filter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.android.database.SwordDocumentInfoDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


val Book.canDelete: Boolean get () {
    val lastBible = BookCategory.BIBLE == bookCategory && SwordDocumentFacade.bibles.size == 1
    return !lastBible && driver!!.isDeletable(this)
}

/** The file-deleting step of [DocumentControl.deleteDocument] (a named seam, so the DI graph can be verified). */
fun interface DocumentFileDeleter { fun delete(book: Book) }

/** Outcome of [DocumentControl.deleteDocuments]: [skipped] = some documents failed the `canDelete` recheck; [failures] = per-document errors. */
class DeleteDocumentsResult(val skipped: Boolean, val failures: List<Pair<Book, Exception>>)

/** Control use of different documents/books/modules - used by front end
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentControl constructor(
    private val windowControl: WindowControl,
    private val settings: AppSettings,
    private val notifier: UserNotifier,
    private val strings: CoreStrings,
    private val appScope: AppCoroutineScope,
    /** Bound in CoreModule (L1a: no platform defaults); seam for the file-deleting step of [deleteDocument]. */
    private val deleteFiles: DocumentFileDeleter,
    /** Bound in CoreModule. */
    private val backupDaoProvider: () -> SwordDocumentInfoDao,
)
{
    private val documentBackupDao get() = backupDaoProvider()

    val isNewTestament get() = windowControl.activeWindowPageManager.currentVersePage.currentBibleVerse.currentBibleBook.ordinal >= BibleBook.MATT.ordinal

    /**
     * Suggest an alternative dictionary to view or return null
     */
    // very occasionally the below has thrown an Exception and I don't know why, so I wrap all this in a try/catch
    val isStrongsInBook get() = windowControl.activeWindowPageManager.hasStrongs

    /**
     * Are we currently in Bible, Commentary, Dict, or Gen Book mode
     */
    val currentCategory: DocumentCategory
        get() = windowControl.activeWindowPageManager.currentPage.documentCategory

    /**
     * Suggest an alternative bible to view or return null
     */
    // only show bibles that contain verse

    private val bookFilter = Filter<Book> { book ->
        if (book !is AbstractPassageBook) {
            Log.w(TAG, "bookFilter: unexpected non-AbstractPassageBook: ${book.initials}")
            false
        } else {
            try {
                book.contains(requiredVerseForSuggestions.toV11n(book.versification))
            } catch (e: BookException) {
                // Module may have missing/corrupted data files (see issue #788)
                false
            }
        }
    }

    private val commentaryFilter = Filter<Book> { book ->
        if (book !is AbstractPassageBook) {
            Log.w(TAG, "commentaryFilter: unexpected non-AbstractPassageBook: ${book.initials}")
            false
        } else {
            try {
                val verse = requiredVerseForSuggestions.toV11n(book.versification)
                if (!book.contains(verse)) {
                    false
                } else book.initials != "TDavid" || verse.book == BibleBook.PS
            } catch (e: BookException) {
                // Module may have missing/corrupted data files (see issue #788)
                false
            }
        }
    }

    val biblesForVerse : List<Book>
        get () = SwordDocumentFacade.unlockedBibles.sortedBy { bookFilter.test(it) }

    val commentariesForVerse: List<Book>
        get () {
            val docs = SwordDocumentFacade.getBooks(BookCategory.COMMENTARY).filter { !it.isLocked }.sortedBy { commentaryFilter.test(it) }.toMutableList()
            docs.addAll(FakeBookFactory.pseudoDocuments.filter { it.bookCategory == BookCategory.COMMENTARY && !it.hideFromSelector })
            return docs
        }

    val isBibleBook: Boolean
        get () = currentDocument?.bookCategory == BookCategory.BIBLE

    val isCommentary: Boolean
        get () = currentDocument?.bookCategory == BookCategory.COMMENTARY

    val currentPage: CurrentPageManager
        get () = windowControl.activeWindowPageManager

    val currentDocument: Book?
        get () = windowControl.activeWindowPageManager.currentPage.currentDocument

    val suggestedBible: Book?
        get() {
            val currentPageManager = windowControl.activeWindowPageManager
            val currentBible = currentPageManager.currentBible.currentDocument

            return getSuggestedBook(SwordDocumentFacade.bibles, currentBible, bookFilter, currentPageManager.isBibleShown)
        }

    /** Suggest an alternative commentary to view or return null
     */
    // only show commentaries that contain verse - extra checks for TDavid because it always returns true
    // book claims to contain the verse but
    // TDavid has a flawed index and incorrectly claims to contain contents for all books of the
    // bible so only return true if !TDavid or is Psalms
    val suggestedCommentary: Book?
        get() {
            val currentPageManager = windowControl.activeWindowPageManager
            val currentCommentary = currentPageManager.currentCommentary.currentDocument

            return getSuggestedBook(SwordDocumentFacade.getBooks(BookCategory.COMMENTARY),
                    currentCommentary, commentaryFilter, currentPageManager.isCommentaryShown)
        }

    /**
     * Possible books will often not include the current verse but most will include chap 1 verse 1
     */
    private val requiredVerseForSuggestions: Verse
        get() = windowControl.activeWindowPageManager.currentBible.singleKey

    /**
     * user wants to change to a different document/module
     */
    fun changeDocument(newDocument: Book) {
        windowControl.activeWindowPageManager.setCurrentDocument(newDocument)
    }

    fun enableManualInstallFolder() {
        try {
            SwordEnvironmentInitialisation.enableDefaultAndManualInstallFolder()
        } catch (e: BookException) {
            notifier.showError(strings.errorOccurred)
        }

    }

    fun turnOffManualInstallFolderSetting() {
        settings.setBoolean("request_sdcard_permission_pref", false)
    }

    /**
     * Book is deletable according to the driver if it is in the download dir i.e. not sdcard\jsword
     * and according to AndBible if it is not currently selected
     */
    fun canDelete(document: Book?): Boolean = document?.canDelete?: false

    /** delete selected document, even of current doc (Map and Gen Book only currently) and tidy up CurrentPage
     */
    @Throws(BookException::class)
    suspend fun deleteDocument(document: Book): Unit = withContext(appScope.coroutineContext) {
        // The files, the backup row and the page tidy-up go together or not at all: all run in the app scope so
        // that a caller that goes away mid-delete (back, finish) cannot leave a deleted book with a stale backup
        // row or a window still showing it.
        deleteFiles.delete(document)
        if (document.bookCategory == BookCategory.AND_BIBLE) return@withContext
        documentBackupDao.deleteByOsisId(document.initials)
        // Window/page state belongs to the main thread (it was always tidied there).
        withContext(Dispatchers.Main) {
            windowControl.activeWindowPageManager.getBookPage(document, null)?.checkCurrentDocumenInstalled()
        }
    }

    /**
     * Delete [documents] in order, re-checking [canDelete] for each (deleting one of two installed Bibles flips the
     * other's flag). The whole loop, the per-document tidy-up and the installed-changed notification run in the app
     * scope: a screen going away mid-way (cancelling the caller) cannot leave the remaining documents installed or
     * other observers of the installed list stale. The per-document tidy-up and the notification run on the main
     * thread (their observers are UI state), still inside the app-scope block. Per-document failures are collected in
     * the result, not thrown.
     */
    suspend fun deleteDocuments(documents: List<Book>): DeleteDocumentsResult = withContext(appScope.coroutineContext) {
        var skipped = false
        val failures = mutableListOf<Pair<Book, Exception>>()
        for (document in documents) {
            if (!canDelete(document)) { skipped = true; continue }
            try {
                deleteDocument(document)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += document to e
            }
        }
        withContext(Dispatchers.Main) { DocumentChanges.notifyInstalledChanged() }
        DeleteDocumentsResult(skipped, failures)
    }

    /**
     * Suggest an alternative document to view or return null
     */
    private fun getSuggestedBook(books: List<Book>, currentDocument: Book?,
                                 filter: Filter<Book>?, isBookTypeShownNow: Boolean): Book? {
        var suggestion: Book? = null
        if (!isBookTypeShownNow) {
            // allow easy switch back to current doc
            suggestion = currentDocument
        } else {
            // only suggest alternative if more than 1
            if (books.size > 1) {
                // find index of current document
                var currentDocIndex = -1
                for (i in books.indices) {
                    if (books[i] == currentDocument) {
                        currentDocIndex = i
                    }
                }

                // find the next doc containing related content e.g. if in NT then don't show TDavid
                var i = 0
                while (i < books.size - 1 && suggestion == null) {
                    val possibleDoc = books[(currentDocIndex + i + 1) % books.size]

                    if (filter == null || filter.test(possibleDoc)) {
                        suggestion = possibleDoc
                    }
                    i++
                }
            }
        }

        return suggestion
    }

    companion object {

        private const val TAG = "DocumentControl"
    }
}
