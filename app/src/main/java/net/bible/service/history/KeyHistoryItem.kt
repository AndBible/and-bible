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

package net.bible.service.history

import android.util.Log
import net.bible.android.control.page.OrdinalRange

import net.bible.android.control.page.window.Window
import net.bible.service.common.CommonUtils

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import java.util.*

/**
 * A normal item in the history list that relates to a document being shown in the main activity view
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class KeyHistoryItem(
    val document: Book,
    val key: Key,
    val anchorOrdinal: OrdinalRange?,
    window: Window,
    override val createdAt: Date = Date(System.currentTimeMillis()),
    // For Bible pages: the verse the user scrolled to (end of reading session)
    val endKey: Key? = null
) : HistoryItemBase(window) {

    // End position ordinal, used for persistence/restoration and for non-Bible document types.
    // For Bible pages, endKey is preferred for display; this field stores the ordinal for DB storage.
    // For other document types (commentaries, etc.), this stores the scroll position.
    var endAnchorOrdinal: OrdinalRange? = null

    override val description: String
        get() {
            val desc = StringBuilder()
            try {
                val startDesc = CommonUtils.getKeyDescription(key)
                val rangeDesc = formatRangeDescription(startDesc)
                desc.append(rangeDesc).append(" ").append(document.abbreviation)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting description", e)
            }

            return desc.toString()
        }

    /**
     * Format the description as a range (e.g., "Matt 5:1–48") if we have an end position.
     * For Bible pages, endKey contains the verse the user scrolled to.
     */
    private fun formatRangeDescription(startDesc: String): String {
        val startKey = key

        // Only show range for Verse keys (Bible content)
        if (startKey !is Verse) return startDesc

        // For Bible pages, use endKey (the verse user scrolled to)
        val endVerse = endKey as? Verse ?: return startDesc

        return try {
            when {
                // Same chapter: show "Matt 5:1–48"
                // Special case: if start verse is 0 (intro/title), use full end description
                // to avoid ambiguous output like "Genesis 1–10"
                endVerse.book == startKey.book &&
                endVerse.chapter == startKey.chapter &&
                endVerse.verse > startKey.verse -> {
                    if (startKey.verse == 0) {
                        val endDesc = CommonUtils.getKeyDescription(endVerse)
                        "$startDesc–$endDesc"
                    } else {
                        "$startDesc–${endVerse.verse}"
                    }
                }
                // Different chapter or book: show "Matt 5:1–6:2"
                endVerse.ordinal > startKey.ordinal -> {
                    val endDesc = CommonUtils.getKeyDescription(endVerse)
                    "$startDesc–$endDesc"
                }
                // End is not after start, just show start
                else -> startDesc
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting range description", e)
            startDesc
        }
    }

    /* (non-Javadoc)
	 * @see net.bible.service.history.HistoryItem#revertTo()
	 */
    override fun revertTo() {
        window.pageManager.setCurrentDocumentAndKey(document, key, anchorOrdinal=anchorOrdinal)
    }

    override fun toString(): String {
        return description
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (document.initials?.hashCode() ?: 0)
        result = prime * result + key.hashCode()
        return result
    }

    //TODO use Book.equals and Key.equals in the below
    override fun equals(obj: Any?): Boolean {
        if (this === obj)
            return true
        if (obj == null)
            return false
        if (javaClass != obj.javaClass)
            return false
        val other = obj as KeyHistoryItem?
        if (document.initials == null) {
            if (other!!.document.initials != null)
				return false
		} else if (document.initials != other!!.document.initials)
			return false
		if (key != other.key)
			return false
        return true
    }

    companion object {

        private val TAG = "KeyHistoryItem"
    }
}
