/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.page

import android.content.Intent

import net.bible.android.SharedConstants
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.mynote.MyNoteDAO
import net.bible.android.control.page.window.Window
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.Logger
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key
import org.json.JSONObject
import java.lang.RuntimeException
import java.lang.ref.WeakReference

import javax.inject.Inject

/** Control instances of the different current document page types
 * Each Window has its own instance of CurrentPageManager, so it is not a singleton.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class CurrentPageManager @Inject constructor(
        swordContentFacade: SwordContentFacade,
        swordDocumentFacade: SwordDocumentFacade,
        bibleTraverser: BibleTraverser,
        myNoteDAO: MyNoteDAO)
{
    // use the same verse in the commentary and bible to keep them in sync
    private val currentBibleVerse: CurrentBibleVerse = CurrentBibleVerse()
    val currentBible: CurrentBiblePage
    val currentCommentary: CurrentCommentaryPage
    val currentDictionary: CurrentDictionaryPage
    val currentGeneralBook: CurrentGeneralBookPage
    val currentMap: CurrentMapPage
    val currentMyNotePage: CurrentMyNotePage
    var windowRef: WeakReference<Window>? = null

    val window get() = windowRef!!.get()!!

    fun destroy() {
        windowRef?.clear()
    }

    var currentPage: CurrentPage
        private set

    private val logger = Logger(this.javaClass.name)

    /**
     * When navigating books and chapters there should always be a current Passage based book
     */
    val currentPassageDocument: AbstractPassageBook
        get() = currentVersePage.currentPassageBook

    /**
     * Get current Passage based page or just return the Bible page
     */
    val currentVersePage: VersePage
        get() {
            val page: VersePage
            page = if (isBibleShown || isCommentaryShown) {
                currentPage as VersePage
            } else {
                currentBible
            }
            return page
        }

    val isCommentaryShown: Boolean
        get() = currentCommentary === currentPage
    val isBibleShown: Boolean
        get() = currentBible === currentPage
    val isDictionaryShown: Boolean
        get() = currentDictionary === currentPage
    val isGenBookShown: Boolean
        get() = currentGeneralBook === currentPage
    val isMyNoteShown: Boolean
        get() = currentMyNotePage === currentPage
    val isMapShown: Boolean
        get() = currentMap === currentPage

    val stateJson: JSONObject
        get() {
            val `object` = JSONObject()
            try {
                `object`.put("biblePage", currentBible.stateJson)
                        .put("commentaryPage", currentCommentary.stateJson)
                        .put("dictionaryPage", currentDictionary.stateJson)
                        .put("generalBookPage", currentGeneralBook.stateJson)
                        .put("mapPage", currentMap.stateJson)
                        .put("currentPageCategory", currentPage.bookCategory.getName())
            } catch (e: Exception) {
                logger.warn("Page manager get state error")
            }

            return `object`
        }

    init {
        currentBible = CurrentBiblePage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade)
        currentCommentary = CurrentCommentaryPage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade)
        currentMyNotePage = CurrentMyNotePage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade, myNoteDAO)

        currentDictionary = CurrentDictionaryPage(swordContentFacade, swordDocumentFacade)
        currentGeneralBook = CurrentGeneralBookPage(swordContentFacade, swordDocumentFacade)
        currentMap = CurrentMapPage(swordContentFacade, swordDocumentFacade)

        currentPage = currentBible
    }

    /** display a new Document and return the new Page
     */
    fun setCurrentDocument(nextDocument: Book?): CurrentPage {
        var nextPage: CurrentPage? = null
        if (nextDocument != null) {
            PassageChangeMediator.getInstance().onBeforeCurrentPageChanged()

            nextPage = getBookPage(nextDocument)

            // is the next doc the same as the prev doc
            val prevDocInPage = nextPage!!.currentDocument
            val sameDoc = nextDocument == prevDocInPage

            // must be in this order because History needs to grab the current doc before change
            nextPage.currentDocument = nextDocument
            currentPage = nextPage

            // page will change due to above
            // if there is a valid share key or the doc (hence the key) in the next page is the same then show the page straight away
            if (nextPage.key != null && (nextPage.isShareKeyBetweenDocs || sameDoc || nextDocument.contains(nextPage.key))) {
                PassageChangeMediator.getInstance().onCurrentPageChanged(this.window)
            } else {
                val context = CurrentActivityHolder.getInstance().currentActivity
                // pop up a key selection screen
                val intent = Intent(context, nextPage.keyChooserActivity)
                context.startActivity(intent)
            }
        } else {
            // should never get here because a doc should always be passed in but I have seen errors lie this once or twice
            nextPage = currentPage
        }

        return nextPage
    }

    /** My Note is different to all other pages.  It has no documents etc but I attempt to make it look a bit like a Commentary page
     *
     * @param verseRange VerseRange to add note to, start verse is the significant key searched for but range is stored
     */
    fun showMyNote(verseRange: Key) {
        setCurrentDocumentAndKey(currentMyNotePage.currentDocument, verseRange)
    }

    @JvmOverloads
    fun setCurrentDocumentAndKey(currentBook: Book, key: Key, updateHistory: Boolean = true): CurrentPage? {
        return setCurrentDocumentAndKeyAndOffset(currentBook, key, SharedConstants.NO_VALUE.toFloat(), updateHistory)
    }

    fun setCurrentDocumentAndKeyAndOffset(currentBook: Book, key: Key, yOffsetRatio: Float): CurrentPage? {
        return setCurrentDocumentAndKeyAndOffset(currentBook, key, yOffsetRatio, true)
    }

    private fun setCurrentDocumentAndKeyAndOffset(currentBook: Book, key: Key, yOffsetRatio: Float, updateHistory: Boolean): CurrentPage? {
        PassageChangeMediator.getInstance().onBeforeCurrentPageChanged(updateHistory)

        val nextPage = getBookPage(currentBook)
        if (nextPage != null) {
            try {
                nextPage.isInhibitChangeNotifications = true
                nextPage.currentDocument = currentBook
                nextPage.key = key
                nextPage.currentYOffsetRatio = yOffsetRatio
                currentPage = nextPage
            } finally {
                nextPage.isInhibitChangeNotifications = false
            }
        }
        // valid key has been set so do not need to show a key chooser therefore just update main view
        PassageChangeMediator.getInstance().onCurrentPageChanged(this.window)

        return nextPage
    }

    fun getBookPage(book: Book?): CurrentPage? {
        // book should never be null but it happened on one user's phone
        return if (book == null) {
            null
        } else if (book == currentMyNotePage.currentDocument) {
            currentMyNotePage
        } else {
            getBookPage(book.bookCategory)
        }

    }

    private fun getBookPage(bookCategory: BookCategory): CurrentPage {
        return when (bookCategory) {
            BookCategory.BIBLE -> currentBible
            BookCategory.COMMENTARY -> currentCommentary
            BookCategory.DICTIONARY -> currentDictionary
            BookCategory.GENERAL_BOOK -> currentGeneralBook
            BookCategory.MAPS -> currentMap
            BookCategory.OTHER -> currentMyNotePage
            else -> throw RuntimeException("Unsupported book category")
        }
    }

    fun showBible() {
        PassageChangeMediator.getInstance().onBeforeCurrentPageChanged()
        currentPage = currentBible
        PassageChangeMediator.getInstance().onCurrentPageChanged(this.window)
    }

    fun restoreState(jsonObject: JSONObject) {
        try {
            currentBible.restoreState(jsonObject.getJSONObject("biblePage"))
            currentCommentary.restoreState(jsonObject.getJSONObject("commentaryPage"))
            currentDictionary.restoreState(jsonObject.getJSONObject("dictionaryPage"))
            currentGeneralBook.restoreState(jsonObject.getJSONObject("generalBookPage"))
            currentMap.restoreState(jsonObject.getJSONObject("mapPage"))

            val restoredPageCategoryName = jsonObject.getString("currentPageCategory")
            if (StringUtils.isNotEmpty(restoredPageCategoryName)) {
                val restoredBookCategory = BookCategory.fromString(restoredPageCategoryName)
                currentPage = getBookPage(restoredBookCategory)
            }
        } catch (e: Exception) {
            logger.warn("Page manager state restore error")
        }

    }
}
