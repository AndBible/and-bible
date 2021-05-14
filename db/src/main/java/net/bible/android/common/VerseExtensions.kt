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

package net.bible.android.common

import net.bible.android.database.WorkspaceEntities
import org.crosswire.jsword.passage.Passage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.VersificationConverter
import kotlin.math.max

val converter = VersificationConverter()

val Verse.entity get() = WorkspaceEntities.Verse(versification.name, book.ordinal, chapter, verse)
fun Verse.isConvertibleTo(v11n: Versification): Boolean = converter.isConvertibleTo(this, v11n)
fun Verse.toV11n(v11n: Versification): Verse = converter.convert(this, v11n)
fun VerseRange.toV11n(v11n: Versification?): VerseRange {
    return if(v11n != null) {
        val startVerse = Verse(start.versification, start.book, max(start.chapter, 1), max(start.verse, 1))
        val endVerse = Verse(end.versification, end.book, max(end.chapter, 1), max(end.verse, 1))

        VerseRange(v11n, startVerse.toV11n(v11n), endVerse.toV11n(v11n))
    } else this
}
fun Passage.toV11n(v11n: Versification): Passage = converter.convert(this, v11n)

fun VerseRange.isConvertibleTo(v11n: Versification): Boolean = start.isConvertibleTo(v11n) && end.isConvertibleTo(v11n)

