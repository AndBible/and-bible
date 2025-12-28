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

import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import net.bible.android.control.versification.Scripture
import org.crosswire.jsword.versification.system.SystemSynodal
import org.crosswire.jsword.book.basic.AbstractBook
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Verse
import java.util.*

/**
 * Calculate which books are actually included in a Bible document.
 * Necessary for boks with v11n like Synodal but without dc books eg IBT.
 * Useful for partial documents eg NT or WIP.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentBibleBooks(private val document: AbstractPassageBook) {
    private lateinit var _bookList: List<BibleBook>
    var isOnlyScripture = true
        private set
    private var isProbablyIBT: Boolean? = null

    init {
        calculateBibleBookList()
    }

    /**
     * Iterate all books checking if document contains a verse from the book
     */
    private fun calculateBibleBookList() {
        val bookList: MutableList<BibleBook> = ArrayList()

        // iterate over all book possible in this document
        val documentVersification = document.versification
        val v11nBookIterator = documentVersification.bookIterator
        while (v11nBookIterator.hasNext()) {
            val bibleBook = v11nBookIterator.next()
            if (Scripture.isIntro(bibleBook)) continue
            // test some random verses - normally ch1 v 1 is sufficient - but we don't want to miss any
            if (isVerseInBook(document, documentVersification, bibleBook, 1, 1) ||
                isVerseInBook(document, documentVersification, bibleBook, 1, 2)
            ) {
                bookList.add(bibleBook)
                isOnlyScripture = isOnlyScripture and Scripture.isScripture(bibleBook)
            }
        }
        this._bookList = bookList
    }

    operator fun contains(book: BibleBook): Boolean {
        return _bookList.contains(book)
    }

    fun getBookList(): List<BibleBook> {
        return Collections.unmodifiableList(_bookList)
    }

    fun setContainsOnlyScripture(containsOnlyScripture: Boolean) {
        isOnlyScripture = containsOnlyScripture
    }

    private fun isVerseInBook(
        document: Book,
        v11n: Versification,
        bibleBook: BibleBook,
        chapter: Int,
        verseNo: Int
    ): Boolean {
        val verse = Verse(v11n, bibleBook, chapter, verseNo)

        // no content for specified verse implies this verse clearly is not in this document
        if (!document.contains(verse)) {
            return false
        }

        // IBT Synodal documents sometimes return stub data for missing verses in dc books e.g. <chapter eID="gen7" osisID="1Esd.1"/>
        return !(isProbablyIBTDocument(document, v11n) && isProbablyEmptyVerseInDocument(
            document,
            verse
        ))
    }

    /** IBT books are Synodal but are known to have mistakenly added empty verses for all dc books
     * Here we check to see if this document probably has that problem.
     */
    private fun isProbablyIBTDocument(document: Book, v11n: Versification): Boolean {
        if (isProbablyIBT == null) {
            isProbablyIBT = SystemSynodal.V11N_NAME == v11n.name &&
                    isProbablyEmptyVerseInDocument(document, Verse(v11n, BibleBook.TOB, 1, 1))
        }
        return isProbablyIBT!!
    }

    /**
     * Some IBT books mistakenly had dummy empty verses which returned the following for verse 1,2,... lastVerse-1
     * <chapter eID="gen7" osisID="1Esd.1"></chapter>
     * <chapter eID="gen1010" osisID="Mal.3"></chapter>
     */
    private fun isProbablyEmptyVerseInDocument(document: Book, verse: Verse): Boolean {
        val rawTextLength = (document as AbstractBook).backend.getRawTextLength(verse)
        return if (verse.book.isShortBook) {
            isProbablyShortBookEmptyVerseStub(rawTextLength)
        } else {
            isProbablyEmptyVerseStub(rawTextLength)
        }
    }

    /** There is a standard type of tag padding in each empty verse that has a fairly predictable length
     */
    private fun isProbablyEmptyVerseStub(rawTextLength: Int): Boolean =
        rawTextLength in IBT_EMPTY_VERSE_STUB_MIN_LENGTH..IBT_EMPTY_VERSE_STUB_MAX_LENGTH

    /** 1 chapter books have a different type of empty verse stub that includes the end of chapter tag
     * <chapter eID="gen955" osisID="Obad.1"></chapter> <div eID="gen954" osisID="Obad" type="book"></div> <div eID="gen953" type="x-Synodal-empty"></div>
     */
    private fun isProbablyShortBookEmptyVerseStub(rawTextLength: Int): Boolean =
        rawTextLength in IBT_1_CHAPTER_BOOK_EMPTY_VERSE_STUB_MIN_LENGTH..IBT_1_CHAPTER_BOOK_EMPTY_VERSE_STUB_MAX_LENGTH

    companion object {
        private const val IBT_EMPTY_VERSE_STUB_MIN_LENGTH =
            "<chapter eID=\"gen4\" osisID=\"Gen.1\"/>".length
        private const val IBT_EMPTY_VERSE_STUB_MAX_LENGTH =
            "<chapter eID=\"gen1146\" osisID=\"1Macc.1\"/>".length
        private const val IBT_1_CHAPTER_BOOK_EMPTY_VERSE_STUB_MIN_LENGTH =
            "<chapter eID=\"gen955\" osisID=\"Obad.1\"/> <div eID=\"gen954\" osisID=\"Obad\" type=\"book\"/> <div eID=\"gen953\" type=\"x-Synodal-empty\"/>".length
        private const val IBT_1_CHAPTER_BOOK_EMPTY_VERSE_STUB_MAX_LENGTH =
            "<chapter eID=\"gen1136\" osisID=\"EpJer.1\"/> <div eID=\"gen1135\" osisID=\"EpJer\" type=\"book\"/> <div eID=\"gen1134\" type=\"x-Synodal-non-canonical\"/>".length
        private const val TAG = "DocumentBibleBooks"
    }
}
