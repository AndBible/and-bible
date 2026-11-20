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

import net.bible.sharedcore.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.RegexSearchBudget
import org.crosswire.jsword.index.RegexSearchResult
import org.crosswire.jsword.index.RegexSearchableIndex
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.Scripture
import net.bible.service.sword.SwordContentFacade.Companion.search
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.fts.SearchFields
import org.crosswire.jsword.index.search.SearchType
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Verse

/** Data classes for multi-translation search results */
data class TranslationMatch(
    val book: SwordBook,
    val key: Key  // Original key in this translation's versification
)

data class GroupedSearchResult(
    val normalizedVerse: Verse,  // Used for grouping and sorting
    val translationMatches: List<TranslationMatch>
) {
    val displayName: String get() = normalizedVerse.getName()
}

class MultiSearchResultsDto {
    val mainSearchResults = mutableListOf<GroupedSearchResult>()
    val otherSearchResults = mutableListOf<GroupedSearchResult>()
    /** A regex scan ran out of its time budget before it had seen every verse. */
    var truncatedByTime: Boolean = false
    val size: Int get() = mainSearchResults.size + otherSearchResults.size
}

/** Support for the document search functionality
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
/** A closed `[...]` or `{...}` group: a term range, or a passage restriction. */
// Every bracket escaped: Android's ICU regex rejects a bare `}` or `]` that the JVM accepts. Robolectric and JVM
// unit tests run java.util.regex, so they cannot catch ICU syntax errors: only a device/emulator run does.
// (Audit 2026-11-14: no other regex in sharedLogic/sharedCore/sharedUi/app uses a bare `}` or `]` outside a set.)
private val bracketGroupRe = Regex("""\[[^\[\]\{\}]*\]|\{[^\[\]\{\}]*\}""")

/** Stands in for a space inside a bracket group while the words are decorated; never typed in a query. */
private const val GROUP_SPACE = '\u0000'

/**
 * [type]'s decoration of [text] with each closed `[...]`/`{...}` group kept as one word (#4093). The decorators
 * split at spaces, so "All words" would turn `[grace TO hope]` into `+[grace +TO +hope]`: no longer a range, the
 * query then failed as an invalid passage or a parse error and found nothing, instead of reporting the
 * unsupported syntax.
 */
internal fun decorateSearchWords(text: String, type: SearchType): String {
    val protected = bracketGroupRe.replace(text) { it.value.replace(' ', GROUP_SPACE) }
    return type.decorate(protected).replace(GROUP_SPACE, ' ')
}

class SearchControl constructor(
    private val documentBibleBooksFactory: DocumentBibleBooksFactory,
    private val windowControl: WindowControl,
    private val appScope: net.bible.sharedcore.platform.AppCoroutineScope,
    )
{
    private val isSearchShowingScripture = true

    enum class SearchBibleSection {
        OT, NT, CURRENT_BOOK, ALL
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
            if (!longName.isNullOrBlank() && longName.length < 14) {
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
        var decorated: String = decorateSearchWords(cleanSearchString, searchType)

        // add bible section limitation to search text
        decorated = getBibleSectionTerm(bibleSection, currentBookName) + " " + decorated
        return decorated
    }

    /**
     * The searchType-decorated query WITHOUT the Bible-section term (e.g. `+[Gen-Mal]`). This is the
     * string used purely to highlight matched words in result previews — it replaces the removed global
     * mutable `originalSearchString` side-channel that the classic result adapter used to read. Callers
     * pass this value explicitly (via intent extra to the classic path, directly to the Compose service).
     * The section term is intentionally excluded because it would otherwise be split into stray
     * single-character regex matches by the highlighter.
     */
    fun highlightSearchString(searchString: String, searchType: SearchType): String =
        decorateSearchWords(cleanSearchString(searchString), searchType)

    /** Search translations and group results by verse
     */
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
                val count = minOf(result.getCardinality(), MAX_SEARCH_RESULTS + 1)
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
                // An unsupported query is the user's to fix: surface it instead of an empty result.
                if (unsupportedSyntaxIn(e) != null) throw e
                Log.e(TAG, "Error searching ${book.initials}: ${e.message}")
            }
        }

        groupAndSort(groupedResults, searchResults)

        Log.i(TAG, "Multi-search found ${searchResults.size} unique verses")
        searchResults
    }

    /**
     * Regex search over the stored verse texts of the Bibles among [documentInitials], grouped like
     * [getMultiSearchResults]. Other documents (commentaries, dictionaries, ...) are skipped: regex search is for Bible
     * texts. One budget is shared by every translation, so the whole search stops at [budgetDuration] (or when the
     * calling coroutine is cancelled); [MultiSearchResultsDto.truncatedByTime] then says the list is partial.
     *
     * Each translation is scanned on an abandonable worker ([scanWithinBudget]): a pattern the regex engine cannot be
     * interrupted in (Android's ICU engine) ends the search [REGEX_ABANDON_GRACE] after the budget, as truncated, with
     * the results of the translations scanned before it. A translation whose index fails makes the result partial, and
     * when all of them fail the [BookException] is thrown (never an empty list). [bookLookup] is injectable for tests.
     */
    suspend fun getMultiRegexResults(
        documentInitials: List<String>,
        regex: Regex,
        bibleSection: SearchBibleSection,
        currentBookName: String?,
        budgetDuration: Duration = REGEX_SEARCH_BUDGET,
        bookLookup: (String) -> Book? = { SwordDocumentFacade.getDocumentByInitials(it) },
    ): MultiSearchResultsDto = withContext(Dispatchers.IO) {
        val searchResults = MultiSearchResultsDto()
        val books = documentInitials.mapNotNull { initials ->
            (bookLookup(initials) as? SwordBook)?.takeIf { it.indexStatus == IndexStatus.DONE && it.bookCategory == BookCategory.BIBLE }
        }
        val outcome = scanWithinBudget(books, budgetDuration) { book, budget -> regexScanOf(book, regex, bibleSection, currentBookName, budget) }
        val failures = outcome.results.mapNotNull { (_, scan) -> scan.error }
        // Every translation that took part failed: that is an error, not an empty result.
        if (!outcome.timedOut && failures.isNotEmpty() && outcome.results.all { (_, scan) -> scan.error != null }) throw failures.first()
        // A translation that failed while others succeeded makes the list partial, which the notice says.
        searchResults.truncatedByTime = outcome.timedOut || failures.isNotEmpty() ||
            outcome.results.any { (_, scan) -> scan.result?.truncated == true }

        val groupedResults = mutableMapOf<String, MutableList<TranslationMatch>>()
        for ((book, scan) in outcome.results) {
            val result = scan.result ?: continue
            val count = minOf(result.keys.getCardinality(), MAX_SEARCH_RESULTS + 1)
            val keyIterator = result.keys.iterator()
            for (i in 0 until count) {
                val key = keyIterator.next()
                if (key is Verse) {
                    val normalizedKey = "${key.book.ordinal}:${key.chapter}:${key.verse}"
                    groupedResults.getOrPut(normalizedKey) { mutableListOf() }.add(TranslationMatch(book, key))
                }
            }
        }
        groupAndSort(groupedResults, searchResults)
        Log.i(TAG, "Regex search found ${searchResults.size} unique verses, truncatedByTime=${searchResults.truncatedByTime}")
        searchResults
    }

    /** The outcome of one translation's regex scan: [result] is null when the translation is skipped; [error] when its index failed. */
    private class BookScan(val result: RegexSearchResult?, val error: Exception? = null)

    /**
     * One translation's regex scan. A translation that cannot take part (no regex index, the section does not exist in
     * it) is skipped with a null result. An index that fails is not hidden as "nothing found": the scan carries the
     * [BookScan.error] for [getMultiRegexResults] to report.
     */
    private fun regexScanOf(
        book: SwordBook, regex: Regex, bibleSection: SearchBibleSection, currentBookName: String?, budget: RegexSearchBudget,
    ): BookScan = try {
        val index = IndexManagerFactory.getIndexManager().getIndex(book) as? RegexSearchableIndex
        if (index == null) BookScan(null) else {
            val restriction = when (bibleSection) {
                SearchBibleSection.ALL -> null
                SearchBibleSection.OT -> book.getKey(SEARCH_OT_RANGE)
                SearchBibleSection.NT -> book.getKey(SEARCH_NT_RANGE)
                SearchBibleSection.CURRENT_BOOK -> {
                    if (currentBookName.isNullOrBlank()) throw NoSuchKeyException("No current book")
                    book.getKey(currentBookName)
                }
            }
            BookScan(index.findRegex(regex, restriction, MAX_SEARCH_RESULTS + 1, budget))
        }
    } catch (e: NoSuchKeyException) {
        // The section does not exist in this book's versification (or the book name is unknown): skip it.
        Log.e(TAG, "No such section in ${book.initials}: ${e.message}")
        BookScan(null)
    } catch (e: BookException) {
        Log.e(TAG, "Error regex-searching ${book.initials}: ${e.message}", e)
        BookScan(null, e)
    }

    /** Splits the per-verse matches into Scripture and other rows and sorts both in canonical order. */
    private fun groupAndSort(
        groupedResults: Map<String, List<TranslationMatch>>,
        searchResults: MultiSearchResultsDto,
    ) {
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
            SwordDocumentFacade.ensureIndexCreation(book!!, appScope)
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
        private const val SEARCH_OLD_TESTAMENT = "+[Gen-Mal]"
        private const val SEARCH_NEW_TESTAMENT = "+[Mat-Rev]"
        private const val SEARCH_OT_RANGE = "Gen-Mal"
        private const val SEARCH_NT_RANGE = "Mat-Rev"
        /** Wall-clock limit of one regex search over all selected translations. */
        val REGEX_SEARCH_BUDGET: Duration = 3.seconds
        const val SEARCH_TEXT = "SearchText"
        /** Section-less decorated query passed to the classic result adapter for highlighting. */
        const val SEARCH_HIGHLIGHT_TEXT = "SearchHighlightText"
        const val SEARCH_DOCUMENT = "SearchDocument"
        const val SELECTED_TRANSLATIONS = "SelectedTranslations"
        const val IS_STRONGS_SEARCH = "IsStrongsSearch"
        // Settings keys (not intent extras): remembered document selection for the search-results
        // selector. Normal Bible searches share the manual search screen's key; Strong's
        // find-all-occurrences uses its own key (its selection is a Strong's-enabled subset).
        const val SEARCH_TRANSLATIONS_PREF = "search_selected_translations"
        const val STRONGS_SEARCH_TRANSLATIONS_PREF = "search_results_strongs_translations"
        private const val STRONG_COLON_STRING = SearchFields.STRONG + ":"
        private const val STRONG_COLON_STRING_PLACE_HOLDER = SearchFields.STRONG + "COLON"
        const val MAX_SEARCH_RESULTS = 5000
        private const val TAG = "SearchControl"
    }
}
