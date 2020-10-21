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
package net.bible.service.format.usermarks

import net.bible.android.control.ApplicationScope
import net.bible.service.db.mynote.MyNoteDBAdapter
import net.bible.service.db.mynote.MyNoteDto
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import java.util.*
import javax.inject.Inject

/**
 * Support display of verses with notes.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class MyNoteFormatSupport @Inject constructor() {
    fun getVersesWithNotesInPassage(passage: Key): List<Verse> {
        // assumes the passage only covers one book, which always happens to be the case here
        val firstVerse = KeyUtil.getVerse(passage)
        val book = firstVerse.book
        val db = MyNoteDBAdapter()
        var myNoteList: List<MyNoteDto>? = null
        myNoteList = try {
            db.getMyNotesInBook(book)
        } finally {
        }

        // convert to required versification and check verse is in passage
        val versesInPassage: MutableList<Verse> = ArrayList()
        if (myNoteList != null) {
            val isVerseRange = passage is VerseRange
            val requiredVersification = firstVerse.versification
            for (myNoteDto in myNoteList) {
                val verseRange = myNoteDto.getVerseRange(requiredVersification) ?: continue
                //TODO should not require VerseRange cast but bug in JSword
                if (isVerseRange) {
                    if ((passage as VerseRange).contains(verseRange.start)) {
                        versesInPassage.add(verseRange.start)
                    }
                } else {
                    if (passage.contains(verseRange)) {
                        versesInPassage.add(verseRange.start)
                    }
                }
            }
        }
        return versesInPassage
    }
}
