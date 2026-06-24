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
import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import net.bible.service.cloudsync.documents.DocumentType

/** Per-item action that can be triggered from a row's overflow menu.
 *
 * TOGGLE_SELECT is reserved for the selection-mode task (Task 13); it is defined
 * here so the selection UI can be added without changing this enum's callers.
 */
enum class CloudDocAction { DOWNLOAD, PUSH, REMOVE_CLOUD, BLOCK, UNBLOCK, TOGGLE_SELECT }

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
) : ListAdapter<DocumentStatusItem, CloudDocumentsAdapter.ViewHolder>(DIFF_CALLBACK) {

    fun submit(items: List<DocumentStatusItem>) = submitList(items)

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
        private val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val overflow: ImageButton = itemView.findViewById(R.id.overflow)

        fun bind(item: DocumentStatusItem) {
            val context = itemView.context
            typeIcon.setImageResource(typeIconRes(item.type))
            title.text = item.name

            subtitle.text = subtitleText(item)

            statusIcon.setImageResource(statusIconRes(item))
            statusIcon.contentDescription = statusText(context, item)

            // Selection mode is added in Task 13; checkbox stays hidden for now.
            checkbox.visibility = View.GONE

            // The activity builds and shows the popup menu of valid actions for this item.
            overflow.setOnClickListener { onOverflow(item, it) }
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

        /** Status icon (single-colour vector, tinted in the layout for e-ink safety). */
        private fun statusIconRes(item: DocumentStatusItem): Int = when {
            item.blocked -> R.drawable.ic_sync_disabled_green_24dp
            item.updateAvailable -> R.drawable.ic_baseline_refresh_24
            item.cloudOnly -> R.drawable.ic_file_download_24dp
            item.localOnly -> R.drawable.ic_baseline_cloud_24
            else -> R.drawable.ic_baseline_check_circle_24
        }

        private fun typeIconRes(type: DocumentType): Int = when (type) {
            DocumentType.SWORD -> R.drawable.ic_bible_24dp
            else -> R.drawable.ic_book_24dp
        }

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DocumentStatusItem>() {
            override fun areItemsTheSame(old: DocumentStatusItem, new: DocumentStatusItem) =
                old.initials == new.initials
            override fun areContentsTheSame(old: DocumentStatusItem, new: DocumentStatusItem) = old == new
        }
    }
}
