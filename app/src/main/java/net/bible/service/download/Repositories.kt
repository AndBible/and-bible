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

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData

class Repository(
    val repoName: String,
    private val supportedDocumentsFilter: BookFilter,
    private val downloadManager: DownloadManager,
) {

    fun getRepoBooks(refresh: Boolean): List<Book> {
        val bookList = getBookList(supportedDocumentsFilter, refresh)
        storeRepoNameInMetaData(bookList)
        return bookList
    }

    private fun getBookList(bookFilter: BookFilter, refresh: Boolean): List<Book> {
        return downloadManager.getDownloadableBooks(bookFilter, repoName, refresh)
    }

    fun downloadDocument(document: Book) {
        downloadManager.installBook(repoName, document)
    }

    private fun storeRepoNameInMetaData(bookList: List<Book>) {
        for (book in bookList) {
            // SwordBookMetaData must not persist these properties because many downloadable books may have the same name,
            // and we set the props every time so they do not need to be persisted
            if (book is SwordBook) {
                (book.getBookMetaData() as SwordBookMetaData).setProperty(DownloadManager.REPOSITORY_KEY, repoName)
            } else {
                book.bookMetaData.putProperty(DownloadManager.REPOSITORY_KEY, repoName)
            }
        }
    }
}
