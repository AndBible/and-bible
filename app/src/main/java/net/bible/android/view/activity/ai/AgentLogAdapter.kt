/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.EntryStatus
import net.bible.service.llm.agent.LogEntryType

/**
 * RecyclerView adapter for displaying agent log entries.
 */
class AgentLogAdapter : ListAdapter<AgentLogEntry, AgentLogAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val typeIcon: ImageView = itemView.findViewById(R.id.typeIcon)
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val detailsText: TextView = itemView.findViewById(R.id.detailsText)
        val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.agent_log_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)

        // Set type icon based on entry type
        val typeIconRes = when (entry.type) {
            LogEntryType.INFO -> R.drawable.ic_info_24dp
            LogEntryType.ACTION -> R.drawable.ic_baseline_build_24
            LogEntryType.PERMISSION_REQUEST -> R.drawable.ic_baseline_security_24
            LogEntryType.ERROR -> R.drawable.ic_baseline_error_24
        }
        holder.typeIcon.setImageResource(typeIconRes)

        // Set type icon color based on type
        val typeColor = when (entry.type) {
            LogEntryType.INFO -> R.color.log_info
            LogEntryType.ACTION -> R.color.log_action
            LogEntryType.PERMISSION_REQUEST -> R.color.log_permission
            LogEntryType.ERROR -> R.color.log_error
        }
        holder.typeIcon.setColorFilter(holder.itemView.context.getColor(typeColor))

        // Set message
        holder.messageText.text = entry.message

        // Set details if available
        if (entry.details != null) {
            holder.detailsText.text = entry.details
            holder.detailsText.visibility = View.VISIBLE
        } else {
            holder.detailsText.visibility = View.GONE
        }

        // Set status icon based on status
        val statusIconRes = when (entry.status) {
            EntryStatus.PENDING -> R.drawable.ic_baseline_hourglass_empty_24
            EntryStatus.APPROVED -> R.drawable.ic_baseline_check_circle_24
            EntryStatus.DENIED -> R.drawable.ic_baseline_cancel_24
            EntryStatus.COMPLETED -> R.drawable.ic_baseline_check_circle_24
            EntryStatus.FAILED -> R.drawable.ic_baseline_error_24
        }
        holder.statusIcon.setImageResource(statusIconRes)

        // Set status icon visibility - only show for non-completed info entries
        holder.statusIcon.visibility = if (entry.type == LogEntryType.INFO && entry.status == EntryStatus.COMPLETED) {
            View.GONE
        } else {
            View.VISIBLE
        }

        // Set status icon color
        val statusColor = when (entry.status) {
            EntryStatus.PENDING -> R.color.status_pending
            EntryStatus.APPROVED, EntryStatus.COMPLETED -> R.color.status_success
            EntryStatus.DENIED, EntryStatus.FAILED -> R.color.status_error
        }
        holder.statusIcon.setColorFilter(holder.itemView.context.getColor(statusColor))
    }

    private class DiffCallback : DiffUtil.ItemCallback<AgentLogEntry>() {
        override fun areItemsTheSame(oldItem: AgentLogEntry, newItem: AgentLogEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AgentLogEntry, newItem: AgentLogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
