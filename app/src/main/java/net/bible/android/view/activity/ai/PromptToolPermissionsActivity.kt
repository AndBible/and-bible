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

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.RadioGroup
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityPromptToolPermissionsBinding
import net.bible.android.activity.databinding.ItemToolPermissionBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry

/**
 * Activity for managing per-prompt tool permission overrides.
 *
 * Displays each write tool with 3 radio buttons: Ask (default), Always allow, Always deny.
 * Green checkmark saves, back button cancels (with dirty confirmation).
 */
class PromptToolPermissionsActivity : ActivityBase() {

    companion object {
        const val EXTRA_ALLOWED_TOOLS = "allowed_tools"
        const val EXTRA_DENIED_TOOLS = "denied_tools"
    }

    private data class ToolRow(val tool: Tool, val radioGroup: RadioGroup)

    private val toolRows = mutableListOf<ToolRow>()
    private var initialAllowed = emptySet<String>()
    private var initialDenied = emptySet<String>()

    private lateinit var binding: ActivityPromptToolPermissionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromptToolPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.prompt_tool_permissions)

        val allowedTools = intent.getStringArrayListExtra(EXTRA_ALLOWED_TOOLS)?.toSet() ?: emptySet()
        val deniedTools = intent.getStringArrayListExtra(EXTRA_DENIED_TOOLS)?.toSet() ?: emptySet()
        initialAllowed = allowedTools
        initialDenied = deniedTools

        val tools = ToolRegistry.getPermissionTools()

        for (tool in tools) {
            val itemBinding = ItemToolPermissionBinding.inflate(LayoutInflater.from(this), binding.toolListContainer, false)

            itemBinding.toolName.text = ToolRegistry.getDisplayName(tool)

            when (tool.name) {
                in allowedTools -> itemBinding.permissionRadioGroup.check(R.id.radioAllow)
                in deniedTools -> itemBinding.permissionRadioGroup.check(R.id.radioDeny)
                else -> itemBinding.permissionRadioGroup.check(R.id.radioAsk)
            }

            binding.toolListContainer.addView(itemBinding.root)
            toolRows.add(ToolRow(tool, itemBinding.permissionRadioGroup))
        }
    }

    private fun collectAllowed(): Set<String> =
        toolRows.filter { it.radioGroup.checkedRadioButtonId == R.id.radioAllow }.map { it.tool.name }.toSet()

    private fun collectDenied(): Set<String> =
        toolRows.filter { it.radioGroup.checkedRadioButtonId == R.id.radioDeny }.map { it.tool.name }.toSet()

    private fun isDirty(): Boolean =
        collectAllowed() != initialAllowed || collectDenied() != initialDenied

    private fun saveAndFinish() {
        val result = Intent().apply {
            putStringArrayListExtra(EXTRA_ALLOWED_TOOLS, ArrayList(collectAllowed()))
            putStringArrayListExtra(EXTRA_DENIED_TOOLS, ArrayList(collectDenied()))
        }
        setResult(Activity.RESULT_OK, result)
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
