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

package net.bible.android.view.activity.readingplan

import net.bible.service.readingplan.OneDaysReadingsDto
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TwoLineListItem
import java.text.SimpleDateFormat

/**
 * Retain similar style to TwoLineListView but for single TextView on each line
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DailyReadingItemAdapter(_context: Context, private val resource: Int, _items: List<OneDaysReadingsDto>) :
		ArrayAdapter<OneDaysReadingsDto>(_context, resource, _items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val item = getItem(position)

        // Pick up the TwoLineListItem defined in the xml file
        val view = when (convertView) {
            null -> {
                val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                inflater.inflate(resource, parent, false) as TwoLineListItem
            }
            else -> convertView as TwoLineListItem
        }
        item ?: return view

        // Set value for the first text field
        if (view.text1 != null) {
            view.text1.text = if (item.isDateBasedPlan) {
                SimpleDateFormat.getDateInstance().format(item.readingDate)
            } else {
                item.dayDesc
            }
        }

        // set value for the second text field
        if (view.text2 != null) {
            val line2 = item.readingsDesc
            view.text2.text = line2
        }

        return view
    }
}
