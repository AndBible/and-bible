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
package net.bible.service.cloudsync.documents

/** A single document-sync transfer the [DocumentSyncService] queue can process. */
sealed class DocumentSyncOp {
    abstract val initials: String
    /** Upload a locally installed document to the cloud. */
    data class Push(override val initials: String) : DocumentSyncOp()
    /** Download + install a document from the cloud. */
    data class Download(override val initials: String) : DocumentSyncOp()
}

/** Builds the ordered op list for a transfer batch: all pushes first, then all downloads. */
fun buildDocumentSyncOps(pushInitials: List<String>, downloadInitials: List<String>): List<DocumentSyncOp> =
    pushInitials.map { DocumentSyncOp.Push(it) } + downloadInitials.map { DocumentSyncOp.Download(it) }

/** Notification/progress label, e.g. "KJV (2/5)". */
fun documentSyncProgressText(name: String, current: Int, total: Int): String = "$name ($current/$total)"

/** Whether an installed document should be auto-uploaded on install. */
fun shouldAutoUpload(enabled: Boolean, automatic: Boolean, blocked: Boolean, autoTransferAllowed: Boolean): Boolean =
    enabled && automatic && !blocked && autoTransferAllowed
