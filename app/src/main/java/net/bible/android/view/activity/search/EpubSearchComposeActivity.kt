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

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.activity.R
import net.bible.android.control.page.PageControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.search.EpubSearchFormController
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.search.EpubSearchScreen
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.index.search.SearchType
import org.koin.android.ext.android.inject

/**
 * Compose host for the EPUB (general-book) search form — the new-path twin of classic [EpubSearch].
 * Resolves the document from the current page, persists/seeds the word-mode via the classic settings
 * key "epubSearch-SearchType" (stored as the JSword [SearchType] name, FTS ↔ absent), then routes the
 * submitted query to [Screen.EpubSearchResults] using the classic ad-hoc extras VERBATIM.
 */
class EpubSearchComposeActivity : ActivityBase() {
    override val integrateWithHistoryManager: Boolean = true

    private val pageControl: PageControl by inject()

    private val documentToSearch: Book
        get() = pageControl.currentPageManager.currentPage.currentDocument!!

    private lateinit var controller: EpubSearchFormController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Compose EPUB search view")
        CommonUtils.settings.setLong("search-last-used", System.currentTimeMillis())

        val doc = documentToSearch
        val title = getString(R.string.search_in, doc.abbreviation)

        controller = EpubSearchFormController(
            loadMode = ::loadMode,
            saveMode = ::saveMode,
            onSubmit = ::onSubmit,
        )
        // Seed from the persisted classic setting without re-persisting (parity with Plan A seeds).
        controller.seedMode(loadMode())

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val query by controller.query.collectAsState()
                    val mode by controller.mode.collectAsState()
                    EpubSearchScreen(
                        title = title,
                        query = query,
                        mode = mode,
                        onQueryChange = controller::setQuery,
                        onMode = controller::setMode,
                        onSubmit = controller::submit,
                        onHelp = ::help,
                        onNavigateUp = { finish() },
                    )
                }
            }
        }
    }

    /**
     * Read the persisted word-mode from the classic settings key. The value is a JSword [SearchType]
     * name (classic stored `searchType?.name`); absent → FTS (classic's `ftsQuery` radio), unknown → FTS.
     */
    private fun loadMode(): EpubSearchMode {
        val name = CommonUtils.settings.getString("epubSearch-SearchType") ?: return EpubSearchMode.FTS
        val jsword = try {
            SearchType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            return EpubSearchMode.FTS
        }
        return when (jsword) {
            SearchType.ALL_WORDS -> EpubSearchMode.ALL_WORDS
            SearchType.ANY_WORDS -> EpubSearchMode.ANY_WORD
            SearchType.PHRASE -> EpubSearchMode.PHRASE
            else -> EpubSearchMode.FTS
        }
    }

    /**
     * Persist the word-mode in the classic settings format: the JSword [SearchType] name, or null for
     * FTS (classic wrote `searchType?.name`, ftsQuery → null). Keeps classic/Compose interoperable.
     */
    private fun saveMode(mode: EpubSearchMode) {
        CommonUtils.settings.setString("epubSearch-SearchType", mode.toClassicSearchTypeName())
    }

    private fun onSubmit(query: String, mode: EpubSearchMode) {
        val intent = ScreenLauncher.intentFor(this, Screen.EpubSearchResults).apply {
            putExtra("searchText", query)
            // CRITICAL: the JSword SearchType name (NOT the EpubSearchMode name); null (FTS) mirrors
            // classic's `searchType?.name` so the results host resolves FTS via an absent extra.
            putExtra("searchType", mode.toClassicSearchTypeName())
            putExtra("searchDocument", documentToSearch.initials)
        }
        startActivity(intent)
        finish()
    }

    /** Port of classic [EpubSearch.help]: the FTS5 query-syntax help dialog with a live link. */
    private fun help() {
        val ftsLink = "https://www.sqlite.org/fts5.html#full_text_query_syntax"
        val link = """<a href="$ftsLink">${getString(R.string.help_fts5)}</a>"""
        val span = htmlToSpan(
            """
            ${getString(R.string.help_search_text2)}<br><br>
            ${getString(R.string.help_search_details, link)}
            """.trimIndent()
        )
        val d = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            .setTitle(title)
            .setIcon(R.drawable.ic_logo)
            .setMessage(span)
            .create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    companion object {
        private const val TAG = "EpubSearchCompose"

        /**
         * Map [EpubSearchMode] to the classic JSword [SearchType] name for the settings/extras wire
         * format: ALL_WORDS→"ALL_WORDS", ANY_WORD→"ANY_WORDS", PHRASE→"PHRASE", FTS→null.
         */
        private fun EpubSearchMode.toClassicSearchTypeName(): String? = when (this) {
            EpubSearchMode.ALL_WORDS -> SearchType.ALL_WORDS.name
            EpubSearchMode.ANY_WORD -> SearchType.ANY_WORDS.name
            EpubSearchMode.PHRASE -> SearchType.PHRASE.name
            EpubSearchMode.FTS -> null
        }
    }
}
