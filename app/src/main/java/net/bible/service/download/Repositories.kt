/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.download

import net.bible.service.common.Logger
import net.bible.service.sword.AcceptableBookTypeFilter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.install.InstallException
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData

abstract class RepoBase {
    @Throws(InstallException::class)
    abstract fun getRepoBooks(refresh: Boolean): List<Book?>?
    abstract val repoName: String

    /** get a list of books that are available in Xiphos repo and seem to work in And Bible
     */
    @Throws(InstallException::class)
    fun getBookList(bookFilter: BookFilter?, refresh: Boolean): List<Book> {
        val crossWireDownloadManager = DownloadManager()
        return crossWireDownloadManager.getDownloadableBooks(bookFilter, repoName, refresh)
    }

    fun storeRepoNameInMetaData(bookList: List<Book>) {
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

    @Throws(InstallException::class, BookException::class)
    fun downloadDocument(document: Book?) {
        val downloadManager = DownloadManager()
        downloadManager.installBook(repoName, document)
    }
}

class AndBibleRepo : RepoBase() {
    private val log = Logger(this.javaClass.name)

    /** get a list of books that are available in AndBible repo
     */
    @Throws(InstallException::class)
    override fun getRepoBooks(refresh: Boolean): List<Book> {
        val bookList = getBookList(SUPPORTED_DOCUMENTS, refresh)
        storeRepoNameInMetaData(bookList)
        return bookList
    }

    override val repoName: String get() = REPOSITORY

    companion object {
        private const val REPOSITORY = "AndBible"
        private val SUPPORTED_DOCUMENTS: BookFilter = AcceptableBookTypeFilter()
    }
}

class BetaRepo : RepoBase() {
    /** get a list of good books that are available in Beta repo and seem to work in And Bible
     */
    @Throws(InstallException::class)
    override fun getRepoBooks(refresh: Boolean): List<Book?>? {
        val books: List<Book> = getBookList(SUPPORTED_DOCUMENTS, refresh)
        storeRepoNameInMetaData(books)
        return books
    }

    override val repoName: String
        get() = REPONAME

    private class BetaBookFilter : AcceptableBookTypeFilter() {
        override fun test(book: Book): Boolean {
            // just Calvin Commentaries for now to see how we go
            //
            // Cannot include Jasher, Jub, EEnochCharles because they are displayed as page per verse for some reason which looks awful.
            return super.test(book) &&
                book.initials == "CalvinCommentaries"
        }
    }

    companion object {
        const val REPONAME = "Crosswire Beta"
        private val SUPPORTED_DOCUMENTS: BookFilter = BetaBookFilter()
    }
}

class CrosswireRepo : RepoBase() {
    /** get a list of books that are available in default repo and seem to work in And Bible
     */
    @Throws(InstallException::class)
    override fun getRepoBooks(refresh: Boolean): List<Book> {
        val books = getBookList(SUPPORTED_DOCUMENTS, refresh)
        storeRepoNameInMetaData(books)
        return books
    }

    override val repoName: String get() = REPOSITORY

    companion object {
        // see here for info ftp://ftp.xiphos.org/mods.d/
        private const val REPOSITORY = "CrossWire"
        private val SUPPORTED_DOCUMENTS: BookFilter = AcceptableBookTypeFilter()
    }
}

class LockmanRepo : RepoBase() {
    /** get a list of books that are available in default repo and seem to work in And Bible
     */
    @Throws(InstallException::class)
    override fun getRepoBooks(refresh: Boolean): List<Book> {
        val books = getBookList(SUPPORTED_DOCUMENTS, refresh)
        storeRepoNameInMetaData(books)
        return books
    }

    override val repoName: String get() = REPOSITORY

    companion object {
        private const val REPOSITORY = "Lockman Foundation"
        private val SUPPORTED_DOCUMENTS: BookFilter = AcceptableBookTypeFilter()
    }
}

class EBibleRepo : RepoBase() {
    /** get a list of books that are available in AndBible repo
     */
    @Throws(InstallException::class)
    override fun getRepoBooks(refresh: Boolean): List<Book?>? {
        val bookList = getBookList(SUPPORTED_DOCUMENTS, refresh)
        storeRepoNameInMetaData(bookList)
        return bookList
    }

    override val repoName: String get() = REPOSITORY

    companion object {
        const val REPOSITORY = "eBible"
        private val SUPPORTED_DOCUMENTS: BookFilter = AcceptableBookTypeFilter()
        private const val TAG = "EBibleRepo"
    }
}

class IBTRepo : RepoBase() {
    /** get a list of books that are available in default repo and seem to work in And Bible
     */
    @Throws(InstallException::class)
    override fun getRepoBooks(refresh: Boolean): List<Book?>? {
        val books = getBookList(SUPPORTED_DOCUMENTS, refresh)
        storeRepoNameInMetaData(books)
        return books
    }

    override val repoName: String get() = REPOSITORY

    companion object {
        const val REPOSITORY = "IBT"
        private val SUPPORTED_DOCUMENTS: BookFilter = AcceptableBookTypeFilter()
    }
}

