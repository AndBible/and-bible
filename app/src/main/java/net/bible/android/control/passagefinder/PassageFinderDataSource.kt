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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * This class is not a Dagger-scoped singleton -- [net.bible.android.view.activity.passagefinder.PassageFinderLauncher]
 * builds one from the activity's NavigationControl and PageControl and hands it to the
 * ViewModel factory, so its lifetime is the launcher's.
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
     * The navigable books of one module, with each book's chapter count alongside.
     *
     * The counts travel with the list because the passage finder needs all of them up
     * front to size its book spines, and resolving them one at a time on the UI thread
     * is exactly the cost this class exists to avoid.
     */
    class BookList(
        val books: List<BookInfo>,
        val chapterCounts: IntArray,
    )

    /** Cached per module, keyed by document initials. See [loadBooks]. */
    @Volatile
    private var cache: Pair<String, BookList>? = null

    /** Held across the scan so concurrent callers share one load rather than racing. */
    private val loadMutex = Mutex()

    /**
     * Returns the book list for the active module if it has already been loaded.
     *
     * Non-blocking, for the tap path: a hit means the finder can render its real content
     * on the very first frame, and a miss means it should show its placeholder and wait
     * for [loadBooks].
     */
    fun cachedBooks(): BookList? {
        val key = currentDocumentKey() ?: return null
        return cache?.takeIf { it.first == key }?.second
    }

    /**
     * Loads every book of the active Bible module in canonical order, including
     * deuterocanonical / apocryphal books for Catholic and Orthodox canons.
     * Introductory pseudo-books are excluded.
     *
     * Runs on [Dispatchers.IO] and caches per module, because the first call for a given
     * module is expensive in a way that is invisible from here: JSword answers
     * "does this module contain this book?" by reading the module's index off disk, once
     * per book. On a cold module that is well over a hundred file reads, which is why
     * this must never sit between the user's tap and the first frame.
     *
     * That same slowness is why the cache key is captured up front and re-checked at the
     * end: the user can switch translation while those file reads are still going, and
     * keying the result on whatever document happens to be open when the load lands would
     * file one module's books under another module's name — a wrong answer that then
     * sticks, because nothing else ever invalidates the entry.
     */
    suspend fun loadBooks(): BookList = withContext(Dispatchers.IO) {
        cachedBooks()?.let { return@withContext it }
        // Serialise the scan itself. Both the warm-up on resume and a tap that misses the
        // cache call this, and the whole reason it exists is that it is slow — so the
        // window in which a second caller arrives while the first is still reading is wide,
        // and letting them both through would run that hundred-odd file scan twice for the
        // same answer. The waiter almost always finds the cache filled by the time it gets
        // the lock, so it re-checks first and usually returns without touching the disk.
        loadMutex.withLock {
            cachedBooks()?.let { return@withLock it }
            loadUncached()
        }
    }

    /** The actual scan. Call only under [loadMutex]. */
    private fun loadUncached(): BookList {
        val loadKey = currentDocumentKey()
        val versification = navigationControl.versification
        val books = navigationControl.getAllDocumentBooksExcludingIntros()
        val infos = books.map { book ->
            BookInfo(
                book = book,
                shortName = versification.getShortName(book),
                longName = versification.getLongName(book),
                category = BookCategory.forBook(book),
            )
        }
        val counts = IntArray(infos.size) { getChapterCount(infos[it].book) }
        val loaded = BookList(infos, counts)
        val settledKey = currentDocumentKey()
        if (loadKey != null && loadKey == settledKey) {
            cache = loadKey to loaded
        } else {
            // The document moved under us. Hand the result to this caller — it asked for
            // the list that was current when it called — but don't cache it, since we can
            // no longer say which module it describes.
            Log.d(TAG, "Document changed during load ($loadKey -> $settledKey); not caching")
        }
        return loaded
    }

    /** Identity of the module the book list belongs to, or null if none is open. */
    private fun currentDocumentKey(): String? = try {
        pageControl.currentPageManager.currentPassageDocument.initials
    } catch (e: Exception) {
        // No usable document yet (e.g. very early in startup). Treat as a cache miss
        // rather than failing the caller.
        Log.d(TAG, "No current passage document while keying the book cache", e)
        null
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
