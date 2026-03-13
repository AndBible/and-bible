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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.llm.tools.ToolRegistry

/**
 * Displays all available AI tools grouped into read and write categories.
 */
class ToolInfoActivity : ActivityBase() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_info)

        title = getString(R.string.ai_available_tools)

        val container = findViewById<LinearLayout>(R.id.toolInfoContainer)
        val inflater = LayoutInflater.from(this)
        val allTools = ToolRegistry.getAllTools()

        val readTools = allTools.filter { !it.requiresPermission }
        val writeTools = allTools.filter { it.requiresPermission }

        addSectionHeader(container, getString(R.string.ai_read_tools))
        for (tool in readTools) {
            addToolItem(inflater, container, ToolRegistry.getDisplayName(tool), tool.description)
        }

        addSectionHeader(container, getString(R.string.ai_write_tools))
        for (tool in writeTools) {
            addToolItem(inflater, container, ToolRegistry.getDisplayName(tool), tool.description)
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

    private fun addToolItem(inflater: LayoutInflater, container: LinearLayout, name: String, description: String) {
        val itemView = inflater.inflate(R.layout.item_tool_info, container, false)
        itemView.findViewById<TextView>(R.id.toolName).text = name
        itemView.findViewById<TextView>(R.id.toolDescription).text = description
        container.addView(itemView)
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
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.andbible.org/en/latest/ai.html")))
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
