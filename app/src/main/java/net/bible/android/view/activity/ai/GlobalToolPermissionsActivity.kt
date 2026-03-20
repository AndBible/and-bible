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

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityPromptToolPermissionsBinding
import net.bible.android.activity.databinding.ItemToolPermissionBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.llm.AgentTool
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry

/**
 * Activity for managing global default tool permissions.
 *
 * Displays all configurable tools in two sections:
 * - Read tools: Enabled (default) / Disabled
 * - Write tools: Ask (default) / Always allow / Always deny
 *
 * Disabled/denied tools are excluded from LLM tool definitions globally,
 * but can be overridden per-prompt.
 */
class GlobalToolPermissionsActivity : ActivityBase() {

    private data class ToolRow(val tool: Tool, val radioGroup: RadioGroup)

    private val toolRows = mutableListOf<ToolRow>()
    private val settings get() = CommonUtils.settings

    private lateinit var binding: ActivityPromptToolPermissionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromptToolPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.global_tool_permissions_title)

        val allowed = settings.permanentlyAllowedTools
        val denied = settings.permanentlyDeniedTools

        val tools = ToolRegistry.getConfigurableTools()
        val readTools = tools.filter { !it.requiresPermission }
        val writeTools = tools.filter { it.requiresPermission }

        if (readTools.isNotEmpty()) {
            addSectionHeader(getString(R.string.tool_section_read_tools))
            for (tool in readTools) {
                addReadToolRow(tool, denied)
            }
        }

        if (writeTools.isNotEmpty()) {
            addSectionHeader(getString(R.string.tool_section_write_tools))
            for (tool in writeTools) {
                addWriteToolRow(tool, allowed, denied)
            }
        }
    }

    private fun addSectionHeader(text: String) {
        val density = resources.displayMetrics.density
        val header = TextView(this).apply {
            this.text = text
            setTextAppearance(android.R.style.TextAppearance_Medium)
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (4 * density).toInt()
            )
        }
        binding.toolListContainer.addView(header)
    }

    private fun addReadToolRow(tool: Tool, deniedTools: Set<AgentTool>) {
        val itemBinding = ItemToolPermissionBinding.inflate(LayoutInflater.from(this), binding.toolListContainer, false)

        itemBinding.toolName.text = ToolRegistry.getDisplayName(tool)

        // Read tools: 2 options — reuse radioAsk as "Enabled", hide radioAllow, radioDeny as "Disabled"
        itemBinding.radioAsk.text = getString(R.string.tool_option_enabled)
        itemBinding.radioAllow.visibility = View.GONE
        itemBinding.radioDeny.text = getString(R.string.tool_option_disabled)

        if (tool.agentTool in deniedTools) {
            itemBinding.permissionRadioGroup.check(R.id.radioDeny)
        } else {
            itemBinding.permissionRadioGroup.check(R.id.radioAsk)
        }

        binding.toolListContainer.addView(itemBinding.root)
        toolRows.add(ToolRow(tool, itemBinding.permissionRadioGroup))
    }

    private fun addWriteToolRow(tool: Tool, allowedTools: Set<AgentTool>, deniedTools: Set<AgentTool>) {
        val itemBinding = ItemToolPermissionBinding.inflate(LayoutInflater.from(this), binding.toolListContainer, false)

        itemBinding.toolName.text = ToolRegistry.getDisplayName(tool)

        when (tool.agentTool) {
            in allowedTools -> itemBinding.permissionRadioGroup.check(R.id.radioAllow)
            in deniedTools -> itemBinding.permissionRadioGroup.check(R.id.radioDeny)
            else -> itemBinding.permissionRadioGroup.check(R.id.radioAsk)
        }

        binding.toolListContainer.addView(itemBinding.root)
        toolRows.add(ToolRow(tool, itemBinding.permissionRadioGroup))
    }

    private fun collectAllowed(): Set<AgentTool> =
        toolRows
            .filter { it.tool.requiresPermission && it.radioGroup.checkedRadioButtonId == R.id.radioAllow }
            .map { it.tool.agentTool }.toSet()

    private fun collectDenied(): Set<AgentTool> =
        toolRows.filter { it.radioGroup.checkedRadioButtonId == R.id.radioDeny }
            .map { it.tool.agentTool }.toSet()

    private fun isDirty(): Boolean =
        collectAllowed() != settings.permanentlyAllowedTools ||
            collectDenied() != settings.permanentlyDeniedTools

    private fun saveAndFinish() {
        settings.permanentlyAllowedTools = collectAllowed()
        settings.permanentlyDeniedTools = collectDenied()
        finish()
    }

    private fun cancelOrConfirmDiscard() {
        if (isDirty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.discard_changes_confirmation)
                .setPositiveButton(R.string.yes) { _, _ -> finish() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            finish()
        }
    }

    private fun resetAll() {
        for (row in toolRows) {
            row.radioGroup.check(R.id.radioAsk)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.prompt_tool_permissions_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save_permissions -> {
                saveAndFinish()
                true
            }
            R.id.reset_all -> {
                resetAll()
                true
            }
            android.R.id.home -> {
                cancelOrConfirmDiscard()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        cancelOrConfirmDiscard()
    }
}
