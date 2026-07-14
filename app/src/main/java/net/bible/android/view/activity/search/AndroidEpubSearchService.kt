/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.search.SearchControl
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.adjustSearchText
import net.bible.sharedcore.search.parseHighlightHtml
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.Books

/**
 * Android [EpubSearchService]: raw SQLite FTS5 via `EpubBackendState.search`, off the main thread.
 * Mirrors the classic [EpubSearchResults] path (resolve Book -> epubBackend.state -> FTS5 query ->
 * highlight() output), producing portable [EpubResultRow]s so the shared search controllers stay
 * iOS-clean (no android.text / JSword leakage past this seam).
 */
class AndroidEpubSearchService : EpubSearchService {
    private fun bookOf(docId: String) = Books.installed().getBook(docId)

    override fun isIndexed(docId: String): Boolean {
        val doc = bookOf(docId) ?: return false
        return doc.isEpub && doc.epubBackend?.state?.isIndexed == true
    }

    override suspend fun searchEpub(docId: String, query: String, mode: EpubSearchMode): List<EpubResultRow> =
        withContext(Dispatchers.IO) {
            val doc = bookOf(docId) ?: return@withContext emptyList()
            if (!doc.isEpub) return@withContext emptyList()
            val state = doc.epubBackend?.state ?: return@withContext emptyList()
            state.search(adjustSearchText(mode, query))
                .take(SearchControl.MAX_SEARCH_RESULTS)
                .map { kt ->
                    EpubResultRow(
                        keyId = kt.key.osisRef,   // fully-qualified BookAndKey id (stable addressing key)
                        keyName = kt.key.name,
                        text = parseHighlightHtml(kt.text),
                    )
                }
        }
}
