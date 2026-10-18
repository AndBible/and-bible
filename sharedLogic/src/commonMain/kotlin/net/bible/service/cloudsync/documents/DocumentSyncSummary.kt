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

import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem

/** What an initial document sync would transfer, used by the enable dialog. */
data class DocumentSyncSummary(
    val uploadInitials: List<String>,
    val downloadInitials: List<String>,
    val uploadBytes: Long,
    val downloadBytes: Long,
) {
    val uploadCount get() = uploadInitials.size
    val downloadCount get() = downloadInitials.size
    val isEmpty get() = uploadInitials.isEmpty() && downloadInitials.isEmpty()
}

/**
 * From a [DocumentSync.scan] result, compute uploads (local-only or local-newer-than-cloud) and
 * downloads (cloud-only or update-available), excluding [blocked] documents. The upload split
 * mirrors [resolveUploads] so the enable dialog's count matches what the first sync actually pushes
 * — a locally-newer document is pushed as an upgrade, so it must be counted here too.
 */
fun computeDocumentSyncSummary(items: List<DocumentStatusItem>, blocked: Set<String>): DocumentSyncSummary {
    val eligible = items.filterNot { it.initials in blocked }
    val uploads = eligible.filter { it.localOnly || it.localNewer }
    val downloads = eligible.filter { it.cloudOnly || it.updateAvailable }
    return DocumentSyncSummary(
        uploadInitials = uploads.map { it.initials },
        downloadInitials = downloads.map { it.initials },
        uploadBytes = uploads.sumOf { it.sizeBytes },
        downloadBytes = downloads.sumOf { it.sizeBytes },
    )
}
