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

package net.bible.service.sword

import net.bible.service.download.doesNotExist
import org.crosswire.common.util.ItemIterator
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.RestrictionType
import java.lang.UnsupportedOperationException

class BookAndKey(val key: Key, document: Book? = null): Key {
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

    override fun compareTo(other: Key?): Int {
        return key.compareTo(other)
    }

    override fun iterator(): MutableIterator<Key> {
        return ItemIterator(this)
    }

    override fun clone(): Key {
        return BookAndKey(key.clone(), document)
    }

    override fun getName(): String {
        return if(document == null) key.name else "${document?.abbreviation}: ${key.name}"
    }

    override fun getName(base: Key?): String {
        return name
    }

    override fun getRootName(): String {
        return name
    }

    override fun getOsisRef(): String {
        return "${document?.initials}:${key.osisRef}"
    }

    override fun getOsisID(): String {
        return "${document?.initials}:${key.osisID}"
    }

    override fun getParent(): Key? {
        return null
    }

    override fun canHaveChildren(): Boolean {
        return false
    }

    override fun getChildCount(): Int {
        return 0
    }

    override fun getCardinality(): Int {
        return 1
    }

    override fun isEmpty(): Boolean {
        return false
    }

    override fun contains(key: Key?): Boolean {
        return this == key
    }

    override fun addAll(key: Key?) {
        throw UnsupportedOperationException()
    }

    override fun removeAll(key: Key?) {
        throw UnsupportedOperationException()
    }

    override fun retainAll(key: Key?) {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        throw UnsupportedOperationException()
    }

    override fun get(index: Int): Key? {
        if(index == 0) return this
        return null
    }

    override fun indexOf(that: Key?): Int {
        if(this == that) return 0
        return -1
    }

    override fun blur(by: Int, restrict: RestrictionType?) {
        throw UnsupportedOperationException()
    }

    override fun blur(by: Int, restrict: RestrictionType?, blurDown: Boolean, blurUp: Boolean) {
        throw UnsupportedOperationException()
    }

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
