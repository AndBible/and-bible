/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.nav

import android.os.Bundle

/**
 * What the My Documents / pages export is waiting for while the system folder picker is open.
 *
 * Holds PERSISTENT document/page ids (their [net.bible.android.database.IdType] string form), never
 * the session-local `Long` tokens of the list controllers: those tokens belong to a session that is
 * rebuilt when the Activity is recreated (e.g. a day/night switch while the picker is up), so a
 * token-based pending target silently resolved to nothing and the export was dropped.
 * Survives recreation through [saveTo]/[restoreFrom].
 */
class PendingMyDocumentExports {
    var documentIds: List<String> = emptyList()
    var pageIds: List<String> = emptyList()

    /** True when [documentIds] is a multi-select export (one subfolder per document); false for a single document. */
    var documentsBatch: Boolean = false

    /** Returns and clears the pending document ids. */
    fun takeDocumentIds(): List<String> = documentIds.also { documentIds = emptyList() }

    /** Returns and clears the pending page ids. */
    fun takePageIds(): List<String> = pageIds.also { pageIds = emptyList() }

    fun saveTo(outState: Bundle) {
        outState.putStringArrayList(KEY_DOCUMENTS, ArrayList(documentIds))
        outState.putStringArrayList(KEY_PAGES, ArrayList(pageIds))
        outState.putBoolean(KEY_BATCH, documentsBatch)
    }

    fun restoreFrom(savedState: Bundle?) {
        documentIds = savedState?.getStringArrayList(KEY_DOCUMENTS).orEmpty()
        pageIds = savedState?.getStringArrayList(KEY_PAGES).orEmpty()
        documentsBatch = savedState?.getBoolean(KEY_BATCH, false) ?: false
    }

    private companion object {
        const val KEY_DOCUMENTS = "pending-my-document-export-ids"
        const val KEY_BATCH = "pending-my-document-export-batch"
        const val KEY_PAGES = "pending-my-document-page-export-ids"
    }
}
