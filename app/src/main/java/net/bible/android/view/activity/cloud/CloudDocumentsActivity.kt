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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.ActivityCloudDocumentsBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import net.bible.service.cloudsync.documents.DocumentSyncProgressEvent
import net.bible.service.cloudsync.documents.DocumentSyncService
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import org.crosswire.jsword.book.BookCategory

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
 * The per-item actions relevant to a document's current sync status, in display order.
 * Only meaningful actions are offered: a fully-synced item has no Push, a cloud-absent
 * item has no Download/Remove-from-cloud/Block, etc.
 */
fun documentMenuActions(
    item: DocumentSync.DocumentStatusItem,
    syncEnabled: Boolean,
): List<CloudDocAction> = buildList {
    // Download: the cloud has a copy this device lacks or that is newer.
    if (item.cloudOnly || item.updateAvailable) add(CloudDocAction.DOWNLOAD)
    // Push: not in the cloud yet, or the local copy is newer than the cloud copy.
    if (item.localOnly || item.localNewer) add(CloudDocAction.PUSH)
    // Remove: only when a cloud copy exists. With sync enabled it also deletes the local copy,
    // so suppress it when the local copy can't be deleted (e.g. the last Bible).
    if (!item.localOnly && !(syncEnabled && !item.canDeleteLocal)) add(CloudDocAction.REMOVE_CLOUD)
    // Block/unblock the per-device auto-download — only meaningful when a cloud copy exists.
    if (item.blocked) add(CloudDocAction.UNBLOCK)
    else if (!item.localOnly) add(CloudDocAction.BLOCK)
}

/**
 * The expected list state right after a remove, applied optimistically before the background
 * removal completes. With sync on (removes everywhere) or for a cloud-only item, the row drops;
 * otherwise (manual mode, local copy kept) the row becomes local-only.
 */
fun applyOptimisticRemoval(
    items: List<DocumentSync.DocumentStatusItem>,
    initials: String,
    syncEnabled: Boolean,
): List<DocumentSync.DocumentStatusItem> {
    val item = items.firstOrNull { it.initials == initials } ?: return items
    return if (syncEnabled || item.cloudOnly) {
        items.filterNot { it.initials == initials }
    } else {
        items.map {
            if (it.initials == initials)
                it.copy(cloudOnly = false, localOnly = true, cloudVersion = null, updateAvailable = false, localNewer = false)
            else it
        }
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

    private var allItems: List<DocumentStatusItem> = emptyList()

    /** Count of in-flight local async operations (scan / Sync now) driving the loading bar. */
    private var busyCount = 0
    /** Whether a foreground-service transfer is in progress (its progress events repeat). */
    private var transferRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudDocumentsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        title = getString(R.string.document_sync_manage_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = CloudDocumentsAdapter(
            onOverflow = { item, anchor -> showItemMenu(item, anchor) },
            onSelectionChanged = { count -> onSelectionChanged(count) },
            onNothingToDownload = {
                Toast.makeText(this, R.string.cloud_doc_nothing_to_download, Toast.LENGTH_SHORT).show()
            },
        )
        binding.recycler.apply {
            layoutManager = LinearLayoutManager(this@CloudDocumentsActivity)
            adapter = this@CloudDocumentsActivity.adapter
        }

        binding.primaryAction.setOnClickListener { performBulkAction() }

        val filterSelectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                applyFilter(resetSelection = true)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.statusSpinner.onItemSelectedListener = filterSelectionListener
        binding.categorySpinner.onItemSelectedListener = filterSelectionListener
        binding.nameSearch.addTextChangedListener(afterTextChanged = { applyFilter(resetSelection = true) })

        binding.swipeRefresh.setOnRefreshListener { refresh() }

        openOrGate()
        ABEventBus.register(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Selection mode is entered by long-pressing a row, so no explicit menu item is needed.
        menu.add(Menu.NONE, MENU_SYNC_NOW, Menu.NONE, R.string.cloud_doc_sync_now)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_SYNC_NOW)?.isVisible = !DocumentSyncSettings.enabled && !adapter.isSelectionMode()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        // TODO Task 6 replaces this with the operation-picker dialog
        MENU_SYNC_NOW -> { runSyncAction { DocumentSync.runSync(download = true, upload = true, delete = true, manual = true) }; true }
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

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    @Suppress("unused") // called by greenrobot EventBus on the main thread
    fun onEventMainThread(event: DocumentSyncProgressEvent) {
        // Per-document progress is shown in the foreground-service notification; in the activity
        // a plain loading indicator is enough. The service posts running=true repeatedly (once
        // per document) and running=false once, so this is a plain boolean state — NOT a counter.
        transferRunning = event.running
        updateLoadingBar()
        // When a transfer finishes, re-scan behind the same loading bar (not the swipe spinner)
        // so the indicator stays continuous across transfer → refresh.
        if (!event.running) lifecycleScope.launch { refreshFromNetwork() }
    }

    /**
     * Brackets a local async operation (scan, Sync now) with the loading bar. Balanced
     * true/false calls only; the service transfer state is tracked separately by
     * [transferRunning] because its progress events repeat. Overlapping sources keep the bar on.
     */
    private fun setBusy(busy: Boolean) {
        busyCount = (busyCount + if (busy) 1 else -1).coerceAtLeast(0)
        updateLoadingBar()
    }

    private fun updateLoadingBar() {
        binding.loadingBar.visibility = if (transferRunning || busyCount > 0) View.VISIBLE else View.GONE
    }

    /**
     * Sign-in-first access gate. Attempts sign-in when not already signed in, renders the
     * cached document list immediately, then refreshes from the network in the background.
     * If sign-in fails AND there is no cached list, shows a toast and closes the activity.
     */
    private fun openOrGate() = lifecycleScope.launch {
        var signedIn = CloudSync.signedIn
        if (!signedIn) signedIn = CloudSync.signIn(this@CloudDocumentsActivity) == true
        // Render the cached listing instantly so the view never blocks on the network.
        val cached = withContext(Dispatchers.IO) { DocumentSync.scanCached() }
        if (!signedIn && cached.isEmpty()) {
            Toast.makeText(this@CloudDocumentsActivity, R.string.document_sync_signin_required, Toast.LENGTH_LONG).show()
            finish()
            return@launch
        }
        allItems = cached
        applyFilter()
        // With automatic sync on, the sync cycle keeps the cache fresh, so trust it on open and
        // don't hit the network (the user can still pull-to-refresh). With sync off, refresh now.
        // Exception: an empty cache (e.g. a fresh device before its first sync cycle) would show
        // only local docs, so refresh from the network even when automatic sync is on.
        if (signedIn && (!DocumentSyncSettings.enabled || cached.isEmpty())) refreshFromNetwork()
    }

    /** Re-scans from the network behind the non-blocking [loadingBar], then updates the list. */
    private suspend fun refreshFromNetwork() {
        setBusy(true)
        try {
            allItems = withContext(Dispatchers.IO) { DocumentSync.scan() }
        } finally {
            setBusy(false)
        }
        applyFilter()
    }

    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        binding.bottomBar.visibility = View.GONE
        invalidateOptionsMenu()
    }

    /** Updates the bottom action bar to reflect the current selection. */
    private fun onSelectionChanged(count: Int) {
        // Selection mode is entered from the adapter (long-press), so refresh the options menu
        // here too — otherwise the overflow icon lingers in selection mode with its only item
        // (Sync now) hidden, opening an empty menu.
        invalidateOptionsMenu()
        if (!adapter.isSelectionMode()) {
            binding.bottomBar.visibility = View.GONE
            return
        }
        binding.bottomBar.visibility = View.VISIBLE
        binding.primaryAction.text = getString(R.string.cloud_doc_bulk_download, count)
        binding.primaryAction.isEnabled = count > 0
    }

    /**
     * Bulk action for the bottom bar: downloads every selected item that is available
     * in the cloud (cloud-only or has an update).
     */
    private fun performBulkAction() {
        val selected = adapter.getSelectedInitials()
        val toDownload = allItems.filter { it.initials in selected && (it.cloudOnly || it.updateAvailable) }.map { it.initials }
        DocumentSyncService.start(this, pushInitials = emptyList(), downloadInitials = toDownload)
        exitSelectionMode()
    }

    /** Re-scans the cloud + local documents and updates the list. */
    private fun refresh() {
        // post() so the spinner shows even when refresh() is triggered programmatically
        // (e.g. from a post-action callback) before the SwipeRefreshLayout has been laid out.
        binding.swipeRefresh.post { binding.swipeRefresh.isRefreshing = true }
        lifecycleScope.launch {
            try {
                allItems = withContext(Dispatchers.IO) { DocumentSync.scan() }
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
            applyFilter()
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

    /**
     * @param resetSelection exit selection mode first. True only when the user changed a filter
     *   input (a new filter makes the current selection meaningless); false for data refreshes,
     *   which must NOT silently drop the user out of selection mode and discard their picks.
     */
    private fun applyFilter(resetSelection: Boolean = false) {
        if (resetSelection && adapter.isSelectionMode()) exitSelectionMode()
        val status = CloudDocFilter.entries[binding.statusSpinner.selectedItemPosition.coerceIn(0, CloudDocFilter.entries.lastIndex)]
        val category = categoryForSpinnerPosition(binding.categorySpinner.selectedItemPosition)
        val name = binding.nameSearch.text?.toString().orEmpty()
        val filtered = filterCloudDocuments(allItems, status, name, category)
        adapter.submit(filtered)
        // emptyText overlays the (empty) recycler; the recycler stays visible so that
        // pull-to-refresh keeps working even when the list is empty.
        binding.emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Builds and shows the popup menu of the actions relevant to the given item's status. */
    private fun showItemMenu(item: DocumentStatusItem, anchor: View) {
        val popup = PopupMenu(this, anchor)
        documentMenuActions(item, DocumentSyncSettings.enabled).forEachIndexed { order, action ->
            popup.menu.add(0, action.ordinal, order, actionLabel(action))
        }
        popup.setOnMenuItemClickListener { menuItem ->
            performAction(item, CloudDocAction.entries.first { it.ordinal == menuItem.itemId })
            true
        }
        popup.show()
    }

    private fun actionLabel(action: CloudDocAction): Int = when (action) {
        CloudDocAction.DOWNLOAD -> R.string.cloud_doc_action_download
        CloudDocAction.PUSH -> R.string.cloud_doc_action_push
        // With sync on, remove deletes everywhere (incl. this device); with sync off, cloud only.
        CloudDocAction.REMOVE_CLOUD ->
            if (DocumentSyncSettings.enabled) R.string.cloud_doc_action_remove_all_devices
            else R.string.cloud_doc_action_remove_cloud
        CloudDocAction.BLOCK -> R.string.cloud_doc_action_block
        CloudDocAction.UNBLOCK -> R.string.cloud_doc_action_unblock
        CloudDocAction.TOGGLE_SELECT -> 0
    }

    private fun performAction(item: DocumentStatusItem, action: CloudDocAction) {
        when (action) {
            CloudDocAction.REMOVE_CLOUD -> confirmRemoveFromCloud(item)
            // Block/unblock only writes the local block list (a SharedPreferences set) and
            // affects nothing in the cloud, so update the row in memory instead of doing a
            // network re-scan — the action is then instant.
            CloudDocAction.BLOCK -> {
                DocumentSyncSettings.blockList.block(item.initials)
                setBlockedInList(item.initials, true)
            }
            CloudDocAction.UNBLOCK -> {
                DocumentSyncSettings.blockList.unblock(item.initials)
                setBlockedInList(item.initials, false)
            }
            CloudDocAction.DOWNLOAD -> DocumentSyncService.start(this, emptyList(), listOf(item.initials))
            CloudDocAction.PUSH -> DocumentSyncService.start(this, listOf(item.initials), emptyList())
            CloudDocAction.TOGGLE_SELECT -> { /* Selection mode added in Task 13. */ }
        }
    }

    /** Reflects a block/unblock in the in-memory list without a network re-scan. */
    private fun setBlockedInList(initials: String, blocked: Boolean) {
        allItems = allItems.map { if (it.initials == initials) it.copy(blocked = blocked) else it }
        applyFilter()
    }

    private fun confirmRemoveFromCloud(item: DocumentStatusItem) {
        val enabled = DocumentSyncSettings.enabled
        val title = if (enabled) R.string.cloud_doc_action_remove_all_devices else R.string.cloud_doc_action_remove_cloud
        val message = if (enabled) R.string.cloud_doc_remove_all_confirm else R.string.cloud_doc_remove_cloud_confirm
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(getString(message, item.name))
            .setPositiveButton(R.string.okay) { _, _ ->
                // Run via the foreground service like other transfers, so it shows a notification
                // and survives leaving the screen. removeFromCloud also deletes the local copy
                // when sync is enabled.
                DocumentSyncService.start(this, emptyList(), emptyList(), removeInitials = listOf(item.initials))
                // Optimistic update: reflect the expected end-state immediately for snappier
                // feedback. The post-completion refresh confirms (or reverts) it.
                allItems = applyOptimisticRemoval(allItems, item.initials, DocumentSyncSettings.enabled)
                applyFilter()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Runs a suspending cloud action (e.g. remove-from-cloud) in the background behind the
     * non-blocking [loadingBar], then re-scans. Does not block the UI with a modal.
     */
    private fun runSyncAction(block: suspend () -> Unit) = lifecycleScope.launch {
        setBusy(true)
        try {
            withContext(Dispatchers.IO) { block() }
            allItems = withContext(Dispatchers.IO) { DocumentSync.scan() }
        } finally {
            setBusy(false)
        }
        applyFilter()
    }

    companion object {
        private const val MENU_SYNC_NOW = 2
    }
}
