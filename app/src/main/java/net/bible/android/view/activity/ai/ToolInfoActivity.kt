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

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityToolInfoBinding
import net.bible.android.activity.databinding.ItemToolInfoBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.llm.tools.ToolRegistry

/**
 * Displays all available AI tools grouped into read and write categories.
 */
class ToolInfoActivity : ActivityBase() {

    private lateinit var binding: ActivityToolInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.ai_available_tools)

        val allTools = ToolRegistry.getAllTools()

        val readTools = allTools.filter { !it.requiresPermission }
        val writeTools = allTools.filter { it.requiresPermission }

        addSectionHeader(binding.toolInfoContainer, getString(R.string.ai_read_tools))
        for (tool in readTools) {
            addToolItem(binding.toolInfoContainer, ToolRegistry.getDisplayName(tool), tool.description)
        }

        addSectionHeader(binding.toolInfoContainer, getString(R.string.ai_write_tools))
        for (tool in writeTools) {
            addToolItem(binding.toolInfoContainer, ToolRegistry.getDisplayName(tool), tool.description)
        }
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    private fun addSectionHeader(container: LinearLayout, text: String) {
        val padding = dpToPx(16)
        val header = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(padding, padding, padding, dpToPx(4))
        }
        container.addView(header)
    }

    private fun addToolItem(container: LinearLayout, name: String, description: String) {
        val itemBinding = ItemToolInfoBinding.inflate(LayoutInflater.from(this), container, false)
        itemBinding.toolName.text = name
        itemBinding.toolDescription.text = description
        container.addView(itemBinding.root)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_DOCUMENTATION, Menu.NONE, R.string.help)
            .setIcon(R.drawable.ic_help_white_24dp)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_DOCUMENTATION -> {
                CommonUtils.showHelpDialog(
                    activity = this,
                    titleResId = R.string.help,
                    messageResId = R.string.help_tool_info_text,
                    helpPath = "ai.html#ai-tools",
                )
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val MENU_DOCUMENTATION = 1
    }
}
