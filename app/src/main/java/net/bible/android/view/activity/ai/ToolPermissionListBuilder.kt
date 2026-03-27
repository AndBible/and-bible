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
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ItemToolCategoryHeaderBinding
import net.bible.android.activity.databinding.ItemToolPermissionBinding
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry

/**
 * Builds a categorized, expandable tool permission list into a [LinearLayout] container.
 *
 * Used by [GlobalToolPermissionsActivity] and inline in [PromptEditActivity]
 * to avoid duplicating the category/expand/collapse/toggle logic.
 */
class ToolPermissionListBuilder(
    private val context: Context,
    private val container: LinearLayout,
    private val mode: Mode,
) {
    enum class Mode { GLOBAL, PROMPT }

    private data class ToolRow(
        val tool: Tool,
        val radioGroup: RadioGroup,
        val rootView: View,
    )

    private data class CategoryState(
        val category: ToolCategory,
        val headerBinding: ItemToolCategoryHeaderBinding,
        val readRows: MutableList<ToolRow> = mutableListOf(),
        val writeRows: MutableList<ToolRow> = mutableListOf(),
        var expanded: Boolean = true,
    )

    private val categories = mutableListOf<CategoryState>()

    /** Guard flag to prevent toggle ↔ row listener recursion. */
    private var updatingFromToggle = false

    /**
     * Build the categorized tool list.
     *
     * @param allowedTools Tools currently set to "always allow" (write tools) or "enabled" override (prompt mode)
     * @param deniedTools Tools currently denied/disabled
     * @param globalAllowed Global permanently allowed tools (only used in PROMPT mode for default labels)
     * @param globalDenied Global permanently denied tools (only used in PROMPT mode for default labels)
     */
    fun build(
        allowedTools: Set<AgentTool>,
        deniedTools: Set<AgentTool>,
        globalAllowed: Set<AgentTool> = emptySet(),
        globalDenied: Set<AgentTool> = emptySet(),
    ) {
        container.removeAllViews()
        categories.clear()

        val toolsByCategory = ToolRegistry.getConfigurableToolsByCategory()

        for ((category, tools) in toolsByCategory) {
            val headerBinding = ItemToolCategoryHeaderBinding.inflate(
                LayoutInflater.from(context), container, false
            )
            headerBinding.categoryName.text = ToolRegistry.getCategoryDisplayName(category)

            val state = CategoryState(category, headerBinding)

            val readTools = tools.filter { !it.requiresPermission }
            val writeTools = tools.filter { it.requiresPermission }

            // Hide read/write toggle if category has no tools of that type
            if (readTools.isEmpty()) {
                headerBinding.readToggle.visibility = View.GONE
            }
            if (writeTools.isEmpty()) {
                headerBinding.writeToggle.visibility = View.GONE
            }

            container.addView(headerBinding.root)

            // Add tool rows
            for (tool in readTools) {
                val row = addToolRow(tool, allowedTools, deniedTools, globalAllowed, globalDenied)
                state.readRows.add(row)
            }
            for (tool in writeTools) {
                val row = addToolRow(tool, allowedTools, deniedTools, globalAllowed, globalDenied)
                state.writeRows.add(row)
            }

            categories.add(state)

            // Set up category toggles and expand/collapse
            setupCategoryHeader(state)
        }
    }

    private fun addToolRow(
        tool: Tool,
        allowedTools: Set<AgentTool>,
        deniedTools: Set<AgentTool>,
        globalAllowed: Set<AgentTool>,
        globalDenied: Set<AgentTool>,
    ): ToolRow {
        val itemBinding = ItemToolPermissionBinding.inflate(
            LayoutInflater.from(context), container, false
        )
        itemBinding.toolName.text = ToolRegistry.getDisplayName(tool)

        if (mode == Mode.GLOBAL) {
            configureGlobalRow(itemBinding, tool, allowedTools, deniedTools)
        } else {
            configurePromptRow(itemBinding, tool, allowedTools, deniedTools, globalAllowed, globalDenied)
        }

        container.addView(itemBinding.root)
        return ToolRow(tool, itemBinding.permissionRadioGroup, itemBinding.root)
    }

    private fun configureGlobalRow(
        binding: ItemToolPermissionBinding,
        tool: Tool,
        allowedTools: Set<AgentTool>,
        deniedTools: Set<AgentTool>,
    ) {
        if (!tool.requiresPermission) {
            // Read tools: Enabled / Disabled (2 options)
            binding.radioAsk.text = context.getString(R.string.tool_option_enabled)
            binding.radioAllow.visibility = View.GONE
            binding.radioDeny.text = context.getString(R.string.tool_option_disabled)

            if (tool.agentTool in deniedTools) {
                binding.permissionRadioGroup.check(R.id.radioDeny)
            } else {
                binding.permissionRadioGroup.check(R.id.radioAsk)
            }
        } else {
            // Write tools: Ask / Always allow / Always deny (3 options)
            when (tool.agentTool) {
                in allowedTools -> binding.permissionRadioGroup.check(R.id.radioAllow)
                in deniedTools -> binding.permissionRadioGroup.check(R.id.radioDeny)
                else -> binding.permissionRadioGroup.check(R.id.radioAsk)
            }
        }
    }

    private fun configurePromptRow(
        binding: ItemToolPermissionBinding,
        tool: Tool,
        allowedTools: Set<AgentTool>,
        deniedTools: Set<AgentTool>,
        globalAllowed: Set<AgentTool>,
        globalDenied: Set<AgentTool>,
    ) {
        if (!tool.requiresPermission) {
            // Read tools: Default (enabled/disabled) / Enabled / Disabled
            val globallyDisabled = tool.agentTool in globalDenied
            binding.radioAsk.text = if (globallyDisabled)
                context.getString(R.string.tool_option_default_disabled)
            else
                context.getString(R.string.tool_option_default_enabled)
            binding.radioAllow.text = context.getString(R.string.tool_option_enabled)
            binding.radioDeny.text = context.getString(R.string.tool_option_disabled)
        } else {
            // Write tools: Default (ask/allowed/denied) / Allow / Deny
            val globallyAllowed = tool.agentTool in globalAllowed
            val globallyDenied = tool.agentTool in globalDenied
            if (globallyAllowed) {
                binding.radioAsk.text = context.getString(R.string.tool_option_default_allowed)
            } else if (globallyDenied) {
                binding.radioAsk.text = context.getString(R.string.tool_option_default_denied)
            }
        }

        when (tool.agentTool) {
            in allowedTools -> binding.permissionRadioGroup.check(R.id.radioAllow)
            in deniedTools -> binding.permissionRadioGroup.check(R.id.radioDeny)
            else -> binding.permissionRadioGroup.check(R.id.radioAsk)
        }
    }

    private fun setupCategoryHeader(state: CategoryState) {
        val header = state.headerBinding

        // Update toggle states from current tool row states
        updateToggleState(header.readToggle, state.readRows)
        updateToggleState(header.writeToggle, state.writeRows)

        // Expand/collapse on header click (excluding checkboxes)
        header.root.setOnClickListener {
            state.expanded = !state.expanded
            applyExpansion(state)
        }

        // Read toggle controls all read tool rows
        header.readToggle.setOnCheckedChangeListener { _, isChecked ->
            if (updatingFromToggle) return@setOnCheckedChangeListener
            updatingFromToggle = true
            setAllRows(state.readRows, isChecked)
            updateExpansionForToggles(state)
            updatingFromToggle = false
        }

        // Write toggle controls all write tool rows
        header.writeToggle.setOnCheckedChangeListener { _, isChecked ->
            if (updatingFromToggle) return@setOnCheckedChangeListener
            updatingFromToggle = true
            setAllRows(state.writeRows, isChecked)
            updateExpansionForToggles(state)
            updatingFromToggle = false
        }

        // Individual row changes update the toggles
        for (row in state.readRows) {
            row.radioGroup.setOnCheckedChangeListener { _, _ ->
                if (!updatingFromToggle) {
                    updatingFromToggle = true
                    updateToggleState(header.readToggle, state.readRows)
                    updateExpansionForToggles(state)
                    updatingFromToggle = false
                }
            }
        }
        for (row in state.writeRows) {
            row.radioGroup.setOnCheckedChangeListener { _, _ ->
                if (!updatingFromToggle) {
                    updatingFromToggle = true
                    updateToggleState(header.writeToggle, state.writeRows)
                    updateExpansionForToggles(state)
                    updatingFromToggle = false
                }
            }
        }

        // Initial expansion state: collapse if all tools are disabled/denied
        val allDisabled = isAllDisabled(state.readRows) && isAllDisabled(state.writeRows)
        state.expanded = !allDisabled
        applyExpansion(state)
    }

    private fun isRowDisabled(row: ToolRow): Boolean =
        row.radioGroup.checkedRadioButtonId == R.id.radioDeny

    private fun isRowEnabled(row: ToolRow): Boolean =
        row.radioGroup.checkedRadioButtonId != R.id.radioDeny

    private fun isAllDisabled(rows: List<ToolRow>): Boolean =
        rows.isNotEmpty() && rows.all { isRowDisabled(it) }

    private fun isAllEnabled(rows: List<ToolRow>): Boolean =
        rows.isNotEmpty() && rows.all { isRowEnabled(it) }

    /**
     * Update a CheckBox toggle to reflect the current state of its tool rows.
     * Checked = all enabled, unchecked = any disabled.
     */
    private fun updateToggleState(toggle: CheckBox, rows: List<ToolRow>) {
        if (rows.isEmpty()) return
        toggle.isChecked = isAllEnabled(rows)
    }

    /** Set all rows to enabled (default/ask) or disabled (deny). */
    private fun setAllRows(rows: List<ToolRow>, enabled: Boolean) {
        for (row in rows) {
            row.radioGroup.check(if (enabled) R.id.radioAsk else R.id.radioDeny)
        }
    }

    /** Auto-collapse when both toggles are off, auto-expand when at least one is on. */
    private fun updateExpansionForToggles(state: CategoryState) {
        val readAllOff = state.readRows.isEmpty() || isAllDisabled(state.readRows)
        val writeAllOff = state.writeRows.isEmpty() || isAllDisabled(state.writeRows)
        val shouldExpand = !(readAllOff && writeAllOff)
        if (state.expanded != shouldExpand) {
            state.expanded = shouldExpand
            applyExpansion(state)
        }
    }

    private fun applyExpansion(state: CategoryState) {
        val visibility = if (state.expanded) View.VISIBLE else View.GONE
        state.headerBinding.expandIcon.setImageResource(
            if (state.expanded) R.drawable.ic_expand_less_24 else R.drawable.ic_expand_more_24
        )
        for (row in state.readRows + state.writeRows) {
            row.rootView.visibility = visibility
        }
    }

    /** Collect tools that are explicitly allowed (radioAllow checked). */
    fun collectAllowed(): Set<AgentTool> =
        categories.flatMap { it.readRows + it.writeRows }
            .filter { it.radioGroup.checkedRadioButtonId == R.id.radioAllow }
            .map { it.tool.agentTool }
            .toSet()

    /** Collect tools that are denied/disabled (radioDeny checked). */
    fun collectDenied(): Set<AgentTool> =
        categories.flatMap { it.readRows + it.writeRows }
            .filter { it.radioGroup.checkedRadioButtonId == R.id.radioDeny }
            .map { it.tool.agentTool }
            .toSet()

    /** Disable all interactive elements for read-only display. */
    fun setReadOnly() {
        for (state in categories) {
            state.headerBinding.readToggle.isEnabled = false
            state.headerBinding.writeToggle.isEnabled = false
            for (row in state.readRows + state.writeRows) {
                for (i in 0 until row.radioGroup.childCount) {
                    row.radioGroup.getChildAt(i).isEnabled = false
                }
            }
        }
    }

    /** Reset all tools to default state (radioAsk). */
    fun resetAll() {
        updatingFromToggle = true
        for (state in categories) {
            for (row in state.readRows + state.writeRows) {
                row.radioGroup.check(R.id.radioAsk)
            }
            updateToggleState(state.headerBinding.readToggle, state.readRows)
            updateToggleState(state.headerBinding.writeToggle, state.writeRows)
            state.expanded = true
            applyExpansion(state)
        }
        updatingFromToggle = false
    }
}
