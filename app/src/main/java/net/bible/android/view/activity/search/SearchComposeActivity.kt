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

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchFormController
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.search.SearchScreen
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.search.SearchType as JSwordSearchType
import org.koin.android.ext.android.inject

/**
 * Compose host for the SWORD search form — the new-path twin of classic [Search]. Owns the
 * translation-persistence + history-restore glue, then routes the built [net.bible.sharedcore.search.SearchRequest]
 * to either [Screen.SearchIndex] (some translation lacks an index) or [Screen.SearchResults].
 */
class SearchComposeActivity : ActivityBase() {
    private val searchControl: SearchControl by inject()
    private val bibleSearchService: BibleSearchService by inject()
    @Suppress("unused")
    private val windowControl: WindowControl by inject()

    private val currentDocument get() = windowControl.activeWindowPageManager.currentPage.currentDocument

    private lateinit var controller: SearchFormController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Compose search view")
        CommonUtils.settings.setLong("search-last-used", System.currentTimeMillis())

        val currentDoc = currentDocument
        // Parity: no document to search → finish immediately.
        if (currentDoc == null) { finish(); return }

        // Default current-book name, overridable by the history-restore extra (returning via Back).
        val currentBookName = intent.getStringExtra(CURRENT_BIBLE_BOOK_SAVE) ?: searchControl.currentBookName

        controller = SearchFormController(
            currentBookName = currentBookName,
            persistTranslations = { saveSelectedTranslations(it) },
        )

        // Available translations: every Bible as id(initials)→abbreviation, sorted by abbreviation.
        val bibles = SwordDocumentFacade.bibles
            .filterIsInstance<SwordBook>()
            .sortedBy { it.abbreviation }
        controller.setAvailableTranslations(bibles.map { it.initials to it.abbreviation })

        // Selection: saved translations, else default to the current document — seeded (not set) so
        // loading does not re-persist.
        val saved = loadSelectedTranslations()
        controller.seedTranslations(saved.ifEmpty { listOf(currentDoc.initials) })

        // History-restore extras (parity with classic Search's onSearch-saved intent extras).
        intent.getStringExtra(SEARCH_TEXT_SAVE)?.takeIf { it.isNotEmpty() }?.let { controller.setQuery(it) }
        intent.getStringExtra(WORDS_SELECTION_SAVE)?.let { restoreSearchType(it)?.let(controller::setSearchType) }
        intent.getStringExtra(SECTION_SELECTION_SAVE)?.let { restoreBibleSection(it)?.let(controller::setBibleSection) }

        val title = getString(R.string.search_in, currentDoc.abbreviation)

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val query by controller.query.collectAsState()
                    val searchType by controller.searchType.collectAsState()
                    val bibleSection by controller.bibleSection.collectAsState()
                    val availableTranslations by controller.availableTranslations.collectAsState()
                    val selectedTranslationIds by controller.selectedTranslationIds.collectAsState()
                    SearchScreen(
                        title = title,
                        query = query,
                        searchType = searchType,
                        bibleSection = bibleSection,
                        availableTranslations = availableTranslations,
                        selectedTranslationIds = selectedTranslationIds,
                        onQueryChange = controller::setQuery,
                        onSearchType = controller::setSearchType,
                        onBibleSection = controller::setBibleSection,
                        onTranslations = controller::setTranslations,
                        onSubmit = ::onSubmit,
                        onNavigateUp = { finish() },
                    )
                }
            }
        }
    }

    private fun onSubmit() {
        val request = controller.buildRequest()
        if (request.query.isBlank()) return

        if (!bibleSearchService.validateIndex(request)) {
            // Redirect to the index screen for the first un-indexed translation (classic parity).
            val firstUnindexed = request.translationIds.firstOrNull {
                SwordDocumentFacade.getDocumentByInitials(it)?.indexStatus != IndexStatus.DONE
            }
            val intent = ScreenLauncher.intentFor(this, Screen.SearchIndex)
                .putExtra(SearchControl.SEARCH_DOCUMENT, firstUnindexed)
            startActivity(intent)
            finish()
            return
        }

        val decorated = bibleSearchService.decorate(request)
        // Section-less highlight query (mirrors classic Search.onSearch → SEARCH_HIGHLIGHT_TEXT).
        val highlightText = searchControl.highlightSearchString(request.query, request.searchType.toClassicJSwordSearchType())
        val currentDocInitials = currentDocument?.initials

        val intent = ScreenLauncher.intentFor(this, Screen.SearchResults).apply {
            putExtra(SearchControl.SEARCH_TEXT, decorated)
            putExtra(SearchControl.SEARCH_HIGHLIGHT_TEXT, highlightText)
            putExtra(SearchControl.SEARCH_DOCUMENT, currentDocInitials)
            putExtra(SearchControl.TARGET_DOCUMENT, currentDocInitials)
            putStringArrayListExtra(SearchControl.SELECTED_TRANSLATIONS, ArrayList(request.translationIds))
        }
        startActivity(intent)
        finish()
    }

    /** Replicates classic Search.saveSelectedTranslations: comma-joined initials to app settings. */
    private fun saveSelectedTranslations(ids: List<String>) {
        CommonUtils.settings.setString(SELECTED_TRANSLATIONS_KEY, ids.joinToString(","))
    }

    /** Replicates classic Search.loadSelectedTranslations: only initials that resolve to a Bible. */
    private fun loadSelectedTranslations(): List<String> {
        val saved = CommonUtils.settings.getString(SELECTED_TRANSLATIONS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        val available = SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().map { it.initials }.toSet()
        return saved.split(",").filter { it in available }
    }

    private fun restoreSearchType(v: String): SearchType? =
        SearchType.entries.firstOrNull { it.name == v }

    private fun restoreBibleSection(v: String): SearchBibleSection? =
        SearchBibleSection.entries.firstOrNull { it.name == v }

    /** :sharedcore SearchType → classic JSword SearchType (kept consistent with BibleSearchServiceImpl). */
    private fun SearchType.toClassicJSwordSearchType(): JSwordSearchType = when (this) {
        SearchType.ALL_WORDS -> JSwordSearchType.ALL_WORDS
        SearchType.ANY_WORDS -> JSwordSearchType.ANY_WORDS
        SearchType.PHRASE -> JSwordSearchType.PHRASE
    }

    companion object {
        private const val TAG = "SearchCompose"
        private const val SELECTED_TRANSLATIONS_KEY = "search_selected_translations"

        // History-restore extra keys (same wire names as classic Search).
        private const val SEARCH_TEXT_SAVE = "Search"
        private const val WORDS_SELECTION_SAVE = "Words"
        private const val SECTION_SELECTION_SAVE = "Selection"
        private const val CURRENT_BIBLE_BOOK_SAVE = "BibleBook"
    }
}
