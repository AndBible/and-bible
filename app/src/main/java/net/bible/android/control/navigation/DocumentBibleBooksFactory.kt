/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.navigation

import androidx.collection.LruCache
import net.bible.android.control.ApplicationScope
import javax.inject.Inject
import org.crosswire.jsword.book.basic.AbstractPassageBook
import net.bible.service.common.Logger
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksListener
import org.crosswire.jsword.book.BooksEvent

/**
 * Caching factory for [DocumentBibleBooks].
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class DocumentBibleBooksFactory @Inject constructor() {
    private val cache: LruCache<AbstractPassageBook, DocumentBibleBooks> = object : LruCache<AbstractPassageBook, DocumentBibleBooks>(CACHE_SIZE) {
        override fun create(document: AbstractPassageBook): DocumentBibleBooks {
            return DocumentBibleBooks(document)
        }
    }
    private val log = Logger(this.javaClass.name)

    init {
        initialise()
    }

    fun initialise() {
        log.debug("Initialising DocumentBibleBooksFactory cache")
        flushCacheIfBooksChange()
    }

    fun getDocumentBibleBooksFor(document: AbstractPassageBook): DocumentBibleBooks {
        return cache[document]!!
    }

    fun getBooksFor(document: AbstractPassageBook): List<BibleBook> {
        return getDocumentBibleBooksFor(document).getBookList()
    }

    val size: Int get() = cache.size()

    /**
     * Different versions of a Book may contain different Bible books so flush cache if a Book may have been updated
     */
    private fun flushCacheIfBooksChange() {
        Books.installed().addBooksListener(object : BooksListener {
            override fun bookAdded(ev: BooksEvent) {
                cache.evictAll()
            }

            override fun bookRemoved(ev: BooksEvent) {
                cache.evictAll()
            }
        })
    }

    companion object {
        private const val CACHE_SIZE = 10
    }
}
