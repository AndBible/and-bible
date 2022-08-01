/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ReadingDayBarBoxBinding
import net.bible.android.view.activity.readingplan.model.DayBarItem
import net.bible.service.common.CommonUtils.getResourceColor
import java.text.SimpleDateFormat
import java.util.Locale

class DailyReadingDayBarAdapter : ListAdapter<DayBarItem, DailyReadingDayBarAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ReadingDayBarBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.binding.apply {
            dayNumberView.text = item.dayNumber.toString()
            dateView.text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(item.date)

            boxContainer.setBackgroundColor(
                if (item.dayActive)
                    getResourceColor(R.color.sync_on_green)
                else if (item.dayReadComplete)
                    getResourceColor(R.color.grey_700)
                else
                    getResourceColor(R.color.grey_500)
            )
        }
    }

    inner class ViewHolder(val binding: ReadingDayBarBoxBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        const val TAG = "DailyReadingDayBarAdapt"
        val DIFF_CALLBACK: DiffUtil.ItemCallback<DayBarItem> = object: DiffUtil.ItemCallback<DayBarItem>() {
            override fun areItemsTheSame(oldItem: DayBarItem, newItem: DayBarItem):Boolean {
                // User properties may have changed if reloaded from the DB, but ID is fixed
                return oldItem.dayNumber == newItem.dayNumber
            }
            override fun areContentsTheSame(oldItem: DayBarItem, newItem: DayBarItem):Boolean {
                // NOTE: if you use equals, your object must properly override Object#equals()
                // Incorrectly returning false here will result in too many animations.
                return oldItem == newItem
            }
        }
    }
}
