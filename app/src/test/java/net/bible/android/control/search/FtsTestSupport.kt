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

import org.crosswire.common.util.CWProject
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.index.fts.FtsIndexManager
import org.crosswire.jsword.index.fts.SearchFields
import java.io.File

/** Shared helpers of the K6 search tests (real [FtsIndexManager], project-dir `search/` and `lucene/`). */
internal object FtsTestSupport {
    val manager: FtsIndexManager get() = IndexManagerFactory.getIndexManager() as FtsIndexManager

    /** The legacy Lucene directory of [book]: `lucene/<driver>/<initials>`. */
    fun legacyDir(book: Book): File {
        val root = CWProject.instance().getWritableProjectSubdirPath(SearchFields.DIR_LEGACY_LUCENE, false)!!.toString()
        return File(File(root, book.bookMetaData.driverName!!), book.initials)
    }

    /** Builds the index synchronously (the manager completes it before returning) unless a valid one exists. */
    fun ensureIndexed(book: Book) {
        if (manager.isIndexed(book) && !manager.needsReindexing(book)) {
            book.indexStatus = IndexStatus.DONE
            return
        }
        manager.scheduleIndexCreation(book)
        check(book.indexStatus == IndexStatus.DONE) { "indexing ${book.initials} ended ${book.indexStatus}" }
    }

    fun dropIndex(book: Book) {
        manager.deleteIndex(book)
        legacyDir(book).deleteRecursively()
    }
}
