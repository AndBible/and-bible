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
import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityRawLogHistoryBinding
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import android.view.LayoutInflater
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmRawLogSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RawLogHistoryActivity : ActivityBase() {

    private lateinit var binding: ActivityRawLogHistoryBinding
    private lateinit var adapter: RawLogHistoryAdapter
    private var actionMode: ActionMode? = null

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (actionMode != null) {
            actionMode?.finish()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRawLogHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.raw_log_history_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = RawLogHistoryAdapter(
            onClick = { summary -> openLog(summary.id) },
            onSelectionChanged = { count -> updateActionMode(count) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RawLogHistoryActivity)
            adapter = this@RawLogHistoryActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val summaries = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().allSummaries()
        adapter.clearSelection()
        adapter.submitList(summaries)
        binding.emptyText.visibility = if (summaries.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.text = getString(R.string.raw_log_history_empty)
        binding.recyclerView.visibility = if (summaries.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openLog(logId: IdType) {
        val intent = Intent(this, RawLlmLogActivity::class.java).apply {
            putExtra(RawLlmLogActivity.EXTRA_LOG_RECORD_ID, logId.toString())
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_DELETE_OLD, Menu.NONE, R.string.raw_log_delete_old)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_DELETE_OLD -> { showDeleteOldDialog(); true }
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showDeleteOldDialog() {
        val options = arrayOf(
            getString(R.string.raw_log_older_1_week),
            getString(R.string.raw_log_older_1_month),
            getString(R.string.raw_log_older_3_months),
            getString(R.string.raw_log_delete_all),
        )
        val cutoffs = longArrayOf(
            7L * 24 * 60 * 60 * 1000,
            30L * 24 * 60 * 60 * 1000,
            90L * 24 * 60 * 60 * 1000,
            0L,
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.raw_log_delete_old)
            .setItems(options) { _, which ->
                val cutoff = cutoffs[which]
                val dao = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao()
                if (cutoff == 0L) {
                    dao.deleteAll()
                } else {
                    dao.deleteOlderThan(System.currentTimeMillis() - cutoff)
                }
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateActionMode(selectedCount: Int) {
        if (selectedCount > 0) {
            if (actionMode == null) {
                actionMode = startActionMode(actionModeCallback)
            }
            actionMode?.title = getString(R.string.raw_log_delete_confirm, selectedCount)
        } else {
            actionMode?.finish()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, MENU_DELETE_SELECTED, Menu.NONE, R.string.raw_log_delete_selected)
                .setIcon(R.drawable.ic_delete_24dp)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            adapter.setSelectionMode(true)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId == MENU_DELETE_SELECTED) {
                val selected = adapter.getSelectedIds()
                if (selected.isNotEmpty()) {
                    DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().deleteByIds(selected)
                    refreshList()
                }
                mode.finish()
                return true
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.setSelectionMode(false)
            actionMode = null
        }
    }

    companion object {
        private const val MENU_DELETE_OLD = 1
        private const val MENU_DELETE_SELECTED = 2
    }
}

class RawLogHistoryAdapter(
    private val onClick: (LlmRawLogSummary) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
) : ListAdapter<LlmRawLogSummary, RawLogHistoryAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val selectedIds = mutableSetOf<IdType>()
    private var selectionMode = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun clearSelection() {
        selectedIds.clear()
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    fun getSelectedIds(): List<IdType> = selectedIds.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.raw_log_history_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val promptName: TextView = itemView.findViewById(R.id.promptName)
        private val modelInfo: TextView = itemView.findViewById(R.id.modelInfo)
        private val tokenInfo: TextView = itemView.findViewById(R.id.tokenInfo)
        private val costInfo: TextView = itemView.findViewById(R.id.costInfo)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val errorIndicator: TextView = itemView.findViewById(R.id.errorIndicator)

        fun bind(item: LlmRawLogSummary) {
            promptName.text = item.promptName.ifBlank { "—" }

            val providerLabel = item.providerType.takeIf { it.isNotBlank() }
                ?.let { type ->
                    try { LlmProvider.valueOf(type).displayName } catch (_: Exception) { type }
                } ?: ""
            modelInfo.text = if (providerLabel.isNotBlank()) "$providerLabel · ${item.modelName}" else item.modelName

            tokenInfo.text = itemView.context.getString(
                R.string.raw_log_item_tokens,
                LlmCostTracker.formatTokenCount(item.totalInputTokens),
                LlmCostTracker.formatTokenCount(item.totalOutputTokens)
            )

            costInfo.text = if (item.estimatedCostUsd > 0) LlmCostTracker.formatCost(item.estimatedCostUsd) else ""
            costInfo.visibility = if (item.estimatedCostUsd > 0) View.VISIBLE else View.GONE

            timestamp.text = dateFormat.format(Date(item.timestamp))

            errorIndicator.visibility = if (item.wasError) View.VISIBLE else View.GONE
            errorIndicator.text = itemView.context.getString(R.string.raw_log_error_indicator)

            checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = item.id in selectedIds
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedIds.add(item.id) else selectedIds.remove(item.id)
                onSelectionChanged(selectedIds.size)
            }

            itemView.setOnClickListener {
                if (selectionMode) {
                    checkbox.isChecked = !checkbox.isChecked
                } else {
                    onClick(item)
                }
            }

            itemView.setOnLongClickListener {
                if (!selectionMode) {
                    selectedIds.add(item.id)
                    onSelectionChanged(selectedIds.size)
                }
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LlmRawLogSummary>() {
            override fun areItemsTheSame(old: LlmRawLogSummary, new: LlmRawLogSummary) = old.id == new.id
            override fun areContentsTheSame(old: LlmRawLogSummary, new: LlmRawLogSummary) = old == new
        }
    }
}
