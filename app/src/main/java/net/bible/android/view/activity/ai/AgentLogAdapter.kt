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

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.AgentLogItemBinding
import net.bible.service.common.CommonUtils
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.EntryStatus
import net.bible.service.llm.agent.LogEntryType

/**
 * RecyclerView adapter for displaying agent log entries.
 * The first item is a synthetic "model selector" entry when [modelSelectorText] is set.
 *
 * Uses a custom [AsyncListDiffer] with position-offset callback so that the synthetic
 * header item doesn't cause DiffUtil notifications to target wrong positions.
 */
class AgentLogAdapter : RecyclerView.Adapter<AgentLogAdapter.ViewHolder>() {

    var onRawLogClick: (() -> Unit)? = null
    var onModelSelectorClick: (() -> Unit)? = null

    /** Text for the model selector entry shown at the top of the log. Null = hidden. */
    var modelSelectorText: String? = null
        set(value) {
            val hadEntry = field != null
            val hasEntry = value != null
            field = value
            when {
                !hadEntry && hasEntry -> notifyItemInserted(0)
                hadEntry && !hasEntry -> notifyItemRemoved(0)
                hadEntry && hasEntry -> notifyItemChanged(0)
            }
        }

    private val hasModelSelector get() = modelSelectorText != null
    private val headerCount get() = if (hasModelSelector) 1 else 0

    /**
     * Offset-aware callback so DiffUtil notifications target the correct adapter positions
     * (shifted past the synthetic model selector header).
     */
    private val offsetCallback = object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) =
            notifyItemRangeInserted(position + headerCount, count)
        override fun onRemoved(position: Int, count: Int) =
            notifyItemRangeRemoved(position + headerCount, count)
        override fun onMoved(fromPosition: Int, toPosition: Int) =
            notifyItemMoved(fromPosition + headerCount, toPosition + headerCount)
        override fun onChanged(position: Int, count: Int, payload: Any?) =
            notifyItemRangeChanged(position + headerCount, count, payload)
    }

    private val differ = AsyncListDiffer(offsetCallback, diffConfig)

    fun submitList(list: List<AgentLogEntry>, commitCallback: Runnable? = null) {
        differ.submitList(list, commitCallback)
    }

    override fun getItemCount(): Int = differ.currentList.size + headerCount

    override fun getItemViewType(position: Int): Int =
        if (hasModelSelector && position == 0) VIEW_TYPE_MODEL_SELECTOR else VIEW_TYPE_LOG_ENTRY

    private fun getLogEntry(position: Int): AgentLogEntry =
        differ.currentList[position - headerCount]

    class ViewHolder(val binding: AgentLogItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AgentLogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.binding.run {
        if (getItemViewType(position) == VIEW_TYPE_MODEL_SELECTOR) {
            bindModelSelector(this)
        } else {
            bindLogEntry(this, getLogEntry(position))
        }
    }

    private fun bindModelSelector(binding: AgentLogItemBinding) = binding.run {
        val monochrome = CommonUtils.settings.monochromeMode
        typeIcon.setImageResource(R.drawable.icon_robot)
        typeIcon.setColorFilter(if (monochrome) Color.BLACK else root.context.getColor(R.color.log_info))
        messageText.visibility = View.GONE
        detailsText.visibility = View.GONE
        costText.visibility = View.GONE
        statusIcon.visibility = View.GONE
        rawLogLink.visibility = View.VISIBLE
        rawLogLink.setTextColor(if (monochrome) Color.BLACK else root.context.getColor(R.color.log_info))
        rawLogLink.text = modelSelectorText
        rawLogLink.setOnClickListener { onModelSelectorClick?.invoke() }
    }

    private fun bindLogEntry(binding: AgentLogItemBinding, entry: AgentLogEntry) = binding.run {
        val context = root.context
        val monochrome = CommonUtils.settings.monochromeMode

        messageText.visibility = View.VISIBLE

        val typeIconRes = when (entry.type) {
            LogEntryType.INFO -> R.drawable.ic_info_24dp
            LogEntryType.ACTION -> R.drawable.ic_baseline_build_24
            LogEntryType.PERMISSION_REQUEST -> R.drawable.ic_baseline_security_24
            LogEntryType.ERROR -> R.drawable.ic_baseline_error_24
            LogEntryType.LLM_COMMENT -> R.drawable.ic_baseline_chat_bubble_outline_24
        }
        typeIcon.setImageResource(typeIconRes)

        val typeColor = if (monochrome) Color.BLACK else when (entry.type) {
            LogEntryType.INFO -> context.getColor(R.color.log_info)
            LogEntryType.ACTION -> context.getColor(R.color.log_action)
            LogEntryType.PERMISSION_REQUEST -> context.getColor(R.color.log_permission)
            LogEntryType.ERROR -> context.getColor(R.color.log_error)
            LogEntryType.LLM_COMMENT -> context.getColor(R.color.log_comment)
        }
        typeIcon.setColorFilter(typeColor)

        messageText.text = entry.message
        if (monochrome) {
            messageText.setTextColor(Color.BLACK)
        }

        rawLogLink.setTextColor(if (monochrome) Color.BLACK else context.getColor(R.color.log_info))
        if (entry.showRawLogLink) {
            rawLogLink.visibility = View.VISIBLE
            rawLogLink.text = context.getString(R.string.agent_log_view_raw)
            rawLogLink.setOnClickListener { onRawLogClick?.invoke() }
        } else {
            rawLogLink.visibility = View.GONE
            rawLogLink.setOnClickListener(null)
        }

        if (entry.details != null) {
            detailsText.text = entry.details
            detailsText.visibility = View.VISIBLE
            if (monochrome) { detailsText.alpha = 1.0f; detailsText.setTextColor(Color.BLACK) }
        } else {
            detailsText.visibility = View.GONE
        }

        if (entry.costInfo != null) {
            costText.text = entry.costInfo
            costText.visibility = View.VISIBLE
            if (monochrome) { costText.alpha = 1.0f; costText.setTextColor(Color.BLACK) }
        } else {
            costText.visibility = View.GONE
        }

        val statusIconRes = when (entry.status) {
            EntryStatus.PENDING -> R.drawable.ic_baseline_hourglass_empty_24
            EntryStatus.APPROVED -> R.drawable.ic_baseline_check_circle_24
            EntryStatus.DENIED -> R.drawable.ic_baseline_cancel_24
            EntryStatus.COMPLETED -> R.drawable.ic_baseline_check_circle_24
            EntryStatus.FAILED -> R.drawable.ic_baseline_error_24
        }
        statusIcon.setImageResource(statusIconRes)

        statusIcon.visibility = if (entry.type == LogEntryType.INFO && entry.status == EntryStatus.COMPLETED) {
            View.GONE
        } else {
            View.VISIBLE
        }

        val statusColor = if (monochrome) Color.BLACK else when (entry.status) {
            EntryStatus.PENDING -> context.getColor(R.color.status_pending)
            EntryStatus.APPROVED, EntryStatus.COMPLETED -> context.getColor(R.color.status_success)
            EntryStatus.DENIED, EntryStatus.FAILED -> context.getColor(R.color.status_error)
        }
        statusIcon.setColorFilter(statusColor)
    }

    companion object {
        private val diffConfig = AsyncDifferConfig.Builder(
            object : DiffUtil.ItemCallback<AgentLogEntry>() {
                override fun areItemsTheSame(oldItem: AgentLogEntry, newItem: AgentLogEntry) = oldItem.id == newItem.id
                override fun areContentsTheSame(oldItem: AgentLogEntry, newItem: AgentLogEntry) = oldItem == newItem
            }
        ).build()
        private const val VIEW_TYPE_MODEL_SELECTOR = 0
        private const val VIEW_TYPE_LOG_ENTRY = 1
    }
}
