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

package net.bible.service.sword

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.versification.toVerseRange
import net.bible.android.view.activity.bookmark.LabelEditActivity
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.ordinalRangeFor
import net.bible.service.common.shortName
import net.bible.service.download.doesNotExist
import org.crosswire.common.util.ItemIterator
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.RestrictionType
import java.lang.UnsupportedOperationException
val BookAndKey.ordinalRange: IntRange? get() = document?.ordinalRangeFor(key)

@Serializable
class BookAndKeySerialized(
    val key: String,
    val document: String,
    val ordinalRange: OrdinalRange?,
    val htmlId: String?,
) {
    val bookAndKey: BookAndKey get () {
        val book = Books.installed().getBook(document)
        var key = book.getKey(key)
        if(key is RangedPassage) {
           key = key.toVerseRange
        }
        return BookAndKey(key, book, ordinalRange, htmlId)
    }

    companion object {
        fun fromJSON(str: String): BookAndKeySerialized = json.decodeFromString(serializer(), str)
    }
}

class BookAndKey(
    _key: Key,
    document: Book? = null,
    @Transient val ordinal: OrdinalRange? = null,
    @Transient val htmlId: String? = null
): Key {
    val serialized: String get() {
        val s = BookAndKeySerialized(key.osisRef, documentInitials, ordinal, htmlId)
        return json.encodeToString(serializer(), s)
    }

    val key: Key = if(_key is BookAndKey) {
        if(_key.document != document) {
            throw RuntimeException("Document does not match")
        }
        _key.key
    } else _key

    private val documentInitials = document?.initials?: ""

    @Transient var _document: Book? = document

    val document: Book?
        get() {
            if (_document == null) {
                _document = Books.installed().getBook(documentInitials)
            }

            if(_document?.doesNotExist == true) {
                val real = Books.installed().getBook(documentInitials)
                if(real != null) {
                    _document = real
                }
            }
            return _document
        }

    override fun compareTo(other: Key?): Int = key.compareTo(other)

    override fun iterator(): MutableIterator<Key> = ItemIterator(this)

    override fun clone(): Key = BookAndKey(key.clone(), document)

    override fun getName(): String = if(document == null) key.name else "${document?.abbreviation}: ${key.name}"

    val shortName: String get() {
        return if(document == null) key.shortName else "${document?.abbreviation}: ${key.shortName}"
    }

    override fun getName(base: Key?): String = name

    override fun getRootName(): String = name

    override fun getOsisRef(): String = "${document?.initials}:${key.osisRef}"

    override fun getOsisID(): String = "${document?.initials}:${key.osisID}"

    override fun getParent(): Key? = null

    override fun canHaveChildren(): Boolean = false

    override fun getChildCount(): Int = 0

    override fun getCardinality(): Int = 1

    override fun isEmpty(): Boolean = false

    override fun contains(key: Key?): Boolean = this == key

    override fun addAll(key: Key?): Unit = throw UnsupportedOperationException()

    override fun removeAll(key: Key?): Unit = throw UnsupportedOperationException()

    override fun retainAll(key: Key?): Unit = throw UnsupportedOperationException()

    override fun clear(): Unit = throw UnsupportedOperationException()

    override fun get(index: Int): Key? {
        if(index == 0) return this
        return null
    }

    override fun indexOf(that: Key?): Int {
        if(this == that) return 0
        return -1
    }

    override fun blur(by: Int, restrict: RestrictionType?): Unit = throw UnsupportedOperationException()

    override fun blur(by: Int, restrict: RestrictionType?, blurDown: Boolean, blurUp: Boolean): Unit =
        throw UnsupportedOperationException()

    companion object {
        private const val serialVersionUID: Long = 1
    }
}

class BookAndKeyList: DefaultKeyList() {
    override fun getOsisRef(): String {
        return this.joinToString("||") { it.osisRef }
    }

    companion object {
        private const val serialVersionUID: Long = 1
    }
}

fun bookAndKeyListOf(keys: Collection<BookAndKey>): BookAndKeyList {
    val list = BookAndKeyList()
    for (it in keys) {
        list.addAll(it)
    }
    return list
}
