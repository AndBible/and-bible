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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.MenuItemCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityRawLlmLogBinding
import net.bible.android.control.report.AiBugReport
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmRawLogRecord
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.RawLlmLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays the raw LLM conversation log for debugging.
 *
 * Two modes:
 * - In-memory mode (EXTRA_WORKSPACE_ID): Shows structured entries from active AgentSession
 * - Database mode (EXTRA_LOG_RECORD_ID): Shows formatted text from persisted LlmRawLogRecord
 */
class RawLlmLogActivity : ActivityBase() {

    private lateinit var binding: ActivityRawLlmLogBinding

    /** In-memory log from active session (mode 1). */
    private var rawLog: RawLlmLog? = null

    /** Persisted log record (mode 2). */
    private var logRecord: LlmRawLogRecord? = null
    private var logRecordText: String? = null

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

        val logRecordIdStr = intent.getStringExtra(EXTRA_LOG_RECORD_ID)
        val workspaceIdStr = intent.getStringExtra(EXTRA_WORKSPACE_ID)

        if (logRecordIdStr != null) {
            setupDatabaseMode(IdType(logRecordIdStr))
        } else if (workspaceIdStr != null) {
            setupInMemoryMode(IdType(workspaceIdStr))
        } else {
            showEmpty()
        }
    }

    private fun setupDatabaseMode(logRecordId: IdType) {
        lifecycleScope.launch {
            val (record, text) = withContext(Dispatchers.IO) {
                val r = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getById(logRecordId)
                    ?: return@withContext null to null
                r to RawLlmLog.gzipDecompress(r.logData)
            }
            if (record == null || text == null) {
                showEmpty()
                return@launch
            }
            logRecord = record
            logRecordText = text

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            title = "${record.modelName} — ${dateFormat.format(Date(record.timestamp))}"

            if (record.estimatedCostUsd > 0 || record.totalInputTokens > 0) {
                val costStr = if (record.estimatedCostUsd > 0) " · ${LlmCostTracker.formatCost(record.estimatedCostUsd)}" else ""
                binding.totalCostHeader.text = getString(
                    R.string.raw_llm_log_total,
                    RawLlmLogAdapter.formatTokenCount(record.totalInputTokens),
                    RawLlmLogAdapter.formatTokenCount(record.totalOutputTokens),
                    costStr
                )
                binding.totalCostHeader.visibility = View.VISIBLE
                binding.headerDivider.visibility = View.VISIBLE
            }

            // Display as scrollable plain text
            binding.recyclerView.visibility = View.GONE
            val scrollView = ScrollView(this@RawLlmLogActivity)
            val textView = TextView(this@RawLlmLogActivity).apply {
                setPadding(24, 16, 24, 16)
                textSize = 11f
                setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE
                setText(text)
            }
            scrollView.addView(textView)

            val parent = binding.recyclerView.parent as? android.view.ViewGroup
            parent?.addView(scrollView, binding.recyclerView.layoutParams)

            invalidateOptionsMenu()
        }
    }

    private fun setupInMemoryMode(workspaceId: IdType) {
        rawLog = AgentSessionManager.getSession(workspaceId)?.rawLlmLog

        val log = rawLog
        if (log == null || log.isEmpty()) {
            showEmpty()
            return
        }

        val entries = log.getEntries()
        val usageByIteration = log.usageByIteration

        if (usageByIteration.isNotEmpty()) {
            var totalCost = 0.0
            var hasCost = false
            var totalInput = 0L
            var totalOutput = 0L
            for ((_, data) in usageByIteration) {
                totalInput += data.usage.inputTokens
                totalOutput += data.usage.outputTokens
                val cost = LlmPricing.estimateCost(data.usage, data.model, data.configuredModelId)
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

    private fun showEmpty() {
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = getString(R.string.raw_llm_log_empty)
        binding.recyclerView.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_COPY, Menu.NONE, android.R.string.copy)
            .setIcon(R.drawable.ic_content_copy_black_24dp)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(Menu.NONE, MENU_SHARE, Menu.NONE, R.string.share)
            .setIcon(R.drawable.ic_baseline_share_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        // Bug report: available in both modes for supported models
        val record = logRecord
        if (record != null) {
            // Database mode: delete + report
            menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.delete)
            val reportItem = menu.add(Menu.NONE, MENU_REPORT_BUG, Menu.NONE, R.string.ai_bug_report_menu)
            reportItem.isEnabled = AiBugReport.isReportAvailable(record.modelName)
        } else if (rawLog != null && rawLog!!.usageByIteration.isNotEmpty()) {
            // In-memory mode: report only
            val modelName = AiBugReport.resolveModelNameFromRawLog(rawLog!!)
            val reportItem = menu.add(Menu.NONE, MENU_REPORT_BUG, Menu.NONE, R.string.ai_bug_report_menu)
            reportItem.isEnabled = AiBugReport.isReportAvailable(modelName)
        }

        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.toolbarTextColor, typedValue, true)
        val tintList = ColorStateList.valueOf(typedValue.data)
        for (i in 0 until menu.size()) {
            MenuItemCompat.setIconTintList(menu.getItem(i), tintList)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_COPY -> {
                val logText = getLogText()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Raw LLM Log", logText))
                Toast.makeText(this, R.string.raw_llm_log_copied, Toast.LENGTH_SHORT).show()
                true
            }
            MENU_SHARE -> {
                val logText = getLogText()
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, logText)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, getString(R.string.share)))
                true
            }
            MENU_DELETE -> {
                val record = logRecord ?: return true
                AlertDialog.Builder(this)
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().deleteByIds(listOf(record.id))
                        finish()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            MENU_REPORT_BUG -> {
                lifecycleScope.launch {
                    val record = logRecord
                    val log = rawLog
                    if (record != null) {
                        AiBugReport.reportAiBug(this@RawLlmLogActivity, record.id)
                    } else if (log != null) {
                        AiBugReport.reportAiBugFromRawLog(this@RawLlmLogActivity, log)
                    }
                }
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getLogText(): String = logRecordText ?: rawLog?.format() ?: ""

    companion object {
        const val EXTRA_WORKSPACE_ID = "workspace_id"
        const val EXTRA_LOG_RECORD_ID = "log_record_id"
        private const val MENU_COPY = 1
        private const val MENU_SHARE = 2
        private const val MENU_DELETE = 3
        private const val MENU_REPORT_BUG = 4
    }
}
