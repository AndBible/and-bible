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
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.search.EpubSearchResultsController
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.search.EpubSearchResultsScreen
import org.crosswire.jsword.book.Books
import org.koin.android.ext.android.inject

/**
 * Compose host for EPUB full-text search results — the new-path twin of classic [EpubSearchResults].
 * It owns the JSword resolution seam: view-data rows carry a stable `keyId` (the `BookAndKey.osisRef`,
 * `"<initials>:<fragmentId>"`); a selected `keyId` is re-resolved to a `Key` host-side via
 * [org.crosswire.jsword.book.Book.getKey] before navigation, mirroring [SearchResultsComposeActivity].
 */
class EpubSearchResultsComposeActivity : ActivityBase() {
    override val integrateWithHistoryManager: Boolean = true

    private val epubSearchService: EpubSearchService by inject()
    private val windowControl: WindowControl by inject()

    private lateinit var docId: String

    private val controller by lazy {
        EpubSearchResultsController(lifecycleScope, epubSearchService, onSelect = ::onSelect)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Compose EPUB search results view")

        // Ad-hoc EPUB extras, read VERBATIM for classic parity (see EpubSearch.onSearch / EpubSearchResults).
        val searchText = intent.getStringExtra("searchText") ?: ""
        val mode = epubSearchModeFromClassicName(intent.getStringExtra("searchType"))
        docId = intent.getStringExtra("searchDocument").let {
            if (it.isNullOrEmpty())
                windowControl.activeWindowPageManager.currentBible.currentDocument!!.initials
            else it
        }
        Log.i(TAG, "Searching '$searchText' ($mode) in $docId")

        // Guard (classic parity): a non-epub doc aborts; a not-yet-indexed epub routes through the
        // ScreenLauncher SearchIndex path (forwarding SEARCH_DOCUMENT so the reindex targets this doc).
        val doc = Books.installed().getBook(docId)
        if (doc == null || !doc.isEpub) {
            Log.e(TAG, "Document ${doc?.name} is not an epub; aborting")
            finish()
            return
        }
        if (!epubSearchService.isIndexed(docId)) {
            startActivity(
                ScreenLauncher.intentFor(this, Screen.SearchIndex)
                    .putExtra(SearchControl.SEARCH_DOCUMENT, docId)
            )
            finish()
            return
        }

        controller.run(docId, searchText, mode)

        val docAbbrev = doc.abbreviation

        setContent {
            AbAppTheme {
                    val loading by controller.loading.collectAsState()
                    val results by controller.results.collectAsState()
                    val error by controller.error.collectAsState()

                    // Classic showed a dialog then backed out on failure; the shared screen has no error
                    // slot, so mirror that behaviour host-side: toast + finish once.
                    LaunchedEffect(error) {
                        if (error) {
                            Toast.makeText(
                                this@EpubSearchResultsComposeActivity,
                                R.string.error_executing_search,
                                Toast.LENGTH_SHORT,
                            ).show()
                            controller.dismissError()
                            finish()
                        }
                    }

                    // Classic "+" overflow affordance: the service caps at MAX+1 so size>MAX is detectable.
                    val resultAmount =
                        if (results.size > SearchControl.MAX_SEARCH_RESULTS) "${SearchControl.MAX_SEARCH_RESULTS}+"
                        else results.size.toString()
                    val title = getString(R.string.search_with_results2, resultAmount, docAbbrev)

                    EpubSearchResultsScreen(
                        title = title,
                        loading = loading,
                        rows = results,
                        onSelect = controller::select,
                        onNavigateUp = { onBackPressedDispatcher.onBackPressed() },
                    )
            }
        }
    }

    /**
     * Resolve the selected hit's key on [docId] and open it in the Bible view.
     * [keyId] is the `BookAndKey.osisRef` (`"<initials>:<fragmentId>"`); strip the initials prefix and
     * re-resolve the inner osisRef via [org.crosswire.jsword.book.Book.getKey] — the same round-trip
     * [net.bible.service.sword.BookAndKeySerialized] uses to restore epub keys from history/workspace.
     *
     * [ordinal] is not yet used — Task 2 attaches the ordinal to the key.
     */
    private fun onSelect(keyId: String, ordinal: Int) {
        val book = Books.installed().getBook(docId) ?: return
        try {
            val innerOsisRef = keyId.removePrefix("$docId:")
            val key = book.getKey(innerOsisRef)
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
            startActivity(Intent(this, MainBibleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        } catch (e: Exception) {
            Log.e(TAG, "Could not resolve key '$keyId' in $docId", e)
        }
    }

    companion object {
        private const val TAG = "EpubSearchResultsCompose"
    }
}
