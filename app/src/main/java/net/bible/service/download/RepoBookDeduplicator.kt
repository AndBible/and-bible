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
package net.bible.service.download

import net.bible.service.common.Logger
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import java.util.*

/**
 * Some repos contain the same books.  Get the latest version of duplicates.
 */
class RepoBookDeduplicator {
    private val bookSet: MutableMap<BookKey, Book> = HashMap()
    private val logger = Logger(this.javaClass.name)
    val books: MutableList<Book>
        get() = ArrayList(bookSet.values)

    /**
     * Add books if not already added or are a later version than previously added version
     */
    fun addAll(books: List<Book>) {
        for (book in books) {
            try {
                val bookKey = BookKey(book)
                val existing = bookSet[bookKey]
                val latest = getLatest(existing, book)
                bookSet[bookKey] = latest
            } catch (e: Exception) {
                logger.error("Error comparing download book versions", e)
            }
        }
    }

    /**
     * Add books if not already added.
     * Used for beta repo - beta should never override live books.
     */
    fun addIfNotExists(books: List<Book>) {
        for (book in books) {
            try {
                val bookKey = BookKey(book)
                if (!bookSet.containsKey(bookKey)) {
                    bookSet[bookKey] = book
                }
            } catch (e: Exception) {
                logger.error("Error comparing download book versions", e)
            }
        }
    }

    private fun getLatest(previous: Book?, current: Book): Book {
        if (previous == null) {
            return current
        }

        // SBMD defaults t0 1.0.0 if version does not exist
        val previousVersion = Version(previous.bookMetaData.getProperty("Version"))
        val currentVersion = Version(current.bookMetaData.getProperty("Version"))
        return if (previousVersion.compareTo(currentVersion) < 0) {
            current
        } else {
            previous
        }
    }

    private inner class BookKey internal constructor(private val book: Book) {
        override fun equals(obj: Any?): Boolean {
            // The real bit ...
            val thatBook = (obj as BookKey?)!!.book
            val thatRepo = thatBook.getProperty(DownloadManager.REPOSITORY_KEY)
            val repo = book.getProperty(DownloadManager.REPOSITORY_KEY)
            return book.bookCategory == thatBook.bookCategory && book.osisID == thatBook.osisID && book.language == thatBook.language && repo == thatRepo
        }

        override fun hashCode(): Int {
            val repo = book.getProperty(DownloadManager.REPOSITORY_KEY) ?:""
            return "$repo-${book.osisID}".hashCode()
        }

        init {
            book.hashCode()
        }
    }
}
