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
import android.widget.TextView
import kotlinx.android.synthetic.main.manage_labels_list_item.view.*
import net.bible.android.activity.R
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.displayName

class ManageLabelItemAdapter(context: Context?,
                             private val resource: Int, items: List<BookmarkEntities.Label?>?,
                             private val manageLabels: ManageLabels,
                             private val checkedLabels: MutableSet<Long>,
                             ) : ArrayAdapter<BookmarkEntities.Label?>(context!!, resource, items!!)
{
    private val bookmarkStyleAdapterHelper = BookmarkStyleAdapterHelper()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val label = getItem(position)
        val rowView: View = if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            inflater.inflate(resource, parent, false)
        } else {
            convertView
        }
        val name = rowView.findViewById<View>(R.id.labelName) as TextView
        name.text = label!!.displayName
        val checkbox = rowView.findViewById<View>(R.id.checkbox) as CheckBox
        if(manageLabels.showCheckboxes) {
            name.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
            checkbox.setOnCheckedChangeListener { _, isChecked -> manageLabels.setEnabled(label, isChecked)}
            checkbox.isChecked = checkedLabels.contains(label.id)
        } else {
            checkbox.visibility = View.GONE
        }
        if(manageLabels.studyPadMode) {
            rowView.labelIcon.setImageResource(R.drawable.ic_pen_24dp)
        }
        bookmarkStyleAdapterHelper.styleView(name, label, context, false, false)
        rowView.editLabel.setOnClickListener { manageLabels.editLabel(label) }
        rowView.deleteLabel.setOnClickListener { manageLabels.delete(label) }
        rowView.editLabel.visibility = if(label.isSpeakLabel) View.INVISIBLE else View.VISIBLE
        rowView.deleteLabel.visibility = if (label.isSpeakLabel || label.isUnlabeledLabel) View.INVISIBLE else View.VISIBLE
        rowView.labelIcon.setColorFilter(label.color)
        if (nightMode) {
            rowView.editLabel.setImageResource(R.drawable.ic_pen_24dp)
            rowView.deleteLabel.setImageResource(R.drawable.ic_delete_24dp)
        } else {
            rowView.editLabel.setImageResource(R.drawable.ic_pen_24dp_black)
            rowView.deleteLabel.setImageResource(R.drawable.ic_delete_24dp_black)
        }
        return rowView
    }

    companion object {
        private const val TAG = "ManageLabelItemAdapter"
    }
}
