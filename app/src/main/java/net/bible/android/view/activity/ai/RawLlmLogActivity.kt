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

import android.content.res.ColorStateList
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityRawLlmLogBinding
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.RawLlmLog

/**
 * Displays the raw LLM conversation log as an expandable list for debugging.
 */
class RawLlmLogActivity : ActivityBase() {

    private lateinit var binding: ActivityRawLlmLogBinding
    private var rawLog: RawLlmLog? = null

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
        rawLog = if (workspaceIdStr != null) {
            AgentSessionManager.getSession(IdType(workspaceIdStr))?.rawLlmLog
        } else null

        val log = rawLog
        if (log == null || log.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.emptyText.text = getString(R.string.raw_llm_log_empty)
            binding.recyclerView.visibility = View.GONE
            return
        }

        val entries = log.getEntries()
        val usageByIteration = log.usageByIteration

        // Show total cost header
        if (usageByIteration.isNotEmpty()) {
            var totalCost = 0.0
            var hasCost = false
            var totalInput = 0L
            var totalOutput = 0L
            for ((_, data) in usageByIteration) {
                totalInput += data.usage.inputTokens
                totalOutput += data.usage.outputTokens
                val cost = LlmPricing.estimateCost(data.usage, data.model)
                if (cost != null) {
                    totalCost += cost
                    hasCost = true
                }
            }
            val costStr = if (hasCost) " · ${LlmCostTracker.formatCost(totalCost)}" else ""
            binding.totalCostHeader.text = getString(
                R.string.raw_llm_log_total,
                RawLlmLogAdapter.formatTokenCount(totalInput),
                RawLlmLogAdapter.formatTokenCount(totalOutput),
                costStr
            )
            binding.totalCostHeader.visibility = View.VISIBLE
            binding.headerDivider.visibility = View.VISIBLE
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RawLlmLogActivity)
            adapter = RawLlmLogAdapter(entries, usageByIteration)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val white = ColorStateList.valueOf(Color.WHITE)
        menu.add(Menu.NONE, MENU_COPY, Menu.NONE, android.R.string.copy)
            .setIcon(R.drawable.ic_content_copy_black_24dp)
            .setIconTintList(white)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, MENU_SHARE, Menu.NONE, R.string.share)
            .setIcon(R.drawable.ic_baseline_share_24)
            .setIconTintList(white)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_COPY -> {
                val logText = rawLog?.format() ?: ""
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Raw LLM Log", logText))
                Toast.makeText(this, R.string.raw_llm_log_copied, Toast.LENGTH_SHORT).show()
                true
            }
            MENU_SHARE -> {
                val logText = rawLog?.format() ?: ""
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
