/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.search

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsController
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.search.SearchResultsScreen
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.sword.SwordBook
import org.koin.android.ext.android.inject

/**
 * Compose host for SWORD multi-translation search results — the new-path twin of classic
 * [SearchResults]. It owns the JSword→portable resolution (view-data rows carry a display
 * reference string, not a `Key`): a selected reference is re-resolved to a `Key` host-side via
 * [org.crosswire.jsword.book.Book.getKey] before navigation.
 */
class SearchResultsComposeActivity : ActivityBase() {
    override val integrateWithHistoryManager: Boolean = true

    private val bibleSearchService: BibleSearchService by inject()
    private val linkControl: LinkControl by inject()
    private val windowControl: WindowControl by inject()
    @Suppress("unused")
    private val searchControl: SearchControl by inject()

    private var selectedTranslations: List<String> = emptyList()
    private var isStrongsSearch = false
    private var searchText = ""

    private val controller by lazy { SearchResultsController(bibleSearchService, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Compose search results view")

        selectedTranslations = intent.getStringArrayListExtra(SearchControl.SELECTED_TRANSLATIONS)
            ?: intent.getStringExtra(SearchControl.SEARCH_DOCUMENT)?.let { listOf(it) }
            ?: emptyList()

        // Strong's find-all (LinkControl.showAllOccurrences) sets this so the document selector only
        // offers Strong's-enabled Bibles and the choice persists under STRONGS_SEARCH_TRANSLATIONS_PREF.
        isStrongsSearch = intent.getBooleanExtra(SearchControl.IS_STRONGS_SEARCH, false)

        searchText = intent.getStringExtra(SearchControl.SEARCH_TEXT) ?: ""

        // Ref-detection parity with classic fetchSearchResults: a plain scripture reference bypasses
        // the search and opens the verse directly, popping this + the launcher off the history stack.
        if (linkControl.tryToOpenRef(searchText)) {
            historyTraversal.historyManager.popHistoryItem()
            historyTraversal.historyManager.popHistoryItem()
            finish()
            return
        }

        // The launcher already decorated the query into SEARCH_TEXT. The portable service re-decorates
        // request.query internally (BibleSearchServiceImpl.searchMulti → decorateSearchString), so we
        // use the IDENTITY decorators to avoid double-decoration: ANY_WORDS.decorate is a no-op and the
        // ALL section term is empty, so re-decoration reproduces the already-decorated SEARCH_TEXT
        // (leading space only, ignored by Lucene) — search results match the classic path exactly.
        controller.run(
            SearchRequest(
                query = searchText,
                searchType = SearchType.ANY_WORDS,
                bibleSection = SearchBibleSection.ALL,
                translationIds = selectedTranslations,
                currentBookName = "",
                isStrongsSearch = isStrongsSearch,
            )
        )

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val loading by controller.loading.collectAsState()
                    val results by controller.results.collectAsState()
                    val rows by controller.displayed.collectAsState()
                    val scriptureShown by controller.scriptureShown.collectAsState()
                    val scriptureToggleVisible by controller.scriptureToggleVisible.collectAsState()
                    val error by controller.error.collectAsState()
                    val selected by controller.selectedTranslations.collectAsState()
                    val candidates by controller.candidates.collectAsState()

                    // Classic showed a dialog then backed out on failure; the shared screen has no error
                    // slot, so mirror that behaviour host-side: toast + finish once.
                    LaunchedEffect(error) {
                        if (error != null) {
                            Toast.makeText(
                                this@SearchResultsComposeActivity,
                                R.string.error_executing_search,
                                Toast.LENGTH_SHORT,
                            ).show()
                            controller.dismissError()
                            finish()
                        }
                    }

                    // Title + selector chip are driven by the controller's live selection state, so a
                    // re-run after choosing new translations refreshes the results-count title for free.
                    val title = getString(R.string.multi_search_results, results.total, selected.size)
                    val selectedAbbreviations = selected
                        .mapNotNull { id -> candidates.firstOrNull { it.id == id }?.abbreviation }
                        .joinToString(", ")
                    SearchResultsScreen(
                        title = title,
                        loading = loading,
                        rows = rows,
                        scriptureToggleVisible = scriptureToggleVisible,
                        scriptureShown = scriptureShown,
                        onToggleScripture = controller::toggleScripture,
                        onOpenInWindow = ::openResultsInAWindow,
                        onSelect = ::onSelect,
                        onNavigateUp = { finish() },
                        selectedAbbreviations = selectedAbbreviations,
                        candidates = candidates,
                        selectedIds = selected,
                        onSelectTranslations = ::onSelectTranslations,
                    )
                }
            }
        }
    }

    /** Resolve the target book by initials, falling back to the first selected translation. */
    private fun resolveBook(translationId: String?): SwordBook? =
        (translationId?.let { SwordDocumentFacade.getDocumentByInitials(it) }
            ?: selectedTranslations.firstOrNull()?.let { SwordDocumentFacade.getDocumentByInitials(it) }) as? SwordBook

    /** Classic onTranslationPillClick: resolve the verse key, set it on the active window, open the Bible. */
    private fun onSelect(referenceName: String, translationId: String?) {
        val book = resolveBook(translationId) ?: return
        try {
            val key = book.getKey(referenceName)
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
            startActivity(Intent(this, MainBibleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        } catch (e: Exception) {
            Log.e(TAG, "Could not resolve key '$referenceName' in ${book.initials}", e)
        }
    }

    /**
     * Apply a new translation selection from the results document selector (classic
     * SearchResults.showDocumentSelector tail). The controller persists the choice and either
     * re-runs the search live or — when a chosen translation is unindexed — hands back the unindexed
     * ids so we route to [Screen.SearchIndex], carrying the full search context (SEARCH_TEXT +
     * SELECTED_TRANSLATIONS + IS_STRONGS_SEARCH + the doc to index) so the flow returns to results
     * and re-runs after indexing completes.
     */
    private fun onSelectTranslations(ids: List<String>) {
        controller.selectTranslations(ids) { unindexed, chosenIds ->
            startActivity(ScreenLauncher.intentFor(this, Screen.SearchIndex).apply {
                putExtra(SearchControl.SEARCH_DOCUMENT, unindexed.first())
                putExtra(SearchControl.SEARCH_TEXT, searchText)
                putExtra(SearchControl.IS_STRONGS_SEARCH, isStrongsSearch)
                putStringArrayListExtra(SearchControl.SELECTED_TRANSLATIONS, ArrayList(chosenIds))
            })
        }
        // Keep the resolveBook fallback in sync with the current selection (title/chip come from the
        // controller's StateFlow, so they update reactively).
        selectedTranslations = ids
    }

    /** Classic openResultsInAWindow: gather every displayed match into a multi-document link. */
    private fun openResultsInAWindow() {
        val list = BookAndKeyList()
        for (row in controller.displayed.value) {
            for (match in row.matches) {
                val book = resolveBook(match.translationId) ?: continue
                val key = try {
                    book.getKey(row.referenceName)
                } catch (e: Exception) {
                    Log.e(TAG, "openResultsInAWindow: bad key '${row.referenceName}' in ${book.initials}", e)
                    continue
                }
                list.addAll(BookAndKey(key, book))
            }
        }
        linkControl.showLink(FakeBookFactory.multiDocument, list)
        finish()
    }

    companion object {
        private const val TAG = "SearchResultsCompose"
    }
}
