/*
 * Copyright (c) 2022-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedlogic.platform

import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocumentDao
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageWithContent

/**
 * Blocking reads of a MyDocument's pages for the JSword synchronous Backend contract.
 *
 * Synchronous because the JSword `Backend`/driver contract is synchronous; the Android implementation blocks
 * through `blockingAtEdge` and iOS supplies its own in I2. Making these `suspend` is the edge-reduction backlog.
 */
interface MyDocumentStoreEdge {
    /** Reads the page list of [documentId]. */
    fun pages(dao: MyDocumentDao, documentId: IdType): List<MyDocumentPage>

    /** Reads one page with its content, or null when [pageKey] is unknown. */
    fun pageWithContent(dao: MyDocumentDao, documentId: IdType, pageKey: String): MyDocumentPageWithContent?
}
