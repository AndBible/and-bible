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
package net.bible.android.control.page

import net.bible.android.control.versification.ConvertibleVerse
import net.bible.android.database.WorkspaceEntities
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentBibleVerse {
    val entity get() = verseVersificationSelected.entity
    private val verseVersificationSelected = ConvertibleVerse(
        Versifications.instance().getVersification(Versifications.DEFAULT_V11N),
        BibleBook.GEN, 1, 1
    )
    val currentBibleBookNo: Int
        get() = verseVersificationSelected.book.ordinal

    val currentBibleBook: BibleBook
        get() = verseVersificationSelected.book

    fun getVerseSelected(versification: Versification?): Verse {
        return verseVersificationSelected.getVerse(versification)
    }

    fun setVerseSelected(versification: Versification?, verseSelected: Verse?) {
        verseVersificationSelected.setVerse(versification, verseSelected)
    }

    var chapterVerse: ChapterVerse
        get() = verseVersificationSelected.chapterVerse
        set(chapterVerse) {
            verseVersificationSelected.chapterVerse = chapterVerse
        }

    val versificationOfLastSelectedVerse: Versification
        get() = verseVersificationSelected.verse.versification

    fun restoreFrom(verse: WorkspaceEntities.Verse) {
        verseVersificationSelected.restoreFrom(verse)
    }
}
