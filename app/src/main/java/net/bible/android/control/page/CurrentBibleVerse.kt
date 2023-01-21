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
package net.bible.android.control.page

import net.bible.android.common.entity
import net.bible.android.common.toV11n
import net.bible.android.control.versification.chapterVerse
import net.bible.android.database.WorkspaceEntities
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentBibleVerse {
    val entity get() = verse.entity
    var verse = Verse(
        Versifications.instance().getVersification(Versifications.DEFAULT_V11N),
        BibleBook.GEN, 1, 1
    )

    val currentBibleBookNo: Int
        get() = verse.book.ordinal

    val currentBibleBook: BibleBook
        get() = verse.book

    fun getVerseSelected(versification: Versification): Verse = verse.toV11n(versification)

    fun setVerseSelected(versification: Versification, verseSelected: Verse) {
        verse = verseSelected.toV11n(versification)
    }

    var chapterVerse: ChapterVerse
        get() = verse.chapterVerse
        set(chapterVerse) {
            verse = Verse(verse.versification, verse.book, chapterVerse.chapter, chapterVerse.verse)
        }

    val versificationOfLastSelectedVerse: Versification
        get() = verse.versification

    fun restoreFrom(verse: WorkspaceEntities.Verse) {
        this.verse = verse.jswordVerse
    }
}
