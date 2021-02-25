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
package net.bible.android.control.navigation

import net.bible.service.common.CommonUtils.getSharedPreference
import net.bible.service.common.CommonUtils.saveSharedPreference
import net.bible.service.common.CommonUtils.getResourceString
import net.bible.android.control.ApplicationScope
import javax.inject.Inject
import net.bible.android.control.page.PageControl
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.book.basic.AbstractPassageBook
import net.bible.android.control.versification.Scripture
import org.crosswire.jsword.versification.Versification
import net.bible.android.activity.R
import java.util.*

/**
 * Used by Passage navigation ui
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class NavigationControl @Inject constructor(
    private val pageControl: PageControl,
    private val documentBibleBooksFactory: DocumentBibleBooksFactory)
{
    /**
     * Get books in current Document - either all Scripture books or all non-Scripture books
     */
    fun getBibleBooks(isScriptureRequired: Boolean): List<BibleBook> {
        var books: MutableList<BibleBook> = ArrayList()
        val currentPassageDocument: AbstractPassageBook = currentPassageDocument
        val documentBookList = documentBibleBooksFactory.getBooksFor(currentPassageDocument)
        for (bibleBook in documentBookList) {
            if (isScriptureRequired == Scripture.isScripture(bibleBook)) {
                books.add(bibleBook)
            }
        }
        books = getSortedBibleBooks(books, currentPassageDocument.versification)
        return books
    }

    /** Is this book of the bible not a single chapter book
     *
     * @param book to check
     * @return true if multi-chapter book
     */
    fun hasChapters(book: BibleBook): Boolean {
        return versification.getLastChapter(book) > 1
    }

    /** default book for use when jumping into the middle of passage selection
     */
    val defaultBibleBookNo: Int
        get() = Arrays.binarySearch(BibleBook.values(), pageControl.currentBibleVerse.book)

    /** default chapter for use when jumping into the middle of passage selection
     */
    val defaultBibleChapterNo: Int
        get() = pageControl.currentBibleVerse.chapter// but safety first// this should always be true

    /**
     * @return v11n of current document
     */
    val versification: Versification
        get() {
            val doc = currentPassageDocument

            // this should always be true
            return doc.versification
        }

    private fun getSortedBibleBooks(bibleBookList: MutableList<BibleBook>, versification: Versification): MutableList<BibleBook> {
        if (bibleBookSortOrder == BibleBookSortOrder.ALPHABETICAL) {
            Collections.sort(bibleBookList, BibleBookAlphabeticalComparator(versification))
        }
        return bibleBookList
    }

    fun changeBibleBookSortOrder() {
        bibleBookSortOrder = if (bibleBookSortOrder == BibleBookSortOrder.BIBLE_BOOK) {
            BibleBookSortOrder.ALPHABETICAL
        } else {
            BibleBookSortOrder.BIBLE_BOOK
        }
    }

    private var bibleBookSortOrder: BibleBookSortOrder
        get() {
            val bibleBookSortOrderStr = getSharedPreference(BIBLE_BOOK_SORT_ORDER, BibleBookSortOrder.BIBLE_BOOK.toString())
            return BibleBookSortOrder.valueOf(bibleBookSortOrderStr!!)
        }
        private set(bibleBookSortOrder) {
            saveSharedPreference(BIBLE_BOOK_SORT_ORDER, bibleBookSortOrder.toString())
        }

    /**
     * The description is the opposite of the current state because the button text describes what will happen if you press it.
     */
    val bibleBookSortOrderButtonDescription: String
        get() = if (BibleBookSortOrder.BIBLE_BOOK == bibleBookSortOrder) {
            getResourceString(R.string.sort_by_alphabetical)
        } else {
            getResourceString(R.string.sort_by_bible_book)
        }

    /**
     * When navigating books and chapters there should always be a current Passage based book
     */
    private val currentPassageDocument: AbstractPassageBook
        get() = pageControl.currentPageManager.currentPassageDocument

    companion object {
        private const val BIBLE_BOOK_SORT_ORDER = "BibleBookSortOrder"
    }
}
