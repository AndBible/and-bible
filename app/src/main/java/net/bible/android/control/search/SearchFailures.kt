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

import androidx.annotation.VisibleForTesting
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.fts.FtsIndexManager
import org.crosswire.jsword.index.fts.UnsupportedSearchSyntaxException
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.index.IndexStatus

/**
 * The [UnsupportedSearchSyntaxException] anywhere in the cause chain of [e], if any. `Book.find` can wrap it
 * (for example `SearcherFactory` wraps failures as "Failed to initialize the search index"), so callers must walk
 * the chain instead of testing the exception type.
 */
fun unsupportedSyntaxIn(e: Throwable): UnsupportedSearchSyntaxException? {
    var t: Throwable? = e
    var depth = 0
    while (t != null && depth++ < 20) {
        if (t is UnsupportedSearchSyntaxException) return t
        t = t.cause
    }
    return null
}

/** The FTS5 index manager, or null if another [org.crosswire.jsword.index.IndexManager] is installed. */
internal fun ftsIndexManager(): FtsIndexManager? = IndexManagerFactory.getIndexManager() as? FtsIndexManager

/**
 * True when [book] has a search index file that can be opened by the current engine and matches the book.
 * `IndexManager.isIndexed` is only a file-exists check: a file written with an older schema counts as indexed
 * but cannot be opened, so [FtsIndexManager.needsReindexing] is consulted as well.
 */
@VisibleForTesting fun hasUsableSearchIndex(book: Book): Boolean {
    // A book without a driver (a fake or synthetic book) has no index location: its status is all there is.
    if (book.bookMetaData.driverName == null) return book.indexStatus == IndexStatus.DONE
    val manager = ftsIndexManager() ?: return IndexManagerFactory.getIndexManager().isIndexed(book)
    return manager.isIndexed(book) && !manager.needsReindexing(book)
}
