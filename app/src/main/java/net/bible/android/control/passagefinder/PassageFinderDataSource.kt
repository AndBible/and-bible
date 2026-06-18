/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.control.passagefinder

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.PageControl
import net.bible.android.view.activity.passagefinder.BookCategory
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.passage.Verse

private const val TAG = "PassageFinderDataSource"

/**
 * Data source for the PassageFinder widget. Reads book, chapter, and verse metadata
 * from the active Bible translation via JSword APIs.
 *
 * This class is not a Dagger-scoped singleton -- it is created by the ViewModel factory
 * with injected NavigationControl and PageControl references.
 */
class PassageFinderDataSource(
    private val navigationControl: NavigationControl,
    private val pageControl: PageControl,
) {
    /** Book metadata exposed to the UI layer. */
    data class BookInfo(
        val book: BibleBook,
        val shortName: String,
        val longName: String,
        val category: BookCategory,
    )

    /**
     * Returns every book of the active Bible module in canonical order, including
     * deuterocanonical / apocryphal books for Catholic and Orthodox canons.
     * Introductory pseudo-books are excluded.
     */
    fun getBooks(): List<BookInfo> {
        val versification = navigationControl.versification
        val books = navigationControl.getAllDocumentBooksExcludingIntros()
        return books.map { book ->
            BookInfo(
                book = book,
                shortName = versification.getShortName(book),
                longName = versification.getLongName(book),
                category = BookCategory.forBook(book),
            )
        }
    }

    /**
     * Returns the chapter count for a given book, clamped to >= 1.
     *
     * Defensive: some modules/versifications can throw or return non-positive
     * values for unusual books. The widget always needs at least one chapter
     * to render a non-empty strip, so we floor at 1 and log on failure.
     */
    fun getChapterCount(book: BibleBook): Int {
        return try {
            navigationControl.versification.getLastChapter(book).coerceAtLeast(1)
        } catch (e: Exception) {
            Log.w(TAG, "getLastChapter failed for $book", e)
            1
        }
    }

    /**
     * Returns the verse count for a given book and chapter, clamped to >= 1.
     * See [getChapterCount] for the same defensive rationale.
     */
    fun getVerseCount(book: BibleBook, chapter: Int): Int {
        return try {
            navigationControl.versification.getLastVerse(book, chapter).coerceAtLeast(1)
        } catch (e: Exception) {
            Log.w(TAG, "getLastVerse failed for $book $chapter", e)
            1
        }
    }

    /** Returns the currently active verse (book + chapter + verse). */
    fun getCurrentVerse(): Verse {
        return pageControl.currentBibleVerse
    }

    /**
     * Loads the canonical text for a single verse from the active Bible translation.
     * Must be called from a coroutine context; runs on [Dispatchers.IO] to avoid blocking the UI.
     */
    suspend fun getVerseText(book: BibleBook, chapter: Int, verse: Int): String =
        withContext(Dispatchers.IO) {
            val versification = navigationControl.versification
            val verseKey = Verse(versification, book, chapter, verse)
            val currentDoc = pageControl.currentPageManager.currentPassageDocument
            SwordContentFacade.getCanonicalText(currentDoc, verseKey)
        }
}
