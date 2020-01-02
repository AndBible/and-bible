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

import net.bible.android.control.versification.ConvertibleVerseRange
import net.bible.android.control.versification.sort.ConvertibleVerseRangeUser
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
class MyNoteDto : ConvertibleVerseRangeUser {
    var id: Long? = null
    private var convertibleVerseRange: ConvertibleVerseRange? = null
    var noteText: String = ""
    var lastUpdatedOn: Date? = null
    var createdOn: Date? = null
    /** was this dto retrieved from the db
     */
    val isNew: Boolean
        get() = id == null

    val isEmpty: Boolean
        get() = StringUtils.isEmpty(noteText)

    var verseRange: VerseRange
        get() = convertibleVerseRange!!.verseRange
        set(verseRange) {
            convertibleVerseRange = ConvertibleVerseRange(verseRange)
        }

    fun getVerseRange(versification: Versification?): VerseRange {
        return convertibleVerseRange!!.getVerseRange(versification)
    }

    override fun getConvertibleVerseRange(): ConvertibleVerseRange {
        return convertibleVerseRange!!
    }

    override fun toString(): String {
        return "MyNoteDto{" +
            "convertibleVerseRange=" + convertibleVerseRange +
            ", noteText='" + noteText + '\'' +
            '}'
    }

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
        if (convertibleVerseRange == null) {
            if (other.convertibleVerseRange != null) return false
        } else if (convertibleVerseRange != other.convertibleVerseRange) return false
        if (noteText == null) {
            if (other.noteText != null) return false
        } else if (noteText != other.noteText) return false
        return true
    }
}
