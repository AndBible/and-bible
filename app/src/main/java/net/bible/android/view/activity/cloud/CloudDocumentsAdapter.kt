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

/** Per-item action that can be triggered from a row's overflow menu.
 *
 * TOGGLE_SELECT is reserved for the selection-mode task (Task 13); it is defined
 * here so the selection UI can be added without changing this enum's callers.
 */
enum class CloudDocAction { DOWNLOAD, PUSH, REMOVE_CLOUD, BLOCK, UNBLOCK, RESTORE, PURGE, TOGGLE_SELECT }

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
    /** Invoked when a long-press tries to start selection but nothing is downloadable. */
    private val onNothingToDownload: () -> Unit = {},
) : ListAdapter<DocumentStatusItem, CloudDocumentsAdapter.ViewHolder>(DIFF_CALLBACK) {

    /** Bulk selection only downloads, so only cloud-only / updatable items are selectable. */
    private fun isDownloadable(item: DocumentStatusItem) = item.cloudOnly || item.updateAvailable

    /** Initials of the currently selected items (only meaningful in selection mode). */
    private val selectedInitials = mutableSetOf<String>()
    private var selectionMode = false

    fun submit(items: List<DocumentStatusItem>) {
        // A refresh can remove items or flip them to non-downloadable (e.g. just downloaded
        // elsewhere). Drop those from the selection so the bottom-bar count and the bulk action
        // stay consistent with what's actually selectable now.
        if (selectionMode) {
            val selectable = items.filter { isDownloadable(it) }.mapTo(mutableSetOf()) { it.initials }
            if (selectedInitials.retainAll(selectable)) onSelectionChanged(selectedInitials.size)
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

    /** Replaces the current selection with the given initials and redraws all rows. */
    fun selectAll(initials: Collection<String>) {
        selectedInitials.clear()
        selectedInitials.addAll(initials)
        notifyItemRangeChanged(0, itemCount)
    }

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
            typeIcon.setImageResource(item.category?.imageResource ?: R.drawable.ic_book_24dp)
            title.text = item.name

            subtitle.text = subtitleText(item)

            // In selection mode only downloadable items are selectable; already-installed /
            // synced items are dimmed. Their checkbox is INVISIBLE (not GONE) so every row stays
            // aligned — the checkbox slot is reserved for all rows in selection mode.
            val downloadable = isDownloadable(item)
            checkbox.visibility = when {
                !selectionMode -> View.GONE
                downloadable -> View.VISIBLE
                else -> View.INVISIBLE
            }
            itemView.alpha = if (selectionMode && !downloadable) 0.4f else 1f
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
                if (selectionMode && downloadable) checkbox.isChecked = !checkbox.isChecked
            }
            itemView.setOnLongClickListener {
                if (!selectionMode) {
                    if (currentList.none { isDownloadable(it) }) {
                        onNothingToDownload()
                    } else {
                        selectionMode = true
                        if (downloadable) selectedInitials.add(item.initials)
                        notifyItemRangeChanged(0, itemCount)
                        onSelectionChanged(selectedInitials.size)
                    }
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
            item.blocked -> context.getString(R.string.cloud_doc_status_blocked)
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
