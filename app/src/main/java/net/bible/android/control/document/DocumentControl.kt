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

package net.bible.android.control.document

import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.control.ApplicationScope
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.download.FakeBookFactory
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.SwordEnvironmentInitialisation

import org.crosswire.common.util.Filter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook

import javax.inject.Inject

/** Control use of different documents/books/modules - used by front end
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class DocumentControl @Inject constructor(
        private val swordDocumentFacade: SwordDocumentFacade,
        private val windowControl: WindowControl)
{
    private val documentBackupDao get() = DatabaseContainer.db.swordDocumentInfoDao()

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
        book.contains(requiredVerseForSuggestions.toV11n((book as AbstractPassageBook).versification))
    }

    private val commentaryFilter = Filter<Book> { book ->
        val verse = requiredVerseForSuggestions.toV11n((book as AbstractPassageBook).versification)
        if (!book.contains(verse)) {
            false
        } else book.getInitials() != "TDavid" || verse.book == BibleBook.PS
    }

    val biblesForVerse : List<Book>
        get () = swordDocumentFacade.unlockedBibles.sortedBy { bookFilter.test(it) }

    val commentariesForVerse: List<Book>
        get () {
            val docs = swordDocumentFacade.getBooks(BookCategory.COMMENTARY).filter { !it.isLocked }.sortedBy { commentaryFilter.test(it) }.toMutableList()
            docs.addAll(FakeBookFactory.pseudoDocuments.filter { it.bookCategory == BookCategory.COMMENTARY })
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

            return getSuggestedBook(swordDocumentFacade.bibles, currentBible, bookFilter, currentPageManager.isBibleShown)
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

            return getSuggestedBook(swordDocumentFacade.getBooks(BookCategory.COMMENTARY),
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
            Dialogs.showErrorMsg(R.string.error_occurred)
        }

    }

    fun turnOffManualInstallFolderSetting() {
        CommonUtils.settings.setBoolean("request_sdcard_permission_pref", false)
    }

    /**
     * Book is deletable according to the driver if it is in the download dir i.e. not sdcard\jsword
     * and according to AndBible if it is not currently selected
     */
    fun canDelete(document: Book?): Boolean {
        if (document == null) {
            return false
        }

        val lastBible = BookCategory.BIBLE == document.bookCategory && swordDocumentFacade.bibles.size == 1

        return !lastBible && document.driver.isDeletable(document)
    }

    /** delete selected document, even of current doc (Map and Gen Book only currently) and tidy up CurrentPage
     */
    @Throws(BookException::class)
    fun deleteDocument(document: Book) {
        swordDocumentFacade.deleteDocument(document)
        if(document.bookCategory == BookCategory.AND_BIBLE) return
        documentBackupDao.deleteByOsisId(document.initials)
        val currentPage = windowControl.activeWindowPageManager.getBookPage(document)
        currentPage?.checkCurrentDocumenInstalled()
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
