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
package net.bible.android.control.search

import android.app.Activity
import android.content.Intent
import android.util.Log
import net.bible.android.control.ApplicationScope
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.versification.Scripture
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.search.Search
import net.bible.android.view.activity.search.SearchIndex
import net.bible.service.common.CommonUtils.limitTextLength
import net.bible.service.sword.SwordContentFacade.getPlainText
import net.bible.service.sword.SwordContentFacade.readOsisFragment
import net.bible.service.sword.SwordContentFacade.search
import net.bible.service.sword.SwordDocumentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.lucene.LuceneIndex
import org.crosswire.jsword.index.search.SearchType
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.jdom2.Element
import javax.inject.Inject

/** Support for the document search functionality
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class SearchControl @Inject constructor(
    private val swordDocumentFacade: SwordDocumentFacade,
    private val documentBibleBooksFactory: DocumentBibleBooksFactory,
    private val activeWindowPageManagerProvider: ActiveWindowPageManagerProvider
    )
{
    private val isSearchShowingScripture = true
//    public final var originalSearchString = ""

    enum class SearchBibleSection {
        OT, NT, CURRENT_BOOK, ALL
    }

    /** if current document is indexed then go to search else go to download index page
     *
     * @return required Intent
     */
    fun getSearchIntent(document: Book?, activity: Activity): Intent {
        val indexStatus = document?.indexStatus
        Log.i(TAG, "Index status:$indexStatus")
        return if (indexStatus == IndexStatus.DONE) {
            Log.i(TAG, "Index status is DONE")
            Intent(activity, Search::class.java)
        } else {
            Log.i(TAG, "Index status is NOT DONE")
            Intent(activity, SearchIndex::class.java)
        }
    }

    fun validateIndex(document: Book?): Boolean {
        return document?.indexStatus == IndexStatus.DONE
    }

    // This should never occur
    val currentBookName: String
        get() = try {
            val currentBiblePage = activeWindowPageManagerProvider.activeWindowPageManager.currentBible
            val v11n = (currentBiblePage.currentDocument as SwordBook).versification
            val book = currentBiblePage.singleKey.book
            val longName = v11n.getLongName(book)
            if (StringUtils.isNotBlank(longName) && longName.length < 14) {
                longName
            } else {
                v11n.getShortName(book)
            }
        } catch (nsve: Exception) {
            // This should never occur
            Log.e(TAG, "Error getting current book name", nsve)
            "-"
        }

    fun decorateSearchString(searchString: String, searchType: SearchType, bibleSection: SearchBibleSection, currentBookName: String?): String {
        val cleanSearchString = cleanSearchString(searchString)
        var decorated: String

        // add search type (all/any/phrase) to search string
        decorated = searchType.decorate(cleanSearchString)
        originalSearchString = decorated

        // add bible section limitation to search text
        decorated = getBibleSectionTerm(bibleSection, currentBookName) + " " + decorated
        return decorated
    }

    /** do the search query and prepare results in lists ready for display
     *
     */
    @Throws(BookException::class)
    fun getSearchResults(document: String?, searchText: String?): SearchResultsDto {
        Log.i(TAG, "Preparing search results")
        val searchResults = SearchResultsDto()

        // search the current book
        val book = swordDocumentFacade.getDocumentByInitials(document)
        var result: Key? = null
        try {
            result = search(book!!, searchText)
        } catch (e: BookException) {
            Log.e(TAG, "Error in executing search: $searchText")
        }
        if (result != null) {
            val resNum = result.cardinality
            Log.i(TAG, "Number of results:$resNum")

            //if Bible or commentary then filter out any non Scripture keys, otherwise don't filter
            val isBibleOrCommentary = book is AbstractPassageBook
            val keyIterator: Iterator<Key> = result.iterator()
            for (i in 0 until Math.min(resNum, MAX_SEARCH_RESULTS + 1)) {
                val key = keyIterator.next()
                val isMain = !isBibleOrCommentary || Scripture.isScripture((key as Verse).book)
                searchResults.add(key, isMain)
            }
        }
        return searchResults
    }

    /** get the verse for a search result
     */
    fun getSearchResultVerseText(key: Key?): String {
        // There is similar functionality in BookmarkControl
        var verseText = ""
        try {
            val doc = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument
            val cat = doc!!.bookCategory
            verseText = if (cat == BookCategory.BIBLE || cat == BookCategory.COMMENTARY) {
                getPlainText(doc, key)
            } else {
                val bible = activeWindowPageManagerProvider.activeWindowPageManager.currentBible.currentDocument!!
                getPlainText(bible, key)
            }
            verseText = limitTextLength(verseText)!!
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verse text", e)
        }
        return verseText
    }

    fun getSearchResultVerseElement(key: Key?): Element {
        // There is similar functionality in BookmarkControl
        var xmlVerse:Element? = null
        try {
            val doc = activeWindowPageManagerProvider.activeWindowPageManager.currentPage.currentDocument
            val cat = doc!!.bookCategory
            xmlVerse = if (cat == BookCategory.BIBLE || cat == BookCategory.COMMENTARY) {
                readOsisFragment(doc, key)
            } else {
                val bible = activeWindowPageManagerProvider.activeWindowPageManager.currentBible.currentDocument!!
                readOsisFragment(bible, key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting verse text", e)
        }
        return xmlVerse!!
    }
    /** double spaces, :, and leading or trailing space cause lucene errors
     */
    private fun cleanSearchString(search: String): String {
        // remove colons but leave Strong lookups
        // replace "strong:" with a place holder, remove ':', replace "strong:"
        var search = search
        search = search.replace(STRONG_COLON_STRING, STRONG_COLON_STRING_PLACE_HOLDER)
        search = search.replace(":", " ")
        search = search.replace(STRONG_COLON_STRING_PLACE_HOLDER, STRONG_COLON_STRING)
        return search.replace("  ", " ").trim { it <= ' ' }
    }

    /** get OT, NT, or all query limitation
     */
    private fun getBibleSectionTerm(bibleSection: SearchBibleSection, currentBookName: String?): String {
        var currentBookName: String? = currentBookName
        return when (bibleSection) {
            SearchBibleSection.ALL -> ""
            SearchBibleSection.OT -> SEARCH_OLD_TESTAMENT
            SearchBibleSection.NT -> SEARCH_NEW_TESTAMENT
            SearchBibleSection.CURRENT_BOOK -> {
                if (currentBookName == null) {
                    currentBookName = currentBookName
                }
                "+[$currentBookName]"
            }
            else -> {
                Log.e(TAG, "Unexpected radio selection")
                ""
            }
        }
    }

    /** download index
     *
     * @return true if managed to start download in background
     */
    fun createIndex(book: Book?): Boolean {
        var ok = false
        try {
            // this starts a new thread to do the indexing and returns immediately
            // if index creation is already in progress then nothing will happen
            swordDocumentFacade.ensureIndexCreation(book!!)
            ok = true
        } catch (e: Exception) {
            Log.e(TAG, "error indexing:" + e.message)
            e.printStackTrace()
        }
        return ok
    }

    /**
     * When navigating books and chapters there should always be a current Passage based book
     */
    private val currentPassageDocument: AbstractPassageBook
        get() = activeWindowPageManagerProvider.activeWindowPageManager.currentPassageDocument

    fun currentDocumentContainsNonScripture(): Boolean {
        return !documentBibleBooksFactory.getDocumentBibleBooksFor(currentPassageDocument).isOnlyScripture
    }

    val isCurrentlyShowingScripture: Boolean
        get() = isSearchShowingScripture || !currentDocumentContainsNonScripture()

    companion object {
        lateinit var originalSearchString: String
        private const val SEARCH_OLD_TESTAMENT = "+[Gen-Mal]"
        private const val SEARCH_NEW_TESTAMENT = "+[Mat-Rev]"
        const val SEARCH_TEXT = "SearchText"
        const val SEARCH_DOCUMENT = "SearchDocument"
        const val TARGET_DOCUMENT = "TargetDocument"
        private const val STRONG_COLON_STRING = LuceneIndex.FIELD_STRONG + ":"
        private const val STRONG_COLON_STRING_PLACE_HOLDER = LuceneIndex.FIELD_STRONG + "COLON"
        const val MAX_SEARCH_RESULTS = 1000
        private const val TAG = "SearchControl"
    }
}
