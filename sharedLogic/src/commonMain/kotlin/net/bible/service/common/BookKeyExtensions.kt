/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.common

import net.bible.sharedlogic.sync.bookNameMonitor
import net.bible.sharedlogic.sync.withMonitor
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.book.sword.GenBookBackend
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseKey
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BookName

suspend fun Book.getBookAndKey(keyStr: String, strings: net.bible.sharedcore.platform.CoreStrings, ordinal: Int? = null): BookAndKey? {
    val k = try {getKey(keyStr)} catch (e: NoSuchKeyException) {null} ?: return null
    return BookAndKey(k, this, ordinal = OrdinalRange(ordinal?: ordinalRangeFor(k, strings).first))
}

suspend fun BookAndKey.prev(strings: net.bible.sharedcore.platform.CoreStrings, traverser: BibleTraverser): BookAndKey {
    val nextKey: Key = when(this.key) {
        is VerseRange -> {
            traverser.getPrevVerse(this.document as AbstractPassageBook, this.key.end)

        }
        is Verse -> {
            traverser.getPrevVerse(this.document as AbstractPassageBook, this.key)
        }
        else -> this.document!!.adjacentGeneralKey(this.key, -1).first
    }
    return BookAndKey(
        nextKey,
        this.document,
        OrdinalRange(this.document!!.ordinalRangeFor(nextKey, strings).last)
    )
}

/**
 * The key after this one, and whether reaching it wrapped past the end of a general book (EPUB, MyDocument,
 * GenBook, dictionary). Verses never wrap here.
 */
private suspend fun BookAndKey.nextKeyOrWrap(traverser: BibleTraverser): Pair<Key, Boolean> =
    when(this.key) {
        is VerseRange -> traverser.getNextVerse(this.document as AbstractPassageBook, this.key.end) to false
        is Verse -> traverser.getNextVerse(this.document as AbstractPassageBook, this.key) to false
        else -> this.document!!.adjacentGeneralKey(this.key, 1)
    }

/**
 * The key [delta] (1 or -1) steps from [key] in a general book or dictionary, and whether the step wrapped
 * past an end to the first (forward) or last (backward) key. A [key] the book does not contain steps forward to
 * the first key without counting as a wrap, and backward to the last key; an empty book stays on [key].
 *
 * Dispatches on the backend type: a key-list backend ([AbstractKeyBackend]: EPUB, MyDocument, dictionaries)
 * is indexed directly; a tree backend ([GenBookBackend]) walks the book's cached global key list once
 * instead of re-reading the index from disk.
 */
internal fun Book.adjacentGeneralKey(key: Key, delta: Int): Pair<Key, Boolean> {
    require(delta == 1 || delta == -1) { "delta must be 1 or -1: $delta" }
    val backend = when(this) {
        is SwordGenBook -> this.backend
        is SwordDictionary -> this.backend
        else -> null
    }
    return when(backend) {
        is AbstractKeyBackend<*> -> {
            val idx = backend.indexOf(key)
            try {
                backend.get(idx + delta)!! to false
            } catch (e: IndexOutOfBoundsException) {
                // wraps to the other end; an empty document (all pages deleted) has none, so stay put
                val count = backend.getCardinality()
                (if (count == 0) key else backend.get(if (delta > 0) 0 else count - 1)!!) to true
            }
        }
        is GenBookBackend -> adjacentTreeKey(getGlobalKeyList(), key, delta)
        else -> throw RuntimeException("Unsupported")
    }
}

/** [adjacentGeneralKey] over a key list in iteration order (TreeKey.iterator already traverses descendants). */
private fun adjacentTreeKey(keys: Key, key: Key, delta: Int): Pair<Key, Boolean> {
    var first: Key? = null
    var previous: Key? = null
    var found = false
    for (k in keys) {
        if (first == null) first = k
        if (found && delta > 0) return k to false
        if (!found && k == key) {
            if (delta < 0 && previous != null) return previous to false
            found = true
        }
        previous = k
    }
    // Here: past the end forward, before the start backward, or not found; previous is the last key.
    if (first == null) return key to true
    return (if (delta > 0) first else previous!!) to found
}

private suspend fun BookAndKey.startOf(key: Key, strings: net.bible.sharedcore.platform.CoreStrings) =
    BookAndKey(key, this.document, OrdinalRange(this.document!!.ordinalRangeFor(key, strings).first))

/**
 * Like [next], but null where [next] would wrap from the end of a general book back to its first key
 * (the `IndexOutOfBoundsException` branches of [AbstractKeyBackend] and [GenBookBackend]). Verses never
 * give null. Speech uses this to stop at the end of a document instead of looping.
 */
suspend fun BookAndKey.nextOrNull(strings: net.bible.sharedcore.platform.CoreStrings, traverser: BibleTraverser): BookAndKey? {
    val (key, wrapped) = nextKeyOrWrap(traverser)
    return if (wrapped) null else startOf(key, strings)
}

/** The next key; wraps from the end of a general book to its first key (navigation). See [nextOrNull]. */
suspend fun BookAndKey.next(strings: net.bible.sharedcore.platform.CoreStrings, traverser: BibleTraverser): BookAndKey =
    startOf(nextKeyOrWrap(traverser).first, strings)

suspend fun Book.ordinalRangeFor(key: Key, strings: net.bible.sharedcore.platform.CoreStrings): IntRange = SwordContentFacade.ordinalRangeFor(this, key, strings)

val Key.tinyName: String get() =
    if(this is VerseKey<*>)
        withMonitor(bookNameMonitor) {
            val prevTruncateLength = BookName.getTruncateShortName()
            try {
                var length = 5
                var name: String
                do {
                    BookName.setTruncateShortName(length--)
                    name = this.getName()
                } while(length > 0 && name.length > 7)
                name
            } finally {
                BookName.setTruncateShortName(prevTruncateLength)
            }
        }
    else getName()


val Key.shortName: String get() =
    if(this is VerseKey<*>)
        withMonitor(bookNameMonitor) {
            val oldValue = BookName.isFullBookName()
            BookName.setFullBookName(false)
            try {
                return getName()
            } finally {
                BookName.setFullBookName(oldValue)
            }
        }
    else getName()
