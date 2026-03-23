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
import android.view.Menu
import android.view.MenuItem
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityPromptToolPermissionsBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.llm.AgentTool

/**
 * Activity for managing per-prompt tool permission overrides.
 *
 * Displays all configurable tools grouped by category with expandable sections
 * and per-category read/write toggles. Each tool can follow the global default
 * or be overridden for this prompt.
 */
class PromptToolPermissionsActivity : ActivityBase() {

    companion object {
        const val EXTRA_ALLOWED_TOOLS = "allowed_tools"
        const val EXTRA_DENIED_TOOLS = "denied_tools"
        const val EXTRA_READ_ONLY = "read_only"
    }

    private var initialAllowed = emptySet<AgentTool>()
    private var initialDenied = emptySet<AgentTool>()
    private var readOnly = false

    private lateinit var binding: ActivityPromptToolPermissionsBinding
    private lateinit var listBuilder: ToolPermissionListBuilder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromptToolPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.prompt_tool_permissions)

        val allowedTools = intent.getStringArrayListExtra(EXTRA_ALLOWED_TOOLS)
            ?.mapNotNull { try { AgentTool.valueOf(it) } catch (_: IllegalArgumentException) { null } }
            ?.toSet() ?: emptySet()
        val deniedTools = intent.getStringArrayListExtra(EXTRA_DENIED_TOOLS)
            ?.mapNotNull { try { AgentTool.valueOf(it) } catch (_: IllegalArgumentException) { null } }
            ?.toSet() ?: emptySet()
        initialAllowed = allowedTools
        initialDenied = deniedTools

        readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)

        listBuilder = ToolPermissionListBuilder(this, binding.toolListContainer, ToolPermissionListBuilder.Mode.PROMPT)
        listBuilder.build(
            allowedTools = allowedTools,
            deniedTools = deniedTools,
            globalAllowed = CommonUtils.aiSettings.permanentlyAllowedTools,
            globalDenied = CommonUtils.aiSettings.permanentlyDeniedTools,
        )

        if (readOnly) {
            title = getString(R.string.prompt_tool_permissions_read_only)
            listBuilder.setReadOnly()
        }
    }

    private fun isDirty(): Boolean =
        listBuilder.collectAllowed() != initialAllowed || listBuilder.collectDenied() != initialDenied

    private fun saveAndFinish() {
        val result = Intent().apply {
            putStringArrayListExtra(EXTRA_ALLOWED_TOOLS, ArrayList(listBuilder.collectAllowed().map { it.name }))
            putStringArrayListExtra(EXTRA_DENIED_TOOLS, ArrayList(listBuilder.collectDenied().map { it.name }))
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.prompt_tool_permissions_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (readOnly) {
            menu.findItem(R.id.save_permissions)?.isVisible = false
            menu.findItem(R.id.reset_all)?.isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save_permissions -> {
                saveAndFinish()
                true
            }
            R.id.reset_all -> {
                listBuilder.resetAll()
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
