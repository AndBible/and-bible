/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import android.text.format.Formatter
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.LlmCacheManagementBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.CacheEntrySummary
import net.bible.service.llm.LlmProcessingDao
import java.text.DateFormat
import java.util.Date

class LlmCacheActivity : ActivityBase() {

    private lateinit var binding: LlmCacheManagementBinding
    private lateinit var adapter: CacheEntryAdapter
    private lateinit var deleteFilteredItem: MenuItem

    private val dao: LlmProcessingDao get() = DatabaseContainer.instance.llmProcessingDb.llmProcessingDao()

    private var allEntries: List<CacheEntrySummary> = emptyList()
    private var filteredEntries: List<CacheEntrySummary> = emptyList()

    private var selectedDocument: String? = null
    private var selectedType: String? = null
    private var selectedModel: String? = null

    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LlmCacheManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        title = getString(R.string.llm_cache_management)

        adapter = CacheEntryAdapter(
            onItemClick = { entry -> showEntryDetail(entry) },
            onItemLongClick = { entry -> startSelectionMode(entry) },
            onSelectionChanged = { updateActionModeTitle() }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.llm_cache_options_menu, menu)
        deleteFilteredItem = menu.findItem(R.id.deleteFiltered)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.deleteAll -> { confirmDeleteAll(); true }
        R.id.deleteOlderThan -> { showDeleteOlderThanDialog(); true }
        R.id.deleteFiltered -> { confirmDeleteFiltered(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { dao.getAllSummaries() }
            allEntries = entries

            val documents = withContext(Dispatchers.IO) { dao.getDistinctDocuments() }
            val types = withContext(Dispatchers.IO) { dao.getDistinctProcessingTypes() }
            val models = withContext(Dispatchers.IO) { dao.getDistinctModels() }
            val stats = withContext(Dispatchers.IO) { dao.getCacheStats() }

            setupSpinner(binding.documentFilter, documents, selectedDocument, getString(R.string.llm_cache_filter_all_documents)) { selectedDocument = it; applyFilter() }
            setupSpinner(binding.typeFilter, types, selectedType, getString(R.string.llm_cache_filter_all_types)) { selectedType = it; applyFilter() }
            setupSpinner(binding.modelFilter, models, selectedModel, getString(R.string.llm_cache_filter_all_models)) { selectedModel = it; applyFilter() }

            binding.statsHeader.text = getString(R.string.llm_cache_stats, stats.entryCount, formatSize(stats.totalSize))
            applyFilter()
        }
    }

    private fun setupSpinner(spinner: Spinner, values: List<String>, currentSelection: String?, allLabel: String, onSelected: (String?) -> Unit) {
        val items = listOf(allLabel) + values
        val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = arrayAdapter

        val selectionIndex = if (currentSelection != null) values.indexOf(currentSelection) + 1 else 0
        spinner.setSelection(selectionIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelected(if (position == 0) null else values[position - 1])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyFilter() {
        filteredEntries = allEntries.filter { entry ->
            (selectedDocument == null || entry.documentInitials == selectedDocument) &&
            (selectedType == null || entry.processingType == selectedType) &&
            (selectedModel == null || entry.modelId == selectedModel)
        }
        adapter.submitList(filteredEntries)

        val hasFilter = selectedDocument != null || selectedType != null || selectedModel != null
        if (::deleteFilteredItem.isInitialized) {
            deleteFilteredItem.isVisible = hasFilter && filteredEntries.isNotEmpty()
        }

        val isEmpty = filteredEntries.isEmpty()
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun showEntryDetail(entry: CacheEntrySummary) {
        val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
            .format(Date(entry.createdAt))
        val sizeStr = formatSize(entry.xmlSize.toLong())

        val message = buildString {
            appendLine(getString(R.string.llm_cache_detail_document, entry.documentInitials))
            appendLine(getString(R.string.llm_cache_detail_key, entry.keyName))
            appendLine(getString(R.string.llm_cache_detail_type, entry.processingType))
            appendLine(getString(R.string.llm_cache_detail_params, entry.processingParams))
            appendLine(getString(R.string.llm_cache_detail_model, entry.modelId))
            appendLine(getString(R.string.llm_cache_detail_date, dateStr))
            appendLine(getString(R.string.llm_cache_detail_size, sizeStr))
            if (entry.languageCode != null) {
                appendLine(getString(R.string.llm_cache_detail_language, entry.languageCode))
            }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.llm_cache_item_primary, entry.documentInitials, entry.keyName))
            .setMessage(message)
            .setPositiveButton(R.string.okay, null)
            .setNegativeButton(R.string.delete) { _, _ ->
                deleteEntry(entry)
            }
            .show()
    }

    private fun deleteEntry(entry: CacheEntrySummary) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteEntry(entry.documentInitials, entry.keyName, entry.processingType, entry.processingParams)
            }
            Toast.makeText(this@LlmCacheActivity, R.string.llm_cache_deleted, Toast.LENGTH_SHORT).show()
            loadData()
        }
    }

    private fun startSelectionMode(entry: CacheEntrySummary) {
        if (actionMode != null) return
        actionMode = startActionMode(actionModeCallback)
        adapter.selectedItems.add(
            CacheEntryAdapter.CompositeKey(entry.documentInitials, entry.keyName, entry.processingType, entry.processingParams)
        )
        adapter.actionModeActive = true
        updateActionModeTitle()
    }

    private fun updateActionModeTitle() {
        actionMode?.title = getString(R.string.llm_cache_selected_count, adapter.selectedItems.size)
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.llm_cache_action_mode_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = when (item.itemId) {
            R.id.deleteSelected -> {
                confirmDeleteSelected()
                true
            }
            else -> false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.actionModeActive = false
            actionMode = null
        }
    }

    private fun confirmDeleteSelected() {
        val count = adapter.selectedItems.size
        if (count == 0) return
        AlertDialog.Builder(this)
            .setTitle(R.string.llm_cache_delete_confirm_title)
            .setMessage(getString(R.string.llm_cache_delete_confirm_selected, count))
            .setPositiveButton(R.string.delete) { _, _ -> deleteSelected() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSelected() {
        val keys = adapter.selectedItems.toList()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                keys.forEach { dao.deleteEntry(it.documentInitials, it.keyName, it.processingType, it.processingParams) }
            }
            actionMode?.finish()
            Toast.makeText(this@LlmCacheActivity, getString(R.string.llm_cache_deleted_count, keys.size), Toast.LENGTH_SHORT).show()
            loadData()
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.llm_cache_delete_confirm_title)
            .setMessage(R.string.llm_cache_delete_confirm_all)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { dao.deleteAll() }
                    Toast.makeText(this@LlmCacheActivity, R.string.llm_cache_deleted, Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteFiltered() {
        val count = filteredEntries.size
        if (count == 0) return
        AlertDialog.Builder(this)
            .setTitle(R.string.llm_cache_delete_confirm_title)
            .setMessage(getString(R.string.llm_cache_delete_confirm_filtered, count))
            .setPositiveButton(R.string.delete) { _, _ -> deleteFiltered() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteFiltered() {
        val entries = filteredEntries.toList()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                entries.forEach { dao.deleteEntry(it.documentInitials, it.keyName, it.processingType, it.processingParams) }
            }
            Toast.makeText(this@LlmCacheActivity, getString(R.string.llm_cache_deleted_count, entries.size), Toast.LENGTH_SHORT).show()
            loadData()
        }
    }

    private fun showDeleteOlderThanDialog() {
        val options = arrayOf(
            getString(R.string.llm_cache_older_than_7),
            getString(R.string.llm_cache_older_than_30),
            getString(R.string.llm_cache_older_than_90),
        )
        val days = intArrayOf(7, 30, 90)

        AlertDialog.Builder(this)
            .setTitle(R.string.llm_cache_older_than_title)
            .setItems(options) { _, which ->
                confirmDeleteOlderThan(days[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteOlderThan(days: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.llm_cache_delete_confirm_title)
            .setMessage(getString(R.string.llm_cache_delete_confirm_older_than, days))
            .setPositiveButton(R.string.delete) { _, _ ->
                val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { dao.deleteByDateRange(0, cutoff) }
                    Toast.makeText(this@LlmCacheActivity, R.string.llm_cache_deleted, Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatSize(bytes: Long): String =
        Formatter.formatShortFileSize(this, bytes)
}
