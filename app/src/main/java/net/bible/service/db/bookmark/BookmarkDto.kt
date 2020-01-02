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

import net.bible.android.control.speak.PlaybackSettings
import net.bible.android.control.versification.ConvertibleVerseRange
import net.bible.android.control.versification.sort.ConvertibleVerseRangeUser
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import java.util.*

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkDto : ConvertibleVerseRangeUser {
    var id: Long? = null
    private var convertibleVerseRange: ConvertibleVerseRange? = null
    var createdOn: Date? = null
    var playbackSettings: PlaybackSettings? = null

    var verseRange: VerseRange
        get() = convertibleVerseRange!!.verseRange
        set(verseRange) {
            convertibleVerseRange = ConvertibleVerseRange(verseRange)
        }

    fun getVerseRange(versification: Versification?): VerseRange {
        return convertibleVerseRange!!.getVerseRange(versification)
    }

    val speakBook: Book?
        get() = if (playbackSettings != null && playbackSettings!!.bookId != null) {
            Books.installed().getBook(playbackSettings!!.bookId)
        } else {
            null
        }

    override fun getConvertibleVerseRange(): ConvertibleVerseRange {
        return convertibleVerseRange!!
    }

    override fun toString(): String {
        return "BookmarkDto{" +
            "convertibleVerseRange=" + convertibleVerseRange +
            '}'
    }

    /* (non-Javadoc)
			 * @see java.lang.Object#hashCode()
			 */
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = if (convertibleVerseRange == null || convertibleVerseRange!!.verseRange == null) {
            prime * result
        } else {
            val verseRange = convertibleVerseRange!!.verseRange
            prime * result + verseRange.hashCode()
        }
        return result
    }

    /* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as BookmarkDto
        if (id == null) {
            if (other.id != null) return false
        } else if (id != other.id) return false
        if (convertibleVerseRange == null) {
            if (other.convertibleVerseRange != null) return false
        } else if (convertibleVerseRange != other.convertibleVerseRange) return false
        return true
    }
}
