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
package net.bible.service.db.mynote

import net.bible.android.control.versification.toV11n
import net.bible.android.database.bookmarks.VerseRangeUser
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import java.util.*

/**
 * DTO for MyNote
 *
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class MyNoteDto : VerseRangeUser {
    var id: Long? = null
    private var _verseRange: VerseRange? = null
    var noteText: String = ""
    var lastUpdatedOn: Date? = null
    var createdOn: Date? = null
    /** was this dto retrieved from the db
     */
    val isNew: Boolean
        get() = id == null

    val isEmpty: Boolean
        get() = StringUtils.isEmpty(noteText)

    override var verseRange: VerseRange
        get() = _verseRange!!
        set(verseRange) {
            _verseRange = verseRange
        }

    fun getVerseRange(versification: Versification): VerseRange = verseRange.toV11n(versification)

    override fun toString(): String {
        return "MyNoteDto{" +
            "convertibleVerseRange=" + _verseRange +
            ", noteText='" + noteText + '\'' +
            '}'
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = if (_verseRange == null || _verseRange == null) {
            prime * result
        } else {
            val verseRange = _verseRange
            prime * result + verseRange.hashCode()
        }
        return result
    }

    /*
	 * compare verse and note text
	 */
    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as MyNoteDto
        if (id == null) {
            if (other.id != null) return false
        } else if (id != other.id) return false
        if (_verseRange == null) {
            if (other._verseRange != null) return false
        } else if (_verseRange != other._verseRange) return false
        if (noteText == null) {
            if (other.noteText != null) return false
        } else if (noteText != other.noteText) return false
        return true
    }
}
