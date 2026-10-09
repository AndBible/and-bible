/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.control.progress

import net.bible.android.database.progress.ReadingSource
import net.bible.sharedcore.platform.OrderedLauncher
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification

/** Fire-and-forget progress writes from BibleView JS, serialized per window (spec L1a §4). */
class ProgressJsActions(private val launcher: OrderedLauncher) {
    fun markMemorized(windowId: Any, range: VerseRange) = launcher.launch(windowId) { ProgressControl.markVerseMemorized(range) }
    fun unmarkMemorized(windowId: Any, range: VerseRange) = launcher.launch(windowId) { ProgressControl.unmarkVerseMemorized(range) }
    fun addTarget(windowId: Any, range: VerseRange) = launcher.launch(windowId) { ProgressControl.addMemorizationTarget(range) }
    fun addTargetIfNeeded(windowId: Any, range: VerseRange) = launcher.launch(windowId) { ProgressControl.addMemorizationTargetIfNeeded(range) }
    fun removeTargetByRange(windowId: Any, range: VerseRange) = launcher.launch(windowId) { ProgressControl.removeMemorizationTargetByRange(range) }
    fun recordChapterRead(
        windowId: Any,
        v11n: Versification,
        book: BibleBook,
        chapter: Int,
        bookInitials: String,
        source: ReadingSource,
    ) = launcher.launch(windowId) { ProgressControl.recordChapterRead(v11n, book, chapter, bookInitials, source) }
}
