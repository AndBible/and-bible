/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import net.bible.android.database.bookmarks.BookmarkEntities
import org.crosswire.common.util.ItemIterator
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.RestrictionType
import java.lang.UnsupportedOperationException

class StudyPadKey(@Transient val label: BookmarkEntities.Label): Key {
    private val labelId = label.id

    override fun compareTo(other: Key?): Int {
        return if(other is StudyPadKey) compareValues(labelId, other.labelId)
        else return -1
    }

    override fun iterator(): MutableIterator<Key> {
        return ItemIterator(this)
    }

    override fun clone(): Key {
        return StudyPadKey(label)
    }

    override fun getName(): String {
        return label.name
    }

    override fun getName(base: Key?): String {
        return name
    }

    override fun getRootName(): String {
        return name
    }

    override fun getOsisRef(): String {
        return "journal:${labelId}"
    }

    override fun getOsisID(): String {
        return osisRef
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
        if (index == 0) return this
        return null
    }

    override fun indexOf(that: Key?): Int {
        if (this == that) return 0
        return -1
    }

    override fun blur(by: Int, restrict: RestrictionType?) {
        throw UnsupportedOperationException()
    }

    companion object {
        private const val serialVersionUID: Long = 1

    }
}
