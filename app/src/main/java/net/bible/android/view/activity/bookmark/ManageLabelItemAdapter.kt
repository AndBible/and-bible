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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.ArrayAdapter
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ManageLabelsListItemBinding
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.displayName

class ManageLabelItemAdapter(context: Context?,
                             items: List<BookmarkEntities.Label?>?,
                             private val manageLabels: ManageLabels,
                             ) : ArrayAdapter<BookmarkEntities.Label?>(context!!, R.layout.manage_labels_list_item, items!!)
{
    private val data get() = manageLabels.data
    private val bookmarkStyleAdapterHelper = BookmarkStyleAdapterHelper()
    private lateinit var bindings: ManageLabelsListItemBinding

    private fun ensureNotAutoAssignPrimaryLabel(label: BookmarkEntities.Label) {
        if (data.autoAssignPrimaryLabel == label.id) {
            data.autoAssignPrimaryLabel = data.selectedLabels.toList().firstOrNull()
        }
    }

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
            if (data.showCheckboxes) {
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (!manageLabels.selectMultiple) {
                            data.selectedLabels.clear()
                        }
                        data.selectedLabels.add(label.id)
                        if (data.autoAssignPrimaryLabel == null) {
                            data.autoAssignPrimaryLabel = label.id
                        }
                    } else {
                        data.selectedLabels.remove(label.id)
                        ensureNotAutoAssignPrimaryLabel(label)
                    }
                    notifyDataSetChanged()
                }
                checkbox.isChecked = data.selectedLabels.contains(label.id)
            } else {
                checkbox.visibility = View.GONE
            }
            if (data.mode == ManageLabels.Mode.STUDYPAD) {
                labelIcon.setImageResource(R.drawable.ic_baseline_studypads_24)
            }

            val isFavourite = data.favouriteLabels.contains(label.id)
            val isPrimary = data.autoAssignPrimaryLabel == label.id

            favouriteIcon.setImageResource(if (isFavourite) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24)
            favouriteIcon.setOnClickListener {
                if(isFavourite) {
                    data.favouriteLabels.remove(label.id)
                } else {
                    data.favouriteLabels.add(label.id)
                }
                notifyDataSetChanged()
            }

            if (setOf(ManageLabels.Mode.ASSIGN, ManageLabels.Mode.AUTOASSIGN).contains(data.mode)) {
                primaryIcon.visibility = if (checkbox.isChecked) View.VISIBLE else View.INVISIBLE
                primaryIcon.setImageResource(if (isPrimary) R.drawable.ic_baseline_star_24 else R.drawable.ic_baseline_star_border_24)
                primaryIcon.setOnClickListener {
                    data.autoAssignPrimaryLabel = label.id
                    notifyDataSetChanged()
                }
            } else {
                primaryIcon.visibility = View.GONE
            }

            if (data.autoAssignLabels.contains(label.id)) {
                labelIcon.setImageResource(R.drawable.ic_label_circle)
            } else {
                labelIcon.setImageResource(R.drawable.ic_label_24dp)
            }

            labelIcon.setOnClickListener {
                if (data.autoAssignLabels.contains(label.id)) {
                    data.autoAssignLabels.remove(label.id)
                } else {
                    data.autoAssignLabels.add(label.id)
                }
                notifyDataSetChanged()
            }

            // TODO: implement otherwise
            bookmarkStyleAdapterHelper.styleView(labelName, label, context, false, false)

            root.setOnClickListener {
                Log.i(TAG, "Edit label clicked")
                val intent = Intent(manageLabels, LabelEditActivity::class.java)
                val labelData = LabelEditActivity.LabelData(
                    isAssigning = data.mode == ManageLabels.Mode.ASSIGN,
                    label = label,
                    isAutoAssign = data.autoAssignLabels.contains(label.id),
                    isFavourite = data.favouriteLabels.contains(label.id),
                    isAutoAssignPrimary = data.autoAssignPrimaryLabel == label.id,
                )
                intent.putExtra("data", json.encodeToString(serializer(), labelData))

                GlobalScope.launch(Dispatchers.Main) {
                    val result = manageLabels.awaitIntent(intent) ?: return@launch
                    if(result.resultCode != Activity.RESULT_CANCELED) {
                        manageLabels.loadLabelList()
                        val newLabelData: LabelEditActivity.LabelData = json.decodeFromString(
                            serializer(), result.resultData.getStringExtra("data")!!)

                        if(newLabelData.isAutoAssign) {
                            data.autoAssignLabels.add(label.id)
                        } else {
                            data.autoAssignLabels.remove(label.id)
                        }
                        if(newLabelData.isFavourite) {
                            data.favouriteLabels.add(label.id)
                        } else {
                            data.favouriteLabels.remove(label.id)
                        }
                        if(newLabelData.isAutoAssignPrimary) {
                            data.autoAssignPrimaryLabel = label.id
                        } else {
                            ensureNotAutoAssignPrimaryLabel(label)
                        }

                        if(newLabelData.delete) {
                            data.deletedLabels.add(label.id)
                            data.selectedLabels.remove(label.id)
                            ensureNotAutoAssignPrimaryLabel(label)
                        }

                        notifyDataSetChanged()
                    }
                }
            }
            labelIcon.setColorFilter(label.color)
        }
        return convertView?: bindings.root
    }

    companion object {
        private const val TAG = "ManageLabelItemAdapter"
    }
}
