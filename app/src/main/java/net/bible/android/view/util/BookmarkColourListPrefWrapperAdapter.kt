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

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.TextView
import net.bible.android.control.bookmark.BookmarkStyle
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper
import net.bible.android.view.util.widget.ListPrefWrapperAdapter

/**
 * Set each list view item to represent background colour od icon of the relevant bookmark style.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkColourListPrefWrapperAdapter(private val context: Context, origAdapter: ListAdapter, val selectedPosition: Int) :
	ListPrefWrapperAdapter(origAdapter)
{
    private val bookmarkStyleAdapterHelper: BookmarkStyleAdapterHelper = BookmarkStyleAdapterHelper()

	override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
		val view = super.getView(position, convertView, parent) as TextView
		//val view = parent.getChildAt(0) as AppCompatRadioButton
		val bookmarkStyle = BookmarkStyle.values()[position]
		bookmarkStyleAdapterHelper.styleView(view, bookmarkStyle, context, true, true, selectedPosition == position)
        return view
    }

}
