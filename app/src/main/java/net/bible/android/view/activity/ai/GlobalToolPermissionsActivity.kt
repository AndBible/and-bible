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
import android.view.Menu
import android.view.MenuItem
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityPromptToolPermissionsBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils

/**
 * Activity for managing global default tool permissions.
 *
 * Displays all configurable tools grouped by category (Bible & Search, Bookmarks, etc.)
 * with expandable sections and per-category read/write toggles.
 */
class GlobalToolPermissionsActivity : ActivityBase() {

    private val settings get() = CommonUtils.aiSettings
    private lateinit var binding: ActivityPromptToolPermissionsBinding
    private lateinit var listBuilder: ToolPermissionListBuilder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromptToolPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.global_tool_permissions_title)

        listBuilder = ToolPermissionListBuilder(this, binding.toolListContainer, ToolPermissionListBuilder.Mode.GLOBAL)
        listBuilder.build(
            allowedTools = settings.permanentlyAllowedTools,
            deniedTools = settings.permanentlyDeniedTools,
        )
    }

    private fun isDirty(): Boolean =
        listBuilder.collectAllowed() != settings.permanentlyAllowedTools ||
            listBuilder.collectDenied() != settings.permanentlyDeniedTools

    private fun saveAndFinish() {
        settings.permanentlyAllowedTools = listBuilder.collectAllowed()
        settings.permanentlyDeniedTools = listBuilder.collectDenied()
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
            R.id.show_help -> {
                CommonUtils.showHelpDialog(
                    activity = this,
                    titleResId = R.string.help,
                    messageResId = R.string.help_global_tool_permissions_text,
                    helpPath = "ai.html#setting-permissions",
                )
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
