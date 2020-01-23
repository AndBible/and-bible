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

package net.bible.android.control.document

import android.util.Log

import net.bible.android.activity.R
import net.bible.android.control.ApplicationScope
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.ConvertibleVerse
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.SwordEnvironmentInitialisation

import org.crosswire.common.util.Filter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.versification.BibleBook

import javax.inject.Inject

/** Control use of different documents/books/modules - used by front end
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class DocumentControl @Inject constructor(
        private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider,
        private val swordDocumentFacade: SwordDocumentFacade,
        private val windowControl: WindowControl)
{

    /**
     * Suggest an alternative dictionary to view or return null
     */
    // very occasionally the below has thrown an Exception and I don't know why, so I wrap all this in a try/catch
    val isStrongsInBook get() = activeWindowPageManagerProvider.activeWindowPageManager.hasStrongs

    /**
     * Are we currently in Bible, Commentary, Dict, or Gen Book mode
     */
    val currentCategory: BookCategory
        get() = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.bookCategory

    /**
     * Suggest an alternative bible to view or return null
     */
    // only show bibles that contain verse

    private val bookFilter = Filter<Book> { book ->
        book.contains(requiredVerseForSuggestions.getVerse((book as AbstractPassageBook).versification))
    }

    private val commentaryFilter = Filter<Book> { book ->
        val verse = requiredVerseForSuggestions.getVerse((book as AbstractPassageBook).versification)
        if (!book.contains(verse)) {
            false
        } else book.getInitials() != "TDavid" || verse.book == BibleBook.PS
    }

    val biblesForVerse : List<Book>
        get () = swordDocumentFacade.bibles.filter { it -> bookFilter.test(it) }
    val commentariesForVerse: List<Book>
        get () = swordDocumentFacade.getBooks(BookCategory.COMMENTARY).filter { it -> commentaryFilter.test(it) }

    val isMyNotes: Boolean
        get () = currentPage.isMyNoteShown

    val isBibleBook: Boolean
        get () = currentDocument?.bookCategory == BookCategory.BIBLE

    val isCommentary: Boolean
        get () = currentDocument?.bookCategory == BookCategory.COMMENTARY

    val currentPage: CurrentPageManager
        get () = activeWindowPageManagerProvider.activeWindowPageManager

    val currentDocument: Book?
        get () = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument

    val suggestedBible: Book?
        get() {
            val currentPageManager = activeWindowPageManagerProvider.activeWindowPageManager
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
            val currentPageManager = activeWindowPageManagerProvider.activeWindowPageManager
            val currentCommentary = currentPageManager.currentCommentary.currentDocument

            return getSuggestedBook(swordDocumentFacade.getBooks(BookCategory.COMMENTARY),
                    currentCommentary, commentaryFilter, currentPageManager.isCommentaryShown)
        }

    /** Suggest an alternative dictionary to view or return null
     */
    val suggestedDictionary: Book?
        get() {
            val currentPageManager = activeWindowPageManagerProvider.activeWindowPageManager
            val currentDictionary = currentPageManager.currentDictionary.currentDocument
            return getSuggestedBook(swordDocumentFacade.getBooks(BookCategory.DICTIONARY),
                    currentDictionary, null, currentPageManager.isDictionaryShown)
        }

    /**
     * Possible books will often not include the current verse but most will include chap 1 verse 1
     */
    private val requiredVerseForSuggestions: ConvertibleVerse
        get() {
            val currentVerse = activeWindowPageManagerProvider.activeWindowPageManager.currentBible.singleKey
            return ConvertibleVerse(currentVerse.book, 1, 1)
        }

    /**
     * user wants to change to a different document/module
     */
    fun changeDocument(newDocument: Book) {
        activeWindowPageManagerProvider.activeWindowPageManager.setCurrentDocument(newDocument)
    }

    fun checkIfAnyPageDocumentsDeleted() {
        windowControl.windowRepository.windows.filter {
            !it.pageManager.currentBible.checkCurrentDocumentStillInstalled()
        }.forEach {
            PassageChangeMediator.getInstance().onCurrentPageChanged(it)
        }
    }

    fun enableManualInstallFolder() {
        try {
            SwordEnvironmentInitialisation.enableDefaultAndManualInstallFolder()
        } catch (e: BookException) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred)
        }

    }

    fun turnOffManualInstallFolderSetting() {
        CommonUtils.sharedPreferences.edit().putBoolean("request_sdcard_permission_pref", false).apply()
    }

    /**
     * Book is deletable according to the driver if it is in the download dir i.e. not sdcard\jsword
     * and according to And Bible if it is not currently selected
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

        val currentPage = activeWindowPageManagerProvider.activeWindowPageManager.getBookPage(document)
        currentPage?.checkCurrentDocumentStillInstalled()
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
