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

import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.book.sword.SwordGenBook
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mydocument.isMyDocument
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
        else -> {
            val backend = when(val book = this.document!!) {
                is SwordGenBook -> book.backend as AbstractKeyBackend
                is SwordDictionary -> book.backend as AbstractKeyBackend
                else -> throw RuntimeException("Unsupported")
            }
            val idx = backend.indexOf(this.key)
            try {
                backend.get(idx - 1)!!
            } catch (e: IndexOutOfBoundsException) {
                backend.lastOrNull() ?: this.key
            }
        }
    }
    return BookAndKey(
        nextKey,
        this.document,
        OrdinalRange(this.document!!.ordinalRangeFor(nextKey, strings).last)
    )
}

suspend fun BookAndKey.next(strings: net.bible.sharedcore.platform.CoreStrings, traverser: BibleTraverser): BookAndKey {
    val nextKey: Key = when(this.key) {
        is VerseRange -> {
            traverser.getNextVerse(this.document as AbstractPassageBook, this.key.end)
        }
        is Verse -> {
            traverser.getNextVerse(this.document as AbstractPassageBook, this.key)
        }
        else -> {
            val backend = when(val book = this.document!!) {
                is SwordGenBook -> {
                    if(book.isEpub || book.isMyDocument) {
                        book.backend as AbstractKeyBackend
                    } else {
                        book.backend as GenBookBackend
                    }
                }
                is SwordDictionary -> book.backend as AbstractKeyBackend
                else -> throw RuntimeException("Unsupported")
            }
            when (backend) {
                is AbstractKeyBackend -> {
                    val idx = backend.indexOf(this.key)
                    try {
                        backend.get(idx + 1)!!
                    } catch (e: IndexOutOfBoundsException) {
                        // wraps to the first key; an empty document (all pages deleted) has none, so stay put
                        backend.firstOrNull() ?: this.key
                    }
                }

                is GenBookBackend -> {
                    val keyList = backend.readIndex().flatten().toList()
                    val idx = keyList.indexOf(this.key)
                    try {
                        keyList[idx + 1]
                    } catch (e: IndexOutOfBoundsException) {
                        keyList.first()
                    }
                }
                else -> throw RuntimeException("Unsupported")
            }

        }
    }
    return BookAndKey(
        nextKey,
        this.document,
        OrdinalRange(this.document!!.ordinalRangeFor(nextKey, strings).first)
    )
}

suspend fun Book.ordinalRangeFor(key: Key, strings: net.bible.sharedcore.platform.CoreStrings): IntRange = SwordContentFacade.ordinalRangeFor(this, key, strings)

val Key.tinyName: String get() =
    if(this is VerseKey<*>)
        synchronized(BookName::class.java) {
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
        synchronized(BookName::class.java) {
            val oldValue = BookName.isFullBookName()
            BookName.setFullBookName(false)
            try {
                return getName()
            } finally {
                BookName.setFullBookName(oldValue)
            }
        }
    else getName()
