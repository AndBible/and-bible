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
package net.bible.android.control.versification

import net.bible.android.control.page.ChapterVerse
import net.bible.android.database.WorkspaceEntities
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications

/** Store a main verse and return it in requested versification after mapping (if map available)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ConvertibleVerse(
	versification: Versification,
	book: BibleBook,
	chapter: Int,
	verseNo: Int
) {

    constructor(verse: Verse) : this(verse.versification, verse.book, verse.chapter, verse.verse)
    constructor(book: BibleBook, chapter: Int, verseNo: Int) :
		this(Versifications.instance().getVersification(Versifications.DEFAULT_V11N),
			book, chapter, verseNo)

	val entity get() = WorkspaceEntities.Verse(
		verse.versification.name,
		verse.book.ordinal,
		verse.chapter,
		verse.verse
	)
	var verse: Verse = Verse(versification, book, chapter, verseNo)
		private set

    fun isConvertibleTo(v11n: Versification?): Boolean {
        return versificationConverter.isConvertibleTo(verse, v11n)
    }

    var chapterVerse: ChapterVerse
        get() = ChapterVerse(verse.chapter, verse.verse)
        set(chapterVerse) {
            verse = Verse(verse.versification, verse.book, chapterVerse.chapter, chapterVerse.verse)
        }

    /** Set the verse, mapping to the required versification if necessary
     */
    fun setVerse(requiredVersification: Versification?, verse: Verse?) {
        this.verse = versificationConverter.convert(verse, requiredVersification)
    }

    fun getVerse(versification: Versification?): Verse {
        return versificationConverter.convert(verse, versification)
    }

    /** books should be the same as they are enums
     */
    val book: BibleBook
        get() = verse.book

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = (prime * result
            + verse.hashCode())
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (javaClass != other.javaClass) return false
        val other = other as ConvertibleVerse
		if (verse != other.verse) return false
        return true
    }

    fun restoreFrom(entity: WorkspaceEntities.Verse) {
        val v11n = Versifications.instance().getVersification(entity.versification)
        val bibleBookNo = entity.bibleBook
        val chapterNo = entity.chapterNo
        val verseNo = entity.verseNo
        verse = Verse(v11n, BibleBook.values()[bibleBookNo], chapterNo, verseNo, true)
    }

    companion object {
        private val versificationConverter = VersificationConverter()
    }
}
