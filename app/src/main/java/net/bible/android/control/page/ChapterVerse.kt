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

import org.crosswire.jsword.passage.Verse

/**
 * Represent a chapter and verse
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
data class ChapterVerse(val chapter: Int, val verse: Int) {

    /**
     * The format used for ids in html
     */
    fun toHtmlId(): String = "$chapter.$verse"
    fun toChapterHtmlId(): String = chapter.toString()

    fun after(other: ChapterVerse?): Boolean =
            other != null && (chapter > other.chapter || (chapter == other.chapter && verse > other.verse))

    fun before(other: ChapterVerse?): Boolean =
            other != null && (chapter < other.chapter || (chapter == other.chapter && verse < other.verse))

    fun sameChapter(other: ChapterVerse?): Boolean =
            other != null && (chapter == other.chapter)

    companion object {
        @JvmStatic fun fromHtmlId(chapterDotVerse: String): ChapterVerse {
            val strings = chapterDotVerse.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val chapter = Integer.parseInt(strings[0])
            val verse = Integer.parseInt(strings[1])
            return ChapterVerse(chapter, verse)
        }

        @JvmStatic fun fromVerse(pVerse: Verse): ChapterVerse {
            val chapter = pVerse.chapter
            val verse = pVerse.verse
            return ChapterVerse(chapter, verse)
        }
    }
}
