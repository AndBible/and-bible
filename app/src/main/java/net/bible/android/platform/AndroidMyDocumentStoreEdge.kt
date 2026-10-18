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

package net.bible.android.platform

import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocumentDao
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.service.db.blockingAtEdge
import net.bible.sharedlogic.platform.MyDocumentStoreEdge

/** Android [MyDocumentStoreEdge]; blockingAtEdge stays in :app. */
object AndroidMyDocumentStoreEdge : MyDocumentStoreEdge {
    override fun pages(dao: MyDocumentDao, documentId: IdType): List<MyDocumentPage> = blockingAtEdge { dao.pagesForDocument(documentId) } // L1-edge: JSword synchronous Backend contract

    override fun pageWithContent(dao: MyDocumentDao, documentId: IdType, pageKey: String): MyDocumentPageWithContent? = blockingAtEdge { dao.pageByKeyWithContent(documentId, pageKey) } // L1-edge: JSword synchronous Backend contract
}
