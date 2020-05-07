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
package net.bible.android.control.versification

import net.bible.android.control.ApplicationScope
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import javax.inject.Inject

/**
 * Enable separation of Scripture books
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class BibleTraverser @Inject constructor(private val documentBibleBooksFactory: DocumentBibleBooksFactory) {

    /** Get next Scriptural Verse with same scriptural status
     */
    fun getNextVerse(document: AbstractPassageBook?, verse: Verse): Verse {
        val v11n = verse.versification
        val book = verse.book
        val chapter = verse.chapter
        val verseNo = verse.verse
        // if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
        return if (verseNo < v11n.getLastVerse(book, chapter)) {
            Verse(v11n, book, chapter, verseNo + 1)
        } else {
            getNextChapter(document, verse)
        }
    }

    /** Get previous Verse with same scriptural status
     */
    fun getPrevVerse(document: AbstractPassageBook?, verse: Verse): Verse {
        val v11n = verse.versification
        var book = verse.book
        var chapter = verse.chapter
        var verseNo = verse.verse
        if (verseNo > 1) {
            verseNo -= 1
        } else {
            val prevChap = getPrevChapter(document, verse)
            if (!v11n.isSameChapter(verse, prevChap)) {
                book = prevChap.book
                chapter = prevChap.chapter
                verseNo = v11n.getLastVerse(book, chapter)
            }
        }
        return Verse(v11n, book, chapter, verseNo)
    }

    fun getNextVerseRange(document: AbstractPassageBook?, verseRange: VerseRange): VerseRange {
        return getNextVerseRange(document, verseRange, true)
    }

    fun getNextVerseRange(document: AbstractPassageBook?, verseRange: VerseRange, continueToNextChapter: Boolean): VerseRange {
        val v11n = verseRange.versification
        val verseCount = verseRange.cardinality

        // shuffle forward
        var start = verseRange.start
        var end = verseRange.end
        var i = 0
        while (i++ < verseCount && (continueToNextChapter || !v11n.isEndOfChapter(end))) {
            start = getNextVerse(document, start)
            end = getNextVerse(document, end)
        }
        return VerseRange(v11n, start, end)
    }

    fun getPreviousVerseRange(document: AbstractPassageBook?, verseRange: VerseRange): VerseRange {
        return getPreviousVerseRange(document, verseRange, true)
    }

    fun getPreviousVerseRange(document: AbstractPassageBook?, verseRange: VerseRange, continueToPreviousChapter: Boolean): VerseRange {
        val v11n = verseRange.versification
        val verseCount = verseRange.cardinality

        // shuffle backward
        var start = verseRange.start
        var end = verseRange.end
        var i = 0
        while (i++ < verseCount && (continueToPreviousChapter || !v11n.isStartOfChapter(start))) {
            start = getPrevVerse(document, start)
            end = getPrevVerse(document, end)
        }
        return VerseRange(v11n, start, end)
    }

    /** Get next chapter consistent with current verses scriptural status ie don't hop between book with different scriptural states
     */
    fun getNextChapter(document: AbstractPassageBook?, verse: Verse): Verse {
        val v11n = verse.versification
        var book = verse.book
        var chapter = verse.chapter
        // if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
        if (chapter < v11n.getLastChapter(book)) {
            chapter += 1
        } else {
            val nextBook = getNextBook(document, v11n, book)
            // if there was a next book then go to it's first chapter
            book = nextBook ?: BibleBook.GEN
            chapter = 1
        }
        return Verse(v11n, book, chapter, 1)
    }

    /** Get previous chapter consistent with current verses scriptural status ie don't hop between book with different scriptural states
     */
    fun getPrevChapter(document: AbstractPassageBook?, verse: Verse): Verse {
        val v11n = verse.versification
        var book = verse.book
        var chapter = verse.chapter
        // if past last chapter of book then go to next book - algorithm not foolproof but we only move one chapter at a time like this
        if (chapter > 1) {
            chapter -= 1
        } else {
            val prevBook = getPrevBook(document, v11n, book)
            // if there was a next book then go to it's first chapter
            if (prevBook != null) {
                book = prevBook
                chapter = v11n.getLastChapter(book)
            }
        }
        return Verse(v11n, book, chapter, 1)
    }

    /**
     * Get next book but separate scripture from other books to prevent unintentional jumping between Scripture and other
     */
    private fun getNextBook(document: AbstractPassageBook?, v11n: Versification, book: BibleBook): BibleBook? {
        val isCurrentlyScripture = Scripture.isScripture(book)
        val documentBibleBooks = documentBibleBooksFactory.getDocumentBibleBooksFor(document)
        var nextBook: BibleBook? = book
        do {
            nextBook = v11n.getNextBook(nextBook)
        } while (nextBook != null &&
            (Scripture.isScripture(nextBook) != isCurrentlyScripture ||
                Scripture.isIntro(nextBook) ||
                !documentBibleBooks.contains(nextBook)))
        return nextBook
    }

    private fun getPrevBook(document: AbstractPassageBook?, v11n: Versification, book: BibleBook): BibleBook? {
        val isCurrentlyScripture = Scripture.isScripture(book)
        val documentBibleBooks = documentBibleBooksFactory.getDocumentBibleBooksFor(document)
        var prevBook: BibleBook? = book
        do {
            prevBook = v11n.getPreviousBook(prevBook)
        } while (prevBook != null &&
            (Scripture.isScripture(prevBook) != isCurrentlyScripture ||
                Scripture.isIntro(prevBook) ||
                !documentBibleBooks.contains(prevBook)))
        return prevBook
    }

    /**
     * Get percentage value of reading progress of the verse within its biblebook.
     */
    fun getPercentOfBook(verse: Verse): Int {
        val v11n = verse.versification
        val bibleBook = verse.book
        val lastChapterNumber = v11n.getLastChapter(bibleBook)
        val lastVerseNumber = v11n.getLastVerse(bibleBook, lastChapterNumber)
        val lastVerse = Verse(v11n, bibleBook, lastChapterNumber, lastVerseNumber)
        val firstVerse = Verse(v11n, bibleBook, 1, 1)
        return ((verse.ordinal - firstVerse.ordinal).toDouble() / (lastVerse.ordinal - firstVerse.ordinal) * 100.0).toInt()
    }

}
