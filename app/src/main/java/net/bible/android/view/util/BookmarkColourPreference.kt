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
package net.bible.android.view.util

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.preference.ListPreference
import android.util.AttributeSet
import android.widget.ListAdapter
import net.bible.android.control.bookmark.BookmarkStyle

/**
 * Allow selection of default Bookmark colour preference.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkColourPreference : ListPreference {
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initialise()
    }

    constructor(context: Context?) : super(context) {
        initialise()
    }

    private fun initialise() {
        val numValues = BookmarkStyle.values().size - 1 // Do not count Speak bookmark style
        val styles = arrayOfNulls<CharSequence>(numValues)
        for (i in 0 until numValues) {
            val bookmarkStyle = BookmarkStyle.values()[i]
            styles[i] = bookmarkStyle.name
        }
        entries = styles
        entryValues = styles
        setDefaultValue(styles[0])
    }

    override fun showDialog(state: Bundle?) {
        super.showDialog(state)
        val dialog = dialog as AlertDialog
        val listView = dialog.listView
        val adapter = listView.adapter
        val fontTypeAdapter = createWrapperAdapter(adapter)
        // Adjust the selection because resetting the adapter loses the selection.
        val selectedPosition = findIndexOfValue(value)
        listView.adapter = fontTypeAdapter
        if (selectedPosition != -1) {
            listView.setItemChecked(selectedPosition, true)
            listView.setSelection(selectedPosition)
        }
    }

    protected fun createWrapperAdapter(origAdapter: ListAdapter?): BookmarkColourListPrefWrapperAdapter {
        return BookmarkColourListPrefWrapperAdapter(context, origAdapter)
    }
}
