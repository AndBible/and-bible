/*
 * Copyright (c) 2026 Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.text.format.Formatter
import net.bible.android.activity.R
import net.bible.service.llm.CacheEntrySummary
import java.text.DateFormat
import java.util.Date

class CacheEntryAdapter(
    private val onItemClick: (CacheEntrySummary) -> Unit,
    private val onItemLongClick: (CacheEntrySummary) -> Unit,
    private val onSelectionChanged: () -> Unit
) : ListAdapter<CacheEntrySummary, CacheEntryAdapter.ViewHolder>(DiffCallback()) {

    var actionModeActive = false
        set(value) {
            if (field != value) {
                field = value
                selectedItems.clear()
                notifyItemRangeChanged(0, itemCount)
            }
        }

    val selectedItems = mutableSetOf<CompositeKey>()

    data class CompositeKey(
        val documentInitials: String,
        val keyName: String,
        val processingType: String,
        val processingParams: String
    )

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        val primaryText: TextView = itemView.findViewById(R.id.primaryText)
        val secondaryText: TextView = itemView.findViewById(R.id.secondaryText)
        val tertiaryText: TextView = itemView.findViewById(R.id.tertiaryText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.llm_cache_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        val key = entry.compositeKey()

        val ctx = holder.itemView.context
        holder.primaryText.text = ctx.getString(R.string.llm_cache_item_primary, entry.documentInitials, entry.keyName)
        holder.secondaryText.text = ctx.getString(R.string.llm_cache_item_secondary, entry.processingType, entry.processingParams)

        val dateStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(entry.createdAt))
        val sizeStr = Formatter.formatShortFileSize(ctx, entry.xmlSize.toLong())
        holder.tertiaryText.text = ctx.getString(R.string.llm_cache_item_tertiary, entry.modelId, dateStr, sizeStr)

        holder.checkBox.visibility = if (actionModeActive) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = key in selectedItems
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedItems.add(key) else selectedItems.remove(key)
            onSelectionChanged()
        }

        holder.itemView.setOnClickListener {
            if (actionModeActive) {
                holder.checkBox.isChecked = !holder.checkBox.isChecked
            } else {
                onItemClick(entry)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!actionModeActive) {
                onItemLongClick(entry)
            }
            true
        }
    }

    private fun CacheEntrySummary.compositeKey() = CompositeKey(
        documentInitials, keyName, processingType, processingParams
    )

    private class DiffCallback : DiffUtil.ItemCallback<CacheEntrySummary>() {
        override fun areItemsTheSame(oldItem: CacheEntrySummary, newItem: CacheEntrySummary): Boolean =
            oldItem.documentInitials == newItem.documentInitials &&
            oldItem.keyName == newItem.keyName &&
            oldItem.processingType == newItem.processingType &&
            oldItem.processingParams == newItem.processingParams

        override fun areContentsTheSame(oldItem: CacheEntrySummary, newItem: CacheEntrySummary): Boolean =
            oldItem == newItem
    }
}
