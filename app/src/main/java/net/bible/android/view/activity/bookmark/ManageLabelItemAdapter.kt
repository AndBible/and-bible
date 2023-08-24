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
package net.bible.android.view.activity.bookmark

import android.content.Context
import android.graphics.Typeface
import android.util.Log
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
                             items: List<Any>?,
                             private val manageLabels: ManageLabels,
                             ) : ArrayAdapter<Any>(context!!, R.layout.manage_labels_list_item, items!!)
{
    private val data get() = manageLabels.data
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
        if(label is LabelCategory) {
            bindings.labelCategory.visibility = View.GONE
            bindings.categoryTitle.visibility = View.VISIBLE
            bindings.categoryTitle.text = when(label) {
                LabelCategory.ACTIVE -> manageLabels.getString(R.string.active_labels)
                LabelCategory.RECENT -> manageLabels.getString(R.string.recent_labels)
                LabelCategory.OTHER -> manageLabels.getString(R.string.other_labels)
            }
            bindings.root.setOnClickListener(null)
            bindings.root.setBackgroundResource(R.color.transparent)

        }
        else if(label is BookmarkEntities.Label) {
            bindings.apply {
                val highlight = label.id == manageLabels.highlightLabel?.id
                labelName.setTypeface(null, if(highlight) Typeface.BOLD else Typeface.NORMAL)
                root.setBackgroundResource(if(highlight) R.drawable.selectable_background_highlight else R.drawable.selectable_background)

                labelCategory.visibility = View.VISIBLE
                categoryTitle.visibility = View.GONE
                labelName.text = label.displayName
                val checkbox = checkbox
                if (data.showCheckboxes) {
                    checkbox.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            data.selectedLabels.add(label.id)
                            if (data.bookmarkPrimaryLabel == null) {
                                data.bookmarkPrimaryLabel = label.id
                            }
                        } else {
                            data.selectedLabels.remove(label.id)
                            manageLabels.ensureNotBookmarkPrimaryLabel(label)
                        }
                        manageLabels.updateLabelList()
                    }
                    checkbox.isChecked = data.contextSelectedItems.contains(label.id)
                } else {
                    checkbox.visibility = View.GONE
                }
                if (data.mode == ManageLabels.Mode.STUDYPAD) {
                    labelIcon.setImageResource(R.drawable.ic_baseline_studypads_24)
                }

                val isFavourite = label.favourite
                val isPrimary = data.contextPrimaryLabel == label.id

                favouriteIcon.setImageResource(if (isFavourite) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24)
                favouriteIcon.setOnClickListener {
                    label.favourite = !label.favourite
                    manageLabels.data.changedLabels.add(label.id)
                    notifyDataSetChanged()
                }

                favouriteIcon.visibility = if (data.workspaceEdits) View.VISIBLE else View.GONE

                if (data.primaryShown) {
                    primaryIcon.visibility = if (data.contextSelectedItems.contains(label.id)) View.VISIBLE else View.GONE
                    primaryIcon.setImageResource(if (isPrimary) R.drawable.ic_baseline_bookmark_24 else R.drawable.ic_bookmark_24dp)
                    primaryIcon.setOnClickListener {
                        data.contextPrimaryLabel = label.id
                        notifyDataSetChanged()
                    }
                } else {
                    primaryIcon.visibility = View.GONE
                }

                if(data.workspaceEdits && label.isUnlabeledLabel) {
                    favouriteIcon.visibility = View.INVISIBLE
                    primaryIcon.visibility = View.GONE
                }

                if (data.workspaceEdits && !label.isUnlabeledLabel) {
                    if (data.autoAssignLabels.contains(label.id)) {
                        labelIcon.setImageResource(R.drawable.ic_label_circle)
                    } else {
                        labelIcon.setImageResource(R.drawable.ic_label_24dp)
                    }

                    labelIcon.setOnClickListener {
                        if (data.autoAssignLabels.contains(label.id)) {
                            data.autoAssignLabels.remove(label.id)
                            manageLabels.ensureNotAutoAssignPrimaryLabel(label)
                        } else {
                            data.autoAssignLabels.add(label.id)
                            if (data.autoAssignPrimaryLabel == null) {
                                data.autoAssignPrimaryLabel = label.id
                            }
                        }
                        manageLabels.updateLabelList()
                    }
                } else {
                    labelIcon.setImageResource(R.drawable.ic_label_24dp)
                }
                labelIcon.setColorFilter(label.color)

                // TODO: implement otherwise
                bookmarkStyleAdapterHelper.styleView(labelName, label, context, false, false)
                if (data.mode != ManageLabels.Mode.STUDYPAD) {
                    root.setOnClickListener {
                        Log.i(TAG, "Edit label clicked")
                        manageLabels.editLabel(label)
                    }
                    // ensure that this listener is cleared when the mode is not STUDYPAD
                    root.setOnLongClickListener(null)
                }
                // In STUDYPAD Mode:
                //      * click selects the label and finishes the activity
                //      * long-click navigates to the edit label activity
                else {
                    root.setOnClickListener {
                        Log.i(TAG, "Select (studypad) label clicked")
                        manageLabels.selectStudyPadLabel(label)
                    }

                    root.setOnLongClickListener {
                        Log.i(TAG, "Edit label long-clicked (in STUDYPAD mode)")
                        manageLabels.editLabel(label)
                        true
                    }
                } 
            }
        }
        return convertView?: bindings.root
    }


    companion object {
        private const val TAG = "ManageLabelItemAdapter"
    }
}
