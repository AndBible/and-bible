/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.BookException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.bible.sharedcore.platform.AppSettings
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.search.BibleOption
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.MultiSearchResults
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedcore.search.TranslationMatchVd
import net.bible.sharedcore.search.SEARCH_EXCERPT_LENGTH
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookData
import fi.sykero.kdom.Element
import net.bible.sharedlogic.sync.withMonitor
import net.bible.sharedcore.search.prepareSearchTerms
import net.bible.sharedcore.search.splitSearchTerms
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.bible.sharedcore.search.SearchType as SharedSearchType
import net.bible.sharedcore.search.SearchBibleSection as SharedSearchBibleSection
import org.crosswire.jsword.index.search.SearchType as JSwordSearchType

/** A search the user must change: carries the localized message the shared controller shows. */
class SearchSyntaxException(message: String, cause: Throwable) : Exception(message, cause)

/**
 * Host (`:app`) implementation of the portable [BibleSearchService] seam. It wraps the existing
 * [SearchControl] JSword logic and, for each grouped result, reads the OSIS fragment and turns the
 * Strong's/term highlighting into portable [StyledText] via [SearchTextHighlighter] — so the shared
 * Compose search UI needs no Android `Spannable` or JSword types.
 */
class BibleSearchServiceImpl(
    private val searchControl: SearchControl,
    private val highlighter: SearchTextHighlighter,
    private val settings: AppSettings,
    private val strings: net.bible.sharedcore.platform.CoreStrings,
) : BibleSearchService {

    override fun validateIndex(request: SearchRequest): Boolean =
        request.translationIds.all {
            isUsable(it)
        }

    override fun decorate(request: SearchRequest): String =
        searchControl.decorateSearchString(
            request.query,
            request.searchType.toClassic(),
            request.bibleSection.toClassic(),
            request.currentBookName,
        )

    override suspend fun searchMulti(request: SearchRequest): MultiSearchResults =
        searchMulti(request, SearchControl.REGEX_SEARCH_BUDGET)

    /** [regexBudget] is injectable for tests; production uses [SearchControl.REGEX_SEARCH_BUDGET]. */
    suspend fun searchMulti(request: SearchRequest, regexBudget: Duration): MultiSearchResults = withContext(Dispatchers.IO) {
        // Result previews read the hit books: a restored results route must not read an enciphered book locked
        // (an empty/garbage preview, cached by readOsisFragment and by the results controller).
        net.bible.service.startup.ReadingAppInit.awaitCipherUnlock()
        if (request.isRegex) return@withContext searchRegex(request, regexBudget)
        // Section-decorated query drives the Lucene search (unchanged from the classic path); the
        // section-less variant supplies the words/lemmas that are actually highlighted in previews.
        val decorated = decorate(request)
        val highlightString = searchControl.highlightSearchString(request.query, request.searchType.toClassic())

        val prepared = prepareSearchTerms(highlightString)
        val isStrongs = prepared.contains("strong:", ignoreCase = true)
        val terms = if (isStrongs) emptyList() else splitSearchTerms(prepared)
        val lemmaTerms = if (isStrongs) listOf(prepared) else emptyList()

        // Reuse the verbatim JSword grouping/partition/cap logic (SwordContentFacade.search,
        // Scripture.isScripture, MAX_SEARCH_RESULTS) then decorate each row with StyledText.
        val dto = try {
            searchControl.getMultiSearchResults(request.translationIds, decorated)
        } catch (e: BookException) {
            // The shared controller shows the message of whatever is thrown. Other failures (a query that does
            // not parse, an index that cannot be opened) keep their JSword message.
            val unsupported = unsupportedSyntaxIn(e) ?: throw e
            throw SearchSyntaxException(strings.searchUnsupportedSyntax(unsupported.syntax), e)
        }
        val previews = PreviewReader(terms, lemmaTerms)
        val main = dto.mainSearchResults.map { it.toRow(previews) }
        val other = dto.otherSearchResults.map { it.toRow(previews) }
        MultiSearchResults(main = main, other = other, total = dto.size)
    }

    /**
     * The regex path: the query reaches [Regex] exactly as typed (no Lucene decoration, so `:` and `[...]` are
     * regex syntax), case-insensitive like the other searches.
     */
    private suspend fun searchRegex(request: SearchRequest, budget: Duration): MultiSearchResults {
        if (request.query.isBlank()) return MultiSearchResults.EMPTY
        val regex = try {
            Regex(request.query, RegexOption.IGNORE_CASE)
        } catch (e: IllegalArgumentException) {
            throw SearchSyntaxException(strings.searchInvalidRegex(e.message ?: ""), e)
        }
        val dto = searchControl.getMultiRegexResults(
            request.translationIds, regex, request.bibleSection.toClassic(), request.currentBookName, budget,
        )
        val ctx = currentCoroutineContext()
        ctx.ensureActive()
        val previews = PreviewReader(emptyList(), emptyList())
        val main = dto.mainSearchResults.map { ctx.ensureActive(); it.toRow(previews) }
        val other = dto.otherSearchResults.map { ctx.ensureActive(); it.toRow(previews) }
        // One abandonable pass with its own allowance: a pattern the engine cannot be interrupted in leaves the rest of
        // the rows unhighlighted instead of holding the search.
        val rows = main + other
        val highlighted = highlightWithinAllowance(rows.flatMap { row -> row.matches.map { it.preview } }, REGEX_HIGHLIGHT_ALLOWANCE) { text, b ->
            highlightRegexMatches(text, regex, b)
        }.iterator()
        val withHighlights = rows.map { row ->
            val matches = row.matches.map { it.copy(preview = highlighted.next()) }
            row.copy(matches = matches, primaryPreview = matches.firstOrNull()?.preview ?: row.primaryPreview)
        }
        return MultiSearchResults(
            main = withHighlights.take(main.size), other = withHighlights.drop(main.size),
            total = dto.size, truncatedByTime = dto.truncatedByTime,
        )
    }

    override fun containsNonScripture(): Boolean = searchControl.currentDocumentContainsNonScripture()

    override fun isCurrentlyShowingScripture(): Boolean = searchControl.isCurrentlyShowingScripture

    override fun candidateBibles(): List<BibleOption> =
        // Parity with classic SearchResults.allBibles(): only SwordBooks, sorted by abbreviation.
        SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().sortedBy { it.abbreviation }.map {
            BibleOption(it.initials, it.abbreviation, it.hasFeature(FeatureType.STRONGS_NUMBERS))
        }

    override fun persistSelection(translationIds: List<String>, strongsSearch: Boolean) {
        // Mirror classic SearchResults.persistSelection: a comma-joined string under the same key
        // that Search.kt / LinkControl.kt read back via getString(...)?.split(","). Do NOT use
        // setStringSet (JSON-encoded) — those readers would then split a JSON array into garbage.
        val key = if (strongsSearch) {
            SearchControl.STRONGS_SEARCH_TRANSLATIONS_PREF
        } else {
            SearchControl.SEARCH_TRANSLATIONS_PREF
        }
        settings.setString(key, translationIds.joinToString(","))
    }

    override fun unindexedAmong(translationIds: List<String>): List<String> =
        translationIds.filter {
            !isUsable(it)
        }

    override fun unindexedStrongsAmong(translationIds: List<String>): List<String> = translationIds.filter { id ->
        val book = SwordDocumentFacade.getDocumentByInitials(id)
        if (book == null || !isUsable(id)) true else try {
            !(book.find("+[Gen 1:1] strong:h7225")!!.getCardinality() > 0 ||
                book.find("+[John 1:1] strong:g746")!!.getCardinality() > 0 ||
                book.find("+[Gen 1:1] strong:g746")!!.getCardinality() > 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Strong's index", e)
            true
        }
    }

    /** DONE status and an index the current engine can open (an index of an older schema needs a rebuild). */
    private fun isUsable(docId: String): Boolean {
        val book = SwordDocumentFacade.getDocumentByInitials(docId) ?: return false
        if (book.indexStatus != IndexStatus.DONE) return false
        return book !is SwordBook || book.isEpub || hasUsableSearchIndex(book)
    }

    private fun GroupedSearchResult.toRow(previews: PreviewReader): SwordResultRow {
        val matches = translationMatches.map { tm ->
            TranslationMatchVd(
                translationId = tm.book.initials,
                abbreviation = tm.book.abbreviation,
                preview = previews.previewFor(tm.book, tm.key),
            )
        }
        return SwordResultRow(
            referenceName = displayName,
            matches = matches,
            primaryPreview = matches.firstOrNull()?.preview ?: StyledText.plain(""),
        )
    }

    /**
     * The previews of one search. A Bible verse is shown whole. A hit in any other document is cut to an excerpt
     * around the match. A commentary hit is also read once per entry: a commentary entry is linked to every verse it
     * covers, and each of those verses is a hit with the same, often very long, text (#4092: Matthew Henry, "vine":
     * 949 hits, 95 entries, 19.6 MB of text).
     */
    private inner class PreviewReader(private val terms: List<String>, private val lemmaTerms: List<String>) {
        /** Keyed by a digest of the entry text, not the text itself: the keys of 95 entries would hold ~20 MB. */
        private val byEntry = HashMap<Triple<String, Int, Long>, StyledText>()

        fun previewFor(book: SwordBook, key: Key): StyledText = when (book.bookCategory) {
            BookCategory.BIBLE -> highlighted { SwordContentFacade.readOsisFragment(book, key, strings = strings) to null }
            BookCategory.COMMENTARY -> commentaryPreview(book, key)
            // Dictionaries, general books, EPUB: their entries have their own installed/key checks in readOsisFragment,
            // so they keep that path (the fast read below is verified for commentaries only), with an excerpt.
            else -> highlighted { SwordContentFacade.readOsisFragment(book, key, strings = strings) to SEARCH_EXCERPT_LENGTH }
        }

        private fun commentaryPreview(book: SwordBook, key: Key): StyledText {
            // Not readOsisFragment: its reading-view preparation (sentence anchors over the whole entry) took ~85 %
            // of the time of a commentary preview, and its cache would fill with entries nobody opened.
            // The text the index was built from: the whole entry without generated titles (the reference heading),
            // not only its <verse> element, because the "preverse" text before it (an introduction) is indexed with
            // it and may hold the match. Wrapped, the fragment has no direct <verse> child, so all of it is read.
            val read = {
                val fragment = withMonitor(book) { BookData(book, key).getOsisFragment(false) }
                Element("div").addContent(fragment) to SEARCH_EXCERPT_LENGTH
            }
            val raw = try {
                book.getRawText(key)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading raw entry for preview", e)
                return highlighted(read)
            }
            return byEntry.getOrPut(Triple(book.initials, raw.length, entryDigest(raw))) { highlighted(read) }
        }

        private fun highlighted(read: () -> Pair<Element, Int?>): StyledText =
            try {
                val (fragment, excerptLength) = read()
                highlighter.highlight(fragment, terms, lemmaTerms, excerptLength)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading verse preview", e)
                StyledText.plain("")
            }
    }

    private fun SharedSearchType.toClassic(): JSwordSearchType = when (this) {
        SharedSearchType.ALL_WORDS -> JSwordSearchType.ALL_WORDS
        SharedSearchType.ANY_WORDS -> JSwordSearchType.ANY_WORDS
        SharedSearchType.PHRASE -> JSwordSearchType.PHRASE
    }

    private fun SharedSearchBibleSection.toClassic(): SearchControl.SearchBibleSection = when (this) {
        SharedSearchBibleSection.ALL -> SearchControl.SearchBibleSection.ALL
        SharedSearchBibleSection.OLD_TESTAMENT -> SearchControl.SearchBibleSection.OT
        SharedSearchBibleSection.NEW_TESTAMENT -> SearchControl.SearchBibleSection.NT
        SharedSearchBibleSection.CURRENT_BOOK -> SearchControl.SearchBibleSection.CURRENT_BOOK
    }

    companion object {
        private const val TAG = "BibleSearchService"
        /** Wall-clock limit of highlighting the matches in the previews of one regex search. */
        val REGEX_HIGHLIGHT_ALLOWANCE: Duration = 1.seconds
    }
}

/**
 * A 64-bit FNV-1a digest of an entry's text, to recognise the same entry reached through different verses without
 * keeping the (possibly 200 KB) text itself as a key.
 */
internal fun entryDigest(text: String): Long {
    var hash = -3750763034362895579L // FNV offset basis
    for (c in text) {
        hash = (hash xor c.code.toLong()) * 1099511628211L
    }
    return hash
}
