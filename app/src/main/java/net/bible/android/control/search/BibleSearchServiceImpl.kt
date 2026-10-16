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

import android.content.Context
import net.bible.sharedcore.log.Log
import net.bible.sharedui.strings.AndroidStrings
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.BookException
import kotlinx.coroutines.Dispatchers
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
import net.bible.sharedcore.search.prepareSearchTerms
import net.bible.sharedcore.search.splitSearchTerms
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
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
    private val context: Context,
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

    override suspend fun searchMulti(request: SearchRequest): MultiSearchResults = withContext(Dispatchers.IO) {
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
            throw SearchSyntaxException(AndroidStrings(context).searchUnsupportedSyntax(unsupported.syntax), e)
        }
        val main = dto.mainSearchResults.map { it.toRow(terms, lemmaTerms) }
        val other = dto.otherSearchResults.map { it.toRow(terms, lemmaTerms) }
        MultiSearchResults(main = main, other = other, total = dto.size)
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

    /** DONE status and an index the current engine can open (an index of an older schema needs a rebuild). */
    private fun isUsable(docId: String): Boolean {
        val book = SwordDocumentFacade.getDocumentByInitials(docId) ?: return false
        if (book.indexStatus != IndexStatus.DONE) return false
        return book !is SwordBook || book.isEpub || hasUsableSearchIndex(book)
    }

    private fun GroupedSearchResult.toRow(terms: List<String>, lemmaTerms: List<String>): SwordResultRow {
        val matches = translationMatches.map { tm ->
            TranslationMatchVd(
                translationId = tm.book.initials,
                abbreviation = tm.book.abbreviation,
                preview = previewFor(tm.book, tm.key, terms, lemmaTerms),
            )
        }
        return SwordResultRow(
            referenceName = displayName,
            matches = matches,
            primaryPreview = matches.firstOrNull()?.preview ?: StyledText.plain(""),
        )
    }

    private fun previewFor(book: SwordBook, key: Key, terms: List<String>, lemmaTerms: List<String>): StyledText =
        try {
            highlighter.highlight(SwordContentFacade.readOsisFragment(book, key, strings = strings), terms, lemmaTerms)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading verse preview", e)
            StyledText.plain("")
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
    }
}
