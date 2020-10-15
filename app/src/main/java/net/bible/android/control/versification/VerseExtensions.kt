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
import net.bible.android.database.bookmarks.converter
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification

fun Verse.isConvertibleTo(v11n: Versification): Boolean = converter.isConvertibleTo(this, v11n)
fun Verse.toV11n(v11n: Versification): Verse = converter.convert(this, v11n)
val Verse.entity get() = WorkspaceEntities.Verse(versification.name, book.ordinal, chapter, verse)
val Verse.chapterVerse: ChapterVerse get() = ChapterVerse(chapter, verse)

fun VerseRange.toV11n(v11n: Versification): VerseRange = VerseRange(v11n, start.toV11n(v11n), end.toV11n(v11n))
fun VerseRange.isConvertibleTo(v11n: Versification?): Boolean = start.isConvertibleTo(v11n!!) && end.isConvertibleTo(v11n)

