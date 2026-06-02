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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bible.android.activity.R
import net.bible.android.activity.databinding.RawLlmLogItemBinding
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.agent.IterationUsageData
import net.bible.service.llm.agent.RawLogEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * RecyclerView adapter for expandable raw LLM log entries.
 */
class RawLlmLogAdapter(
    private val entries: List<RawLogEntry>,
    private val usageByIteration: Map<Int, IterationUsageData>
) : RecyclerView.Adapter<RawLlmLogAdapter.ViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()
    private val contentCache = mutableMapOf<Int, String>()
    private lateinit var context: Context

    class ViewHolder(val binding: RawLlmLogItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val binding = RawLlmLogItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val isExpanded = expandedPositions.contains(position)

        holder.binding.apply {
            entryTitle.text = getTitle(entry)
            tokenInfo.text = getTokenInfo(entry)

            expandArrow.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less_24 else R.drawable.ic_expand_more_24
            )

            if (isExpanded) {
                contentText.visibility = View.VISIBLE
                contentText.text = getContent(position, entry)
            } else {
                contentText.visibility = View.GONE
            }

            headerLayout.setOnClickListener {
                val adapterPosition = holder.adapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                if (expandedPositions.contains(adapterPosition)) {
                    expandedPositions.remove(adapterPosition)
                } else {
                    expandedPositions.add(adapterPosition)
                }
                notifyItemChanged(adapterPosition)
            }
        }
    }

    private fun getTitle(entry: RawLogEntry): String = when (entry) {
        is RawLogEntry.Message -> when (entry.role.uppercase()) {
            "SYSTEM" -> context.getString(R.string.raw_llm_log_entry_system)
            "USER" -> context.getString(R.string.raw_llm_log_entry_user)
            "ASSISTANT" -> context.getString(R.string.raw_llm_log_entry_assistant)
            else -> entry.role
        }
        is RawLogEntry.ToolCallEntry -> context.getString(R.string.raw_llm_log_entry_tool_call, entry.toolName)
        is RawLogEntry.ToolResultEntry -> context.getString(R.string.raw_llm_log_entry_tool_result, entry.id)
        is RawLogEntry.ToolDefinitionsEntry -> context.getString(R.string.raw_llm_log_entry_tool_definitions, entry.toolDefs.size)
        is RawLogEntry.RawApiResponse -> context.getString(R.string.raw_llm_log_entry_api_response, entry.iteration)
    }

    private fun getTokenInfo(entry: RawLogEntry): String = when (entry) {
        is RawLogEntry.RawApiResponse -> {
            val data = usageByIteration[entry.iteration]
            if (data != null) {
                val usage = data.usage
                val cost = LlmPricing.estimateCost(usage, data.model, data.configuredModelId)
                val costStr = if (cost != null) " · ${LlmCostTracker.formatCost(cost)}" else ""
                context.getString(R.string.raw_llm_log_entry_usage,
                    formatTokenCount(usage.inputTokens),
                    formatTokenCount(usage.outputTokens),
                    costStr)
            } else {
                context.getString(R.string.raw_llm_log_entry_tokens, formatTokenCount(entry.estimateTokens().toLong()))
            }
        }
        else -> context.getString(R.string.raw_llm_log_entry_tokens, formatTokenCount(entry.estimateTokens().toLong()))
    }

    private fun getContent(position: Int, entry: RawLogEntry): String =
        contentCache.getOrPut(position) { formatEntry(entry) }

    private fun formatEntry(entry: RawLogEntry): String = when (entry) {
        is RawLogEntry.Message -> entry.content ?: context.getString(R.string.raw_llm_log_entry_empty)
        is RawLogEntry.ToolCallEntry -> prettyFormatJson(entry.arguments)
        is RawLogEntry.ToolResultEntry -> prettyFormatJson(entry.result)
        is RawLogEntry.ToolDefinitionsEntry -> buildString {
            for (def in entry.toolDefs) {
                appendLine("--- ${def.name} ---")
                appendLine(context.getString(R.string.raw_llm_log_entry_tool_desc, def.description))
                appendLine(context.getString(R.string.raw_llm_log_entry_tool_params, prettyJson.encodeToString(def.parametersSchema)))
                appendLine()
            }
        }
        is RawLogEntry.RawApiResponse -> prettyFormatJson(entry.body)
    }

    companion object {
        private val prettyJson = Json { prettyPrint = true }

        fun formatTokenCount(count: Long): String = LlmCostTracker.formatTokenCount(count)

        private val longStringValueRegex = Regex(""""((?:[^"\\]|\\.){80,})"""")

        private fun prettyFormatJson(json: String): String = try {
            val trimmed = json.trim()
            val formatted = when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> json
            }
            longStringValueRegex.replace(formatted) { match ->
                match.value
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
            }
        } catch (_: Exception) {
            json
        }
    }
}
