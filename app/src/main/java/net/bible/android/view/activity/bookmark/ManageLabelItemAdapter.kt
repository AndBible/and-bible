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
import net.bible.service.device.ScreenSettings.nightMode
import android.widget.ArrayAdapter
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.displayName

class ManageLabelItemAdapter(context: Context?,
                             private val resource: Int, items: List<BookmarkEntities.Label?>?,
                             private val manageLabels: ManageLabels,
                             private val checkedLabels: MutableSet<Long>,
                             private val showCheckboxes: Boolean
                             ) : ArrayAdapter<BookmarkEntities.Label?>(context!!, resource, items!!)
{
    private val bookmarkStyleAdapterHelper = BookmarkStyleAdapterHelper()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val label = getItem(position)
        val rowView: View
        rowView = if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(resource, parent, false)
        } else {
            convertView
        }
        val name = rowView.findViewById<View>(R.id.labelName) as TextView
        name.text = label!!.displayName
        val checkbox = rowView.findViewById<View>(R.id.checkbox) as CheckBox
        name.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
        if(showCheckboxes) {
            checkbox.isChecked = checkedLabels.contains(label.id)
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checkedLabels.add(label.id) else checkedLabels.remove(label.id)
            }
        } else {
            checkbox.visibility = View.GONE
        }
        bookmarkStyleAdapterHelper.styleView(name, label, context, false, false)
        val editButton = rowView.findViewById<View>(R.id.editLabel) as ImageView
        editButton.setOnClickListener { manageLabels.editLabel(label) }
        val deleteButton = rowView.findViewById<View>(R.id.deleteLabel) as ImageView
        deleteButton.setOnClickListener { manageLabels.delete(label) }
        if (label.isSpeakLabel) {
            editButton.visibility = View.GONE
            deleteButton.visibility = View.GONE
        } else {
            editButton.visibility = View.VISIBLE
            deleteButton.visibility = View.VISIBLE
        }
        if (nightMode) {
            editButton.setImageResource(R.drawable.ic_pen_24dp)
            deleteButton.setImageResource(R.drawable.ic_delete_24dp)
        } else {
            editButton.setImageResource(R.drawable.ic_pen_24dp_black)
            deleteButton.setImageResource(R.drawable.ic_delete_24dp_black)
        }
        return rowView
    }

    companion object {
        private const val TAG = "ManageLabelItemAdapter"
    }
}
