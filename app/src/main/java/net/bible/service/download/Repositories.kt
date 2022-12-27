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

import net.bible.service.common.CommonUtils
import net.bible.service.sword.AcceptableBookTypeFilter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData

class RepoBase(
    val repoName: String,
    private val supportedDocumentsFilter: BookFilter,
    ) {
    lateinit var repoFactory: RepoFactory

    private val downloadManager get() = repoFactory.downloadManager

    fun getRepoBooks(refresh: Boolean): List<Book> {
        val bookList = getBookList(supportedDocumentsFilter, refresh)
        storeRepoNameInMetaData(bookList)
        return bookList
    }

    fun getBookList(bookFilter: BookFilter, refresh: Boolean): List<Book> {
        return downloadManager.getDownloadableBooks(bookFilter, repoName, refresh)
    }

    fun downloadDocument(document: Book) {
        downloadManager.installBook(repoName, document)
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
}

fun AndBibleRepo() = RepoBase("AndBible", AcceptableBookTypeFilter())

fun StepRepo() = RepoBase("STEP Bible (Tyndale)", AcceptableBookTypeFilter())

fun AndBibleExtraRepo() = RepoBase("AndBible Extra", AcceptableBookTypeFilter())

fun AndBibleBetaRepo() = RepoBase(
    "AndBible Beta",
    object: AcceptableBookTypeFilter() {
        override fun test(book: Book): Boolean = CommonUtils.isBeta
    }
    )


fun CrosswireBetaRepo() = RepoBase(
    "Crosswire Beta",
    object : AcceptableBookTypeFilter() {
        override fun test(book: Book): Boolean {
            // just Calvin Commentaries for now to see how we go
            //
            // Cannot include Jasher, Jub, EEnochCharles because they are displayed as page per verse for some reason which looks awful.
            if(CommonUtils.isBeta) return true
            return super.test(book) &&
                book.initials == "CalvinCommentaries"
        }
    }
    )

        // see here for info ftp://ftp.xiphos.org/mods.d/
fun CrosswireRepo() = RepoBase("CrossWire", AcceptableBookTypeFilter())

fun LockmanRepo() = RepoBase("Lockman (CrossWire)", AcceptableBookTypeFilter())

fun WycliffeRepo() = RepoBase("Wycliffe (CrossWire)", AcceptableBookTypeFilter())

fun EBibleRepo() = RepoBase("eBible", AcceptableBookTypeFilter())

fun IBTRepo() = RepoBase("IBT", AcceptableBookTypeFilter())
