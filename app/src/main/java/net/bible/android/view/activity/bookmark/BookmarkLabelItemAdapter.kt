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
package net.bible.android.view.activity.bookmark

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import net.bible.android.view.util.UiUtils.getThemeBackgroundColour
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper
import net.bible.service.db.bookmark.LabelDto

/**
 * Adapter which shows highlight colour of labels
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkLabelItemAdapter(context: Context, items: List<LabelDto>)
    : ArrayAdapter<LabelDto?>(context, android.R.layout.simple_list_item_multiple_choice, items)
{
    private val bookmarkStyleAdapterHelper = BookmarkStyleAdapterHelper()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val labelDto = getItem(position)!!
        val rowView: View
        rowView = if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
        } else {
            convertView
        }
        val nameView = rowView as CheckedTextView
        nameView.text = labelDto.name
        if (labelDto.bookmarkStyle == null) {
            nameView.setBackgroundColor(getThemeBackgroundColour(context))
        } else {
            bookmarkStyleAdapterHelper.styleView(nameView, labelDto.bookmarkStyle, context, false, false)
        }
        return rowView
    }

    companion object {
        private const val TAG = "BookmarkLabelItemAdapter"
    }

}
