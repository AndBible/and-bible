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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncService
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.documents.SyncPlan
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.cloud.CloudDocAction
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.CloudDocumentsController
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.cloud.CloudDocumentsScreen
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.BookCategory

/**
 * Compose host for the cloud documents management view — the new-path twin of classic
 * [CloudDocumentsActivity]. Wires the shared [CloudDocumentsController] seams to the real
 * `CloudSync`/`DocumentSync`/`DocumentSyncService`/`DocumentSyncSettings` services, bridges
 * `DocumentSyncProgressEvent` → a [StateFlow][kotlinx.coroutines.flow.StateFlow] on the main
 * looper via [CloudSyncProgressBridge], flattens `DocumentSync.DocumentStatusItem` → [CloudDocItem]
 * off-main, and renders [CloudDocumentsScreen]. No result contract — Cloud communicates via
 * EventBus/service.
 *
 * Tombstone contract (cross-task): the controller's ALL filter already hides `cloudDeleted`
 * tombstones and only REMOVED surfaces them, so this host feeds the FULL cache/scan list
 * (INCLUDING tombstones, `includeDeleted = true`) into [CloudDocumentsController.setItems] and gates
 * the REMOVED filter's *availability* on [DocumentSyncSettings.showRemovedDocuments] (see
 * [statusFilterLabels]) rather than pre-stripping tombstone rows.
 */
class CloudDocumentsComposeActivity : ActivityBase() {
    private val bridge = CloudSyncProgressBridge()
    /** The last resolved plan (retained so Sync-now confirm dispatches without a second resolve). */
    private var lastPlan: SyncPlan? = null

    private val controller by lazy {
        CloudDocumentsController(
            syncEnabled = { DocumentSyncSettings.enabled },
            onAction = ::handleAction,
            onBulkAction = ::handleBulkAction,
            onSyncNow = ::handleSyncNowConfirm,
            onRescan = { runSyncAction { DocumentSync.resetListingCache() } },
            onSignIn = { /* gate runs in onCreate */ },
            onOpenGate = { /* gate runs in onCreate */ },
            onShowRemovedChange = ::handleShowRemovedChange,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openOrGate()
        bridge.register()
        // Post-transfer re-scan: when running flips true→false, re-scan behind the loading bar.
        // drop(1) ignores the StateFlow's replayed initial `false` (which fires immediately at
        // collect and would otherwise trigger an ungated network scan on every open, racing the
        // gated scan in openOrGate); we only react to REAL posted DocumentSyncProgressEvents, so
        // refreshFromNetwork() runs only after a genuine transfer-completion (running=false) event.
        lifecycleScope.launch {
            bridge.running.drop(1).collect { running ->
                controller.setTransferRunning(running)
                if (!running) refreshFromNetwork()
            }
        }
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val displayed by controller.displayed.collectAsState()
                    val statusFilter by controller.statusFilter.collectAsState()
                    val categoryFilter by controller.categoryFilter.collectAsState()
                    val query by controller.query.collectAsState()
                    val selectionMode by controller.selectionMode.collectAsState()
                    val selectedIds by controller.selectedIds.collectAsState()
                    val busy by controller.busy.collectAsState()
                    val transferRunning by controller.transferRunning.collectAsState()
                    val showRemoved by controller.showRemoved.collectAsState()
                    val syncNowDialog by controller.syncNowDialog.collectAsState()

                    CloudDocumentsScreen(
                        title = getString(R.string.document_sync_manage_title),
                        loading = busy || transferRunning,
                        isRefreshing = false,
                        onRefresh = { refresh() },
                        displayed = displayed,
                        statusFilters = statusFilterLabels(showRemoved),
                        selectedStatusFilter = statusFilter,
                        categoryFilters = categoryFilterLabels(),
                        selectedCategoryFilter = categoryFilter,
                        query = query,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        syncEnabled = DocumentSyncSettings.enabled,
                        syncNowDialog = syncNowDialog,
                        topBarActions = { OverflowMenu(showRemoved) },
                        onQueryChange = controller::setQuery,
                        onStatusFilterChange = controller::setStatusFilter,
                        onCategoryFilterChange = controller::setCategoryFilter,
                        onRowClick = { if (selectionMode) controller.toggle(it.initials) },
                        onRowLongClick = { controller.enterSelection(); controller.toggle(it.initials) },
                        onRowAction = { item, action -> controller.performAction(item, action) },
                        onBulkAction = { controller.performBulk(it) },
                        onSyncNowConfirm = controller::confirmSyncNow,
                        onSyncNowDismiss = controller::dismissSyncNow,
                        onNavigateUp = { if (selectionMode) controller.clearSelection() else finish() },
                        onExitSelection = controller::clearSelection,
                    )
                }
            }
        }
    }

    override fun onDestroy() { bridge.unregister(); super.onDestroy() }

    // --- Gate + loading (ported from classic openOrGate / refresh / renderFromCache) --------
    private fun openOrGate() = lifecycleScope.launch {
        var signedIn = CloudSync.signedIn
        if (!signedIn) signedIn = CloudSync.signIn(this@CloudDocumentsComposeActivity) == true
        // Always include tombstones so they reach the controller; REMOVED filter availability (not the
        // input rows) is gated on showRemovedDocuments — see the class KDoc tombstone contract.
        val cached = withContext(Dispatchers.IO) { DocumentSync.scanCached(includeDeleted = true) }
        if (!signedIn && cached.isEmpty()) {
            Toast.makeText(this@CloudDocumentsComposeActivity, R.string.document_sync_signin_required, Toast.LENGTH_LONG).show()
            finish(); return@launch
        }
        controller.setShowRemoved(DocumentSyncSettings.showRemovedDocuments) // seed the filter-availability flag
        // Flatten off-main too: toCloudDocItem() calls Formatter.formatShortFileSize per row.
        val items = withContext(Dispatchers.IO) { cached.map { it.toCloudDocItem() } }
        controller.setItems(items)
        if (signedIn && (!DocumentSyncSettings.enabled || cached.isEmpty())) refreshFromNetwork()
    }

    private fun refresh() = lifecycleScope.launch { refreshFromNetwork() }

    private suspend fun refreshFromNetwork() {
        controller.pushBusy(true)
        try {
            val items = withContext(Dispatchers.IO) { DocumentSync.scan(includeDeleted = true).map { it.toCloudDocItem() } }
            controller.setItems(items)
        } finally { controller.pushBusy(false) }
    }

    private fun runSyncAction(block: suspend () -> Unit): kotlinx.coroutines.Job = lifecycleScope.launch {
        controller.pushBusy(true)
        try {
            withContext(Dispatchers.IO) { block() }
            val items = withContext(Dispatchers.IO) { DocumentSync.scan(includeDeleted = true).map { it.toCloudDocItem() } }
            controller.setItems(items)
        } finally { controller.pushBusy(false) }
    }

    /**
     * Persists the "show removed" preference. No re-scan is needed under the feed-all tombstone
     * model: the controller's item list already contains tombstones, so this toggle only changes
     * the REMOVED filter's availability (and the controller resets a stranded REMOVED selection to
     * ALL itself in [CloudDocumentsController.setShowRemoved]).
     */
    private fun handleShowRemovedChange(show: Boolean) {
        DocumentSyncSettings.showRemovedDocuments = show
    }

    // --- Per-item + bulk actions (ported from classic performAction / performBulkAction) ----
    private fun handleAction(action: CloudDocAction, initials: String) {
        val item = controller.items.value.firstOrNull { it.initials == initials } ?: return
        when (action) {
            CloudDocAction.DOWNLOAD -> DocumentSyncService.start(this, emptyList(), listOf(initials))
            CloudDocAction.PUSH, CloudDocAction.RESTORE -> DocumentSyncService.start(this, listOf(initials), emptyList())
            CloudDocAction.BLOCK -> { DocumentSyncSettings.blockList.block(initials); controller.setBlocked(initials, true) }
            CloudDocAction.UNBLOCK -> { DocumentSyncSettings.blockList.unblock(initials); controller.setBlocked(initials, false) }
            CloudDocAction.REMOVE_CLOUD -> confirmRemove(listOf(initials), item.name)
            CloudDocAction.PURGE -> confirmPurge(listOf(initials), item.name)
        }
    }

    private fun handleBulkAction(action: CloudDocAction, initials: List<String>) {
        when (action) {
            CloudDocAction.DOWNLOAD -> { DocumentSyncService.start(this, emptyList(), initials); controller.clearSelection() }
            CloudDocAction.PUSH, CloudDocAction.RESTORE -> { DocumentSyncService.start(this, initials, emptyList()); controller.clearSelection() }
            CloudDocAction.BLOCK -> { initials.forEach { DocumentSyncSettings.blockList.block(it); controller.setBlocked(it, true) }; controller.clearSelection() }
            CloudDocAction.UNBLOCK -> { initials.forEach { DocumentSyncSettings.blockList.unblock(it); controller.setBlocked(it, false) }; controller.clearSelection() }
            CloudDocAction.REMOVE_CLOUD -> confirmRemove(initials, null)
            CloudDocAction.PURGE -> confirmPurge(initials, null)
        }
    }

    private fun confirmRemove(initials: List<String>, name: String?) {
        val enabled = DocumentSyncSettings.enabled
        val title = if (enabled) R.string.cloud_doc_action_remove_all_devices else R.string.cloud_doc_action_remove_cloud
        val message = if (name != null) getString(if (enabled) R.string.cloud_doc_remove_all_confirm else R.string.cloud_doc_remove_cloud_confirm, name)
            else resources.getQuantityString(if (enabled) R.plurals.cloud_doc_bulk_remove_all_confirm else R.plurals.cloud_doc_bulk_remove_cloud_confirm, initials.size, initials.size)
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncService.start(this, emptyList(), emptyList(), removeInitials = initials)
                initials.forEach { controller.applyRemoval(it) }
                controller.clearSelection()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmPurge(initials: List<String>, name: String?) {
        val message = if (name != null) getString(R.string.cloud_doc_purge_confirm, name)
            else resources.getQuantityString(R.plurals.cloud_doc_bulk_purge_confirm, initials.size, initials.size)
        AlertDialog.Builder(this).setTitle(R.string.cloud_doc_action_purge).setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncService.start(this, emptyList(), emptyList(), purgeInitials = initials)
                initials.forEach { controller.applyPurge(it) }
                controller.clearSelection()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    // --- Sync now (ported from classic showSyncNowDialog / countLabel) ----------------------
    private fun showSyncNow() = lifecycleScope.launch {
        controller.pushBusy(true)
        val plan = try {
            withContext(Dispatchers.IO) { DocumentSync.computeSyncPlan(download = true, upload = true, delete = true) }
        } catch (e: Exception) {
            Toast.makeText(this@CloudDocumentsComposeActivity, R.string.sync_error, Toast.LENGTH_SHORT).show()
            return@launch
        } finally { controller.pushBusy(false) }
        lastPlan = plan
        val labels = listOf(
            getString(R.string.cloud_doc_sync_now_download) + "\n" + countLabel(plan.toDownload.size, plan.downloadBytes),
            getString(R.string.cloud_doc_sync_now_upload) + "\n" + countLabel(plan.toUpload.size, plan.uploadBytes),
            getString(R.string.cloud_doc_sync_now_delete) + "\n" + countLabel(plan.toUninstall.size, null),
        )
        val checked = listOf(DocumentSyncSettings.syncNowDownload, DocumentSyncSettings.syncNowUpload, DocumentSyncSettings.syncNowDelete)
        controller.showSyncNow(labels, checked)
    }

    private fun handleSyncNowConfirm(download: Boolean, upload: Boolean, delete: Boolean) {
        val plan = lastPlan ?: return
        DocumentSyncSettings.syncNowDownload = download
        DocumentSyncSettings.syncNowUpload = upload
        DocumentSyncSettings.syncNowDelete = delete
        DocumentSyncService.start(
            this,
            pushInitials = if (upload) plan.toUpload else emptyList(),
            downloadInitials = if (download) plan.toDownload else emptyList(),
            uninstallInitials = if (delete) plan.toUninstall else emptyList(),
        )
    }

    private fun countLabel(count: Int, bytes: Long?): String = when {
        count == 0 -> getString(R.string.cloud_doc_sync_now_count_none)
        bytes != null && bytes > 0 -> resources.getQuantityString(R.plurals.cloud_doc_sync_now_count_size, count, count, Formatter.formatShortFileSize(this, bytes))
        else -> resources.getQuantityString(R.plurals.cloud_doc_sync_now_count, count, count)
    }

    // --- Overflow menu (Sync now / Re-scan / Show removed / Help) ---------------------------
    @Composable
    private fun OverflowMenu(showRemoved: Boolean) {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) { Text("⋮") } // vertical ellipsis
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (CloudSync.signedIn) {
                DropdownMenuItem(text = { Text(getString(R.string.cloud_doc_sync_now)) }, onClick = { expanded = false; showSyncNow() })
                DropdownMenuItem(text = { Text(getString(R.string.cloud_doc_rescan)) }, onClick = { expanded = false; controller.rescan() })
                DropdownMenuItem(
                    text = { Text((if (showRemoved) "✓ " else "") + getString(R.string.cloud_doc_show_removed)) },
                    onClick = { expanded = false; controller.setShowRemoved(!showRemoved) },
                )
            }
            DropdownMenuItem(text = { Text(getString(R.string.help)) }, onClick = {
                expanded = false
                CommonUtils.showHelpDialog(
                    activity = this@CloudDocumentsComposeActivity,
                    titleResId = R.string.help,
                    messageResId = R.string.help_document_sync_text,
                    helpPath = "document_sync.html",
                )
            })
        }
    }

    // --- View-data flatten + filter labels --------------------------------------------------
    private fun DocumentSync.DocumentStatusItem.toCloudDocItem(): CloudDocItem = CloudDocItem(
        initials = initials, name = name, category = category?.toDocCategory(),
        cloudVersion = cloudVersion, localVersion = localVersion,
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = updateAvailable, localNewer = localNewer,
        blocked = blocked, canDeleteLocal = canDeleteLocal, cloudDeleted = cloudDeleted,
        sizeLabel = if (sizeBytes > 0) Formatter.formatShortFileSize(this@CloudDocumentsComposeActivity, sizeBytes) else null,
    )

    private fun BookCategory.toDocCategory(): DocCategory = when (this) {
        BookCategory.BIBLE -> DocCategory.BIBLE
        BookCategory.COMMENTARY -> DocCategory.COMMENTARY
        BookCategory.DICTIONARY -> DocCategory.DICTIONARY
        BookCategory.GENERAL_BOOK -> DocCategory.GENERAL_BOOK
        BookCategory.MAPS -> DocCategory.MAPS
        BookCategory.AND_BIBLE -> DocCategory.AND_BIBLE
        else -> DocCategory.OTHER
    }

    private fun statusFilterLabels(showRemoved: Boolean): List<Pair<CloudDocFilter, String>> = buildList {
        add(CloudDocFilter.ALL to getString(R.string.cloud_doc_filter_all))
        add(CloudDocFilter.INSTALLED to getString(R.string.cloud_doc_filter_installed))
        add(CloudDocFilter.CLOUD to getString(R.string.cloud_doc_filter_cloud))
        add(CloudDocFilter.UPDATES to getString(R.string.cloud_doc_filter_updates))
        add(CloudDocFilter.BLOCKED to getString(R.string.cloud_doc_filter_blocked))
        add(CloudDocFilter.DEVICE_ONLY to getString(R.string.cloud_doc_filter_device_only))
        add(CloudDocFilter.CLOUD_ONLY to getString(R.string.cloud_doc_filter_cloud_only))
        if (showRemoved) add(CloudDocFilter.REMOVED to getString(R.string.cloud_doc_filter_removed))
    }

    private fun categoryFilterLabels(): List<Pair<DocCategory?, String>> = listOf(
        null to getString(R.string.doc_type_all),
        DocCategory.BIBLE to getString(R.string.doc_type_bible),
        DocCategory.COMMENTARY to getString(R.string.doc_type_commentary),
        DocCategory.DICTIONARY to getString(R.string.doc_type_dictionary),
        DocCategory.GENERAL_BOOK to getString(R.string.doc_type_book),
        DocCategory.MAPS to getString(R.string.doc_type_map),
        DocCategory.AND_BIBLE to getString(R.string.doc_type_addons),
    )
}
