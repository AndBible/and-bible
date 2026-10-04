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

import android.util.Log
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.search.SearchIndexService

/**
 * Host (`:app`) implementation of the portable [SearchIndexService] seam. Wraps the existing
 * [SwordDocumentFacade] index checks and the [SearchControl.createIndex] kickoff (verbatim from
 * classic `SearchIndex.onIndex`). The `docId` is a document's initials; progress is reported by
 * `Screen.SearchIndexProgress`, launched by the Compose host.
 */
class SearchIndexServiceImpl(
    private val searchControl: SearchControl,
) : SearchIndexService {

    override fun hasIndex(docId: String): Boolean {
        val book = SwordDocumentFacade.getDocumentByInitials(docId)
        return SwordDocumentFacade.hasIndex(book)
    }

    override fun createIndex(docId: String) {
        try {
            // start background thread to create index (verbatim from classic SearchIndex.onIndex)
            val doc = SwordDocumentFacade.getDocumentByInitials(docId)
            SwordDocumentFacade.deleteDocumentIndex(doc)
            searchControl.createIndex(doc)
        } catch (e: Exception) {
            Log.e(TAG, "error indexing:" + e.message)
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "SearchIndexService"
    }
}
