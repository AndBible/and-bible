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
package net.bible.android.control.search

import android.app.Activity
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.ApplicationScope
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.Scripture
import net.bible.android.view.activity.search.EpubSearch
import net.bible.android.view.activity.search.Search
import net.bible.android.view.activity.search.SearchIndex
import net.bible.service.sword.SwordContentFacade.search
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.isEpub
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
import javax.inject.Inject

/** Data classes for multi-translation search results */
data class TranslationMatch(
    val book: SwordBook,
    val key: Key  // Original key in this translation's versification
)

data class GroupedSearchResult(
    val normalizedVerse: Verse,  // Used for grouping and sorting
    val translationMatches: List<TranslationMatch>
) {
    val displayName: String get() = normalizedVerse.name
}

class MultiSearchResultsDto {
    val mainSearchResults = mutableListOf<GroupedSearchResult>()
    val otherSearchResults = mutableListOf<GroupedSearchResult>()
    val size: Int get() = mainSearchResults.size + otherSearchResults.size
}

/** Support for the document search functionality
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class SearchControl @Inject constructor(
    private val documentBibleBooksFactory: DocumentBibleBooksFactory,
    private val windowControl: WindowControl,
    )
{
    private val isSearchShowingScripture = true

    enum class SearchBibleSection {
        OT, NT, CURRENT_BOOK, ALL
    }

    /** if current document is indexed then go to search else go to download index page
     *
     * @return required Intent
     */
    fun getSearchIntent(document: Book?, activity: Activity): Intent? {
        val indexStatus = document?.indexStatus
        Log.i(TAG, "Index status:$indexStatus")
        return if (indexStatus == IndexStatus.DONE) {
            Log.i(TAG, "Index status is DONE")
            if(document.isEpub) {
                Intent(activity, EpubSearch::class.java)
            } else
                Intent(activity, Search::class.java)
        } else if (document?.bookCategory == BookCategory.GENERAL_BOOK) {
            return null
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
            val currentBiblePage = windowControl.activeWindowPageManager.currentBible
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

        // add search type (all/any/phrase) to search string
        var decorated: String = searchType.decorate(cleanSearchString)
        originalSearchString = decorated

        // add bible section limitation to search text
        decorated = getBibleSectionTerm(bibleSection, currentBookName) + " " + decorated
        return decorated
    }

    /** Search translations and group results by verse
     */
    @Throws(BookException::class)
    suspend fun getMultiSearchResults(
        documentInitials: List<String>,
        searchText: String?
    ): MultiSearchResultsDto = withContext(Dispatchers.IO) {
        Log.i(TAG, "Preparing multi-translation search results for ${documentInitials.size} translations")
        val searchResults = MultiSearchResultsDto()

        if (searchText.isNullOrBlank()) {
            return@withContext searchResults
        }

        // Map: normalized verse key -> list of matches
        val groupedResults = mutableMapOf<String, MutableList<TranslationMatch>>()

        for (initials in documentInitials) {
            val book = SwordDocumentFacade.getDocumentByInitials(initials) as? SwordBook
                ?: continue
            if (book.indexStatus != IndexStatus.DONE) continue

            try {
                val result = search(book, searchText)
                val count = minOf(result.cardinality, MAX_SEARCH_RESULTS + 1)
                val keyIterator = result.iterator()

                for (i in 0 until count) {
                    val key = keyIterator.next()
                    if (key is Verse) {
                        // Use book ordinal, chapter, verse as grouping key
                        val normalizedKey = "${key.book.ordinal}:${key.chapter}:${key.verse}"
                        groupedResults.getOrPut(normalizedKey) { mutableListOf() }
                            .add(TranslationMatch(book, key))
                    }
                }
            } catch (e: BookException) {
                Log.e(TAG, "Error searching ${book.initials}: ${e.message}")
            }
        }

        // Convert to list and sort
        for ((_, matches) in groupedResults) {
            val firstMatch = matches.first()
            val verse = firstMatch.key as Verse
            val isMain = Scripture.isScripture(verse.book)
            val grouped = GroupedSearchResult(verse, matches)

            if (isMain) searchResults.mainSearchResults.add(grouped)
            else searchResults.otherSearchResults.add(grouped)
        }


        searchResults.mainSearchResults.sortWith(compareBy(
            { it.normalizedVerse.book.ordinal },
            { it.normalizedVerse.chapter },
            { it.normalizedVerse.verse }
        ))
        searchResults.otherSearchResults.sortWith(compareBy(
            { it.normalizedVerse.book.ordinal },
            { it.normalizedVerse.chapter },
            { it.normalizedVerse.verse }
        ))

        Log.i(TAG, "Multi-search found ${searchResults.size} unique verses")
        searchResults
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
            SwordDocumentFacade.ensureIndexCreation(book!!)
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
        get() = windowControl.activeWindowPageManager.currentPassageDocument

    fun currentDocumentContainsNonScripture(): Boolean {
        return !documentBibleBooksFactory.getDocumentBibleBooksFor(currentPassageDocument).isOnlyScripture
    }

    val isCurrentlyShowingScripture: Boolean
        get() = isSearchShowingScripture || !currentDocumentContainsNonScripture()

    companion object {
        var originalSearchString: String? = null
        private const val SEARCH_OLD_TESTAMENT = "+[Gen-Mal]"
        private const val SEARCH_NEW_TESTAMENT = "+[Mat-Rev]"
        const val SEARCH_TEXT = "SearchText"
        const val SEARCH_DOCUMENT = "SearchDocument"
        const val SELECTED_TRANSLATIONS = "SelectedTranslations"
        const val IS_STRONGS_SEARCH = "IsStrongsSearch"
        // Settings keys (not intent extras): remembered document selection for the search-results
        // selector. Normal Bible searches share the manual search screen's key; Strong's
        // find-all-occurrences uses its own key (its selection is a Strong's-enabled subset).
        const val SEARCH_TRANSLATIONS_PREF = "search_selected_translations"
        const val STRONGS_SEARCH_TRANSLATIONS_PREF = "search_results_strongs_translations"
        private const val STRONG_COLON_STRING = LuceneIndex.FIELD_STRONG + ":"
        private const val STRONG_COLON_STRING_PLACE_HOLDER = LuceneIndex.FIELD_STRONG + "COLON"
        const val MAX_SEARCH_RESULTS = 5000
        private const val TAG = "SearchControl"
    }
}
