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
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.displayName

class ManageLabelItemAdapter(context: Context?,
                             items: List<BookmarkEntities.Label?>?,
                             private val manageLabels: ManageLabels,
                             private val checkedLabels: MutableSet<Long>,
                             ) : ArrayAdapter<BookmarkEntities.Label?>(context!!, R.layout.manage_labels_list_item, items!!)
{
    private val workspaceSettings get() = MainBibleActivity.mainBibleActivity.windowRepository.windowBehaviorSettings
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

        bindings.apply {
            labelName.text = label!!.displayName
            val checkbox = checkbox
            if (manageLabels.showCheckboxes) {
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (!manageLabels.selectMultiple) {
                            checkedLabels.clear()
                        }
                        checkedLabels.add(label.id)
                        if (workspaceSettings.autoAssignPrimaryLabel == 0L) {
                            workspaceSettings.autoAssignPrimaryLabel = label.id
                        }
                    } else {
                        checkedLabels.remove(label.id)
                        if (workspaceSettings.autoAssignPrimaryLabel == label.id) {
                            workspaceSettings.autoAssignPrimaryLabel = checkedLabels.toList().firstOrNull() ?: 0L
                        }
                    }
                    notifyDataSetChanged()
                }
                checkbox.isChecked = checkedLabels.contains(label.id)
            } else {
                checkbox.visibility = View.GONE
            }
            if (manageLabels.studyPadMode) {
                labelIcon.setImageResource(R.drawable.ic_baseline_studypads_24)
            }

            val isFavourite = workspaceSettings.favouriteLabels.contains(label.id)
            val isPrimary = workspaceSettings.autoAssignPrimaryLabel == label.id

            favouriteIcon.setImageResource(if (isFavourite) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24)
            favouriteIcon.setOnClickListener {
                if(isFavourite) {
                    workspaceSettings.favouriteLabels.remove(label.id)
                } else {
                    workspaceSettings.favouriteLabels.add(label.id)
                }
                notifyDataSetChanged()
            }

            if (manageLabels.assignMode || manageLabels.autoAssignMode) {
                primaryIcon.visibility = if (checkbox.isChecked) View.VISIBLE else View.INVISIBLE
                primaryIcon.setImageResource(if (isPrimary) R.drawable.ic_baseline_star_24 else R.drawable.ic_baseline_star_border_24)
                primaryIcon.setOnClickListener {
                    workspaceSettings.autoAssignPrimaryLabel = label.id
                    notifyDataSetChanged()
                }
            } else {
                primaryIcon.visibility = View.GONE
            }

            if (workspaceSettings.autoAssignLabels.contains(label.id)) {
                labelIcon.setImageResource(R.drawable.ic_label_circle)
            } else {
                labelIcon.setImageResource(R.drawable.ic_label_24dp)
            }

            labelIcon.setOnClickListener {
                if (workspaceSettings.autoAssignLabels.contains(label.id)) {
                    workspaceSettings.autoAssignLabels.remove(label.id)
                } else {
                    workspaceSettings.autoAssignLabels.add(label.id)
                }
                notifyDataSetChanged()
            }

            bookmarkStyleAdapterHelper.styleView(labelName, label, context, false, false)
            root.setOnClickListener { manageLabels.editLabel(label) }
            labelIcon.setColorFilter(label.color)
        }
        return convertView?: bindings.root
    }

    companion object {
        private const val TAG = "ManageLabelItemAdapter"
    }
}
