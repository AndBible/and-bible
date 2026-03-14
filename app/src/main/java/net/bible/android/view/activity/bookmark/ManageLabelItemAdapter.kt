/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.widget.ArrayAdapter
import net.bible.android.view.util.widget.BookmarkStyleAdapterHelper
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ManageLabelsListItemBinding
import net.bible.android.activity.databinding.ManageLabelsSearchResultItemBinding
import net.bible.android.control.bookmark.StudyPadSearchResult
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.displayName
import net.bible.service.common.CommonUtils.getResourceColor

class ManageLabelItemAdapter(context: Context?,
                             items: List<Any>?,
                             private val manageLabels: ManageLabels,
                             ) : ArrayAdapter<Any>(context!!, R.layout.manage_labels_list_item, items!!)
{
    private val data get() = manageLabels.data
    private val bookmarkStyleAdapterHelper = BookmarkStyleAdapterHelper()
    private lateinit var bindings: ManageLabelsListItemBinding

    companion object {
        private const val TAG = "ManageLabelItemAdapter"
        private const val VIEW_TYPE_LABEL = 0
        private const val VIEW_TYPE_SEARCH_RESULT = 1
    }

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is StudyPadSearchResult -> VIEW_TYPE_SEARCH_RESULT
            else -> VIEW_TYPE_LABEL
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        when (val item = getItem(position)) {
            // Handle search result items
            is StudyPadSearchResult -> {
                val binding = if (convertView == null || convertView.tag != VIEW_TYPE_SEARCH_RESULT) {
                    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    ManageLabelsSearchResultItemBinding.inflate(inflater, parent, false).also {
                        it.root.tag = VIEW_TYPE_SEARCH_RESULT
                    }
                } else {
                    ManageLabelsSearchResultItemBinding.bind(convertView)
                }

                binding.apply {
                    labelIcon.setColorFilter(item.label.color)
                    labelName.text = item.label.displayName

                    // Set match count text
                    val matchText = if (item.matchCount == 1) {
                        context.getString(R.string.search_results_match)
                    } else {
                        context.getString(R.string.search_results_matches, item.matchCount)
                    }
                    matchCount.text = matchText

                    // Set first match snippet with highlighting
                    if (item.matches.isNotEmpty()) {
                        val firstMatch = item.matches[0]
                        val spannable = SpannableString(firstMatch.textSnippet)
                        spannable.setSpan(
                            BackgroundColorSpan(getResourceColor(R.color.yellow_200)),
                            firstMatch.matchStart,
                            firstMatch.matchEnd,
                            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        textSnippet.text = spannable
                    } else {
                        textSnippet.text = ""
                    }

                    root.setOnClickListener {
                        Log.i(TAG, "Search result clicked: ${item.label.displayName}")
                        // Open the study pad and navigate to first match
                        manageLabels.selectStudyPadLabel(item.label, item.matches.firstOrNull())
                    }
                }

                binding.root
            }

            // Handle label category items
            is LabelCategory -> {
                bindings = if (convertView == null || convertView.tag != VIEW_TYPE_LABEL) {
                    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    ManageLabelsListItemBinding.inflate(inflater, parent, false).also {
                        it.root.tag = VIEW_TYPE_LABEL
                    }
                } else {
                    ManageLabelsListItemBinding.bind(convertView)
                }
                bindings.apply {
                    labelCategory.visibility = View.GONE
                    categoryTitle.visibility = View.VISIBLE
                    categoryTitle.text = when(item) {
                        LabelCategory.ACTIVE -> manageLabels.getString(R.string.active_labels)
                        LabelCategory.RECENT -> manageLabels.getString(R.string.recent_labels)
                        LabelCategory.OTHER -> manageLabels.getString(R.string.other_labels)
                    }
                    root.setOnClickListener(null)
                    root.setBackgroundResource(R.color.transparent)
                }
                bindings.root
            }

            // Handle regular label items
            is BookmarkEntities.Label -> {
                bindings = if (convertView == null || convertView.tag != VIEW_TYPE_LABEL) {
                    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    ManageLabelsListItemBinding.inflate(inflater, parent, false).also {
                        it.root.tag = VIEW_TYPE_LABEL
                    }
                } else {
                    ManageLabelsListItemBinding.bind(convertView)
                }
                bindings.apply {
                    val highlight = item.id == manageLabels.highlightLabel?.id
                    labelName.setTypeface(null, if(highlight) Typeface.BOLD else Typeface.NORMAL)
                    root.setBackgroundResource(if(highlight) R.drawable.selectable_background_highlight else R.drawable.selectable_background)

                    labelCategory.visibility = View.VISIBLE
                    categoryTitle.visibility = View.GONE
                    labelName.text = item.displayName
                    val checkbox = checkbox
                    if (data.showCheckboxes) {
                        checkbox.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                data.selectedLabels.add(item.id)
                                if (data.bookmarkPrimaryLabel == null) {
                                    data.bookmarkPrimaryLabel = item.id
                                }
                            } else {
                                data.selectedLabels.remove(item.id)
                                manageLabels.ensureNotBookmarkPrimaryLabel(item)
                            }
                            manageLabels.updateLabelList()
                        }
                        checkbox.isChecked = data.contextSelectedItems.contains(item.id)
                    } else {
                        checkbox.visibility = View.GONE
                    }
                    if (data.mode == ManageLabels.Mode.STUDYPAD) {
                        labelIcon.setImageResource(R.drawable.ic_baseline_studypads_24)
                    }

                    val isFavourite = item.favourite
                    val isPrimary = data.contextPrimaryLabel == item.id

                    favouriteIcon.setImageResource(if (isFavourite) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24)
                    favouriteIcon.setOnClickListener {
                        item.favourite = !item.favourite
                        manageLabels.data.changedLabels.add(item.id)
                        notifyDataSetChanged()
                    }

                    favouriteIcon.visibility = if (data.workspaceEdits) View.VISIBLE else View.GONE

                    if (data.primaryShown) {
                        primaryIcon.visibility = if (data.contextSelectedItems.contains(item.id)) View.VISIBLE else View.GONE
                        primaryIcon.setImageResource(if (isPrimary) R.drawable.ic_baseline_bookmark_24 else R.drawable.ic_bookmark_24dp)
                        primaryIcon.setOnClickListener {
                            data.contextPrimaryLabel = item.id
                            notifyDataSetChanged()
                        }
                    } else {
                        primaryIcon.visibility = View.GONE
                    }

                    if(data.workspaceEdits && item.isUnlabeledLabel) {
                        favouriteIcon.visibility = View.INVISIBLE
                        primaryIcon.visibility = View.GONE
                    }

                    if (data.workspaceEdits && !item.isUnlabeledLabel) {
                        if (data.autoAssignLabels.contains(item.id)) {
                            labelIcon.setImageResource(R.drawable.ic_label_circle)
                        } else {
                            labelIcon.setImageResource(R.drawable.ic_label_24dp)
                        }

                        labelIcon.setOnClickListener {
                            if (data.autoAssignLabels.contains(item.id)) {
                                data.autoAssignLabels.remove(item.id)
                                manageLabels.ensureNotAutoAssignPrimaryLabel(item)
                            } else {
                                data.autoAssignLabels.add(item.id)
                                if (data.autoAssignPrimaryLabel == null) {
                                    data.autoAssignPrimaryLabel = item.id
                                }
                            }
                            manageLabels.updateLabelList()
                        }
                    } else {
                        labelIcon.setImageResource(R.drawable.ic_label_24dp)
                    }
                    labelIcon.setColorFilter(item.color)

                    val resId = customIconMap[item.customIcon]
                    if (resId != null) {
                        customIcon.setImageResource(resId)
                        customIcon.visibility = View.VISIBLE
                    } else {
                        customIcon.visibility = View.GONE
                    }
                    customIcon.setColorFilter(item.color)

                    overrideIndicator.visibility = if (manageLabels.overriddenLabelIds.contains(item.id)) View.VISIBLE else View.GONE

                    // TODO: implement otherwise
                    bookmarkStyleAdapterHelper.styleView(labelName, item, context, false, false)
                    if (data.mode != ManageLabels.Mode.STUDYPAD) {
                        root.setOnClickListener {
                            Log.i(TAG, "Edit label clicked")
                            manageLabels.editLabel(item)
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
                            manageLabels.selectStudyPadLabel(item)
                        }

                        root.setOnLongClickListener {
                            Log.i(TAG, "Edit label long-clicked (in STUDYPAD mode)")
                            manageLabels.editLabel(item)
                            true
                        }
                    }
                }
                bindings.root
            }

            // Unknown item type - should not happen
            else -> {
                Log.e(TAG, "Unknown item type: ${item?.javaClass?.name}")
                // Return empty view as fallback
                View(context)
            }
        }
}
