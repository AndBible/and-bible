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
package net.bible.service.db.bookmark

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkStyle
import org.apache.commons.lang3.StringUtils

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class LabelDto : Comparable<LabelDto> {
    var id: Long? = null
    var name: String? = null
		get() = if (bookmarkStyle == BookmarkStyle.SPEAK) {
			application.getString(R.string.speak)
		} else field

    var bookmarkStyle: BookmarkStyle? = null

    constructor()
	constructor(id: Long?, name: String?, bookmarkStyle: BookmarkStyle?) {
        this.id = id
        this.name = name
        this.bookmarkStyle = bookmarkStyle
    }

    override fun toString(): String {
        return name!!
    }

    /* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + if (id == null) 0 else id.hashCode()
        return result
    }

    /* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as LabelDto
        if (id == null) {
            if (other.id != null) return false
        } else if (id != other.id) return false
        return true
    }

    val bookmarkStyleAsString: String?
        get() = if (bookmarkStyle == null) {
            null
        } else {
            bookmarkStyle!!.name
        }

    fun setBookmarkStyleFromString(bookmarkStyle: String?) {
        if (StringUtils.isEmpty(bookmarkStyle)) {
            this.bookmarkStyle = null
        } else {
            this.bookmarkStyle = BookmarkStyle.valueOf(bookmarkStyle!!)
        }
    }

    override fun compareTo(another: LabelDto): Int {
        return name!!.compareTo(another.name!!, ignoreCase = true)
    }
}
