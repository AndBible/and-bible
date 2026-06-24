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
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
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
import org.crosswire.jsword.book.Books

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
    private var filter: Filter = Filter.ALL
    private var allItems: List<DocumentStatusItem> = emptyList()

    private enum class Filter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudDocumentsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)

        setupMode = intent.getBooleanExtra(EXTRA_SETUP_MODE, false)

        title = getString(R.string.document_sync_manage_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = CloudDocumentsAdapter(onOverflow = { item, anchor -> showItemMenu(item, anchor) })
        binding.recycler.apply {
            layoutManager = LinearLayoutManager(this@CloudDocumentsActivity)
            adapter = this@CloudDocumentsActivity.adapter
        }

        binding.filters.setOnCheckedChangeListener { _, checkedId ->
            filter = when (checkedId) {
                R.id.filterInstalled -> Filter.INSTALLED
                R.id.filterCloud -> Filter.CLOUD
                R.id.filterUpdates -> Filter.UPDATES
                R.id.filterBlocked -> Filter.BLOCKED
                else -> Filter.ALL
            }
            applyFilter()
        }

        refresh()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
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
    }

    private fun applyFilter() {
        val filtered = allItems.filter { item ->
            when (filter) {
                Filter.ALL -> true
                Filter.INSTALLED -> !item.cloudOnly
                Filter.CLOUD -> !item.localOnly
                Filter.UPDATES -> item.updateAvailable
                Filter.BLOCKED -> item.blocked
            }
        }
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
    }
}
