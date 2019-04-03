/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.reading_plan_list_single.view.*
import net.bible.service.db.readingplan.ReadingPlanInformationDB


/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ReadingPlanItemAdapter(context: Context, private val resource: Int, items: List<ReadingPlanInformationDB>):
		ArrayAdapter<ReadingPlanInformationDB>(context, resource, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val item = getItem(position)

        val view: View
        if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(resource, parent, false)
        } else {
            view = convertView
        }

        // Set value for the first text field
        if (view.planNameTextView != null) {
            var name = item?.planName ?: ""
            if (name == "") { name = item?.fileName ?: "" }
            view.planNameTextView.text = name
        }

        // set value for the second text field
        if (view.planDescriptionTextView != null) {
            view.planDescriptionTextView.text = item?.planDescription ?: ""
        }

        return view
    }
}
