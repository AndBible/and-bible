/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.AgentLogItemBinding
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.EntryStatus
import net.bible.service.llm.agent.LogEntryType

/**
 * RecyclerView adapter for displaying agent log entries.
 */
class AgentLogAdapter : ListAdapter<AgentLogEntry, AgentLogAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: AgentLogItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AgentLogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.binding.run {
        val entry = getItem(position)
        val context = root.context

        // Set type icon based on entry type
        val typeIconRes = when (entry.type) {
            LogEntryType.INFO -> R.drawable.ic_info_24dp
            LogEntryType.ACTION -> R.drawable.ic_baseline_build_24
            LogEntryType.PERMISSION_REQUEST -> R.drawable.ic_baseline_security_24
            LogEntryType.ERROR -> R.drawable.ic_baseline_error_24
            LogEntryType.LLM_COMMENT -> R.drawable.ic_baseline_chat_bubble_outline_24
        }
        typeIcon.setImageResource(typeIconRes)

        // Set type icon color based on type
        val typeColor = when (entry.type) {
            LogEntryType.INFO -> R.color.log_info
            LogEntryType.ACTION -> R.color.log_action
            LogEntryType.PERMISSION_REQUEST -> R.color.log_permission
            LogEntryType.ERROR -> R.color.log_error
            LogEntryType.LLM_COMMENT -> R.color.log_comment
        }
        typeIcon.setColorFilter(context.getColor(typeColor))

        // Set message
        messageText.text = entry.message

        // Set details if available
        if (entry.details != null) {
            detailsText.text = entry.details
            detailsText.visibility = View.VISIBLE
        } else {
            detailsText.visibility = View.GONE
        }

        // Set cost info if available
        if (entry.costInfo != null) {
            costText.text = entry.costInfo
            costText.visibility = View.VISIBLE
        } else {
            costText.visibility = View.GONE
        }

        // Set status icon based on status
        val statusIconRes = when (entry.status) {
            EntryStatus.PENDING -> R.drawable.ic_baseline_hourglass_empty_24
            EntryStatus.APPROVED -> R.drawable.ic_baseline_check_circle_24
            EntryStatus.DENIED -> R.drawable.ic_baseline_cancel_24
            EntryStatus.COMPLETED -> R.drawable.ic_baseline_check_circle_24
            EntryStatus.FAILED -> R.drawable.ic_baseline_error_24
        }
        statusIcon.setImageResource(statusIconRes)

        // Set status icon visibility - only show for non-completed info entries
        statusIcon.visibility = if (entry.type == LogEntryType.INFO && entry.status == EntryStatus.COMPLETED) {
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
        statusIcon.setColorFilter(context.getColor(statusColor))
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
