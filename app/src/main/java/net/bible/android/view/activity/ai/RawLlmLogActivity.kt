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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityRawLlmLogBinding
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.llm.agent.AgentSessionManager

/**
 * Displays the raw LLM conversation log for debugging purposes.
 */
class RawLlmLogActivity : ActivityBase() {

    private lateinit var binding: ActivityRawLlmLogBinding
    private var logText: String = ""

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRawLlmLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.raw_llm_log_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val workspaceIdStr = intent.getStringExtra(EXTRA_WORKSPACE_ID)
        logText = if (workspaceIdStr != null) {
            val workspaceId = IdType(workspaceIdStr)
            AgentSessionManager.getSession(workspaceId)?.rawLlmLog?.format() ?: ""
        } else {
            ""
        }

        binding.logContent.text = logText.ifEmpty { getString(R.string.raw_llm_log_empty) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_COPY, Menu.NONE, android.R.string.copy)
            .setIcon(R.drawable.ic_content_copy_black_24dp)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, MENU_SHARE, Menu.NONE, R.string.share)
            .setIcon(R.drawable.ic_baseline_share_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_COPY -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Raw LLM Log", logText))
                Toast.makeText(this, R.string.raw_llm_log_copied, Toast.LENGTH_SHORT).show()
                true
            }
            MENU_SHARE -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, logText)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, getString(R.string.share)))
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
        const val EXTRA_WORKSPACE_ID = "workspace_id"
        private const val MENU_COPY = 1
        private const val MENU_SHARE = 2
    }
}
