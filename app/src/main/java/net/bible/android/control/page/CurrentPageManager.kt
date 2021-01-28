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

package net.bible.android.control.page

import android.content.Intent
import android.util.Log

import net.bible.android.SharedConstants
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.database.WorkspaceEntities
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Key
import java.lang.IllegalArgumentException
import java.lang.RuntimeException

import javax.inject.Inject

/** Control instances of the different current document page types
 * Each Window has its own instance of CurrentPageManager, so it is not a singleton.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */


enum class DocumentCategory {
    BIBLE, COMMENTARY, DICTIONARY, GENERAL_BOOK, MAPS, MYNOTE;

    val bookCategory: BookCategory get() = BookCategory.valueOf(this.name)
}

val BookCategory.documentCategory: DocumentCategory get() {
    return when(this) {
        BookCategory.BIBLE -> DocumentCategory.BIBLE
        BookCategory.COMMENTARY -> DocumentCategory.COMMENTARY
        BookCategory.DICTIONARY -> DocumentCategory.DICTIONARY
        BookCategory.GENERAL_BOOK -> DocumentCategory.GENERAL_BOOK
        BookCategory.MAPS -> DocumentCategory.MAPS
        else -> throw RuntimeException("Unsupported category")
    }
}

open class CurrentPageManager @Inject constructor(
        swordContentFacade: SwordContentFacade,
        swordDocumentFacade: SwordDocumentFacade,
        bibleTraverser: BibleTraverser,
        val bookmarkControl: BookmarkControl,
        val windowRepository: WindowRepository,
        )
{
    // use the same verse in the commentary and bible to keep them in sync
    val currentBibleVerse: CurrentBibleVerse = CurrentBibleVerse()
    val currentBible = CurrentBiblePage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade, this)
    val currentCommentary = CurrentCommentaryPage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade, this)
    val currentMyNotePage = CurrentMyNotePage(currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade, this)
    val currentDictionary = CurrentDictionaryPage(swordContentFacade, swordDocumentFacade, this)
    val currentGeneralBook = CurrentGeneralBookPage(swordContentFacade, swordDocumentFacade, this)
    val currentMap = CurrentMapPage(swordContentFacade, swordDocumentFacade, this)

    var textDisplaySettings = WorkspaceEntities.TextDisplaySettings()


    val hasStrongs: Boolean get() {
        return try {
            val currentBook = currentPage.currentDocument
            currentBook!!.bookMetaData.hasFeature(FeatureType.STRONGS_NUMBERS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for strongs Numbers in book", e)
            false
        }
    }

    val actualTextDisplaySettings: WorkspaceEntities.TextDisplaySettings
        get() = WorkspaceEntities.TextDisplaySettings.actual(textDisplaySettings, windowRepository.textDisplaySettings)

    lateinit var window: Window

    var currentPage: CurrentPage = currentBible
        private set

    /**
     * When navigating books and chapters there should always be a current Passage based book
     */
    val currentPassageDocument: AbstractPassageBook get() = currentVersePage.currentPassageBook

    /**
     * Get current Passage based page or just return the Bible page
     */
    val currentVersePage: VersePage
        get() {
            return if (isBibleShown || isCommentaryShown) {
                currentPage as VersePage
            } else {
                currentBible
            }
        }

    val isCommentaryShown: Boolean
        get() = currentCommentary === currentPage
    val isBibleShown: Boolean
        get() = currentBible === currentPage
    val isMyNotesShown: Boolean
        get() = currentMyNotePage === currentPage

    val isDictionaryShown: Boolean
        get() = currentDictionary === currentPage
    val isGenBookShown: Boolean
        get() = currentGeneralBook === currentPage
    val isMapShown: Boolean
        get() = currentMap === currentPage


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
            nextPage.setCurrentDocument(nextDocument)
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

    @JvmOverloads
    fun setCurrentDocumentAndKey(currentBook: Book?,
                                 key: Key,
                                 updateHistory: Boolean = true,
                                 yOffsetRatio: Float = SharedConstants.NO_VALUE.toFloat()
    ): CurrentPage? {
        PassageChangeMediator.getInstance().onBeforeCurrentPageChanged(updateHistory)

        val nextPage = getBookPage(currentBook)
        if (nextPage != null) {
            try {
                nextPage.isInhibitChangeNotifications = true
                nextPage.setCurrentDocument(currentBook)
                nextPage.setKey(key)
                nextPage.currentYOffsetRatio = yOffsetRatio
                currentPage = nextPage
            } finally {
                nextPage.isInhibitChangeNotifications = false
            }
        }
        // valid key has been set so do not need to show a key chooser therefore just update main view
        PassageChangeMediator.getInstance().onCurrentPageChanged(window)

        return nextPage
    }

    fun getBookPage(book: Book?): CurrentPage? {
        return if (book == null) {
            null
        } else {
            if(book.osisID == "Commentaries.MyNote")
                currentMyNotePage
            else
                getBookPage(book.bookCategory.documentCategory)
        }

    }

    private fun getBookPage(bookCategory: DocumentCategory): CurrentPage =
        when (bookCategory) {
            DocumentCategory.BIBLE -> currentBible
            DocumentCategory.COMMENTARY -> currentCommentary
            DocumentCategory.DICTIONARY -> currentDictionary
            DocumentCategory.GENERAL_BOOK -> currentGeneralBook
            DocumentCategory.MAPS -> currentMap
            DocumentCategory.MYNOTE -> currentMyNotePage
        }

    fun showBible() {
        PassageChangeMediator.getInstance().onBeforeCurrentPageChanged()
        currentPage = currentBible
        PassageChangeMediator.getInstance().onCurrentPageChanged(this.window)
    }

    val entity get() =
        WorkspaceEntities.PageManager(
            window.id,
            currentBible.entity.copy(),
            currentCommentary.entity.copy(),
            currentDictionary.entity.copy(),
            currentGeneralBook.pageEntity.copy(),
            currentMap.pageEntity.copy(),
            currentPage.documentCategory.name,
            textDisplaySettings.copy()
        )

    fun restoreFrom(pageManagerEntity: WorkspaceEntities.PageManager?, workspaceDisplaySettings: WorkspaceEntities.TextDisplaySettings?=null) {
        pageManagerEntity ?: return

        // Order between these two following lines is critical!
        // otherwise currentYOffsetRatio is not set with respect to correct currentBibleVerse!
        currentBible.restoreFrom(pageManagerEntity.biblePage)
        currentCommentary.restoreFrom(pageManagerEntity.commentaryPage)

        currentDictionary.restoreFrom(pageManagerEntity.dictionaryPage)
        currentGeneralBook.restoreFrom(pageManagerEntity.generalBookPage)
        currentMap.restoreFrom(pageManagerEntity.mapPage)

        val restoredBookCategory = try {
            DocumentCategory.valueOf(pageManagerEntity.currentCategoryName)
        } catch (e: IllegalArgumentException) {
            BookCategory.fromString(pageManagerEntity.currentCategoryName).documentCategory
        }
        val settings = pageManagerEntity.textDisplaySettings
        if(workspaceDisplaySettings != null) {
            WorkspaceEntities.TextDisplaySettings.markNonSpecific(settings, workspaceDisplaySettings)
            textDisplaySettings = settings ?: WorkspaceEntities.TextDisplaySettings()
        }
        currentPage = getBookPage(restoredBookCategory)
        if(currentPage.key == null) {
            currentPage = currentBible
        }
    }

    val TAG get() = "PageManager[${window.id}]"
}
