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

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.android.view.activity.download.imageResource
import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem

/** Per-item action that can be triggered from a row's overflow menu. */
enum class CloudDocAction { DOWNLOAD, PUSH, REMOVE_CLOUD, BLOCK, UNBLOCK, RESTORE, PURGE }

/**
 * RecyclerView adapter for the cloud documents management list. Each row shows a
 * document type icon, title, a subtitle combining version + size + status, a status
 * icon, and an overflow button exposing the valid actions for that item's status.
 *
 * Status is always conveyed by both an icon and text (never colour alone) so the
 * list remains readable on monochrome / e-ink devices.
 */
class CloudDocumentsAdapter(
    private val onOverflow: (DocumentStatusItem, View) -> Unit,
    /** Invoked whenever the set of selected items changes (selection mode only). */
    private val onSelectionChanged: (Int) -> Unit = {},
) : ListAdapter<DocumentStatusItem, CloudDocumentsAdapter.ViewHolder>(DIFF_CALLBACK) {

    /** Initials of the currently selected items (only meaningful in selection mode). */
    private val selectedInitials = mutableSetOf<String>()
    private var selectionMode = false

    fun submit(items: List<DocumentStatusItem>) {
        // A refresh can drop rows (e.g. a document removed elsewhere). Keep in the selection only
        // the initials still present in the new list, so the CAB count stays consistent.
        if (selectionMode) {
            val present = items.mapTo(mutableSetOf()) { it.initials }
            if (selectedInitials.retainAll(present)) onSelectionChanged(selectedInitials.size)
        }
        submitList(items)
    }

    /** Toggles selection mode on/off. Leaving selection mode clears the selection. */
    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedInitials.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    fun isSelectionMode(): Boolean = selectionMode

    fun getSelectedInitials(): Set<String> = selectedInitials.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cloud_document, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeIcon: ImageView = itemView.findViewById(R.id.typeIcon)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val overflow: ImageButton = itemView.findViewById(R.id.overflow)

        fun bind(item: DocumentStatusItem) {
            // Icon by book category (Bible/Commentary/Dictionary/…), matching the
            // Download Documents list. Cloud-only items may lack a category → generic book.
            // Removed (tombstone) documents get a distinct cloud-off icon instead, reinforcing
            // the "Removed from cloud" status at a glance.
            typeIcon.setImageResource(
                if (item.cloudDeleted) R.drawable.ic_cloud_off_24dp
                else item.category?.imageResource ?: R.drawable.ic_book_24dp
            )
            title.text = item.name

            subtitle.text = subtitleText(item)

            // In selection mode every row is selectable: show a checkable box and let taps toggle it.
            checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            itemView.alpha = 1f
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = item.initials in selectedInitials
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedInitials.add(item.initials) else selectedInitials.remove(item.initials)
                onSelectionChanged(selectedInitials.size)
            }

            overflow.visibility = if (selectionMode) View.GONE else View.VISIBLE
            // The activity builds and shows the popup menu of valid actions for this item.
            overflow.setOnClickListener { onOverflow(item, it) }

            itemView.setOnClickListener {
                if (selectionMode) checkbox.isChecked = !checkbox.isChecked
            }
            itemView.setOnLongClickListener {
                if (!selectionMode) {
                    selectionMode = true
                    selectedInitials.add(item.initials)
                    notifyItemRangeChanged(0, itemCount)
                    onSelectionChanged(selectedInitials.size)
                }
                true
            }
        }

        private fun subtitleText(item: DocumentStatusItem): String {
            val context = itemView.context
            val version = item.localVersion ?: item.cloudVersion
            val parts = buildList {
                if (version != null) add(context.getString(R.string.cloud_doc_version_prefix, version))
                if (item.sizeBytes > 0) add(Formatter.formatShortFileSize(context, item.sizeBytes))
                add(statusText(context, item))
            }
            return parts.joinToString(" · ")
        }
    }

    companion object {
        /** Maps a document status to a localised, human-readable status label. */
        fun statusText(context: android.content.Context, item: DocumentStatusItem): String = when {
            item.cloudDeleted -> {
                val removed = context.getString(R.string.cloud_doc_status_removed)
                if (item.localOnly) "$removed · ${context.getString(R.string.cloud_doc_status_still_installed)}"
                else removed
            }
            item.blocked ->
                if (item.localOnly) context.getString(R.string.cloud_doc_status_wont_sync)
                else context.getString(R.string.cloud_doc_status_blocked)
            item.updateAvailable -> context.getString(R.string.cloud_doc_status_update)
            item.cloudOnly -> context.getString(R.string.cloud_doc_status_cloud_only)
            item.localOnly -> context.getString(R.string.cloud_doc_status_local_only)
            else -> context.getString(R.string.cloud_doc_status_synced)
        }

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DocumentStatusItem>() {
            override fun areItemsTheSame(old: DocumentStatusItem, new: DocumentStatusItem) =
                old.initials == new.initials
            override fun areContentsTheSame(old: DocumentStatusItem, new: DocumentStatusItem) = old == new
        }
    }
}
