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

package net.bible.android.view.activity.cloud

import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityCloudDocumentsBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.util.Hourglass
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books

enum class CloudDocFilter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED }

/**
 * Pure filter used by the cloud documents management view: keeps items matching the
 * selected status, a case-insensitive name substring, and (when non-null) an exact
 * book category. A null [category] argument means "any category"; an item whose own
 * category is null only matches when [category] is null.
 */
fun filterCloudDocuments(
    items: List<DocumentSync.DocumentStatusItem>,
    status: CloudDocFilter,
    nameQuery: String,
    category: BookCategory?,
): List<DocumentSync.DocumentStatusItem> {
    val query = nameQuery.trim()
    return items.filter { item ->
        val statusOk = when (status) {
            CloudDocFilter.ALL -> true
            CloudDocFilter.INSTALLED -> !item.cloudOnly
            CloudDocFilter.CLOUD -> !item.localOnly
            CloudDocFilter.UPDATES -> item.updateAvailable
            CloudDocFilter.BLOCKED -> item.blocked
        }
        val nameOk = query.isEmpty() || item.name.contains(query, ignoreCase = true)
        val categoryOk = category == null || item.category == category
        statusOk && nameOk && categoryOk
    }
}

/**
 * Management view for the document-sync feature: a list of the user's documents
 * (local and/or in the cloud) with their sync status, a filter selector, and
 * per-item actions (download, push, remove from cloud, block/unblock).
 *
 * Status is conveyed by an icon plus a text label (never colour alone) so the
 * screen works on monochrome / e-ink devices.
 */
class CloudDocumentsActivity : ActivityBase() {

    private lateinit var binding: ActivityCloudDocumentsBinding
    private lateinit var adapter: CloudDocumentsAdapter

    private var setupMode: Boolean = false
    /** True until the setup-mode UI has been applied after the first scan completes. */
    private var pendingSetup: Boolean = false
    private var allItems: List<DocumentStatusItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudDocumentsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        setupMode = intent.getBooleanExtra(EXTRA_SETUP_MODE, false)
        pendingSetup = setupMode

        title = getString(R.string.document_sync_manage_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = CloudDocumentsAdapter(
            onOverflow = { item, anchor -> showItemMenu(item, anchor) },
            onSelectionChanged = { count -> onSelectionChanged(count) },
        )
        binding.recycler.apply {
            layoutManager = LinearLayoutManager(this@CloudDocumentsActivity)
            adapter = this@CloudDocumentsActivity.adapter
        }

        binding.primaryAction.setOnClickListener {
            if (setupMode) performSetupSync() else performBulkAction()
        }

        val filterSelectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = applyFilter()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.statusSpinner.onItemSelectedListener = filterSelectionListener
        binding.categorySpinner.onItemSelectedListener = filterSelectionListener
        binding.nameSearch.addTextChangedListener(afterTextChanged = { applyFilter() })

        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_SELECT, Menu.NONE, R.string.cloud_doc_select)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_SELECT)?.isVisible = !adapter.isSelectionMode()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_SELECT -> { enterSelectionMode(); true }
        android.R.id.home -> {
            if (adapter.isSelectionMode()) exitSelectionMode() else finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (adapter.isSelectionMode()) exitSelectionMode() else super.onBackPressed()
    }

    private fun enterSelectionMode() {
        adapter.setSelectionMode(true)
        onSelectionChanged(0)
        invalidateOptionsMenu()
    }

    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        binding.bottomBar.visibility = View.GONE
        invalidateOptionsMenu()
    }

    /** Updates the bottom action bar to reflect the current selection. */
    private fun onSelectionChanged(count: Int) {
        if (!adapter.isSelectionMode()) {
            binding.bottomBar.visibility = View.GONE
            return
        }
        binding.bottomBar.visibility = View.VISIBLE
        if (setupMode) {
            binding.primaryAction.text = getString(R.string.cloud_doc_setup_start)
            binding.primaryAction.isEnabled = true
            updateSetupHeader()
        } else {
            binding.primaryAction.text = getString(R.string.cloud_doc_bulk_download, count)
            binding.primaryAction.isEnabled = count > 0
        }
    }

    /**
     * Enters the onboarding setup mode: shows the intro header, selects every scanned
     * item, and reveals the "Start syncing" bottom bar. Called once after the first scan.
     */
    private fun enterSetupMode() {
        binding.header.visibility = View.VISIBLE
        adapter.setSelectionMode(true)
        adapter.selectAll(allItems.map { it.initials })
        onSelectionChanged(adapter.getSelectedInitials().size)
        invalidateOptionsMenu()
    }

    /**
     * Computes and displays the setup-mode header: how many items (and their total
     * cloud-known size) would be uploaded vs downloaded, plus a Wi-Fi waiting note when
     * downloads are deferred until an unmetered connection.
     */
    private fun updateSetupHeader() {
        val selected = adapter.getSelectedInitials()
        val selectedItems = allItems.filter { it.initials in selected }
        val uploads = selectedItems.filter { it.localOnly }
        val downloads = selectedItems.filter { it.cloudOnly || it.updateAvailable }
        val uploadSize = Formatter.formatShortFileSize(this, uploads.sumOf { it.sizeBytes })
        val downloadSize = Formatter.formatShortFileSize(this, downloads.sumOf { it.sizeBytes })
        val totals = getString(
            R.string.cloud_doc_header_totals,
            uploads.size, uploadSize, downloads.size, downloadSize,
        )
        var text = getString(R.string.cloud_doc_setup_intro) + "\n" + totals
        if (DocumentSyncSettings.wifiOnly && CommonUtils.isMeteredNetwork) {
            text += "\n" + getString(R.string.cloud_doc_wifi_waiting)
        }
        binding.header.text = text
    }

    /**
     * Bulk action for the bottom bar. In normal (non-setup) mode it downloads every
     * selected item that is available in the cloud (cloud-only or has an update).
     */
    private fun performBulkAction() {
        val selected = adapter.getSelectedInitials()
        val toDownload = allItems.filter { it.initials in selected && (it.cloudOnly || it.updateAvailable) }
        if (toDownload.isEmpty()) {
            exitSelectionMode()
            return
        }
        runSyncAction {
            for (item in toDownload) {
                DocumentSync.downloadAndInstall(item.initials)
            }
        }
        exitSelectionMode()
    }

    /**
     * Setup-mode primary action ("Start syncing"). Performs the initial bulk sync over
     * the current selection: local-only items are pushed, cloud-only/update items are
     * downloaded. Cloud-only items the user *deselected* are blocked so they are not
     * downloaded later; deselected local-only items are simply left as-is. When done,
     * setup mode is dropped and the screen becomes the normal management view.
     */
    private fun performSetupSync() {
        // "Start syncing" is the commit point: enable document sync now (auto mode
        // defers enabling until here, so backing out of setup leaves sync off).
        DocumentSyncSettings.enabled = true
        val selected = adapter.getSelectedInitials()
        val toPush = allItems.filter { it.initials in selected && it.localOnly }
            .mapNotNull { Books.installed().getBook(it.initials) }
        val toDownload = allItems.filter { it.initials in selected && (it.cloudOnly || it.updateAvailable) }
        // Cloud-only items the user opted out of get blocked so they aren't pulled later.
        val toBlock = allItems.filter { it.initials !in selected && it.cloudOnly }

        runSyncAction {
            for (initials in toBlock.map { it.initials }) {
                DocumentSyncSettings.blockList.block(initials)
            }
            for (book in toPush) {
                DocumentSync.pushDocument(book)
            }
            for (item in toDownload) {
                DocumentSync.downloadAndInstall(item.initials)
            }
        }
        // Drop setup mode: refresh() (triggered by runSyncAction) rebuilds the list, and
        // exitSetupMode() turns the screen into the normal management view.
        exitSetupMode()
    }

    /** Leaves setup mode, restoring the plain management view (intro hidden, no selection). */
    private fun exitSetupMode() {
        setupMode = false
        binding.header.visibility = View.GONE
        exitSelectionMode()
    }

    /** Re-scans the cloud + local documents and updates the list. */
    private fun refresh() = lifecycleScope.launch {
        val hourglass = Hourglass(this@CloudDocumentsActivity)
        hourglass.show()
        try {
            allItems = withContext(Dispatchers.IO) { DocumentSync.scan() }
        } finally {
            hourglass.dismiss()
        }
        applyFilter()
        if (pendingSetup) {
            pendingSetup = false
            enterSetupMode()
        }
    }

    /** Maps the category spinner position (the @array/documentTypes order) to a BookCategory; 0 = All. */
    private fun categoryForSpinnerPosition(pos: Int): BookCategory? = when (pos) {
        1 -> BookCategory.BIBLE
        2 -> BookCategory.COMMENTARY
        3 -> BookCategory.DICTIONARY
        4 -> BookCategory.GENERAL_BOOK
        5 -> BookCategory.MAPS
        6 -> BookCategory.AND_BIBLE
        else -> null
    }

    private fun applyFilter() {
        if (adapter.isSelectionMode()) exitSelectionMode()
        val status = CloudDocFilter.entries[binding.statusSpinner.selectedItemPosition.coerceIn(0, CloudDocFilter.entries.lastIndex)]
        val category = categoryForSpinnerPosition(binding.categorySpinner.selectedItemPosition)
        val name = binding.nameSearch.text?.toString().orEmpty()
        val filtered = filterCloudDocuments(allItems, status, name, category)
        adapter.submit(filtered)
        binding.emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Builds and shows the popup menu of valid actions for the given item. */
    private fun showItemMenu(item: DocumentStatusItem, anchor: View) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        val localPresent = !item.cloudOnly
        val cloudPresent = !item.localOnly
        // Download: the cloud has a copy this device lacks or that is newer.
        if (item.cloudOnly || item.updateAvailable) {
            menu.add(0, CloudDocAction.DOWNLOAD.ordinal, 0, R.string.cloud_doc_action_download)
        }
        // Push: the document is installed locally (upload new, update, or re-push).
        if (localPresent) {
            menu.add(0, CloudDocAction.PUSH.ordinal, 1, R.string.cloud_doc_action_push)
        }
        // Remove from cloud: only meaningful when a cloud copy exists.
        if (cloudPresent) {
            menu.add(0, CloudDocAction.REMOVE_CLOUD.ordinal, 2, R.string.cloud_doc_action_remove_cloud)
        }
        if (item.blocked) {
            menu.add(0, CloudDocAction.UNBLOCK.ordinal, 3, R.string.cloud_doc_action_unblock)
        } else {
            menu.add(0, CloudDocAction.BLOCK.ordinal, 3, R.string.cloud_doc_action_block)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            val action = CloudDocAction.entries.first { it.ordinal == menuItem.itemId }
            performAction(item, action)
            true
        }
        popup.show()
    }

    private fun performAction(item: DocumentStatusItem, action: CloudDocAction) {
        when (action) {
            CloudDocAction.REMOVE_CLOUD -> confirmRemoveFromCloud(item)
            CloudDocAction.BLOCK -> {
                DocumentSyncSettings.blockList.block(item.initials)
                refresh()
            }
            CloudDocAction.UNBLOCK -> {
                DocumentSyncSettings.blockList.unblock(item.initials)
                refresh()
            }
            CloudDocAction.DOWNLOAD -> runSyncAction { DocumentSync.downloadAndInstall(item.initials) }
            CloudDocAction.PUSH -> {
                val book = Books.installed().getBook(item.initials)
                if (book != null) runSyncAction { DocumentSync.pushDocument(book) }
            }
            CloudDocAction.TOGGLE_SELECT -> { /* Selection mode added in Task 13. */ }
        }
    }

    private fun confirmRemoveFromCloud(item: DocumentStatusItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_doc_action_remove_cloud)
            .setMessage(R.string.cloud_doc_remove_confirm)
            .setPositiveButton(R.string.okay) { _, _ ->
                runSyncAction { DocumentSync.removeFromCloud(item.initials, item.name, item.type) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Runs a suspending DocumentSync action with an hourglass, then re-scans. */
    private fun runSyncAction(block: suspend () -> Unit) = lifecycleScope.launch {
        val hourglass = Hourglass(this@CloudDocumentsActivity)
        hourglass.show()
        try {
            withContext(Dispatchers.IO) { block() }
        } finally {
            hourglass.dismiss()
        }
        refresh()
    }

    companion object {
        const val EXTRA_SETUP_MODE = "setupMode"
        private const val MENU_SELECT = 1
    }
}
