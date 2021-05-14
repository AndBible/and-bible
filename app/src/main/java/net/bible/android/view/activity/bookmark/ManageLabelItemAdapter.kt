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
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ManageLabelsListItemBinding
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.displayName

class ManageLabelItemAdapter(context: Context?,
                             items: List<BookmarkEntities.Label?>?,
                             private val manageLabels: ManageLabels,
                             private val checkedLabels: MutableSet<Long>,
                             ) : ArrayAdapter<BookmarkEntities.Label?>(context!!, R.layout.manage_labels_list_item, items!!)
{
    private val bookmarkStyleAdapterHelper = BookmarkStyleAdapterHelper()
    private lateinit var bindings: ManageLabelsListItemBinding
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val label = getItem(position)

        bindings = if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            ManageLabelsListItemBinding.inflate(inflater, parent, false)
        } else {
            ManageLabelsListItemBinding.bind(convertView)
        }        

        val name = bindings.labelName
        
        name.text = label!!.displayName
        val checkbox = bindings.checkbox
        if(manageLabels.showCheckboxes) {
            name.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
            checkbox.setOnCheckedChangeListener { _, isChecked -> manageLabels.setEnabled(label, isChecked)}
            checkbox.isChecked = checkedLabels.contains(label.id)
        } else {
            checkbox.visibility = View.GONE
        }
        if(manageLabels.studyPadMode) {
            bindings.labelIcon.setImageResource(R.drawable.ic_baseline_studypads_24)
        }

        val isFavourite = manageLabels.favouriteLabels.contains(label.id)
        val isPrimary = manageLabels.primaryLabel == label.id

        if(manageLabels.assignMode || manageLabels.autoAssignMode) {
            bindings.primaryIcon.visibility = if(checkbox.isChecked) View.VISIBLE else View.INVISIBLE
            bindings.primaryIcon.setImageResource(if(isPrimary) R.drawable.ic_baseline_star_24 else R.drawable.ic_baseline_star_border_24)
            bindings.primaryIcon.setOnClickListener {
                manageLabels.primaryLabel = label.id
                notifyDataSetChanged()
            }
        } else {
            bindings.primaryIcon.visibility = View.GONE
        }

        if(manageLabels.autoAssignMode) {
            bindings.favouriteIcon.visibility = View.VISIBLE
            bindings.favouriteIcon.setImageResource(if(isFavourite) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24)

            bindings.favouriteIcon.setOnClickListener {
                if(isFavourite) {
                    manageLabels.favouriteLabels.remove(label.id)
                } else {
                    manageLabels.favouriteLabels.add(label.id)
                }
                notifyDataSetChanged()
            }

        } else {
            bindings.favouriteIcon.visibility = View.GONE
        }
        bookmarkStyleAdapterHelper.styleView(name, label, context, false, false)
        bindings.editLabel.setOnClickListener { manageLabels.editLabel(label) }
        bindings.deleteLabel.setOnClickListener { manageLabels.delete(label) }
        bindings.editLabel.visibility = if(label.isSpeakLabel) View.INVISIBLE else View.VISIBLE
        bindings.deleteLabel.visibility = if (label.isSpeakLabel || label.isUnlabeledLabel) View.INVISIBLE else View.VISIBLE
        bindings.labelIcon.setColorFilter(label.color)
        if (nightMode) {
            bindings.editLabel.setImageResource(R.drawable.ic_pen_24dp)
            bindings.deleteLabel.setImageResource(R.drawable.ic_delete_24dp)
        } else {
            bindings.editLabel.setImageResource(R.drawable.ic_pen_24dp_black)
            bindings.deleteLabel.setImageResource(R.drawable.ic_delete_24dp_black)
        }
        return convertView?: bindings.root
    }

    companion object {
        private const val TAG = "ManageLabelItemAdapter"
    }
}
