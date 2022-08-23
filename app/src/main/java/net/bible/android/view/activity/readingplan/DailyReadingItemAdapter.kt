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

package net.bible.android.view.activity.readingplan

import net.bible.service.readingplan.OneDaysReadingsDto
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TwoLineListItem
import net.bible.android.activity.databinding.TwoLineListItemBinding
import java.text.SimpleDateFormat

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DailyReadingItemAdapter(_context: Context, private val resource: Int, _items: List<OneDaysReadingsDto>) :
		ArrayAdapter<OneDaysReadingsDto>(_context, resource, _items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val item = getItem(position)

        val binding = if (convertView == null)
            TwoLineListItemBinding.inflate(context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater, parent, false)
        else TwoLineListItemBinding.bind(convertView)
        val view = convertView ?: binding.root
        item ?: return view

        // Set value for the first text field
        binding.text1.text = if (item.isDateBasedPlan && item.readingDate != null) {
            SimpleDateFormat.getDateInstance().format(item.readingDate!!)
        } else {
            item.dayDesc
        }

        // set value for the second text field
        val line2 = item.readingsDesc
        binding.text2.text = line2

        return view
    }
}
