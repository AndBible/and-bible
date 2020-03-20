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

package net.bible.service.history

import android.util.Log

import net.bible.android.control.page.window.Window
import net.bible.service.common.CommonUtils

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import java.util.*

/**
 * A normal item in the history list that relates to a document being shown in the main activity view
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class KeyHistoryItem(
    val document: Book,
    val key: Key,
    val yOffsetRatio: Float,
    window: Window,
    override val createdAt: Date = Date(System.currentTimeMillis())
) : HistoryItemBase(window) {

    override val description: String
        get() {
            val desc = StringBuilder()
            try {
                val verseDesc = CommonUtils.getKeyDescription(key)
                desc.append(document.abbreviation).append(" ").append(verseDesc)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting description", e)
            }

            return desc.toString()
        }

    /* (non-Javadoc)
	 * @see net.bible.service.history.HistoryItem#revertTo()
	 */
    override fun revertTo() {
        window.pageManager.setCurrentDocumentAndKey(document, key, yOffsetRatio=yOffsetRatio)
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
