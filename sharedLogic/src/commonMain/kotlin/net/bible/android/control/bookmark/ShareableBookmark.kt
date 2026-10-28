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
package net.bible.android.control.bookmark

import kotlin.coroutines.cancellation.CancellationException
import net.bible.android.common.isConvertibleTo
import net.bible.android.common.toV11n
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.basic.AbstractPassageBook

private const val TAG = "ShareableBookmark"

/**
 * The bookmark to share for [bookmark]. A translation-independent bookmark (no `book`) needs a Bible to
 * resolve shareable verse text, so it is moved onto [defaultBible] in that Bible's versification, as a
 * whole-verse range (character offsets dropped). A bookmark with its own Bible is returned as it is.
 *
 * When no Bible can be resolved ([defaultBible] throws, e.g. none installed) or the range does not exist in
 * the default Bible's versification (e.g. a KJVA Apocrypha verse with a KJV default), the bookmark itself is
 * returned, which shares its note without verse text. Both cases are logged. [bookmark] is never modified.
 */
fun shareableBookmark(
    bookmark: BibleBookmarkWithNotes,
    defaultBible: () -> AbstractPassageBook,
): BibleBookmarkWithNotes {
    if (bookmark.book != null) return bookmark
    val book = try {
        defaultBible()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No default Bible to share bookmark ${bookmark.id} with; sharing it without verse text", e)
        return bookmark
    }
    val versification = book.versification
    val range = try {
        if (!bookmark.verseRange.isConvertibleTo(versification)) {
            Log.i(TAG, "Bookmark ${bookmark.id} range is not in ${book.initials}'s versification ${versification.name}; sharing it without verse text")
            return bookmark
        }
        bookmark.verseRange.toV11n(versification)
    } catch (e: Exception) {
        Log.w(TAG, "Could not convert bookmark ${bookmark.id} to ${versification.name}; sharing it without verse text", e)
        return bookmark
    }
    return bookmark.copy(
        book = book, v11n = range.versification,
        ordinalStart = range.start.ordinal, ordinalEnd = range.end.ordinal,
        startOffset = null, endOffset = null,
    )
}
