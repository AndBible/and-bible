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

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.menu.MenuBuilder
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
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.BookCategory

enum class CloudDocFilter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED, DEVICE_ONLY, CLOUD_ONLY, REMOVED }

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
            CloudDocFilter.INSTALLED -> !item.cloudOnly && !item.cloudDeleted
            CloudDocFilter.CLOUD -> !item.localOnly && !item.cloudDeleted
            CloudDocFilter.UPDATES -> item.updateAvailable && !item.cloudDeleted
            CloudDocFilter.BLOCKED -> item.blocked && !item.cloudDeleted
            CloudDocFilter.DEVICE_ONLY -> item.localOnly && !item.cloudDeleted
            CloudDocFilter.CLOUD_ONLY -> item.cloudOnly && !item.cloudDeleted
            CloudDocFilter.REMOVED -> item.cloudDeleted
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
    // A removed (tombstoned) document: the cloud archive is gone, so the only paths forward are
    // re-uploading a still-installed local copy, or purging the tombstone entirely.
    if (item.cloudDeleted) {
        if (item.localOnly) add(CloudDocAction.RESTORE)
        add(CloudDocAction.PURGE)
        return@buildList
    }
    // Download: the cloud has a copy this device lacks or that is newer.
    if (item.cloudOnly || item.updateAvailable) add(CloudDocAction.DOWNLOAD)
    // Push: not in the cloud yet, or the local copy is newer than the cloud copy.
    if (item.localOnly || item.localNewer) add(CloudDocAction.PUSH)
    // Remove: only when a cloud copy exists. With sync enabled it also deletes the local copy,
    // so suppress it when the local copy can't be deleted (e.g. the last Bible).
    if (!item.localOnly && !(syncEnabled && !item.canDeleteLocal)) add(CloudDocAction.REMOVE_CLOUD)
    // Block/unblock the per-device sync opt-out. For a local-only document this means
    // "do not sync to cloud" (the block list already excludes it from auto-upload); for a
    // cloud-backed document it also blocks auto-download to this device.
    if (item.blocked) add(CloudDocAction.UNBLOCK)
    else add(CloudDocAction.BLOCK)
}

/**
 * The string resource for a per-item action's menu label. Context-sensitive: for a local-only
 * document, Block/Unblock read as "do not sync to cloud" / "sync to cloud"; for a cloud-backed
 * document they keep the block-on-this-device wording. Remove adapts to whether sync is enabled
 * (it then deletes everywhere, otherwise cloud only).
 */
fun actionLabelRes(action: CloudDocAction, localOnly: Boolean, syncEnabled: Boolean): Int = when (action) {
    CloudDocAction.DOWNLOAD -> R.string.cloud_doc_action_download
    CloudDocAction.PUSH -> R.string.cloud_doc_action_push
    CloudDocAction.REMOVE_CLOUD ->
        if (syncEnabled) R.string.cloud_doc_action_remove_all_devices else R.string.cloud_doc_action_remove_cloud
    CloudDocAction.BLOCK ->
        if (localOnly) R.string.cloud_doc_action_dont_sync else R.string.cloud_doc_action_block
    CloudDocAction.UNBLOCK ->
        if (localOnly) R.string.cloud_doc_action_allow_sync else R.string.cloud_doc_action_unblock
    CloudDocAction.RESTORE -> R.string.cloud_doc_action_restore
    CloudDocAction.PURGE -> R.string.cloud_doc_action_purge
}

/**
 * The bulk actions offered for a multi-selection: the union of [documentMenuActions] over
 * [selected], in canonical [CloudDocAction] declaration order. An action is offered when at least
 * one selected item supports it; it then operates only on that supporting subset (see
 * [applicableInitials]). An empty selection yields no actions.
 */
fun bulkMenuActions(
    selected: List<DocumentSync.DocumentStatusItem>,
    syncEnabled: Boolean,
): List<CloudDocAction> {
    val supported = selected.flatMapTo(mutableSetOf()) { documentMenuActions(it, syncEnabled) }
    return CloudDocAction.entries.filter { it in supported }
}

/**
 * Initials of the [selected] items that support [action] — the exact subset a bulk [action] runs on.
 * Items that don't support it (e.g. a device-only row under a bulk Download) are skipped.
 */
fun applicableInitials(
    action: CloudDocAction,
    selected: List<DocumentSync.DocumentStatusItem>,
    syncEnabled: Boolean,
): List<String> =
    selected.filter { action in documentMenuActions(it, syncEnabled) }.map { it.initials }

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
 * The expected list state right after purging a tombstone (deleting the removed-document marker
 * from the cloud), applied optimistically before the background purge completes. A tombstone with
 * a local copy becomes a plain local-only row — the cloud marker is gone but the install remains;
 * a tombstone with no local copy drops out of the list entirely. The post-completion refresh
 * confirms (or reverts) it.
 */
fun applyOptimisticPurge(
    items: List<DocumentSync.DocumentStatusItem>,
    initials: String,
): List<DocumentSync.DocumentStatusItem> {
    val item = items.firstOrNull { it.initials == initials } ?: return items
    return if (item.localOnly) {
        items.map {
            if (it.initials == initials) it.copy(cloudDeleted = false, cloudVersion = null) else it
        }
    } else {
        items.filterNot { it.initials == initials }
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

    /** The active selection-mode Contextual Action Bar, or null when not in selection mode. */
    private var actionMode: ActionMode? = null

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
        )
        binding.recycler.apply {
            layoutManager = LinearLayoutManager(this@CloudDocumentsActivity)
            adapter = this@CloudDocumentsActivity.adapter
        }

        val filterSelectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                applyFilter(resetSelection = true)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        setupStatusFilter()
        binding.statusSpinner.onItemSelectedListener = filterSelectionListener
        binding.categorySpinner.onItemSelectedListener = filterSelectionListener
        binding.nameSearch.addTextChangedListener(afterTextChanged = { applyFilter(resetSelection = true) })

        binding.swipeRefresh.setOnRefreshListener { refresh() }

        // Honour the no-animations preference (default on for e-ink): a continuously animating
        // indeterminate bar causes constant screen refresh / ghosting on e-ink. Set the bar to a
        // static determinate state once, while it is still GONE — its presence then means "working"
        // and its disappearance means "done", with no motion. (Material forbids switching the
        // indeterminate mode while the indicator is visible, so this must be done up front.)
        if (CommonUtils.settings.disableAnimations) {
            binding.loadingBar.isIndeterminate = false
            binding.loadingBar.progress = 100
        }

        openOrGate()
        ABEventBus.register(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Selection mode is entered by long-pressing a row, so no explicit menu item is needed.
        // Explicit order args keep the display order: Sync now → Re-scan → Show removed → Help.
        menu.add(Menu.NONE, MENU_SYNC_NOW, 0, R.string.cloud_doc_sync_now).apply {
            setIcon(R.drawable.ic_syncdb_24dp)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        menu.add(Menu.NONE, MENU_RESCAN, 1, R.string.cloud_doc_rescan).apply {
            setIcon(R.drawable.ic_baseline_refresh_gray_24)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        menu.add(Menu.NONE, MENU_SHOW_REMOVED, 2, R.string.cloud_doc_show_removed).apply {
            setIcon(R.drawable.ic_cloud_off_24dp)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            isCheckable = true
        }
        menu.add(Menu.NONE, MENU_HELP, 3, R.string.help).apply {
            setIcon(R.drawable.ic_help_white_24dp)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    @SuppressLint("RestrictedApi")
    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        // The ActionBar overflow hides item icons by default; show them so the menu reads at a glance.
        if (menu is MenuBuilder) menu.setOptionalIconsVisible(true)
        return super.onMenuOpened(featureId, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_SYNC_NOW)?.isVisible = CloudSync.signedIn && !adapter.isSelectionMode()
        menu.findItem(MENU_SHOW_REMOVED)?.apply {
            isVisible = CloudSync.signedIn && !adapter.isSelectionMode()
            isChecked = DocumentSyncSettings.showRemovedDocuments
        }
        menu.findItem(MENU_RESCAN)?.isVisible = CloudSync.signedIn && !adapter.isSelectionMode()
        // Help is documentation, always relevant; hidden only while the selection CAB overlays the app bar.
        menu.findItem(MENU_HELP)?.isVisible = !adapter.isSelectionMode()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_SYNC_NOW -> { showSyncNowDialog(); true }
        MENU_SHOW_REMOVED -> {
            val show = !DocumentSyncSettings.showRemovedDocuments
            DocumentSyncSettings.showRemovedDocuments = show
            item.isChecked = show
            // Rebuild the spinner (adds/removes the "Removed" entry) and reset to ALL if the
            // currently-selected filter no longer exists, then re-render from the cache.
            if (!show && binding.statusSpinner.selectedItemPosition == CloudDocFilter.REMOVED.ordinal) {
                binding.statusSpinner.setSelection(CloudDocFilter.ALL.ordinal)
            }
            setupStatusFilter()
            // Tombstones are already in the cloud-listing cache, so toggling their visibility is a
            // local re-render — no network fetch, just a quick cache read behind the loading bar.
            renderFromCache()
            true
        }
        MENU_RESCAN -> {
            runSyncAction { DocumentSync.resetListingCache() }   // clears cache+watermark, then re-scans
            true
        }
        MENU_HELP -> {
            CommonUtils.showHelpDialog(
                activity = this,
                titleResId = R.string.help,
                messageResId = R.string.help_document_sync_text,
                helpPath = "document_sync.html",
            )
            true
        }
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
        val cached = withContext(Dispatchers.IO) { DocumentSync.scanCached(DocumentSyncSettings.showRemovedDocuments) }
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

    /**
     * Re-renders the list from the local cloud-listing cache only — no network — behind the
     * non-blocking [loadingBar]. Used when only the local view changes (e.g. toggling "show
     * removed documents"): the cache already holds the full cloud listing, tombstones included,
     * so there is nothing to fetch.
     */
    private fun renderFromCache() = lifecycleScope.launch {
        setBusy(true)
        try {
            allItems = withContext(Dispatchers.IO) { DocumentSync.scanCached(DocumentSyncSettings.showRemovedDocuments) }
        } finally {
            setBusy(false)
        }
        applyFilter()
    }

    /** Re-scans from the network behind the non-blocking [loadingBar], then updates the list. */
    private suspend fun refreshFromNetwork() {
        setBusy(true)
        try {
            allItems = withContext(Dispatchers.IO) { DocumentSync.scan(DocumentSyncSettings.showRemovedDocuments) }
        } finally {
            setBusy(false)
        }
        applyFilter()
    }

    /** The currently selected rows, resolved from the adapter's selected initials. */
    private fun selectedItems(): List<DocumentStatusItem> {
        val selected = adapter.getSelectedInitials()
        return allItems.filter { it.initials in selected }
    }

    private val selectionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.cloud_documents_selection, menu)
            return true
        }

        // Re-evaluated on every selection change: show only the actions at least one selected row supports.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val actions = bulkMenuActions(selectedItems(), DocumentSyncSettings.enabled)
            menu.findItem(R.id.bulk_download).isVisible = CloudDocAction.DOWNLOAD in actions
            menu.findItem(R.id.bulk_upload).isVisible = CloudDocAction.PUSH in actions
            menu.findItem(R.id.bulk_remove).isVisible = CloudDocAction.REMOVE_CLOUD in actions
            menu.findItem(R.id.bulk_dont_sync).isVisible = CloudDocAction.BLOCK in actions
            menu.findItem(R.id.bulk_allow_sync).isVisible = CloudDocAction.UNBLOCK in actions
            menu.findItem(R.id.bulk_restore).isVisible = CloudDocAction.RESTORE in actions
            menu.findItem(R.id.bulk_purge).isVisible = CloudDocAction.PURGE in actions
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean {
            val action = when (menuItem.itemId) {
                R.id.bulk_download -> CloudDocAction.DOWNLOAD
                R.id.bulk_upload -> CloudDocAction.PUSH
                R.id.bulk_remove -> CloudDocAction.REMOVE_CLOUD
                R.id.bulk_dont_sync -> CloudDocAction.BLOCK
                R.id.bulk_allow_sync -> CloudDocAction.UNBLOCK
                R.id.bulk_restore -> CloudDocAction.RESTORE
                R.id.bulk_purge -> CloudDocAction.PURGE
                else -> return false
            }
            performBulkAction(action)
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            adapter.setSelectionMode(false)
        }
    }

    private fun exitSelectionMode() {
        actionMode?.finish()
    }

    /** Starts / updates / ends the Contextual Action Bar as the selection changes. */
    private fun onSelectionChanged(count: Int) {
        if (!adapter.isSelectionMode() || count == 0) {
            actionMode?.finish()
            return
        }
        if (actionMode == null) actionMode = startSupportActionMode(selectionActionModeCallback)
        actionMode?.apply {
            title = getString(R.string.cloud_doc_selected_count, count)
            invalidate()   // re-run onPrepareActionMode for the new selection
        }
    }

    /**
     * Runs a bulk [action] over the selected rows it applies to (others are skipped). Destructive
     * actions (remove / purge) confirm first; the rest dispatch immediately. A skipped count is
     * surfaced as a brief toast.
     */
    private fun performBulkAction(action: CloudDocAction) {
        val selected = selectedItems()
        val applicable = applicableInitials(action, selected, DocumentSyncSettings.enabled)
        val skipped = selected.size - applicable.size
        if (applicable.isEmpty()) { exitSelectionMode(); return }
        when (action) {
            CloudDocAction.REMOVE_CLOUD -> confirmBulkRemove(applicable, skipped)
            CloudDocAction.PURGE -> confirmBulkPurge(applicable, skipped)
            else -> {
                dispatchBulk(action, applicable)
                reportSkipped(skipped)
                exitSelectionMode()
            }
        }
    }

    /** Dispatches a non-destructive bulk action over [initials]. */
    private fun dispatchBulk(action: CloudDocAction, initials: List<String>) {
        when (action) {
            CloudDocAction.DOWNLOAD -> DocumentSyncService.start(this, emptyList(), initials)
            // Restore is a re-push of the still-installed local copy — same engine path as Push.
            CloudDocAction.PUSH, CloudDocAction.RESTORE -> DocumentSyncService.start(this, initials, emptyList())
            CloudDocAction.BLOCK -> {
                initials.forEach { DocumentSyncSettings.blockList.block(it) }
                allItems = allItems.map { if (it.initials in initials) it.copy(blocked = true) else it }
                applyFilter()
            }
            CloudDocAction.UNBLOCK -> {
                initials.forEach { DocumentSyncSettings.blockList.unblock(it) }
                allItems = allItems.map { if (it.initials in initials) it.copy(blocked = false) else it }
                applyFilter()
            }
            else -> {}
        }
    }

    private fun confirmBulkRemove(initials: List<String>, skipped: Int) {
        val enabled = DocumentSyncSettings.enabled
        val title = if (enabled) R.string.cloud_doc_action_remove_all_devices else R.string.cloud_doc_action_remove_cloud
        val message = resources.getQuantityString(
            if (enabled) R.plurals.cloud_doc_bulk_remove_all_confirm else R.plurals.cloud_doc_bulk_remove_cloud_confirm,
            initials.size, initials.size,
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncService.start(this, emptyList(), emptyList(), removeInitials = initials)
                initials.forEach { allItems = applyOptimisticRemoval(allItems, it, enabled) }
                applyFilter()
                reportSkipped(skipped)
                exitSelectionMode()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmBulkPurge(initials: List<String>, skipped: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_doc_action_purge)
            .setMessage(resources.getQuantityString(R.plurals.cloud_doc_bulk_purge_confirm, initials.size, initials.size))
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncService.start(this, emptyList(), emptyList(), purgeInitials = initials)
                initials.forEach { allItems = applyOptimisticPurge(allItems, it) }
                applyFilter()
                reportSkipped(skipped)
                exitSelectionMode()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun reportSkipped(skipped: Int) {
        if (skipped > 0) {
            Toast.makeText(this, getString(R.string.cloud_doc_bulk_skipped, skipped), Toast.LENGTH_SHORT).show()
        }
    }

    /** Re-scans the cloud + local documents and updates the list. */
    private fun refresh() {
        // post() so the spinner shows even when refresh() is triggered programmatically
        // (e.g. from a post-action callback) before the SwipeRefreshLayout has been laid out.
        binding.swipeRefresh.post { binding.swipeRefresh.isRefreshing = true }
        lifecycleScope.launch {
            try {
                allItems = withContext(Dispatchers.IO) { DocumentSync.scan(DocumentSyncSettings.showRemovedDocuments) }
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
            applyFilter()
        }
    }

    /**
     * (Re)populates the status-filter spinner. The "Removed" entry is appended only while
     * [DocumentSyncSettings.showRemovedDocuments] is on; because REMOVED is the last CloudDocFilter
     * value, omitting it keeps the spinner-position → enum mapping correct for the other filters.
     * The current selection is preserved when still in range, otherwise reset to ALL (position 0).
     */
    private fun setupStatusFilter() {
        val labels = mutableListOf(
            getString(R.string.cloud_doc_filter_all),
            getString(R.string.cloud_doc_filter_installed),
            getString(R.string.cloud_doc_filter_cloud),
            getString(R.string.cloud_doc_filter_updates),
            getString(R.string.cloud_doc_filter_blocked),
            getString(R.string.cloud_doc_filter_device_only),
            getString(R.string.cloud_doc_filter_cloud_only),
        )
        if (DocumentSyncSettings.showRemovedDocuments) labels.add(getString(R.string.cloud_doc_filter_removed))
        val previous = binding.statusSpinner.selectedItemPosition
        binding.statusSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, labels,
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.statusSpinner.setSelection(previous.coerceIn(0, labels.lastIndex))
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
            popup.menu.add(0, action.ordinal, order, actionLabelRes(action, item.localOnly, DocumentSyncSettings.enabled))
        }
        popup.setOnMenuItemClickListener { menuItem ->
            performAction(item, CloudDocAction.entries.first { it.ordinal == menuItem.itemId })
            true
        }
        popup.show()
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
            // Restore = re-push the still-installed local copy; the tombstone is overwritten by a
            // fresh, non-deleted meta + uploaded archive (same engine path as Push).
            CloudDocAction.RESTORE -> DocumentSyncService.start(this, listOf(item.initials), emptyList())
            CloudDocAction.PURGE -> confirmPurge(item)
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

    private fun confirmPurge(item: DocumentStatusItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_doc_action_purge)
            .setMessage(getString(R.string.cloud_doc_purge_confirm, item.name))
            .setPositiveButton(R.string.okay) { _, _ ->
                // Run the purge via the foreground service like the other transfers, so it shows a
                // notification and survives leaving the screen. Reflect the expected end-state
                // optimistically for snappier feedback; the service's completion event re-scans to
                // confirm (or revert) it.
                DocumentSyncService.start(this, emptyList(), emptyList(), purgeInitials = listOf(item.initials))
                allItems = applyOptimisticPurge(allItems, item.initials)
                applyFilter()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * "Sync now" is a manual, infrequent action. It first resolves what each operation would
     * transfer (via [DocumentSync.computeSyncPlan]) and shows per-direction counts/sizes, then asks
     * which operations to run (download / upload / delete), pre-filled from the remembered last
     * choice. The chosen directions are dispatched straight to [DocumentSyncService] from the
     * resolved plan; the block list is already applied during resolution and a manual run bypasses
     * the enabled and Wi-Fi-only guards.
     */
    private fun showSyncNowDialog() = lifecycleScope.launch {
        // Resolve what each operation would actually transfer (same resolver path as the real run),
        // behind the non-blocking loading bar, so the dialog can show counts/sizes per direction.
        setBusy(true)
        val plan = try {
            withContext(Dispatchers.IO) { DocumentSync.computeSyncPlan(download = true, upload = true, delete = true) }
        } catch (e: Exception) {
            // e.g. a network failure while refreshing the cloud listing: surface it instead of
            // silently showing no dialog, mirroring the automatic sync cycle's error handling.
            Toast.makeText(this@CloudDocumentsActivity, R.string.sync_error, Toast.LENGTH_SHORT).show()
            return@launch
        } finally {
            setBusy(false)
        }
        val labels = arrayOf<CharSequence>(
            getString(R.string.cloud_doc_sync_now_download) + "\n" + countLabel(plan.toDownload.size, plan.downloadBytes),
            getString(R.string.cloud_doc_sync_now_upload) + "\n" + countLabel(plan.toUpload.size, plan.uploadBytes),
            // Removals transfer nothing measurable, so show the count only (no size).
            getString(R.string.cloud_doc_sync_now_delete) + "\n" + countLabel(plan.toUninstall.size, null),
        )
        val checked = booleanArrayOf(
            DocumentSyncSettings.syncNowDownload,
            DocumentSyncSettings.syncNowUpload,
            DocumentSyncSettings.syncNowDelete,
        )
        AlertDialog.Builder(this@CloudDocumentsActivity)
            .setTitle(R.string.cloud_doc_sync_now)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncSettings.syncNowDownload = checked[0]
                DocumentSyncSettings.syncNowUpload = checked[1]
                DocumentSyncSettings.syncNowDelete = checked[2]
                // Start the transfer directly from the already-resolved plan, including only the
                // checked directions — no second cache refresh. start() ignores empty lists.
                DocumentSyncService.start(
                    this@CloudDocumentsActivity,
                    pushInitials = if (checked[1]) plan.toUpload else emptyList(),
                    downloadInitials = if (checked[0]) plan.toDownload else emptyList(),
                    uninstallInitials = if (checked[2]) plan.toUninstall else emptyList(),
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Second-line label for a "Sync now" operation: a pluralised document count with an optional
     * size, or "nothing to transfer" when the operation has no work. [bytes] is null for removals
     * (no size) and ignored when not greater than zero (size unknown, e.g. a local-only document
     * with no declared install size).
     */
    private fun countLabel(count: Int, bytes: Long?): String = when {
        count == 0 -> getString(R.string.cloud_doc_sync_now_count_none)
        bytes != null && bytes > 0 ->
            resources.getQuantityString(R.plurals.cloud_doc_sync_now_count_size, count, count, Formatter.formatShortFileSize(this, bytes))
        else -> resources.getQuantityString(R.plurals.cloud_doc_sync_now_count, count, count)
    }

    /**
     * Runs a suspending cloud action (e.g. remove-from-cloud) in the background behind the
     * non-blocking [loadingBar], then re-scans. Does not block the UI with a modal.
     */
    private fun runSyncAction(block: suspend () -> Unit) = lifecycleScope.launch {
        setBusy(true)
        try {
            withContext(Dispatchers.IO) { block() }
            allItems = withContext(Dispatchers.IO) { DocumentSync.scan(DocumentSyncSettings.showRemovedDocuments) }
        } finally {
            setBusy(false)
        }
        applyFilter()
    }

    companion object {
        private const val MENU_SYNC_NOW = 2
        private const val MENU_SHOW_REMOVED = 3
        private const val MENU_RESCAN = 4
        private const val MENU_HELP = 5
    }
}
